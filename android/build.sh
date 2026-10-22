#!/usr/bin/env bash
set -e

PROJ="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/usr/lib/android-sdk}}"
BUILD_TOOLS="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"

if [ -d "$PROJ/../app" ]; then
  rm -rf "$PROJ/assets/app"
  mkdir -p "$PROJ/assets"
  cp -r "$PROJ/../app" "$PROJ/assets/app"
fi

rm -rf "$PROJ/build"
mkdir -p "$PROJ/build/gen" "$PROJ/build/obj" "$PROJ/build/res" "$PROJ/out"

"$BUILD_TOOLS/aapt2" compile --dir "$PROJ/res" -o "$PROJ/build/res/resources.zip"

"$BUILD_TOOLS/aapt2" link \
  -o "$PROJ/build/unaligned.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  --java "$PROJ/build/gen" \
  -A "$PROJ/assets" \
  "$PROJ/build/res/resources.zip"

javac -source 8 -target 8 -g:none \
  -bootclasspath "$PLATFORM" \
  -classpath "$PLATFORM" \
  -d "$PROJ/build/obj" \
  "$PROJ/build/gen/com/cleanframe/tv/R.java" \
  "$PROJ/src/com/cleanframe/tv/MainActivity.java" \
  "$PROJ/src/com/cleanframe/tv/HomeAccessibilityService.java" \
  "$PROJ/src/com/cleanframe/tv/BootReceiver.java"

"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM" \
  --min-api 26 \
  --output "$PROJ/build" \
  "$PROJ/build/obj/com/cleanframe/tv/"*.class

cd "$PROJ/build"
jar uf "$PROJ/build/unaligned.apk" classes.dex

"$BUILD_TOOLS/zipalign" -f -p 4 "$PROJ/build/unaligned.apk" "$PROJ/build/aligned.apk"

if [ ! -f "$PROJ/debug.keystore" ]; then
  keytool -genkeypair \
    -keystore "$PROJ/debug.keystore" \
    -alias androiddebugkey \
    -storepass android \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=CleanFrame,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$PROJ/debug.keystore" \
  --ks-key-alias androiddebugkey \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$PROJ/out/cleanframe.apk" \
  "$PROJ/build/aligned.apk"

echo "Built APK: $PROJ/out/cleanframe.apk"
