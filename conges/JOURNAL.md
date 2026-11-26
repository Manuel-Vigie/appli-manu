# Journal — Congés

## Identité
Appli pour **suivre mes congés** : combien de jours il reste (un anneau par compteur : congés payés, RTT…), le prochain congé en grand, un calendrier du mois et la liste de ce qui est posé. Pensée pour le téléphone, elle marche aussi sur PC.
Version actuelle : **V10 · 26/11/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/conges/conges.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `conges.html` | L'appli entière |
| `conges-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `conges-sw.js` | Mode hors connexion : garde seulement l'enveloppe de l'appli. Contient `CACHE = 'conges-app-v10'` |
| `version.json` | Version et date lues par la pastille ↻ (voir CLAUDE.md) |
| `conges-icon-192.png`, `-512.png`, `-masque-512.png`, `conges-apple-touch-icon.png` | Icônes (soleil sur la mer) |

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements : une page ouverte comme fichier ne peut pas s'installer).
2. Menu **⋮ → Installer l'application** (ou « Ajouter à l'écran d'accueil »).

## Ce que l'appli fait
- **Compteurs** : au départ « Congés annuels » (22,5 jours), « RTT » (6) et « Ancienneté » (3), les vrais chiffres de Manuel ; nom, couleur, jours acquis et report réglables, jusqu'à 8 compteurs. Anneau = pris (plein) + prévus (clair) + reste.
- **Poser des congés** (bouton en bas, ou toucher un jour du calendrier) : compteur, dates du/au, demi-journée possible (après-midi du premier jour, matinée du dernier), note. Le nombre de jours décomptés et le solde restant s'affichent en direct. **Partager entre deux compteurs** (case dans la fenêtre) : on choisit le compteur du début du congé, le 2e compteur de la fin et le nombre de jours pris sur ce 2e compteur (par pas d'un demi-jour). Exemple : une semaine = 2 jours « Congés annuels » puis 3 jours « Ancienneté ». Le calendrier montre chaque jour dans la couleur de son compteur (jour coupé en deux couleurs si un jour est partagé), la liste montre « Congés annuels 2 j + Ancienneté 3 j ». Dans les données : `c2` (2e compteur) et `n2` (jours sur le 2e). Un congé de moins d'1 jour ne peut pas être partagé. Un congé qui chevauche un autre ou qui ne contient que des week-ends/fériés ne peut pas être enregistré ; un dépassement du solde est signalé mais permis.
- **Semaine de 4,5 jours** (Réglages › « Ta semaine de travail », ex. « 4,5 jours : vendredi à demi ») : on choisit le jour où l'on ne travaille qu'une demi-journée ; ce jour-là ne compte que pour 0,5 dans les congés (calendrier en demi-teinte). Réglage global : il s'applique aussi aux congés déjà posés. 1,5 jour = du lundi au mardi avec « Premier jour : seulement l'après-midi » (ou mardi « seulement la matinée »).
- **Calcul** : dimanches et jours fériés français (Pâques, Ascension, Pentecôte calculés) jamais comptés ; samedis comptés seulement en mode « jours ouvrables » (Réglages). « Pris » = jusqu'à aujourd'hui inclus, « prévus » = après aujourd'hui.
- **Année de congés** : civile par défaut ; réglable (ex. juin → mai) dans Réglages. Les flèches du haut passent d'une année à l'autre. Le report est saisi par année (lien « Reprendre le solde de l'année précédente » quand l'année d'avant contient des congés).
- **Calendrier du mois** (fond toujours gris clair `#d6dadf`, même si le téléphone est en mode sombre : variables CSS redéfinies sur `#calendrier`) : jours de congé en couleur (demi-journée = moitié), week-end/fériés d'un congé en teinte claire, case rouge remplie = jour férié (avec une barre de la couleur du congé en bas s'il tombe pendant un congé), cercle = aujourd'hui.
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
- 26/11/2026 — V4 : dans « Poser des congés » (et dans le choix du deuxième compteur), chaque compteur affiche son solde (« reste 22,5 j »). Cache conges-app-v4.
- 26/11/2026 — V5 : semaine de 4,5 jours (demi-journée libre au choix dans les réglages, comptée 0,5). Cache conges-app-v5.
- 26/11/2026 — V6 : jours fériés en case remplie (jaune) au lieu d'un point ; teinte des week-ends/fériés d'un congé plus marquée. Cache conges-app-v6.
- 26/11/2026 — V7 : calendrier toujours blanc (couleurs du mode clair), même quand le téléphone est en mode sombre, à la demande de Manuel. Cache conges-app-v7.
- 26/11/2026 — V8 : jours fériés du calendrier en rouge (`--ferie` #d6303f, texte blanc) au lieu du jaune. Cache conges-app-v8.
- 26/11/2026 — V9 : fond du calendrier en gris clair (`--panel` #e4e7ea, week-ends #d4d9dd) au lieu du blanc, moins éblouissant. Cache conges-app-v9.
- 26/11/2026 — V10 : bouton « Poser des congés » plus petit et plus discret (44 px, ombre légère, teinte adoucie en mode sombre) ; gris du calendrier un peu plus foncé (`--panel` #d6dadf, week-ends #c4cacf). Cache conges-app-v10.
