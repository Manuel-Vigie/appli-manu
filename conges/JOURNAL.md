# Journal — Congés

## Identité
Appli pour **suivre mes congés** : combien de jours il reste (un anneau par compteur : congés payés, RTT…), le prochain congé en grand, un calendrier du mois et la liste de ce qui est posé. Pensée pour le téléphone, elle marche aussi sur PC.
Version actuelle : **V3 · 26/11/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/conges/conges.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `conges.html` | L'appli entière |
| `conges-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `conges-sw.js` | Mode hors connexion : garde seulement l'enveloppe de l'appli. Contient `CACHE = 'conges-app-v3'` |
| `version.json` | Version et date lues par la pastille ↻ (voir CLAUDE.md) |
| `conges-icon-192.png`, `-512.png`, `-masque-512.png`, `conges-apple-touch-icon.png` | Icônes (soleil sur la mer) |

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements : une page ouverte comme fichier ne peut pas s'installer).
2. Menu **⋮ → Installer l'application** (ou « Ajouter à l'écran d'accueil »).

## Ce que l'appli fait
- **Compteurs** : au départ « Congés annuels » (22,5 jours), « RTT » (6) et « Ancienneté » (3), les vrais chiffres de Manuel ; nom, couleur, jours acquis et report réglables, jusqu'à 8 compteurs. Anneau = pris (plein) + prévus (clair) + reste.
- **Poser des congés** (bouton en bas, ou toucher un jour du calendrier) : compteur, dates du/au, demi-journée possible (après-midi du premier jour, matinée du dernier), note. Le nombre de jours décomptés et le solde restant s'affichent en direct. **Partager entre deux compteurs** (case dans la fenêtre) : on choisit le compteur du début du congé, le 2e compteur de la fin et le nombre de jours pris sur ce 2e compteur (par pas d'un demi-jour). Exemple : une semaine = 2 jours « Congés annuels » puis 3 jours « Ancienneté ». Le calendrier montre chaque jour dans la couleur de son compteur (jour coupé en deux couleurs si un jour est partagé), la liste montre « Congés annuels 2 j + Ancienneté 3 j ». Dans les données : `c2` (2e compteur) et `n2` (jours sur le 2e). Un congé de moins d'1 jour ne peut pas être partagé. Un congé qui chevauche un autre ou qui ne contient que des week-ends/fériés ne peut pas être enregistré ; un dépassement du solde est signalé mais permis.
- **Calcul** : dimanches et jours fériés français (Pâques, Ascension, Pentecôte calculés) jamais comptés ; samedis comptés seulement en mode « jours ouvrables » (Réglages). « Pris » = jusqu'à aujourd'hui inclus, « prévus » = après aujourd'hui.
- **Année de congés** : civile par défaut ; réglable (ex. juin → mai) dans Réglages. Les flèches du haut passent d'une année à l'autre. Le report est saisi par année (lien « Reprendre le solde de l'année précédente » quand l'année d'avant contient des congés).
- **Calendrier du mois** : jours de congé en couleur (demi-journée = moitié), week-end/fériés d'un congé en teinte claire, point doré = férié, cercle = aujourd'hui.
- **Sauvegarde** (Réglages) : « Sauvegarder » télécharge un fichier `conges-sauvegarde-AAAA-MM-JJ.json` ; « Restaurer » le relit ; « Tout effacer » (confirmation en deux touches).

## Où sont les données
**Uniquement dans le navigateur du téléphone** (`localStorage`, clé `conges-donnees-v1`). Rien n'est envoyé en ligne et **rien n'est dans ce dépôt public**. Conséquence : vider les données de Chrome efface les congés → faire une sauvegarde de temps en temps. Les données ne se partagent pas entre téléphone et PC (passer par la sauvegarde).

## Mettre à jour
1. Modifier `conges.html`.
2. Mettre à jour `version.json` (version, date, nouveautés, `sw.cache`) puis lancer `python3 outils/poser-pastille.py conges`.
3. Sur le téléphone : toucher la pastille ↻ en haut → **Actualiser**.

## Pièges connus
- La pastille ↻ est en haut à droite : la page laisse 52 px de marge en haut pour qu'elle ne cache aucun bouton.
- Les jours fériés d'Alsace-Moselle (Vendredi saint, 26 décembre) ne sont pas pris en compte.
- Le nombre de jours acquis saisi sert aussi de valeur de départ pour les années suivantes ; les années déjà réglées gardent leur propre valeur.

## Historique
- 26/11/2026 — V1 : première version (compteurs, calendrier, liste, réglages, sauvegarde). Cache conges-app-v1.
- 26/11/2026 — V2 : compteurs de départ réglés sur les vrais chiffres de Manuel (22,5 / 6 / 3). Si l'appli avait déjà été ouverte avec les anciens compteurs de départ (25 / 0), jamais modifiés et sans congé posé, ils sont remplacés automatiquement. Cache conges-app-v2.
- 26/11/2026 — V3 : à la demande de Manuel (« différencier les annuels avec l'ancienneté »), un congé peut être partagé entre deux compteurs (jours du début sur le 1er, jours de la fin sur le 2e). Cache conges-app-v3.
