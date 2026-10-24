#!/usr/bin/env bash
# Builds a signed debug APK without Gradle: aapt -> javac -> dx -> zipalign -> apksigner.
# Needs: JDK 17+, aapt, dx (dalvik-exchange), zipalign, apksigner, and android-34 android.jar.
set -euo pipefail
cd "$(dirname "$0")"
ANDROID_JAR="${ANDROID_JAR:-${ANDROID_HOME:-$HOME/android-sdk}/platforms/android-34/android.jar}"
DX="${DX:-/usr/lib/android-sdk/build-tools/debian/dx}"
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" \
  -F "$OUT/unsigned.apk" --min-sdk-version 31 --target-sdk-version 34

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v "^Note:" || true
[ -f "$OUT/classes/com/marquee/launcher/MainActivity.class" ] || { echo "javac failed"; exit 1; }

"$DX" --dex --min-sdk-version=26 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)

zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEYSTORE="${KEYSTORE:-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android -alias androiddebugkey \
    -dname "CN=Android Debug,O=Android,C=US" -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/marquee-launcher-debug.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/marquee-launcher-debug.apk"
echo "Built $OUT/marquee-launcher-debug.apk"
