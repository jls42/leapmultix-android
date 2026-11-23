---
name: publishing-on-google-play
description: >-
  Mène une publication de l'appli LeapMultix sur Google Play, de la construction signée à
  l'ouverture au public : AAB de publication et contrôle de son contenu, fiche Play Store en
  français, anglais et espagnol (textes, captures, vidéo, déclaration des éléments générés par
  IA), notes de version, test fermé, envoi pour examen, passage en production. À utiliser quand
  le propriétaire veut publier ou mettre à jour l'appli sur le Play Store, préparer une
  nouvelle version, modifier la fiche ou ses visuels, vérifier ce que contient l'AAB avant
  l'envoi, ou reprendre une publication en cours dans la Play Console. Pas pour une appli
  d'essai sur le téléphone : `scripts/build-aab.sh` et la section « Le téléphone d'essai » de
  `CLAUDE.md` suffisent.
---

# Publier sur Google Play

Le détail de chaque script est dans `README.md` (« Publier sur le Play Store », « Outils »,
« Réglages ») : le lire d'abord. Ce skill donne l'ordre, les contrôles, ce qui revient au
propriétaire et les pièges de la Play Console.

## Règles, toujours

- **Ce qui engage est un geste du propriétaire** : accepter des conditions, certifier,
  enregistrer une déclaration, importer l'AAB, envoyer pour examen, demander la production. La
  session prépare, remplit, relit, puis lui rend la main en disant sur quoi cliquer. Son accord
  dans la conversation vaut pour une action nommée, pas pour la suivante.
- **La clé d'envoi est à lui** : ne jamais lire `~/.gradle/gradle.properties`, ne jamais ouvrir
  un `.jks`, ne jamais voir, demander ni taper un mot de passe. Ne pas répondre à la place d'un
  gestionnaire de mots de passe qui s'ouvre sur un champ : demander au propriétaire de le
  fermer, puis relire le champ (une saisie peut y avoir été doublée).
