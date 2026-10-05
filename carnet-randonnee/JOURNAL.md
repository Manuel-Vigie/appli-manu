# Journal — Carnet de randonnée

## Identité
Appli de carnets de randonnée (albums photos, musique, pages). Fonctionne hors connexion (PWA).
Version actuelle : **V10 · 04/10/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/carnet-randonnee/carnet-randonnee.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `carnet-randonnee.html` | L'appli entière |
| `carnet-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `carnet-sw.js` | Mode hors connexion (cache). Contient `SHELL = 'carnet-app-V10'` |
| `carnet-icone-192.png`, `carnet-icone-512.png`, `carnet-icone-masque-512.png` | Icônes |

Le manifest, le sw et les icônes servent **uniquement** à cette appli.

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements : une appli ouverte comme fichier ne peut pas s'installer et a un stockage vide).
2. Menu **⋮ → Installer l'application**.

## Mettre à jour
1. Changer le contenu de `carnet-randonnee.html` (et la version dans `id="version-appli"`).
2. **Changer le nom `SHELL` dans `carnet-sw.js`** (V10 → V10-b, V11…) : c'est ce qui force les téléphones à recharger.
3. Sur le téléphone : toucher la pastille ↻, ou fermer et rouvrir l'appli deux fois.
Les albums ne sont pas touchés par une mise à jour.

## Où sont les données
Dans le navigateur du téléphone : IndexedDB `carnet-rando` (magasins `meta` et `media`).
**Vider les « données de sites » de Chrome efface tous les albums.** (Arrivé le 02/10/2026, récupéré grâce à un fichier de sauvegarde.)

## Sauvegarder / restaurer
- Dans l'appli, section **Sauvegarde** : « Sauvegarder TOUTE la bibliothèque (avec musique et médias) » → fichier `CarnetRando - Bibliotheque complete (N albums) AAAA-MM-JJ.json` (plus de 200 Mo possible).
- Restaurer : **Restaurer une sauvegarde** → choisir le fichier.
- Ne pas mettre ces fichiers sur GitHub (trop gros, dépôt public). Les copier sur Google Drive ou le PC.
- Ne **jamais** renommer la clé `format:"carnet-de-rando"` dans le code : les anciennes sauvegardes ne se restaureraient plus.

## Pièges connus
- Les noms de fichiers dans `carnet-sw.js` et `carnet-manifest.webmanifest` doivent suivre le nom réel du html (`./carnet-randonnee.html`). Si on renomme un fichier, corriger ces deux fichiers.
- Anciennes adresses (plus à jour) : `…/appli-manu/carnet-de-rando.html` (racine du dépôt, copie V9 encore présente) et `…/carnet-de-rando/` (supprimée).

## Test rapide avant de publier
Servir le dossier en local, ouvrir la page dans Chromium : aucune erreur d'installabilité (hors « navigation privée »), cache `carnet-app-V10` rempli. Tester aussi l'affichage à 320, 360 et 412 px de large : aucun bouton coupé ni chevauché (l'en-tête de la bibliothèque est le point sensible).

## Historique
- 28/09/2026 — V3 (zip à la racine, supprimé).
- 30/09/2026 — V8.
- 02/10/2026 — V9 : section Sauvegarde plus claire, date de dernière sauvegarde, avertissement sur l'effacement des données.
- 02/10/2026 — Rangement dans `carnet-randonnee/` (avant : fichiers à la racine). V9 placée ici le 04/10/2026.
- 04/10/2026 — V10 : en-tête de « Ma bibliothèque » corrigé (le bouton « Ouvrir » sortait de l'écran sur téléphone étroit). Les zones invisibles de changement de page sur les bords ont été vérifiées : elles ne volent pas les appuis.
- 04/10/2026 — V11 : photos rangées par date et heure de prise de vue (EXIF, sinon date dans le nom du fichier, sinon date du fichier). Automatique quand on ajoute plusieurs photos ; bouton « Trier par heure » (barre Retoucher) pour toute une sortie. Cache : carnet-app-V11.
- 04/10/2026 — V12 : étapes (Départ, Préparation, Grotte, Retour). Sur une photo (Retoucher) on touche une étape ; le bouton « Trier » regroupe par étape puis par heure. Chaque étape commence sur une nouvelle page avec un bandeau. Cache : carnet-app-V12.
- 04/10/2026 — V13 : bouton « ✨ Classer tout seul » : range par heure puis coupe aux 3 plus longues pauses = 4 étapes. Cache : carnet-app-V13.
- 04/10/2026 — V14 : bouton ▾/▴ dans la barre de Retoucher pour plier / déplier le menu (la page reste visible en grand). Cache : carnet-app-V14.
- 04/10/2026 — V15 : le menu Retoucher se déplie en entier (la page s'adapte à la vraie hauteur du menu ; le menu défile s'il est plus haut que l'écran). Cache : carnet-app-V15.
- 04/10/2026 — V16 : « ▶ Classer en regardant les photos » (Retoucher) : écran plein avec une photo à la fois et 4 gros boutons (Départ, Préparation, Grotte, Retour) ; un appui = étape donnée et photo suivante ; à la fin, rangement automatique par étape puis heure. Cache : carnet-app-V16.
- 04/10/2026 — V17 : « ✋ Déplacer cette photo ailleurs » (Retoucher) : on prend la photo, on va à la page voulue, on touche une autre photo puis « Poser avant / après ». Même sortie seulement. Cache : carnet-app-V17.
- 04/10/2026 — V18 : 7 étapes (Départ, Montée, Presque arrivé, Préparation, Grotte, Sortie de grotte, Retour), dans les boutons d'étape et l'écran « Classer en regardant ». Cache : carnet-app-V18.
- 04/10/2026 — V19 : mode « 🏷 Étiqueter les photos » plus simple : on choisit une étape dans la barre du bas, puis on touche les photos (étiquette posée sans rien redessiner) ; « Ranger l'album » trie par étape puis heure. L'ancien écran plein « Classer en regardant » est supprimé. Cache : carnet-app-V19.
- 04/10/2026 — V20 : simplification. Un seul gros bouton « ✨ Ranger les photos tout seul » dans Retoucher : tri par heure, la grotte = la série de photos sombres, avant = Départ, après = Retour (sinon coupe aux 2 plus longues pauses). Boutons d'étapes, Trier, Étiqueter masqués (code gardé). Cache : carnet-app-V20.
- 04/10/2026 — V21 : rangement automatique refait : heure + luminosité. Seuil clair/sombre automatique (Otsu) ; toute photo sombre = Grotte ; la grotte = plus gros groupe de photos sombres à la suite (pauses < 2 h) ; photos claires avant = Départ, après = Retour. Cache : carnet-app-V21.
- 04/10/2026 — V22 : classement par Claude. « 📤 Miniatures pour Claude » enregistre carnet-miniatures.json (petites images + heures) ; Claude renvoie carnet-classement.json ({format:'carnet-classement', etapes:{id:étape}, ordre:[ids]}) ; « 📥 Appliquer le classement » l'applique. Cache : carnet-app-V22.
- 04/10/2026 — V23 : le fichier de classement de Claude peut aussi contenir « cadres » : {id:{x,y,z}} (point de visée 0-100 et zoom 1-3) pour recadrer les photos. Cache : carnet-app-V23.
- 04/10/2026 — V24 : étape « Préparation » reconnue par le tri. Premier classement par Claude appliqué sur « Ma première Grotte » (99 photos) via carnet-classement.json. Cache : carnet-app-V24.
- 04/10/2026 — V25 : « Appliquer le classement » affiche maintenant une fenêtre de résultat (ou d'erreur précise) et ouvre l'album à la première photo. Cache : carnet-app-V25.
- 04/10/2026 — V26 : menu Retoucher simplifié (boutons masqués : ✨ ranger, pencher, photos par page, Replacer, + Une photo, étapes, Trier). Reste : Envoyer à Claude / Appliquer le classement, légende, − +, ‹ ›, 🗑, Déplacer, + Ajouter des photos. Cache : carnet-app-V26.
- 04/10/2026 — V27 : le choix « Photos sur cette page » (1, 2, 3, 4, auto) est de retour dans le menu Retoucher. Cache : carnet-app-V27.
- 04/10/2026 — V28 : nouveau mode « 🏷 Classer les photos » (Retoucher). Sur chaque photo de tout l'album : Départ, Parcours 1, 2, 3, Grotte, Départ des grottes, Arrivée. Barre du bas : ‹ › pages, compteur, Valider (range tout l'album par étape puis heure, boutons retirés), Annuler. Menu Retoucher réduit (Claude, Déplacer cachés). Cache : carnet-app-V28.
- 04/10 V29 : boutons Classer plus gros (plus de page qui tourne), flèches ← ↑ ↓ → et − + pour cadrer la photo choisie.
- 04/10 V30 : choix du nombre de photos par page (1 à 4, Auto) dans la barre Classer.
- 04/10 V31 : Parcours 4 et 5 ajoutés, flèches de déplacement retirées (reste − +).
- 04/10 V32 : étapes Parcours 6, Devant la grotte, Départ 1, Départ 2 ; bouton flottant 🏷 Classer / ▾ Replier toujours visible.
- 04/10 V33 : bouton 📤 Envoyer à l'accueil (bibliothèque complète en un fichier ou en dossier-lien, lecture seule).
- 04/10 V34 : Classer — étapes rangées en lignes, affichées seulement sur la photo touchée (pastille d'étape sur les autres), la page ne dépasse plus de l'écran.
- 04/10 V35 : barre du bas de Classer compactée (3 lignes), photos plus grandes.
- 05/10 V36 : Classer — un tap sur la photo n'active plus par accident un bouton d'étape (délai de sécurité).
- 05/10 V37 : accueil en plateau tournant (glisser, flèches, toucher l'album du devant pour l'ouvrir).
- 05/10 V38 : envoi bibliothèque en application installable (manifeste + programme de cache, mise à jour par renvoi du dossier).
- 05/10 V39 : envoi album par album (cases à cocher), titres des albums non choisis retirés du fichier envoyé.
- 05/10 V40 : la musique ne démarre plus tant que l'album n'est pas choisi ; changer d'album arrête l'ancienne musique.
- 05/10 V41 : plateau — chaque album est noté Album 1, Album 2… au lieu de « N sorties ».
- 05/10 V42 : fichier envoyé = lecture seule garantie (marqueur dans la page, lecture tardive de l'album embarqué) ; plus aucun bouton d'édition en haut.
- 05/10 V43 : bouton « Installer sur le bureau » dans l'application de lecture hébergée.
- 05/10 V44 : envoi Application avec vidéos — vidéos copiées sans conversion en mémoire, progression affichée.
- 05/10 V45 : mot de passe pour l'application en ligne (contenu chiffré) ; corrige l'enregistrement du programme de cache cassé depuis V43.
- 05/10 V46 : fenêtre d'envoi — tous les albums cochés à chaque ouverture, compteur « N albums sur N seront envoyés ».
- 05/10 V47 : case « fichier plus léger » (photos 1000 px) pour l'envoi application.
- 05/10 V48 : envoi « Vidéos seules » pour compléter un lien déjà en ligne.
- 05/10 V49 : fenêtre Envoyer — seul l'album du devant est choisi au départ (évite d'envoyer les deux autres par erreur).
- V50 : l'album envoyé range chaque photo dans sa propre balise (plus léger à lire) ; si le fichier reçu est illisible, un message clair s'affiche au lieu de l'appli normale.
- V51 : photo seule en pleine page (ex. portrait en mode paysage) : montrée entière au lieu d'être coupée, sauf si un cadrage a été choisi à la main.
- V52 : photo seule en pleine page : montrée entière même si elle avait un zoom/cadrage (qui la coupait).
- V53 : photo seule entière — le réglage se refait aussi quand l'image arrive en retard (chargement différé), ce qui manquait.
- V54 : deux affichages — à l'horizontale (double page) la photo seule est montrée entière ; à la verticale (une page) elle reste zoomée et remplit la page, comme avant.
- V55 : photo seule — entière à l'horizontale, zoomée à la verticale, décidé d'après l'orientation réelle de l'écran et refait à chaque rotation (tous les albums).
- V56 : vidéo plein écran — bouton « Horizontal » (téléphone en vertical, la vidéo se couche) ; « Vertical » pour revenir.
- V57 : bouton « Horizontal » — vrai plein écran + paysage verrouillé quand le téléphone l'accepte, sinon vidéo couchée par l'appli.
