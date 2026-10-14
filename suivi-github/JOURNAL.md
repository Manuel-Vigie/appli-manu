# Journal — Suivi GitHub

## Identité
Appli de suivi des dépôts de Manuel : **place restante en pourcentage**, ce qu'il y a **à nettoyer**, ce qu'il y a **à réparer**. Elle ne modifie rien : elle prépare des demandes à copier et à envoyer à Claude.
Version actuelle : **V1 · 14/10/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/suivi-github/suivi-github.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `suivi-github.html` | L'appli entière |
| `suivi-github-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `suivi-github-sw.js` | Mode hors connexion : garde seulement l'enveloppe de l'appli. Contient `CACHE = 'suivi-github-app-v1'` |
| `version.json` | Version et date lues par la pastille ↻ (voir CLAUDE.md) |
| `suivi-github-icon-192.png`, `-512.png`, `-masque-512.png`, `-apple-touch-icon.png` | Icônes |

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements : une page ouverte comme fichier ne peut pas s'installer).
2. Menu **⋮ → Installer l'application** (ou « Ajouter à l'écran d'accueil »).

## Ce que l'appli regarde
- **Place restante** : taille des fichiers actuels (ce qui est publié) et du dépôt complet (historique compris) de `appli-manu` et `vigie-depo`, en pourcentage de 1 Go (taille maximale recommandée par GitHub). Un fichier de 50 Mo déclenche un avertissement, 100 Mo est refusé.
- **À nettoyer** : fichiers que l'appli sait inutiles (modèles de visages de Mes Photos, retirés à la V41), anciennes copies à la racine (README « À ranger »), archives et restes de téléchargement, doublons exacts de 200 Ko et plus, fichiers de 5 Mo et plus. Supprimer allège le site tout de suite ; la taille du dépôt complet ne baisse pas avec une simple suppression (GitHub garde l'historique).
- **À réparer** : pour chaque appli de `appli-manu` : `version.json` répond, la page principale répond, le nom de cache du service worker correspond à `version.json`, les fichiers cités dans `version.json` existent, `rando-3d/index.html` est identique à `rando-3d.html`.
- **État du service GitHub** : voyant en haut de la page.

## Dépôt privé vigie-depo
Sans jeton, la page ne voit pas ce dépôt. Dans **Réglages** (en bas), coller un jeton GitHub **en lecture seule** (droit « Contents : Read-only » sur `vigie-depo` et `appli-manu`). Il n'est gardé sur le téléphone que si la case « Mémoriser » est cochée. **Ne jamais mettre de jeton dans un fichier du dépôt.**

## Mettre à jour
1. Modifier `suivi-github.html`.
2. Mettre à jour `version.json` (version, date, nouveautés, `sw.cache`) puis lancer `python3 outils/poser-pastille.py suivi-github`.
3. Sur le téléphone : toucher la pastille ↻ en haut → **Actualiser**.

## Où sont les données
Aucune donnée personnelle. Seul le jeton (si « Mémoriser » est coché) est dans le navigateur du téléphone : `localStorage`, clé `gh-token`.

## Pièges connus
- Les règles « fichiers inutiles » et « anciennes copies » sont écrites en dur dans la page (`INUTILISES`, `A_RANGER`, `DOUBLONS_VOULUS`). Si le README « À ranger » ou les journaux changent, les mettre à jour ici.
- Sans jeton, GitHub limite à 60 requêtes par heure : chaque actualisation en utilise environ 4.
- La pastille ↻ est en haut à droite : la page laisse 52 px de marge en haut pour qu'elle ne cache aucun bouton.

## Historique
- 14/10/2026 — V1 : première version publiée (place restante, à nettoyer, à réparer, demandes à copier pour Claude). Cache suivi-github-app-v1.
