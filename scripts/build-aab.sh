#!/bin/bash
# Appli Android avec ses deux packs (images, voix) livrés à l'installation : site de la branche
# donnée, AAB de débogage, APK du téléphone branché tirés de l'AAB par bundletool (comme le
# Play Store), tailles de téléchargement, puis installation par-dessus l'appli en place.
set -euo pipefail
BRANCH=${1:-main}
# Téléphone visé : DEVICE si donné, sinon le premier de « adb devices »
DEVICE=${DEVICE:-$("$HOME/Android/Sdk/platform-tools/adb" devices | awk 'NR==2 {print $1}')}
# Dossier de travail : le .apks et les journaux, hors du dépôt
S=${TMPDIR:-/tmp}/leapmultix-android
mkdir -p "$S"
APP=$(cd "$(dirname "$0")/.." && pwd)
ADB=$HOME/Android/Sdk/platform-tools/adb
BT=$HOME/Android/bundletool/bundletool-all-1.18.3.jar
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
cd "$APP"
echo "=== site ($BRANCH) $(date +%H:%M:%S)"
[ "${SKIP_SITE:-}" = 1 ] || bash scripts/bundle-www.sh "$BRANCH" | tail -4
echo "=== cap sync $(date +%H:%M:%S)"
[ "${SKIP_SITE:-}" = 1 ] || npx cap sync android 2>&1 | tail -3
echo "=== packs $(date +%H:%M:%S)"
[ "${SKIP_SITE:-}" = 1 ] || bash scripts/pack-assets.sh
echo "=== AAB $(date +%H:%M:%S)"
(cd android && ./gradlew --no-daemon --quiet -Dorg.gradle.jvmargs=-Xmx4g bundleDebug 2>&1 | grep -v "^20..-..-..T" | tail -15)
AAB=$APP/android/app/build/outputs/bundle/debug/app-debug.aab
ls -l "$AAB"
unzip -l "$AAB" | awk '{print $4}' | cut -d/ -f1 | sort | uniq -c
bash scripts/check-bundle.sh "$AAB" --debug
echo "=== APK du téléphone $(date +%H:%M:%S)"
rm -f "$S/leapmultix.apks"
"$JAVA_HOME/bin/java" -jar "$BT" build-apks --bundle="$AAB" --output="$S/leapmultix.apks" \
  --connected-device --device-id="$DEVICE" --adb="$ADB" \
  --ks="$HOME/.config/.android/debug.keystore" --ks-key-alias=androiddebugkey --ks-pass=pass:android --key-pass=pass:android
"$JAVA_HOME/bin/java" -jar "$BT" get-size total --apks="$S/leapmultix.apks" --dimensions=ALL 2>&1 | head
unzip -l "$S/leapmultix.apks"
echo "=== installation $(date +%H:%M:%S)"
"$JAVA_HOME/bin/java" -jar "$BT" install-apks --apks="$S/leapmultix.apks" --device-id="$DEVICE" --adb="$ADB"
"$ADB" -s "$DEVICE" shell pm path org.jls42.leapmultix
echo "=== fin $(date +%H:%M:%S)"
