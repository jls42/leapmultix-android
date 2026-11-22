# Guide du dépôt LeapMultix pour Android

Le jeu web LeapMultix emballé par Capacitor 8 dans une appli Android (`org.jls42.leapmultix`),
publiée sur Google Play. Ce dépôt ne contient pas le jeu : il le prend dans le dépôt voisin et
l'emballe. Ce guide reste court ; le détail de chaque réglage est dans `README.md`, à lire avant
de toucher à un script ou à la configuration Android.

## Dépôts voisins

Ce dépôt se clone à côté des autres (`LEAPMULTIX_REPO`, `LEAPMULTIX_VOICES` pour un autre
emplacement) :

- `../leapmultix` : le jeu (public). Tout ce que l'appli affiche et dit en vient. **Avant de
  modifier le jeu, lire `../leapmultix/CLAUDE.md`** : ses règles (branche, PR, batterie de tests,
  version à monter, phrases parlées, pages juridiques) s'appliquent là-bas, pas celles d'ici.
  Ses règles par dossier (`.claude/rules/`) et ses skills ne se chargent pas depuis ce dépôt :
  pour un vrai changement du jeu, ouvrir la session dans le dépôt du jeu.
- `../leapmultix-voices` : les clips de la voix enregistrée (privé). Son fichier `.env` ne se lit
  jamais, ni par un outil ni par `cat`.
- `docs/`, `notes/` et `CLAUDE.local.md` sont ignorés par git : les notes privées y vivent,
  jamais dans un fichier suivi.

## Commandes

```bash
scripts/build-aab.sh main                 # appli d'essai : site de la branche, packs, AAB, installation
SKIP_SITE=1 scripts/build-aab.sh          # sans refaire www/ ni les packs
SANS_VOIX=1 scripts/build-aab.sh main     # sans le dépôt des voix (jamais pour une publication)
scripts/build-release.sh main             # AAB de publication, signé et contrôlé
scripts/check-bundle.sh <fichier.aab>     # contenu de l'AAB : le jeu et rien d'autre
shellcheck scripts/*.sh                   # comme la CI
(cd android && ./gradlew --no-daemon lintDebug bundleDebug)   # comme la CI
```

- La branche donnée est une référence **locale** du dépôt du jeu : la mettre à jour avant
  (`git -C ../leapmultix fetch origin main:main`, hors de la branche courante).
- Les images viennent du dossier de travail du jeu (`assets/generated-images`, non versionné) :
  s'il est en retard sur la branche, `bundle-www.sh` s'arrête et demande `npm run assets:generate`
  dans le dépôt du jeu.
- Gradle toujours en `--no-daemon`. Une construction complète écrit plus de 3 Go dans `www/` et
  `android/` : vérifier la place avant, et ne pas en lancer deux à la fois.

## Ce qui ne se change pas

- **L'adresse de l'appli** (`server` de `capacitor.config.json`, `https://localhost`) : les
  profils des joueurs sont rangés par adresse.
- **L'identifiant** `org.jls42.leapmultix` et le nom des plugins que le jeu appelle
  (`LeapMultixFiles`, `LeapMultixSpeech`) : le jeu les détecte par leur nom.
- **Le numéro de version** se tire du site embarqué (v102 donne 10200) ; il ne s'écrit pas à la
  main. Google Play refuse un numéro déjà envoyé, même d'une version retirée.
- **Une appli publiée embarque la voix enregistrée** : `build-release.sh` refuse `SANS_VOIX=1`.

## Dépôt public : rien de privé n'y entre

- Aucun secret, aucune clé, aucun mot de passe, aucun chemin ni nom du poste de travail, aucune
  adresse e-mail autre que celle du contact, aucun identifiant d'appareil, dans les fichiers
  comme dans les messages de commit et les PR.
- **Avant chaque push** : `gitleaks git --log-opts="origin/main..HEAD"`, puis relire le diff à la
  recherche de chemins personnels. Un fichier créé par un outil (`__pycache__`, journaux) ne se
  commite pas.
