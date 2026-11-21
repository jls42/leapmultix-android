#!/bin/bash
# Après `npx cap sync android` : déplace les images et les voix du site copié dans l'appli vers
# leurs packs de ressources (Play Asset Delivery, install-time), au même chemin sous public/.
# Le module de base garde le reste (pages, code, sons, polices). Puis : ./gradlew bundleRelease.
# SANS_VOIX=1 : pas de voix enregistrée (scripts/bundle-www.sh), le pack de voix ne porte qu'une note.
set -euo pipefail
ANDROID=$(cd "$(dirname "$0")/../android" && pwd)
PUBLIC="$ANDROID/app/src/main/assets/public"
move() { # <pack> <chemin sous public/>
  local dest="$ANDROID/$1/src/main/assets/public/$2"
  [ -e "$PUBLIC/$2" ] || { echo "absent : $2" >&2; exit 1; }
  rm -rf "$dest"
  mkdir -p "$(dirname "$dest")"
  mv "$PUBLIC/$2" "$dest"
}
rm -rf "$ANDROID/images_pack/src" "$ANDROID/voix_pack/src"
move images_pack assets/generated-images
move images_pack img
if [ "${SANS_VOIX:-}" = 1 ]; then
  # Appli construite sans voix enregistrée : le pack existe toujours (il est déclaré dans le
  # build), avec un seul fichier qui le dit
  [ ! -e "$PUBLIC/voice" ] || { echo "SANS_VOIX=1, mais le site embarque des voix" >&2; exit 1; }
  mkdir -p "$ANDROID/voix_pack/src/main/assets/public/voice"
  printf '{ "voix": "aucune voix enregistrée dans cette appli (SANS_VOIX=1)" }\n' \
    > "$ANDROID/voix_pack/src/main/assets/public/voice/absente.json"
else
  move voix_pack voice
fi
du -sh "$PUBLIC" "$ANDROID/images_pack/src" "$ANDROID/voix_pack/src"
