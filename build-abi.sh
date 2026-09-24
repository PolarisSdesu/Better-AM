#!/usr/bin/env bash
set -euo pipefail

# Build release APK ABI splits (arm64-v8a, armeabi-v7a) plus a universal
# ("all" ABIs) APK, then copy them into dist/ with the app version in the name.
#
# Usage: ./build-abi.sh

cd "$(dirname "$0")"

if [[ -n "${JAVA_HOME:-}" && ! -d "$JAVA_HOME" ]]; then
  echo "JAVA_HOME points to a missing JDK; trying Android Studio's JBR." >&2
  export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

./gradlew :app:assembleRelease -PabiSplits=true

version="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts)"
: "${version:=unknown}"

dist="dist"
mkdir -p "$dist"
rm -f "$dist"/BetterAM-*.apk

out="app/build/outputs/apk/release"
cp "$out/app-arm64-v8a-release.apk" "$dist/BetterAM-$version-arm64-v8a.apk"
cp "$out/app-armeabi-v7a-release.apk" "$dist/BetterAM-$version-armeabi-v7a.apk"
cp "$out/app-universal-release.apk" "$dist/BetterAM-$version-universal.apk"

echo "Built $version APKs into $dist/:"
ls -lh "$dist"/BetterAM-*.apk