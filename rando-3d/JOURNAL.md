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
| `sw.js` | Mode hors connexion. Contient `VERSION = 'rr3d-v27'` (laisse passer `version.json` sans cache) |
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
- 06/10/2026 — V5 : bouton « Survol (essai) » : carte MapLibre 5.24 à vue inclinée avec ombrage du relief (Photos IGN, Satellite, TOP 25, Topo) où la caméra suit la rando, avec départ vu du ciel, stats, curseur et vitesses ; option « Relief 3D (essai) » (désactivée par défaut, non vérifiée sur un vrai téléphone). Le replay 3D (Three.js) reste inchangé. Cache rr3d-v27.
- 06/10/2026 — V6 : dans le Survol, le tracé se dessine au fur et à mesure que le marcheur avance (plus de tracé complet affiché d'avance). Cache rr3d-v27.
- 06/10/2026 — V7 : Survol : le tracé n'est plus masqué par un dégradé (ne marchait pas sur le téléphone de Manuel) ; la carte ne reçoit que la partie déjà parcourue. Cache rr3d-v27.
- 06/10/2026 — V8 : replay 3D en mode Suivi : le trait blanc du parcours complet est caché (il reste le chemin bleu déjà parcouru) et les bornes km n'apparaissent qu'au passage du marcheur. Bouton « Survol (essai) » renommé « Carte animée (essai) » (confusion avec le mode Survol). Cache rr3d-v27.
- 06/10/2026 — V9 : Carte animée : Relief 3D activé par défaut ; en relief, zoom reculé de 1,3, inclinaison 50° (au lieu de 64°), brume du ciel supprimée (écran tout bleu), relief ×1,1 ; zoom et inclinaison choisis au doigt gardés par « Suivre le marcheur ». Cache rr3d-v27.
- 06/10/2026 — V10 : Carte animée : tracé plus fin (3,2 px, contour 5,5), caméra en relief plus haute (inclinaison 40°) et relief ×1 pour limiter les pentes sans photo. Cache rr3d-v27.
- 06/10/2026 — V11 : Carte animée : bornes km affichées au passage (4 dernières), animation plus fluide (définition d'écran plafonnée à 1,75, sans anticrénelage, tracé remis à jour 3 fois par seconde au lieu de chaque image). Cache rr3d-v27.
- 06/10/2026 — V12 : Carte animée : fond de carte couleur terre (au lieu de bleu clair), photo de secours moins nette sous la photo principale (tuiles 1024), bornes km dessinées par la carte (couche symbole) au lieu d'éléments HTML. Cache rr3d-v27.
- 06/10/2026 — V13 : Carte animée : vue d'ensemble du parcours au départ (3 s, tracé complet visible) et à l'arrivée (caméra reste dessus), tracé plus fin (2,2 px, contour 3,6), relief 3D ×1,8. Le bouton ↻ rejoue la vue d'ensemble de départ. Cache rr3d-v27.
- 06/10/2026 — V14 : Carte animée : repart toujours du départ (avant, reprenait où en était le replay 3D), caméra qui tourne plus vite dans les virages (cap 300 m devant / 100 m derrière), zoom adapté à la longueur et au dénivelé de la rando (au lieu de 4 paliers), tracé en deux parties (terminé + morceau en cours remis à jour 11 fois par seconde). Cache rr3d-v27.
- 06/10/2026 — V15 : Carte animée : bouton « ● Filmer » (MediaRecorder sur la carte, bandeau : km, zoom, inclinaison, images/s, état des photos ; 90 s max ; fichier .webm/.mp4 dans Téléchargements) pour aider au diagnostic. Cache rr3d-v27.
- 06/10/2026 — V16 : le bouton « ● Filmer » (rouge) passait sous la pastille de version : la ligne de boutons du haut du Survol laisse maintenant la place à la pastille. Cache rr3d-v27.
- 06/10/2026 — V17 (d'après la vidéo de Manuel : caméra collée à la montagne, 9–15 im/s) : Carte animée : inclinaison 28° (au lieu de 40°), relief ×1,3 (au lieu de ×1,8), zoom plus large en haute montagne, modèle de relief moins détaillé (z12), définition plafonnée à 1,4. Cache rr3d-v27.
- 07/10/2026 — V18 : Carte animée : bouton « Vue de dessus » (inclinaison 0°, zoom +0,4) : alternative sûre au relief incliné, qui entrait dans la montagne. Google Earth écarté (images non utilisables) : on garde les photos IGN. Cache rr3d-v27.
- 07/10/2026 — V19 : refonte de la présentation. Barre du bas à 5 onglets (Replay, Carte animée, Ma rando, Photos, Mes randos) ; l'en-tête ne garde que le titre (les anciens boutons existent toujours, cachés, et sont appelés par les pages) ; puces de réglage directes dans le replay 3D (Vitesse, Fond, Relief, Noms, Météo, Pente, Km, Plus) ; la carte animée garde ses boutons (Relief 3D, Filmer, De dessus). Sauvegarde de la V18 gardée hors dépôt. Cache rr3d-v27.
- 07/10/2026 — V20 : pages Ma rando / Photos / Mes randos : texte foncé forcé (illisible en mode sombre du téléphone), bandeau bleu avec distance et profil de dénivelé coloré par pente, tuiles avec icônes, miniatures des photos (touche = aller à la photo), liste des randos enregistrées avec bouton Ouvrir. Cache rr3d-v27.
- 07/10/2026 — V21 : « 🎬 Film de la carte » (bouton dans Carte, tuiles dans Ma rando et Photos) : vidéo MP4 faite image par image sur une carte MapLibre cachée (HD 1280×720, Full HD ou vertical 1080×1920 ; 20 à 60 s ; 30 im/s), WebCodecs + mp4-muxer 5.1.3 (ajouté au cache du sw) ; déroulé : vue d'ensemble, descente, marche à vitesse constante, retour en vue d'ensemble avec titre et chiffres. Caméra lissée (`camPath`, moyennes glissantes) aussi utilisée dans la carte animée pour qu'elle tourne moins. « ● Filmer » devient « ● Écran ». Cache rr3d-v27.
- 07/10/2026 — V22 (d'après le film de Manuel : zone blanche en haut, hors de France) : les photos IGN passent par un protocole `ign://` (addProtocol) qui repère les carrés entièrement blancs renvoyés par l'IGN hors couverture et les remplace par la tuile Esri. Cache rr3d-v27.
- 07/10/2026 — V23 : la V22 laissait du blanc quand on zoome (capture de Manuel) : `ignBlank` accepte maintenant les carrés presque blancs (min ≥ 228 et écart ≤ 22 sur 8×8 px). À confirmer sur de vraies tuiles IGN. Cache rr3d-v27.
- 07/10/2026 — V24 : le blanc restant (film de Manuel, au loin en haut, rando en Savoie vers 2570 m) ressemble à de la neige/glacier surexposé dans les photos IGN, pas à un manque de couverture. `ignBlank` remplace aussi les carrés dont plus de la moitié est blanc (≥ 238) par la tuile Esri. À confirmer sur le téléphone. Cache rr3d-v27.
- 07/10/2026 — V25 (captures de Manuel : bande blanche partielle au loin + différences de couleur IGN/Esri) : `ignTile` remplace maintenant pixel par pixel (blanc ≥ 236, bords élargis de 2 px) par la tuile Esri, passée dans un filtre canvas saturate(.6) brightness(.95) (réglage `IGN_MIX`) ; tuile IGN renvoyée telle quelle s'il y a moins de 0,4 % de blanc. Remplace `ignBlank`. À régler selon son retour sur les couleurs. Cache rr3d-v27.
- 07/10/2026 — V26 (capture de Manuel : blanc réglé, reste le patchwork de couleurs) : le traitement des tuiles IGN passe dans un Web Worker (OffscreenCanvas, `ignWorkerBody`) : remplacement du blanc par Esri (V25) + harmonisation de la teinte (gains par canal vers la moyenne glissante des tuiles vues, `cast` .6, bornés 0,88–1,14) et du contraste (`contrast` .5, borné 0,88–1,2). Réglages dans `IGN_MIX`. Sans OffscreenCanvas : tuile IGN brute. Cache rr3d-v27.
- 07/10/2026 — V27 : harmonisation renforcée (`IGN_MIX` cast .9, contrast .8, nouveau `light` .55 = luminosité moyenne rapprochée de la moyenne vue, bornes de gains 0,8–1,25, contraste 0,8–1,35, décalage de luminosité ±28), pour la zone brumeuse de la capture de Manuel. À régler selon son retour. Cache rr3d-v27.
