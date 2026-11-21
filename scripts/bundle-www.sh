#!/bin/bash
# Prépare www/ de l'appli Android d'essai : le site de la branche donnée (git archive, sans
# toucher au dossier de travail), sans les sources PNG en haute définition ; puis, depuis le
# dossier de travail (non versionné), toutes les variantes WebP générées et leur carte.
# Les fichiers de la liste hors ligne (sw.js) sont tous ajoutés, PNG compris.
set -euo pipefail
# Le dépôt du jeu, à côté de celui-ci (LEAPMULTIX_REPO pour un autre emplacement)
REPO=${LEAPMULTIX_REPO:-$(cd "$(dirname "$0")/../../leapmultix" && pwd)}
BRANCH=${1:-main}
WWW=$(cd "$(dirname "$0")/.." && pwd)/www
LIST=$(mktemp)
rm -rf "$WWW"
mkdir -p "$WWW"
git -C "$REPO" archive "$BRANCH" index.html parents.html modes.html pwa.html offline.html sw.js \
  manifest.json favicon.ico favicon.png js css img assets \
  | tar -x -C "$WWW" --exclude='assets/voice/*' --exclude='assets/images/*' --exclude='img/*.png'
# Images générées : toutes les variantes WebP (pas de perte de qualité) et la carte des images
(cd "$REPO" && find assets/generated-images -type f \( -name '*.webp' -o -name 'image-map.json' \) -print0 \
  | rsync -a --from0 --files-from=- ./ "$WWW/")
# Tout ce que la liste hors ligne nomme, PNG compris (pris dans l'arbre de la branche)
git -C "$REPO" show "$BRANCH:sw.js" | grep -oE "^  '/[^']+'" | tr -d " '" | sed 's#^/##' > "$LIST"
while read -r f; do
  [ -e "$WWW/$f" ] && continue
  mkdir -p "$WWW/$(dirname "$f")"
  if git -C "$REPO" cat-file -e "$BRANCH:$f" 2>/dev/null; then git -C "$REPO" show "$BRANCH:$f" > "$WWW/$f";
  elif [ -e "$REPO/$f" ]; then cp "$REPO/$f" "$WWW/$f";
  else echo "absent : $f"; fi
done < "$LIST"
du -sh "$WWW"; du -sh "$WWW"/* | sort -h | tail -6
grep -m1 "const VERSION" "$WWW/sw.js"

# Pas de mesure d'audience dans l'appli : les blocs de Plausible partent, comme sur un site sans
# domaine (deploy.sh), pré-connexion à plausible.io comprise : aucune connexion au démarrage
sed -i '/<!-- plausible:start -->/,/<!-- plausible:end -->/d' "$WWW/index.html"
if grep -qE "rel=\"(preconnect|dns-prefetch)\" href=\"https://plausible|plausible-init" "$WWW/index.html"; then echo "Plausible encore chargé par index.html" >&2; exit 1; fi
# Le traceur lui-même reste hors de l'appli : ses deux fichiers partent, et leur ligne dans la
# liste hors ligne aussi (un fichier listé mais absent ferait échouer l'installation du service
# worker). Le bouton de refus du site, seul à pouvoir le charger, vit dans la politique du site,
# remplacée plus bas par celle de l'appli.
rm -f "$WWW/js/plausible-init.js" "$WWW/js/vendor/plausible.js"
rmdir --ignore-fail-on-non-empty "$WWW/js/vendor"
sed -i -E "/^  '\/js\/(plausible-init|vendor\/plausible)\.js',$/d" "$WWW/sw.js"
if grep -q "plausible" "$WWW/sw.js" || find "$WWW" -iname '*plausible*' | grep -q .; then echo "Traceur de Plausible encore embarqué" >&2; exit 1; fi

# Aucun paiement hors de Google Play dans l'appli (règle Paiements de Google Play) : la section
# « Soutenir le projet » de l'écran « À propos » (lien PayPal) part ; le site la garde
python3 -I - "$WWW/index.html" <<'PY'
import re, sys
path = sys.argv[1]
page = open(path, encoding="utf-8").read()
section = re.compile(
    r'\s*<div class="about-section">\s*<h2 class="section-title" data-translate="about_support_title">.*?</div>',
    re.S,
)
page, count = section.subn("", page, count=1)
if count != 1 or "paypal" in page.lower():
    sys.exit("Section de soutien (PayPal) introuvable ou encore présente dans index.html")
open(path, "w", encoding="utf-8").write(page)
PY

# Textes du site qui seraient faux dans l'appli (mesure d'audience, première visite en ligne) :
# la politique de confidentialité affichée est celle de l'appli, les mentions légales disent
# qu'elles décrivent le site, deux réponses des questions fréquentes décrivent l'appli
POLICY=$(mktemp)
git -C "$REPO" show "$BRANCH:confidentialite-android.html" > "$POLICY"
python3 -I "$(dirname "$0")/app-texts.py" "$WWW" "$POLICY"
rm -f "$POLICY"
if grep -qi "plausible analytics" "$WWW/assets/translations/"*.json; then echo "Plausible encore décrit dans les textes de l'appli" >&2; exit 1; fi

# Voix enregistrée embarquée (sans réseau) : l'adresse des voix que deploy.sh écrit sur le site,
# puis la voix par défaut de chaque langue, copiée depuis le dépôt privé des voix (le serveur
# des voix n'est pas joignable depuis l'appli)
VOICE_META_EMPTY='<meta name="leapmultix-voice-base" content="" />'
VOICE_META='<meta name="leapmultix-voice-base" content="/voice/" />'
grep -qF "$VOICE_META_EMPTY" "$WWW/index.html" || { echo "balise des voix introuvable" >&2; exit 1; }
sed -i "s#$VOICE_META_EMPTY#$VOICE_META#" "$WWW/index.html"
for lang in fr en es; do
  (cd "$REPO" && npm run -s voice:publish -- local --site "$WWW" --lang "$lang" --audience all --default-on)
done
# Les liens deviennent des copies : l'APK ne garde pas de lien symbolique
for link in "$WWW"/voice/*/*; do
  [ -L "$link" ] || continue
  src=$(readlink -f "$link"); rm "$link"; cp -r "$src" "$link"
done
du -sh "$WWW/voice" "$WWW"
