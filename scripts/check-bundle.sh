#!/bin/bash
# Contrôle de ce que contient un AAB (ou un APK) avant de l'envoyer ou de l'installer : le jeu,
# et rien d'autre. Refuse un fichier de travail (.env, .md, scripts, tests, cartes de sources),
# un type de fichier inattendu dans le site embarqué, un chemin du poste de construction, une
# clé ou un jeton, une adresse e-mail autre que celle du contact, une trace du dépôt, le traceur
# de mesure d'audience du site, une image d'une fête que le site embarqué dit absente.
#   scripts/check-bundle.sh <fichier .aab ou .apk> [--debug]
# --debug : build de débogage, jamais envoyé au Play Store. Gradle y laisse le chemin de
# chaque ressource (table des ressources, ~/.gradle/caches) : ces chemins sont tolérés, tout
# le reste est contrôlé de la même façon.
# Code de sortie 0 : rien d'inattendu. 1 : au moins un refus, chacun listé.
set -euo pipefail
BUNDLE=${1:?fichier .aab ou .apk attendu}
VARIANT=${2:-}
[ -f "$BUNDLE" ] || { echo "introuvable : $BUNDLE" >&2; exit 1; }
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
FAILED=0

# Affiche un refus et les 20 premières lignes de sa liste (lue sur l'entrée standard)
refuse() {
  echo "REFUSÉ : $1" >&2
  head -20 | sed 's/^/  /' >&2
  FAILED=1
}

unzip -Z1 "$BUNDLE" | grep -v '/$' > "$T/files"

# 1. Le site embarqué (assets/public) : seulement les types de fichiers du jeu, et les deux
#    licences des polices
SITE_TYPES='mp3|ogg|webp|png|js|css|wav|mp4|webm|json|html|woff2|ico'
grep -E '(^|/)assets/public/' "$T/files" \
  | grep -vE "\.($SITE_TYPES)$" \
  | grep -vE '/assets/fonts/OFL-[A-Za-z0-9]+\.txt$' > "$T/types" || true
[ -s "$T/types" ] && refuse "type de fichier inattendu dans le site embarqué" < "$T/types"

# 1 bis. Aucun traceur de mesure d'audience : l'appli ne mesure rien, le code n'y a pas sa place
grep -iE '(^|/)assets/public/.*plausible' "$T/files" > "$T/tracker" || true
[ -s "$T/tracker" ] && refuse "traceur de mesure d'audience embarqué" < "$T/tracker"

# 2. Partout : fichiers cachés, de travail, de configuration ou de secrets
WORK_FILES='(^|/)\.[^/]'
WORK_FILES+='|\.(env|md|map|sh|py|cjs|mjs|ts|lock|log|bak|orig|key|jks|keystore|pem|p12|zip|gz)$'
WORK_FILES+='|(^|/)\.?env(\.[a-z]+)?$'
WORK_FILES+='|(^|/)(CLAUDE|AGENTS|GEMINI|README|Makefile|Dockerfile)[^/]*$'
WORK_FILES+='|(^|/)(node_modules|tests?|tests-esm|analysis|scripts|docs|coverage|terraform)/'
WORK_FILES+='|(^|/)package(-lock)?\.json$|(^|/)(gradle|local|keystore)\.properties$'
grep -E "$WORK_FILES" "$T/files" > "$T/work" || true
[ -s "$T/work" ] && refuse "fichier de travail ou de configuration" < "$T/work"

# 3. Le contenu de tout ce qui n'est pas un son, une image, une vidéo ou une police : ni chemin
#    du poste, ni clé, ni jeton (recherche aussi dans les fichiers binaires : code, manifestes)
MEDIA='mp3|ogg|webp|png|wav|mp4|webm|woff2|ico'
grep -vE "\.($MEDIA)$" "$T/files" > "$T/readable"
mkdir "$T/content"
# Les noms sont passés tels quels : aucun motif de unzip ne s'y trouve (ni *, ni ?, ni [)
if grep -q '[][*?]' "$T/readable"; then
  grep '[][*?]' "$T/readable" | refuse "nom de fichier avec un caractère de motif"
