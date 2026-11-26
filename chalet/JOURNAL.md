# Journal — Mon chalet

## Identité
Appli pour **ne plus oublier les courses et le matériel** à acheter pour la maison de montagne. C'est une **armoire de chalet en 3D avec des tiroirs** (bois sculpté, toit enneigé, lanternes, enseigne « Mon chalet »). Chaque tiroir est une rubrique ; à l'intérieur, la **liste des articles à acheter, avec des cases à cocher** quand on les met dans le vrai panier. Pensée pour le téléphone, elle marche aussi sur PC.
Version actuelle : **V2 · 26/11/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/chalet/chalet.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `chalet.html` | L'appli entière (page, 3D, listes, formulaires) |
| `chalet-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `chalet-sw.js` | Mode hors connexion : garde l'enveloppe de l'appli **et** la bibliothèque 3D (three.js). Contient `CACHE = 'chalet-app-v2'` |
| `version.json` | Version et date lues par la pastille ↻ (voir CLAUDE.md) |
| `chalet-icon-192.png`, `-512.png`, `-masque-512.png`, `chalet-apple-touch-icon.png` | Icônes (chalet enneigé devant les montagnes) |

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements).
2. Menu **⋮ → Installer l'application** (ou « Ajouter à l'écran d'accueil »).
3. Ouvrir l'appli **une fois avec du réseau** : c'est là que la 3D est mise en réserve pour marcher ensuite sans réseau.

## Ce que l'appli fait
- **L'armoire** : un tiroir par rubrique, le premier en haut. Façade en bois sculpté avec une plaque portant le nom, un anneau de fer et une **pastille** : rouge avec le nombre d'articles restant à prendre, verte avec ✓ quand tout est coché, pas de pastille si le tiroir est vide. Départ : 4 tiroirs (Garde-manger, Frais, Maison, Matériel), tous vides.
- **Toucher un tiroir** : il coulisse hors de l'armoire (on voit des fiches à onglets de couleur, une par article restant), la caméra se rapproche et la **liste s'affiche en bas** sur du papier : une ligne par article, grosse case ronde à toucher (cochée = vert, barrée, descend en bas de liste), ✕ pour retirer. Retoucher le tiroir ou le ✕ du haut le referme. Sur écran large (PC), la liste s'ouvre à droite.
- **Ajouter un article** : champ en haut du tiroir (touche Entrée ou bouton ＋). Le nom est mémorisé dans **« Souvent acheté »** : ces noms reviennent sous forme de boutons ＋ pour racheter en un seul geste (« Modifier » permet d'en oublier).
- **Ranger les articles cochés** : bouton vert en bas du tiroir (ou dans « Ma liste ») : les articles pris disparaissent de la liste mais restent dans « Souvent acheté ».
- **Bouton « Ma liste (N) »** : tout ce qui reste à acheter, tiroir par tiroir, avec les mêmes cases à cocher — pratique dans le magasin. N = nombre d'articles non cochés.
- **« ＋ Tiroir »** crée une rubrique ; le ⚙ d'un tiroir ouvert permet de le renommer ou de le supprimer (2 touches, efface son contenu).
- **Gestes** : glisser = faire défiler l'armoire (avec un peu d'élan) et pencher la vue de côté (elle revient seule) ; pincer = zoomer ; sur PC : molette = défiler, Ctrl + molette = zoom, flèches haut/bas. Un tiroir ouvert verrouille le défilement.
- **Sans 3D** (WebGL coupé, téléphone très ancien, three.js jamais chargé et pas en réserve) : l'appli bascule sur une liste de gros boutons (un par tiroir) ; le reste est identique.
- Une liste cachée pour lecteurs d'écran (`#acces`) donne un bouton par tiroir.

## Où sont les données
**Uniquement dans le navigateur du téléphone** (`localStorage`, clé `chalet-donnees-v2` : `{v:2, rubriques:[{id, nom, produits:[{id, nom, fait}], habitudes:[nom…]}]}`). Rien n'est envoyé en ligne et **rien n'est dans ce dépôt public**. Vider les données de Chrome efface tout. Pas encore de sauvegarde/restauration (à ajouter si Manuel le demande, comme dans Congés).
**Reprise de la V1** (étagères avec bocaux) : au premier lancement de la V2, si la clé `chalet-donnees-v1` existe, ses rubriques deviennent des tiroirs, les produits qui étaient « à acheter » deviennent des articles, et tous ses produits deviennent des habitudes. La clé V1 n'est jamais effacée.

## Côté technique (pour Claude)
- 3D : **three.js r128** chargé depuis cdnjs (`<script src>`), mis en réserve par `chalet-sw.js` (cache `chalet-libs`, même méthode que Rando 3D). Pas d'OrbitControls : caméra et gestes écrits à la main (`vue = {yaw, zoom, ty, vy, tz, e}`).
- Tout le décor est dessiné dans le code (textures de bois, tapis, façades, enseigne avec des `canvas`) : aucune image à charger. L'armoire est reconstruite en entier par `reconstruire()` à chaque changement de données (rapide) ; l'état ouvert/fermé et la caméra sont conservés.
- Tiroir ouvert : `G.cadrer(id)` règle la cible de caméra (`cibleOuverte()` : zoom pour que la façade remplisse la largeur, `tz = 0.5`, élévation `e = 0.5`) ; fermer revient au zoom et au défilement d'avant. La liste est du HTML (`#tiroir`), pas de la 3D (texte lisible et touches faciles) : l'écran se partage entre `#zone` (3D) et `#tiroir` (62 % de la hauteur sur téléphone, 460 px à droite sur PC).
- Mesures en mètres : armoire 3,6 de large, tiroir 0,94 de pas (façade 0,84), toit 1,5 de haut, sortie d'un tiroir 0,95 (constantes en haut de `demarrer3D`). Couleurs passées par `C(hex)` (sRGB → linéaire, nécessaire avec three r128).
- Rendu à la demande (pas de boucle permanente) : peu de batterie.
- Pour tester sans réseau : servir le dépôt avec `python3 -m http.server`, ouvrir Chrome (swiftshader) et remplacer `three.min.js` par la copie npm `three@0.128.0` (`page.route`). `window.__chalet.ecran('tiroir', id)` donne la position écran d'un tiroir pour simuler une touche ; `window.__chalet.etat()` donne l'état de la caméra.

## Pièges connus
- Si l'armoire est plus basse que l'écran (4 tiroirs ou moins sur un grand téléphone), il n'y a rien à faire défiler : tout est visible.
- Les fiches à onglets dans un tiroir sont décoratives (une par article, 12 au maximum) : le texte lisible est dans la liste en bas.
- Au-delà d'une quinzaine de tiroirs, l'armoire devient très haute : l'ombre portée perd en finesse (la 3D reste utilisable).
- Le clavier du téléphone peut cacher le bas de la liste pendant la saisie d'un article : c'est normal, le champ reste visible en haut.

## Historique
- **V2 · 26/11/2026** — Nouvelle présentation : armoire de chalet à tiroirs (remplace les étagères et bocaux). Liste à cocher dans chaque tiroir, « Souvent acheté », « Ma liste », ranger les articles cochés, reprise des données de la V1, liste à droite sur grand écran.
- **V1 · 26/11/2026** — Première version : étagères 3D en chalet, produits en bocaux et boîtes, liste « À acheter ».
