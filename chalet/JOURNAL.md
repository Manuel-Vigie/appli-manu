# Journal — Mon chalet

## Identité
Appli pour **ne plus oublier les courses et le matériel** à acheter pour la maison de montagne. Les produits sont rangés sur des **étagères en 3D dans un décor de chalet** (bois, toit enneigé, lanternes, enseigne « Mon chalet »). Pensée pour le téléphone, elle marche aussi sur PC.
Version actuelle : **V1 · 26/11/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/chalet/chalet.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `chalet.html` | L'appli entière (page, 3D, formulaires) |
| `chalet-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `chalet-sw.js` | Mode hors connexion : garde l'enveloppe de l'appli **et** la bibliothèque 3D (three.js). Contient `CACHE = 'chalet-app-v1'` |
| `version.json` | Version et date lues par la pastille ↻ (voir CLAUDE.md) |
| `chalet-icon-192.png`, `-512.png`, `-masque-512.png`, `chalet-apple-touch-icon.png` | Icônes (chalet enneigé devant les montagnes) |

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements).
2. Menu **⋮ → Installer l'application** (ou « Ajouter à l'écran d'accueil »).
3. Ouvrir l'appli **une fois avec du réseau** : c'est là que la 3D est mise en réserve pour marcher ensuite sans réseau.

## Ce que l'appli fait
- **Étagères 3D** : une rubrique = une étagère (poutre de bois avec son étiquette). Chaque produit est un **bocal** (rubriques 1, 3, 5…) ou une **boîte** (rubriques 2, 4…) avec son nom écrit sur une étiquette. 4 produits par rangée : au-delà, une rangée « · suite » s'ajoute sous la même rubrique.
- **Toucher un produit** : il avance hors de l'étagère, une pastille rouge « ! » apparaît, il entre dans la liste **À acheter** (le compteur du bouton rouge monte). Retoucher = il retourne à sa place.
- **Bouton « À acheter »** : liste en texte, groupée par rubrique. Toucher un produit de la liste (il est dans le panier) le fait retourner sur l'étagère. « Tout vider » demande une 2e touche.
- **Gestes** : glisser = faire défiler le meuble de haut en bas (avec un peu d'élan) et pencher légèrement la vue de côté (elle revient seule) ; pincer = zoomer (jusqu'à ×2,4) ; sur PC : molette = défiler, Ctrl + molette = zoom, flèches haut/bas.
- **« ＋ Ajouter »** : nom du produit, rubrique (ou « Nouvelle rubrique… »), case « Mettre aussi dans la liste à acheter » (cochée). La vue défile jusqu'au nouveau produit.
- **« ✎ Ranger »** (devient « ✓ Terminé ») : bandeau jaune ; toucher un produit = renommer / changer de rubrique / supprimer (2 touches) ; toucher une étiquette de rubrique = renommer / supprimer (seulement si elle est vide).
- **Départ** : 4 rubriques d'exemple (Garde-manger, Frais, Maison, Matériel) avec 4 produits chacune, tout modifiable.
- **Sans 3D** (téléphone très ancien, WebGL coupé, ou three.js jamais chargé et pas en réserve) : l'appli bascule sur une simple liste de boutons par rubrique, mêmes fonctions.

## Où sont les données
**Uniquement dans le navigateur du téléphone** (`localStorage`, clé `chalet-donnees-v1` : `{rubriques:[{id, nom, produits:[{id, nom}]}], achat:{idProduit:true}}`). Rien n'est envoyé en ligne et **rien n'est dans ce dépôt public**. Conséquence : vider les données de Chrome efface les produits ajoutés. Pas encore de sauvegarde/restauration (à ajouter si Manuel le demande, comme dans Congés).

## Côté technique (pour Claude)
- 3D : **three.js r128** chargé depuis cdnjs (`<script src>`), mis en réserve par `chalet-sw.js` (cache `chalet-libs`, même méthode que Rando 3D). Pas d'OrbitControls : la caméra et les gestes sont écrits à la main (`vue = {yaw, zoom, ty, vy}`).
- Tout le décor est dessiné dans le code (textures de bois, tapis, étiquettes, enseigne dessinées avec des `canvas`) : aucune image à charger. Le meuble est reconstruit en entier par `reconstruire()` à chaque ajout/renommage/suppression ; toucher un produit ne reconstruit rien (`maj`).
- Mesures en mètres : meuble 3,6 de large, rangée 1,0, poutre 0,26, toit 1,5 de haut (constantes en haut de `demarrer3D`). Les couleurs sont passées par `C(hex)` (conversion sRGB → linéaire, nécessaire avec three r128).
- Rendu à la demande (pas de boucle permanente) : `demander()` relance l'affichage seulement pendant un geste, une animation ou un redimensionnement → peu de batterie.
- Pour tester sans réseau : servir le dépôt avec `python3 -m http.server`, ouvrir Chrome (swiftshader) et remplacer `three.min.js` par la copie npm `three@0.128.0` (`page.route`). `window.__chalet.ecran(type, id)` donne la position écran d'un produit (`'produit'`) ou d'une étiquette (`'rubrique'`) pour simuler des touches.

## Pièges connus
- Si le meuble est plus bas que l'écran (4 rangées ou moins sur un grand téléphone), il n'y a rien à faire défiler : c'est normal, tout est visible.
- La couleur d'un bocal dépend du nom (même nom = même couleur) ; la hauteur aussi. Renommer change l'apparence.
- Au-delà d'une vingtaine de rangées, le meuble devient très haut : l'ombre portée perd en finesse (la 3D reste utilisable).

## Historique
- **V1 · 26/11/2026** — Première version : étagères 3D en chalet, produits en bocaux et boîtes, liste « À acheter », ajout/rangement, zoom, défilement, secours sans 3D.