else
  xargs -a "$T/readable" -d '\n' unzip -q -o "$BUNDLE" -d "$T/content"
fi
BUILD_PATHS='/home/[a-z]|/Users/[A-Za-z]|[A-Z]:[\\/]Users[\\/][A-Za-z]'
SECRETS='AKIA[0-9A-Z]{16}|aws_secret_access_key|-----BEGIN [A-Z ]*PRIVATE KEY'
SECRETS+='|xi-api-key|[A-Z][A-Z0-9_]*(API_KEY|SECRET|TOKEN|PASSWORD) *[=:] *["'"'"']?[A-Za-z0-9/+_-]{20}'
SECRETS+='|GOOGLE_APPLICATION_CREDENTIALS|sk-[A-Za-z0-9_-]{24}|AIza[0-9A-Za-z_-]{35}'
SECRETS+='|gh[pousr]_[A-Za-z0-9]{36}|xox[abp]-[A-Za-z0-9-]{20}'
(cd "$T/content" && grep -raoE ".{0,40}($SECRETS).{0,20}" . | cut -c1-200) > "$T/secrets" || true
[ -s "$T/secrets" ] && refuse "clé ou jeton" < "$T/secrets"
if [ "$VARIANT" != --debug ]; then
  (cd "$T/content" && grep -raoE ".{0,40}($BUILD_PATHS).{0,20}" . | cut -c1-200) > "$T/paths" || true
  [ -s "$T/paths" ] && refuse "chemin du poste de construction" < "$T/paths"
fi

# 4. Adresses e-mail : seulement celle du contact du jeu (les autres « @ » sont du code : un nom
#    d'image « @2x », une annotation ; d'où la liste des extensions écartées)
(cd "$T/content" && grep -rIohE '[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[a-z]{2,}' . \
  | grep -vE '\.(png|webp|js|css|json|html)$' | sort -u | grep -vxF 'contact@jls42.org') \
  > "$T/mails" || true
[ -s "$T/mails" ] && refuse "adresse e-mail autre que celle du contact" < "$T/mails"

# 5. Aucune trace du dépôt (Gradle peut y écrire l'adresse du dépôt et le commit)
VCS=$(find "$T/content" -name 'version-control-info.textproto' -print -quit)
if [ -n "$VCS" ] && grep -qE 'revision|http|git@' "$VCS"; then
  refuse "le build nomme le dépôt ou son commit (version-control-info)" < "$VCS"
fi

# 6. Fêtes que le site embarqué dit absentes (balise d'index.html, écrite par
#    scripts/drop-seasons.mjs) : aucune de leurs images ne reste, le jeu ne les demanderait jamais
INDEX=$(find "$T/content" -path '*/assets/public/index.html' -print -quit)
ABSENT=
if [ -n "$INDEX" ]; then
  ABSENT=$(grep -oE '<meta name="leapmultix-seasons-absent" content="[a-z,]*"' "$INDEX" \
    | sed -E 's/.*content="([a-z,]*)"/\1/' | tr ',' ' ' || true)
fi
for season in $ABSENT; do
  grep -E "[_-]${season}[_.-][^/]*$" "$T/files" > "$T/season" || true
  [ -s "$T/season" ] && refuse "image d'une fête que le site embarqué dit absente ($season)" < "$T/season"
done

[ "$FAILED" = 0 ] || { echo "Contenu refusé : $BUNDLE" >&2; exit 1; }
echo "contenu contrôlé : $(wc -l < "$T/files") fichiers, $(wc -l < "$T/readable") lus, rien d'inattendu"
echo "fêtes absentes : ${ABSENT:-aucune}"
awk -F. '{ print $NF }' "$T/files" | grep -E "^($SITE_TYPES)$" | sort | uniq -c | sort -rn \
  | awk '{ printf "%s %s  ", $1, $2 } END { print "" }'
