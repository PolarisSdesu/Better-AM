# Third-party notices

The root MIT license covers Better AM's original code. It does not replace the
licenses of third-party code, build tooling, or dependencies.

## AndroidLiquidGlass / Backdrop

- Source: https://github.com/Kyant0/AndroidLiquidGlass
- Catalog reference commit: `65ab177e90e5c1d8c62e70cf7755841982da65f6`
- Copyright: 2025 Kyant
- License: Apache-2.0; see [AndroidLiquidGlass-LICENSE](AndroidLiquidGlass-LICENSE).
- Adapted files: `DampedDragAnimation.kt`, `DragGestureInspector.kt`,
  `InteractiveHighlight.kt`, `LiquidBottomTab.kt`, and `LiquidBottomTabs.kt`
  under `app/src/main/java/dev/polaris/betteram/ui/official/`.

These files retain upstream source references. Better AM changes include host
view integration, synchronous drag tracking, non-consuming gesture inspection,
layout-based lens positioning, and coordinated Apple Music tab dispatch. Keep
these references and the Apache-2.0 license when redistributing the adapted files.
`Coroutines.kt` is a small local frame-wait helper in the same directory.

Backdrop and shapes are also resolved as Maven dependencies; their upstream
licenses continue to apply.

## Gradle Wrapper

The generated `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`
come from Gradle and use Apache-2.0. Keep the script license headers and the
wrapper JAR's embedded notices. See [Gradle-LICENSE](Gradle-LICENSE).

## UI reference

The manager home and settings layouts refer to Shizuku Manager:
https://github.com/RikkaApps/Shizuku/tree/b844bc491f1790c72328e1a8e5b2349f8978f0ea/manager

Better AM implements the screens in Compose while using the same Android
`MaterialToolbar`/XML-menu pattern for the app bars. Shizuku's application
code, branding, and translation resources are not bundled here. The original settings and back vector paths are standard Material Icons,
distributed under Apache-2.0. The manager now uses Material Symbols Rounded.

## Dependencies

AndroidX, Jetpack Compose, libxposed, Backdrop, and other transitive dependencies
retain their respective licenses. The declarations in `app/build.gradle.kts`
and Gradle's dependency report identify the artifacts used by a particular build:

```sh
./gradlew :app:dependencies --configuration releaseRuntimeClasspath
```

## Material Symbols

- Source: https://github.com/google/material-design-icons/tree/master/symbols/web
- Copyright: Google LLC
- License: Apache-2.0; see [MaterialSymbols-LICENSE](MaterialSymbols-LICENSE).
- Assets: `app/src/main/res/drawable/ic_symbol_*.xml`.

Material Symbols Rounded 24px SVG paths are converted to Android vector
resources without changing their geometry. The back arrow supports RTL mirroring.
