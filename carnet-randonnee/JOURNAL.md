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
