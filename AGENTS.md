# AGENTS.md

Guidance for contributors and coding agents working on Better AM.

## Project

Better AM is an LSPosed module that adds Backdrop liquid glass navigation and a
mini player to Apple Music for Android. It also has a standalone Compose manager.

- Module package: `moe.polariss.betteram`.
- Target package: `com.apple.android.music`.
- Host adaptation baseline: Apple Music 6.5.3 (1599).
- Module API: modern libxposed API 102, configured in `META-INF/xposed/`.
- Android min / target / compile SDK: 30 / 37 / 37.
- Source root: `app/src/main/java/moe/polariss/betteram/`.
- Kotlin package prefix: `moe.polariss.betteram`.

Read the current build files before changing toolchain versions. Do not assume
that another Apple Music version has the same resource IDs or view hierarchy.

## Build

Use JDK 21 and Android SDK Platform 37. Run:

```sh
./gradlew :app:assembleRelease
```

On macOS, if `JAVA_HOME` points to a missing JDK, Android Studio's JBR works:

```sh
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  ./gradlew :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`. The release build runs
R8 and resource shrinking and currently signs with the local debug key. Do not
commit a signing key or silently change the signing identity.

Keep `app/proguard-rules.pro` intact: libxposed instantiates `BetterAmModule` by
name, and the log provider/bridge cross process boundaries. Check the entry point
and keep rules if a build stops injecting.

There is no automated test suite. There is also no device connected by default;
use build and lint checks for code changes. A successful build is not device
verification. State clearly when a feature has not been verified on a device.
Do not create tests that merely repeat constants or implementation details.

## Device sessions

Device work happens only when explicitly requested, not as a default step. Do
not connect to a device, install the APK, or touch the LSPosed installation
unless the user asks for on-device verification.

When device verification is in scope, plan both APK installation and LSPosed
path synchronization. Every `adb install` may change Android's `/data/app/…`
directory. A stale LSPosed `apk_path` can silently prevent the module from
loading.

1. Install the APK with `adb install` (no `-r` flag).
2. Check that LSPosed references the newly installed module path.
3. Confirm the module is enabled and Apple Music is in scope.
4. Force-stop and relaunch Apple Music, then confirm injection in logs.

Do not modify `/data/adb` or any LSPosed database without an explicit user
request. Reading them is allowed. If direct database synchronization is
necessary and has not been authorized, explain the concrete change and obtain
authorization before installing an update that depends on it. Never treat silent
injection failure as a rendering regression.

Useful checks during a device session:

```sh
adb shell pm path moe.polariss.betteram
adb shell am force-stop com.apple.android.music
adb logcat -c
adb shell am start -W -n com.apple.android.music/.onboarding.activities.SplashActivity
adb logcat -d -s BetterAMCapture:I '*:S'
```

With both components visible, expect capture messages for `NAVIGATION` and
`MINI_PLAYER`. Zero capture messages means injection or capture needs
investigation. The manager's Hook version text comes from a structured
target-process report, stored separately from logs and matched against the
target installation. It is not a live-process health check or proof that both
renderers installed successfully.

## Source map

All paths are relative to `app/src/main/java/moe/polariss/betteram/`.

- `hook/BetterAmModule.kt`: entry point; hooks `Activity.onPostResume()`.
- `hook/GlassInstaller.kt`: host view scanning, navigation/player geometry and
  idempotent installation; keep this layer free of Compose types.
- `ui/LiquidNavigationGlass.kt`: RenderNode capture and injected Compose/Backdrop
  rendering. Keep capture padding and draw offsets consistent.
- `ui/LiquidHeader.kt`: injected Backdrop header rendering for the player.
- `ui/InjectedComposeOwner.kt`: lifecycle/saved-state/view-model owners for host
  activities that do not provide AndroidX owners.
- `ui/official/`: adapted AndroidLiquidGlass catalog components (lens, spring,
  drag, and tab rendering).
- `MainActivity.kt`: standalone manager; uses `ComponentActivity` and its owners.
- `ui/ModuleScreen.kt`: home, logs, system bar styling and Navigation 3 routes.
- `ui/SettingsPage.kt`, `settings/AppSettings.kt`: manager preferences and storage.
- `status/ModuleStatus.kt`: process-local framework service connection and scope
  checks.
- `log/`: cross-process logging and the hook report via the module's content
  provider (`LogBridge.kt`, `LogProvider.kt`, `HookReportStore.kt`).

## Manager UI

Use the Shizuku Manager layout reference already recorded in `ModuleScreen.kt`:
28dp cards, 40dp icon discs, 16dp horizontal / 20dp vertical card padding, 16sp
body titles, and compact app bars. Settings use grouped preference rows.

- Keep visible strings in Android resources. Default resources are English;
  translations are `values-b+zh+Hans`, `values-b+zh+Hant`, and `values-ja`.
