#!/bin/bash
# Après `npx cap sync android` : déplace les images et les voix du site copié dans l'appli vers
# leurs packs de ressources (Play Asset Delivery, install-time), au même chemin sous public/.
# Le module de base garde le reste (pages, code, sons, polices). Puis : ./gradlew bundleRelease.
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
move voix_pack voice
du -sh "$PUBLIC" "$ANDROID/images_pack/src" "$ANDROID/voix_pack/src"
