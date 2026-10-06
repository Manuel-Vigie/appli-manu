# Journal — Replay Rando 3D

## Identité
Rejoue des randonnées GPX en 3D sur la carte IGN. Fonctionne hors connexion (PWA).

## Adresse
https://manuel-vigie.github.io/appli-manu/rando-3d/rando-3d.html
(l'adresse du dossier `…/rando-3d/` marche aussi grâce à `index.html`)

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `rando-3d.html` | L'appli entière |
| `index.html` | **Copie identique** de `rando-3d.html`, uniquement pour que l'adresse du dossier marche |
| `manifest.webmanifest` | Nom, icônes, `start_url: ./rando-3d.html` |
| `sw.js` | Mode hors connexion. Contient `VERSION = 'rr3d-v9'` (laisse passer `version.json` sans cache) |
| `version.json` | Version, date et nouveautés affichées par la pastille ↻ (voir CLAUDE.md) |
| `icon-192.png`, `icon-512.png`, `icon-maskable-512.png`, `apple-touch-icon.png`, `favicon.png` | Icônes |

## Installer sur le téléphone (Android)
Ouvrir l'adresse dans Chrome (pas un fichier du dossier Téléchargements), puis **⋮ → Installer l'application**.

## Mettre à jour
1. Modifier `rando-3d.html`, puis **recopier le résultat dans `index.html`** (les deux doivent rester identiques).
2. Mettre à jour `version.json` (version, date, nouveautés, `sw.cache`) puis lancer `python3 outils/poser-pastille.py rando-3d`.
3. Sur le téléphone : toucher la pastille ↻ en haut à droite → **Actualiser**.

## Où sont les données
Dans le navigateur : `localStorage`, clés `rr3d-*` (`rr3d-lib` bibliothèque, `rr3d-places:`, `rr3d-relief`, `rr3d-pace`, `rr3d-area`, `rr3d-fs`). Caches : `rr3d-v2-app`, `rr3d-libs`, `rr3d-tiles`.
Vider les données de sites de Chrome les efface. Aucune fonction de sauvegarde n'a été vérifiée dans l'appli : à regarder avant de promettre une restauration.

## Pièges connus
- Il existe à la racine du dépôt une **autre copie** de cette appli (`index.html`, `manifest.webmanifest`, `sw.js`, icônes, `rando-3d-pwa.zip`, envoyée le 02/10/2026). Ne pas installer celle-là : deux installations de la même appli au même endroit se gênent.
- Le 02/10/2026, la suppression de `rando-3d/index.html` a cassé l'adresse du dossier (« disparu » sur le téléphone). Remis le 03/10/2026.

## Historique
- 02/10/2026 — Rangement dans `rando-3d/` (`index.html` retiré par erreur, remis le 03/10).
- 06/10/2026 — V3 : pastille de mise à jour en haut à droite (version, date, bouton Actualiser, devient verte si une nouvelle version est en ligne). Cache rr3d-v3.
- 06/10/2026 — V4 : nouvelle page d'accueil claire (ciel bleu, nuages qui défilent, boutons Importer / Itinéraire / Mes randos / Voir un exemple) à la place de la scène 3D de départ. Le replay 3D sur vraie carte est inchangé. Cache rr3d-v4.
- 06/10/2026 — V5 : bouton « Survol (essai) » : carte MapLibre 5.24 à vue inclinée avec ombrage du relief (Photos IGN, Satellite, TOP 25, Topo) où la caméra suit la rando, avec départ vu du ciel, stats, curseur et vitesses ; option « Relief 3D (essai) » (désactivée par défaut, non vérifiée sur un vrai téléphone). Le replay 3D (Three.js) reste inchangé. Cache rr3d-v9.
- 06/10/2026 — V6 : dans le Survol, le tracé se dessine au fur et à mesure que le marcheur avance (plus de tracé complet affiché d'avance). Cache rr3d-v9.
- 06/10/2026 — V7 : Survol : le tracé n'est plus masqué par un dégradé (ne marchait pas sur le téléphone de Manuel) ; la carte ne reçoit que la partie déjà parcourue. Cache rr3d-v9.
- 06/10/2026 — V8 : replay 3D en mode Suivi : le trait blanc du parcours complet est caché (il reste le chemin bleu déjà parcouru) et les bornes km n'apparaissent qu'au passage du marcheur. Bouton « Survol (essai) » renommé « Carte animée (essai) » (confusion avec le mode Survol). Cache rr3d-v9.
- 06/10/2026 — V9 : Carte animée : Relief 3D activé par défaut ; en relief, zoom reculé de 1,3, inclinaison 50° (au lieu de 64°), brume du ciel supprimée (écran tout bleu), relief ×1,1 ; zoom et inclinaison choisis au doigt gardés par « Suivre le marcheur ». Cache rr3d-v9.
