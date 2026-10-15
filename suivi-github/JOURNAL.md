# Journal — Suivi GitHub

## Identité
Appli de suivi des dépôts de Manuel : **un gros pourcentage de place restante** et un bouton **Nettoyer** pour tout ce qui ne sert à rien (anciens dossiers, vieilles copies, restes). Elle ne modifie rien elle-même : le bouton copie un message à coller dans la conversation avec Claude, qui fait le ménage.
Version actuelle : **V3 · 15/10/2026** (affichée dans la pastille ↻ en haut de l'appli).

## Adresse
https://manuel-vigie.github.io/appli-manu/suivi-github/suivi-github.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `suivi-github.html` | L'appli entière |
| `suivi-github-manifest.webmanifest` | Nom, icônes, adresse de démarrage (`start_url`) |
| `suivi-github-sw.js` | Mode hors connexion : garde seulement l'enveloppe de l'appli. Contient `CACHE = 'suivi-github-app-v3'` |
| `version.json` | Version et date lues par la pastille ↻ (voir CLAUDE.md) |
| `suivi-github-icon-192.png`, `-512.png`, `-masque-512.png`, `-apple-touch-icon.png` | Icônes |

## Installer sur le téléphone (Android)
1. Ouvrir l'adresse ci-dessus **dans Chrome** (jamais depuis un fichier du dossier Téléchargements : une page ouverte comme fichier ne peut pas s'installer).
2. Menu **⋮ → Installer l'application** (ou « Ajouter à l'écran d'accueil »).

## Ce que l'appli montre
- **Place restante** : taille des fichiers actuels (ce qui est publié) de `appli-manu` en pourcentage de 1 Go (taille maximale recommandée par GitHub pour un site publié), arrondi vers le bas. La partie rayée de la jauge est ce que le nettoyage libérerait. Un fichier de 50 Mo déclenche un avertissement chez GitHub, 100 Mo est refusé.
- **À nettoyer** : **un seul candidat, parce que c'est le seul dont on est sûr** : les anciens modèles de reconnaissance de visages de Mes Photos (`mes-photos-android/app/src/main/assets/faces/` et `nudenet/`, 3 fichiers, ≈ 49 Mo). Vérifié le 15/10/2026 : aucune ligne de code, de build ni de workflow ne les utilise, et le journal de Mes Photos dit qu'ils ont été retirés à la V41 (oubliés lors du ménage). Le bouton **Nettoyer** copie un message qui liste ces fichiers et dit « j'accepte que tu les supprimes » : l'envoyer à Claude vaut accord (règle « ne rien supprimer sans l'accord de Manuel »). La page ne supprime rien elle-même.
- **État de GitHub** : petit voyant en haut de la page.
- Retiré en V2 (trop compliqué) : la partie « à réparer », les doublons, les fichiers lourds, la jauge de l'historique.
- **Volontairement jamais proposés** (Manuel ne veut pas de risque) : les vieilles copies à la racine (« À ranger » du README : on ne peut pas savoir si une ancienne copie est installée sur son téléphone), les archives (`rando-3d-pwa.zip`, `Vigie-depo-main.zip`), les gros fichiers et le dossier `biblio-…` (bibliothèque envoyée en ligne par le carnet de rando). Pour les proposer un jour, il faut l'accord explicite de Manuel.

## Dépôt privé vigie-depo
Sans jeton, la page ne voit pas ce dépôt. En bas : « Ajouter mon dépôt privé » ; coller un jeton GitHub **en lecture seule** (droit « Contents : Read-only » sur `vigie-depo`). Il n'est gardé sur le téléphone que si la case est cochée. **Ne jamais mettre de jeton dans un fichier du dépôt.**

## Mettre à jour
1. Modifier `suivi-github.html`.
2. Mettre à jour `version.json` (version, date, nouveautés, `sw.cache`) puis lancer `python3 outils/poser-pastille.py suivi-github`.
3. Sur le téléphone : toucher la pastille ↻ en haut → **Actualiser**.

## Où sont les données
Aucune donnée personnelle. Seul le jeton (si la case est cochée) est dans le navigateur du téléphone : `localStorage`, clé `gh-token`.

## Pièges connus
- Supprimer des fichiers allège le site tout de suite ; la taille du dépôt complet (historique) ne baisse pas avec une simple suppression.
- La règle « fichiers inutiles » est écrite en dur dans la page (`INUTILISES`). N'y ajouter un dossier que s'il est vérifié inutile (code, build, journaux) **et** avec l'accord de Manuel. Une fois les modèles supprimés, la page affiche « Rien à nettoyer ».
- Sans jeton, GitHub limite à 60 demandes par heure : chaque actualisation en utilise 3.
- La pastille ↻ est en haut à droite : la page laisse 52 px de marge en haut pour qu'elle ne cache aucun bouton.

## Historique
- 14/10/2026 — V1 : première version publiée (place restante, à nettoyer, à réparer, demandes à copier pour Claude). Cache suivi-github-app-v1.
- 15/10/2026 — V2 : page simplifiée à la demande de Manuel (un gros pourcentage, une liste claire de ce qui ne sert à rien, un seul bouton Nettoyer ; partie « à réparer », doublons et fichiers lourds retirés). Cache suivi-github-app-v2.
- 15/10/2026 — V3 : à la demande de Manuel (« pas de risque »), la liste « à nettoyer » est réduite à ce qui est sûr : seulement les anciens modèles de visages de Mes Photos. Vieilles copies à la racine et archives ne sont plus proposées. Cache suivi-github-app-v3.
