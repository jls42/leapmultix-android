#!/bin/bash
# AAB de publication pour le Play Store : site de la branche donnée, packs, bundleRelease signé
# par la clé d'envoi (propriétés Gradle du propriétaire, que ce script ne lit jamais), puis
# contrôles : numéro de version tiré du site, appli non débogable, signature présente et qui
# n'est pas la clé de débogage, taille de chaque module, contenu (scripts/check-bundle.sh).
# REVISION=1 : seconde publication d'un même site (le Play Store refuse un numéro déjà envoyé).
# Rien n'est installé sur un téléphone : l'appli des testeurs vient du Play Store.
set -euo pipefail
BRANCH=${1:-main}
REVISION=${REVISION:-0}
APP=$(cd "$(dirname "$0")/.." && pwd)
S=${TMPDIR:-/tmp}/leapmultix-android
mkdir -p "$S"
BT=$HOME/Android/bundletool/bundletool-all-1.18.3.jar
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/Android/Sdk
AAB=$APP/android/app/build/outputs/bundle/release/app-release.aab
cd "$APP"
echo "=== site ($BRANCH) $(date +%H:%M:%S)"
[ "${SKIP_SITE:-}" = 1 ] || bash scripts/bundle-www.sh "$BRANCH" | tail -4
echo "=== cap sync $(date +%H:%M:%S)"
[ "${SKIP_SITE:-}" = 1 ] || npx cap sync android 2>&1 | tail -3
echo "=== packs $(date +%H:%M:%S)"
[ "${SKIP_SITE:-}" = 1 ] || bash scripts/pack-assets.sh
echo "=== AAB de publication $(date +%H:%M:%S)"
rm -f "$AAB"
if ! (cd android && ./gradlew --no-daemon --quiet -Dorg.gradle.jvmargs=-Xmx4g \
  -PappRevision="$REVISION" bundleRelease) > "$S/release-gradle.log" 2>&1; then
  tail -30 "$S/release-gradle.log" >&2
  echo "Construction en échec (journal : $S/release-gradle.log)" >&2
  exit 1
fi
ls -l "$AAB"

echo "=== contrôles $(date +%H:%M:%S)"
manifest() { "$JAVA_HOME/bin/java" -jar "$BT" dump manifest --bundle="$AAB" --xpath="$1"; }
SITE=$(grep -oE "const VERSION = 'v[0-9]+'" www/sw.js | grep -oE '[0-9]+')
EXPECTED=$((SITE * 100 + REVISION))
CODE=$(manifest /manifest/@android:versionCode)
NAME=$(manifest /manifest/@android:versionName)
echo "versionCode $CODE, versionName $NAME (site v$SITE)"
[ "$CODE" = "$EXPECTED" ] || { echo "versionCode $CODE, attendu $EXPECTED" >&2; exit 1; }
DEBUGGABLE=$(manifest /manifest/application/@android:debuggable)
[ -z "$DEBUGGABLE" ] || [ "$DEBUGGABLE" = false ] || { echo "AAB débogable" >&2; exit 1; }
echo "non débogable"
# Taille de chaque module (fichiers décompressés) : base, images_pack, voix_pack
unzip -l "$AAB" | awk 'NF == 4 && $4 ~ "/" { split($4, p, "/"); size[p[1]] += $1 }
  END { for (m in size) if (m != "META-INF" && m != "BUNDLE-METADATA") printf "%-12s %6.1f Mo\n", m, size[m] / 1048576 }' | sort
# Signature : jarsigner et keytool la lisent sans mot de passe
if "$JAVA_HOME/bin/jarsigner" -verify "$AAB" | grep -q 'jar is unsigned'; then
  echo "AAB NON SIGNÉ, pas prêt pour le Play Store : clé d'envoi absente (README, « Publier »)" >&2
  exit 2
fi
CERT=$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$AAB")
if grep -q 'CN=Android Debug' <<< "$CERT"; then
  echo "AAB signé par la clé de débogage : le Play Store le refuse" >&2
  exit 1
fi
grep -m1 -E 'SHA256:' <<< "$CERT"
# Contenu : le jeu et rien d'autre (ni fichier de travail, ni chemin du poste, ni clé)
bash scripts/check-bundle.sh "$AAB"
echo "=== prêt à envoyer : $AAB"
