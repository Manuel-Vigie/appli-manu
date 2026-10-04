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