- **Dépôt public** : les adresses de la console (elles portent l'identifiant du compte et de
  l'appli), la liste des testeurs, l'empreinte du certificat, l'état d'avancement et les
  décisions en attente vont dans `CLAUDE.local.md`, jamais dans un fichier suivi ni dans une PR.
- **Un numéro de version envoyé est consommé**, même si la version est retirée. Le numéro vient
  du site embarqué (`README.md`) : une correction après envoi demande une nouvelle version du
  site, ou `REVISION=1`.
- **Rien d'autre que le jeu dans l'appli**, pas de monétisation, pas de mesure d'audience.
- **Ne jamais cliquer** dans la console : « Acheter des traductions », « Importer un fichier »
  (fenêtre du système, qui bloque les outils), « Tout sélectionner » dans les langues,
  « Commencer » sur une offre facultative (assistant de jeu, traduction automatique de
  l'appli). Une offre inconnue se décrit au propriétaire avant tout clic.

## Procédure (copier la liste et la cocher)

```
- [ ] 1. Le site : la version à publier est en ligne
- [ ] 2. Images : identiques à celles du site en ligne
- [ ] 3. Construction signée et ses contrôles
- [ ] 4. Contenu de l'AAB, passé au crible
- [ ] 5. Fiche : textes, captures, vidéo, déclaration IA
- [ ] 6. Version dans la console : AAB, notes de version
- [ ] 7. Envoi pour examen (propriétaire), puis suivi
- [ ] 8. Test fermé, puis production
```

### 1. Le site

L'appli embarque le jeu d'une branche locale du dépôt voisin. La version publiée doit être
celle qui est en ligne sur le site : `git -C ../leapmultix fetch origin main:main` (hors de sa
branche courante), puis comparer `sw.js` en ligne et dans la branche (commandes dans le
`CLAUDE.md` du jeu, « Déploiement »). Une page juridique ou la politique de confidentialité de
l'appli modifiée dans le jeu demande de reconstruire l'appli : elle y est embarquée.

### 2. Images

Les images viennent du dossier de travail du jeu (`assets/generated-images`, non versionné).
Son générateur ne refait pas une variante quand seuls ses réglages changent : un vieux cache
peut donc mettre dans l'appli des images moins nettes que celles du site. Avant une
construction signée, comparer les variantes locales à celles du site en ligne (somme MD5 du
fichier contre l'en-tête `ETag` de la même adresse) ; retirer du cache celles qui diffèrent et
relancer `npm run assets:generate` dans le dépôt du jeu. Attendu : aucune différente, aucune
absente.

### 3. Construction signée

Place libre d'abord (plus de 3 Go écrits), aucune autre construction en cours, puis
`scripts/build-release.sh main`, en tâche de fond. Le script s'arrête de lui-même si le numéro
n'est pas celui attendu, si l'appli est débogable, si la signature manque ou est celle de
débogage (« AAB NON SIGNÉ » : la clé n'est pas configurée, c'est au propriétaire), si la voix
enregistrée n'est pas embarquée. Noter dans `CLAUDE.local.md` la taille et la somme SHA-256 du
fichier : c'est la trace de ce qui sera envoyé. **Ne pas supprimer un AAB envoyé.**

### 4. Contenu de l'AAB

`scripts/check-bundle.sh` (lancé par la construction) refuse déjà un fichier de travail, un
chemin du poste, une clé, un traceur. Avant un premier envoi, ou quand le contenu de l'appli a
changé de nature (nouveau plugin, nouvelle famille de fichiers), ajouter une lecture à la main
du fichier dézippé, sur le disque (1,2 Go) et hors du dépôt :

- la liste des fichiers, à la recherche d'un nom inattendu ;
- `gitleaks dir <dossier>` sur tout le contenu ;
- le nom d'utilisateur du poste, son dossier personnel et toute adresse e-mail, cherchés dans
  les textes et, par `strings`, dans le code compilé (`*.dex`, `*.pb`) : seule l'adresse du
  contact est attendue ;
- `exiftool -r` sur les médias : ni position, ni auteur, ni appareil ;
- les fichiers du jeu comparés à `git ls-files` du dépôt du jeu : tout ce qui n'en vient pas
  doit s'expliquer (les deux fichiers de Capacitor).

Dire au propriétaire ce qui porte son nom (mentions légales, politique, certificat d'envoi) et
ce qui porte une marque d'origine (des images générées gardent un manifeste C2PA).

### 5. Fiche

- **Textes** : `store/fiche/<fr|en|es>/` (`titre.txt` 30 caractères au plus,
  `description-courte.txt` 80, `description-complete.txt` 4 000, `notes-de-version.txt` 500).
  Un texte change dans le dépôt d'abord (PR), puis dans la console. Après la saisie, **relire
  chaque champ** : la somme SHA-256 du champ, calculée dans la page, doit être celle du fichier
  (un retour à la ligne se glisse facilement). L'anglais est celui du jeu (américain).
- **Langues** : fr-FR par défaut, en-US, es-ES. Une langue ajoutée montre les visuels de la
  langue par défaut tant qu'on ne lui en donne pas : lui téléverser les siens.
- **Captures** : `store/fiche/captures/<langue>/<telephone|tablette-7|tablette-10>/`, douze
  par dossier, huit sur la fiche (c'est le maximum), prises sur le `www/` de l'appli dans la
  langue de la fiche, hors fête. Téléphone 1080 × 1920 : la console doit l'afficher « 9:16 »,
  le format qu'elle demande pour mettre un jeu en avant. Tablettes 1200 × 1920 et 1600 × 2560.
  L'ordre, le même dans les trois langues, est celui du propriétaire (noté dans
  `CLAUDE.local.md`). Les captures d'un jeu où un personnage est tiré au sort se regardent une
  à une : ce que le propriétaire veut écarter des visuels est dans `CLAUDE.local.md`.
- **Vidéo** : une par langue, vraie capture du jeu. Outil et règles de Google dans le dépôt du
  jeu : skill `filming-store-video` (`../leapmultix/.claude/skills/`, à lancer depuis ce
  dépôt-là). Le propriétaire la met sur YouTube ; l'adresse va dans la fiche de sa langue.
- **Déclaration des éléments générés par IA** (étape « Avis » de la fiche) : c'est une
  auto-déclaration, élément par élément, et un élément déclaré porte une étiquette sur le Play
  Store. Demander au propriétaire ce qui a été généré ; une vraie capture du jeu n'est pas un
  élément généré. Toute modification des visuels referme cette fenêtre : la rouvrir et la
  recocher avant de lui rendre la main.
- Une fiche modifiée repart en examen.

### 6. La version dans la console

Le propriétaire importe l'AAB dans « Créer une release » de la piste voulue. Vérifier ensuite
ce que la console y lit (numéro, version, API minimale et cible), saisir les notes de version
dans les trois langues, relire, enregistrer en brouillon. **Les notes de version suivent la
version quand elle passe en production** : ne pas y écrire « test ». À l'étape de
prévisualisation, lire chaque avertissement et dire au propriétaire lesquels sont sans
conséquence (« aucun fichier de désobscurcissement » : l'appli n'obscurcit pas son code).

### 7. Envoi pour examen

Sur la page d'ensemble de la publication, **relire la liste des modifications avant le clic du
propriétaire** : chaque ligne doit être attendue. Un réglage activé par défaut par Google peut
s'y trouver (facteurs de forme : PC, casque) : le signaler, c'est à lui de dire s'il le garde.
Regarder aussi si la publication gérée est désactivée : dans ce cas, ce qui est approuvé est
publié aussitôt. Des vérifications automatiques tournent une quinzaine de minutes après un
enregistrement ; un envoi fait pendant ce temps part à leur fin. Après l'envoi : noter la date,
le nombre de modifications et le numéro consommé dans `CLAUDE.local.md`. En cas de refus, lire
le motif exact dans la console avant toute correction.

### 8. Test fermé, puis production

Un compte personnel récent doit faire tester l'appli par au moins 12 personnes inscrites
pendant 14 jours avant de demander la production (relire la règle en cours dans l'aide de
Google). Une fois la version acceptée, le lien d'inscription s'affiche dans l'onglet des
testeurs : le propriétaire l'envoie avec sa consigne. Tenir la liste datée des retours, lire le
rapport de pré-lancement. À l'ouverture au public : le lien de la fiche dans `README.md` (phrase
« sera ajouté ici ») et dans le dépôt du jeu.

## La Play Console pilotée par Chrome

Le propriétaire y est connecté dans son navigateur ; la session y navigue avec l'extension
Chrome, sur son invitation.

- **Lire une page** : l'outil de lecture de page ne rend rien sur la console. Lire
  `document.body.innerText` par tranches courtes, en masquant les adresses et les e-mails.
- **Cases et boutons radio** : de vrais clics ; un clic par script reste sans effet.
- **Téléverser un visuel** : déplier la rubrique, cliquer « Ajouter des composants » **de cette
  rubrique**, retrouver le champ de fichier caché qui vient d'apparaître, y envoyer **un
  fichier à la fois** en attendant une dizaine de secondes entre deux (deux envois rapprochés :
  le premier est perdu), puis relire le compteur de la rubrique (« 8/8 »). Un champ retrouvé
  avant d'avoir cliqué dans la bonne rubrique envoie le fichier dans la précédente.
- **Retirer un visuel** : survoler sa vignette, cliquer la corbeille qui apparaît. Les
  vignettes se recentrent après chaque retrait et le panneau latéral peut se refermer : refaire
  une capture d'écran avant le clic suivant plutôt que de réutiliser des coordonnées.
- **Enregistrer en brouillon** après chaque rubrique ; l'enregistrement d'une déclaration ou de
  la fiche complète reste un clic du propriétaire.
- **L'aperçu par langue** de la fiche confirme que chaque langue montre ses propres visuels.

## Tenir les notes

À chaque étape, mettre à jour `CLAUDE.local.md` : ce qui est fait (avec la date), ce qui reste
au propriétaire, les décisions en attente. Une session suivante reprend de là, pas de la
console.
