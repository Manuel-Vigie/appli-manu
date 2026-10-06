# appli-manu

Mes petites applis. Chaque dossier a son **JOURNAL.md** (adresse, fichiers, comment installer, mettre à jour, sauvegarder, pièges connus) et son **version.json** (version et date affichées par la pastille ↻ en haut de l'appli).
La marche à suivre pour Claude est dans **CLAUDE.md** ; les outils communs dans `outils/`.

| Appli | Adresse | Journal |
|---|---|---|
| Carnet de randonnée | https://manuel-vigie.github.io/appli-manu/carnet-randonnee/carnet-randonnee.html | [JOURNAL](carnet-randonnee/JOURNAL.md) |
| Rando 3D | https://manuel-vigie.github.io/appli-manu/rando-3d/rando-3d.html | [JOURNAL](rando-3d/JOURNAL.md) |
| Carnet de santé (coquille vide, aucune donnée) | https://manuel-vigie.github.io/appli-manu/carnet-sante/carnet-sante.html | [JOURNAL](carnet-sante/JOURNAL.md) |
| Bibliothèque Photo | https://manuel-vigie.github.io/appli-manu/ma-bibliotheque-photo/bibliotheque-photo.html (copie de test ; ancienne adresse : `bibliotheque-photo/`) | [JOURNAL](ma-bibliotheque-photo/JOURNAL.md) |

## Règles
- Un dossier par appli, noms sans espaces ni accents, adresse du type `…/NOM/NOM.html`.
- **Dépôt public : jamais de données personnelles ni de sauvegardes** (albums, santé).
- Avant de déplacer un fichier : vérifier quelle appli l'utilise, puis corriger les noms dans son `sw.js` et son manifest.
- À chaque mise à jour d'une appli : changer le nom de cache dans son `sw.js`.
- Chaque modification importante : ajouter une ligne à l'**Historique** du journal.

## À ranger
Fichiers encore à la racine, envoyés le 02/10/2026 : copie de Rando 3D (`index.html`, `manifest.webmanifest`, `sw.js`, icônes, `rando-3d-pwa.zip`) et copie V9 du carnet de rando (`carnet-de-rando.html`, `carnet-sw.js`, `carnet-manifest.webmanifest`, icônes `carnet-icone-*`). À supprimer seulement après accord de Manuel.

L'appli **Vigie** est dans un autre dépôt privé (`Vigie-depo`).