- **La clé d'envoi et ses mots de passe sont au propriétaire** : ne jamais lire
  `~/.gradle/gradle.properties`, ne jamais ouvrir un `.jks`, ne jamais demander ni afficher un
  mot de passe. Gradle seul s'en sert.
- Un correctif de sécurité dit ce qu'il change, jamais la faiblesse qu'il ferme.
- **Rien d'autre que le jeu dans l'appli** : `check-bundle.sh` refuse un fichier de travail, un
  chemin du poste, une clé, un traceur. Un refus se corrige à la source, il ne se contourne pas.

## Commits et PR

- Jamais de commit sur `main` : une branche (`feat/…`, `fix/…`), poussée, puis une PR fusionnée
  en squash quand la CI est verte (`mergeStateStatus` CLEAN).
- Conventional Commits, en français, à l'impératif ; aucun outil d'IA nommé dans un commit ni
  dans une PR ; pas de ligne d'attribution.
- Le titre de la PR devient le message du commit sur `main` : le tenir à jour quand le lot change.
- Un changement de `.github/`, de `CLAUDE.md` ou de `.claude/` attend l'accord du propriétaire.
- Un changement qui dépend d'une version du jeu se fusionne après elle, et le dit dans la PR.

## Vérifier : mesuré, jamais supposé

- Un script modifié se prouve par une construction complète (`build-aab.sh`) et son
  `check-bundle.sh`, pas par une relecture. La CI ne construit qu'une appli d'essai sans le jeu.
- Ce que l'appli contient se demande à l'appli : dans sa page, un `fetch` qui répond 200 ou 404
  vaut mieux que la sortie d'un script de construction.
- Un comportement du jeu se vérifie aussi dans Chrome, en simulant l'appli : pas de
  `speechSynthesis`, un faux `Capacitor.PluginHeaders` et `Capacitor.nativePromise`.
- Une vérification qui n'a pas pu se faire se dit ; elle ne se présente jamais comme faite.

## Le téléphone d'essai

- **Installer par-dessus, jamais désinstaller** : la désinstallation efface les profils des
  joueurs. L'appli du Play Store, signée par une autre clé, ne s'installe pas par-dessus l'appli
  de débogage.
- Ne saisir ni code ni mot de passe dessus, ne pas changer ses réglages, ne pas répéter les
  données personnelles vues à l'écran. Jouer sur un profil d'essai, pas sur celui d'un enfant.
- Sans l'accord du propriétaire ce jour-là, on n'y touche pas ; il parle tout haut : prévenir.
- Piloter la page de l'appli : `adb forward tcp:<port> localabstract:webview_devtools_remote_<pid>`
  (pid de l'appli), puis Puppeteer sur ce port. La redirection saute quand l'appli redémarre.
- **Écran verrouillé, Android met l'appli en sommeil** : une mesure prise à ce moment (délais,
  appels sans réponse) ne vaut rien. Mesurer écran allumé, appli au premier plan.
- La page de l'appli n'a pas `speechSynthesis` : la « voix de l'appareil » y passe par le
  plugin `LeapMultixSpeech`. Sa première phrase après le lancement attend le réveil de la
  synthèse du téléphone (environ 3 s), les suivantes non.

## Poids de l'appli

Environ 1,05 Go, dont 535 Mo de voix (trois langues, une voix chacune) et 487 Mo d'images. Les
variantes de 1024 px servent sur tablette (MultiPop, MultiGoal) et les costumes de fête servent
quatre périodes par an : les retirer se voit à l'écran, c'est un choix du propriétaire. Proposer
une autre voix dans l'appli, c'est environ 180 Mo de plus par voix.

## Publication

Marche à suivre dans `README.md` (« Publier sur le Play Store »). Le tout premier envoi d'une
appli se fait à la main dans la Play Console ; l'envoi à Google pour examen est toujours un
geste du propriétaire.