- Keep resource keys and formatted arguments consistent in every locale.
- Language and appearance preferences must update immediately and persist.
- Theme changes must update status bar and navigation bar icon contrast.
- Preserve transparent system navigation and bottom safe insets.
- Manager navigation runs on Navigation 3 (`NavDisplay` + `rememberNavBackStack`).
  Destinations are the `@Serializable` `HomeRoute` / `SettingsRoute` / `LogsRoute`
  keys in `ModuleScreen.kt`, so the back stack survives rotation and process
  death; keep them serializable and keep the serialization Gradle plugin applied.
  Navigation 3 owns the system back gesture, and navigation uses the
  Navigation 3 defaults (the 700ms fade, and the predictive pop transition that
  slides the outgoing page out in the drag direction while the page underneath
  stays pinned, both matching Shizuku's platform-owned motion). At the root
  destination back is not intercepted, so it still closes the manager via
  system predictive back (`enableOnBackInvokedCallback`).
- The native `MaterialToolbar` and its overflow popup are re-themed at runtime
  in `ModuleScreen.kt` (`toolbarThemeContext`) so the scheme the Compose pages
  resolved reaches the view toolkit: `Theme.BetterAM.Toolbar.DynamicColors.*`
  on Android 12+ together with `DynamicColors.wrapContextIfAvailable`, plus the
  `Theme.BetterAM.Toolbar.BlackOverlay` overlay for the black evening theme.
  Keep the v31 variant of the theme matching its base, and keep the black
  overlay's palette in sync with the Compose black scheme.
- Show the connection loading state only for a new manager process; foreground
  returns and activity recreation reuse the last result while refreshing it.
- Preserve animated status content/height changes without restarting loading
  on periodic checks.
- Manager appearance preferences must not silently change the host app theme.

## Host integration constraints

Apple Music owns navigation, playback, and its view tree. Preserve its actions,
accessibility, and content scrolling under the glass bars.

- Find resources by name at runtime; duplicate player IDs exist in different
  subtrees, so walking the hierarchy can be necessary.
- Host views often rewrite alpha and text metrics every frame or track change.
  Reassert required values in pre-draw, writing only when they differ.
- `setText` does not reliably trigger layout listeners. Observe text in pre-draw.
- Font layout ascent/descent does not measure visible glyph bounds; keep the
  offscreen pixel-based title alignment approach.
- Determine the player's collapsed position from time-based rests, not the
  largest observed top or a count of idle frames.
- Clear the system navigation inset using `navigationBars()` and the host's
  initial bottom padding; reapply after the first layout.
- Injected Compose owners may need to be on the host `decorView`, since Compose
  resolves the window recomposer from the root. The manager already has owners.
- Lens sampling extends outside its visible shape. Keep extra capture height
  below the glass (`CAPTURE_PAD`) without moving existing draw offsets.
- Excessive lens radius folds the shape: the 46dp mini-player capsule uses 16dp.
- Do not use BlurView; the rendering design uses Backdrop vibrancy/blur/lens.

## Upstream animation

The source of truth is AndroidLiquidGlass commit
`65ab177e90e5c1d8c62e70cf7755841982da65f6`, recorded in the adapted file headers.
Read the matching upstream source before changing animation behavior; do not
reconstruct the curve from a screen recording.

Preserve upstream spring specifications unless the task explicitly changes them:
`spring(1f, 1000f)` for value/press, and `spring(0.6f, 250f)` /
`spring(0.7f, 250f)` for lens scales.

Deliberate host workarounds:

- Read raw pointer position differences; consumed `positionChange()` is zero.
- Do not consume the shared drag gesture. Place it on a tab ancestor, not a
  full-bar overlay that prevents child hit testing.
- Use synchronous finger tracking; restarting a spring on every event prevents
  it from advancing. Preserve layout offsets where required by backdrop capture.
- Funnel tap and drag release through one dispatch window to avoid duplicate
  host reselect callbacks and page flashes.
- Hold the lens until its slide arrives and the host selects the target, with a
  bounded wait for taps on the already-selected item.
- Sample the actual tab colors; do not recolor the invisible lens source row.

## Open-source hygiene

Original project code is MIT-licensed. Preserve Apache-2.0 headers, attribution,
and license files for adapted AndroidLiquidGlass code and the Gradle Wrapper.
See `third_party/README.md`; do not relabel third-party files as MIT-only.

Never commit credentials, local SDK paths, signing keys, build outputs, logs,
Apple Music APK sets, or decompiled proprietary source. Keep private host
analysis outside the repository or in gitignored local directories. Use `jadx`,
`aapt2 dump xmltree`, and `uiautomator dump` with a locally supplied target APK
when investigating host behavior; do not imply that those private inputs ship
with this repository.

Preserve unrelated user edits. Do not rewrite Git history, publish a release,
or push to a remote unless the user requested that action.