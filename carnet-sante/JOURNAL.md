# Journal — Carnet de santé (coquille vide)

## Règle absolue
**Ce dossier est public. Il ne contient et ne doit contenir AUCUNE donnée personnelle** (ni nom, ni traitement, ni image médicale).
Les données sont dans un fichier privé `carnet-sante.html` que Manuel garde sur son téléphone / son PC. Ne jamais le mettre sur GitHub.

## Principe
L'appli en ligne est une « coquille » : au premier lancement elle demande le fichier privé `carnet-sante.html`, le range dans le téléphone (IndexedDB `carnet-sante-coffre`, magasin `k`, clé `fichier`), puis l'ouvre dans un cadre plein écran, hors connexion. Le fichier privé reste inchangé.

## Adresse
https://manuel-vigie.github.io/appli-manu/carnet-sante/carnet-sante.html

## Fichiers (tous dans ce dossier)
| Fichier | Rôle |
|---|---|
| `carnet-sante.html` | La coquille (écran d'import, cadre, bouton ⚙) |
| `carnet-sante-manifest.webmanifest` | Nom, icônes, démarrage |
| `carnet-sante-sw.js` | Cache de la coquille seulement (`carnet-sante-app-v1`) |
| `carnet-sante-icon-192.png`, `-512.png`, `-masque-512.png`, `-apple-touch-icon.png` | Icônes |

## Installer sur le téléphone
1. Ouvrir l'adresse dans Chrome. 2. **Choisir mon fichier** → `carnet-sante.html` (Téléchargements). 3. **⋮ → Installer l'application**.

## Mettre à jour
- **La coquille** : modifier le html, changer `CACHE` dans `carnet-sante-sw.js` (v1 → v2).
- **Le contenu santé** (fichier privé) : sur le téléphone, **⚙ → Remplacer par un nouveau fichier**. Rien à publier.
- Le fichier privé est vérifié à l'import : il doit contenir `<title>Carnet de Santé`.

## Où sont les données
Le fichier privé est copié dans le téléphone (IndexedDB). Les scans ajoutés dans l'appli sont dans `localStorage` (clés commençant par `imagerie`, `analyses`, `ordonnances`).
Vider les données de sites de Chrome efface tout : il faut alors réimporter le fichier privé (**garder une copie dans Téléchargements ou sur le PC**). Les scans ajoutés après l'import ne sont alors pas récupérables.
Le bouton **⚙ → Effacer mes données** supprime le fichier et les scans du téléphone, pas la copie dans Téléchargements.

## Fichier privé (hors GitHub) — version 2.20 (04/10/2026)
Chaque section et chaque information dépliable a les boutons **Imprimer** (page propre dans un nouvel onglet, PDF possible) et **Envoyer par mail** (mail rédigé ; avec images : feuille de partage du téléphone, images jointes).
Ces boutons sont un bloc de code ajouté en fin de script du fichier privé. Pour les refaire : Claude peut les ajouter à partir du fichier que Manuel lui envoie.

## Pièges connus
- Ouvrir le fichier depuis Téléchargements au lieu de l'adresse : pas d'installation possible.
- Impression : dans l'appli installée, c'est la page ouverte par le bouton qui s'imprime (l'impression directe dans le cadre n'est pas fiable sur Android).
- Non testé sur un vrai téléphone au 04/10/2026 : à confirmer par Manuel.

## Test rapide avant de publier
Servir le dossier en local, importer un fichier de test, vérifier : cadre visible, rechargement sans serveur (hors connexion), ⚙ → effacer revient à l'écran d'import. **Vérifier qu'aucun mot personnel n'est dans les fichiers publics avant tout envoi.**

## Historique
- 01–02/10/2026 — Ancienne version : archive `carnet-sante.zip` publique contenant des données privées. Retirée, dépôt recréé pour effacer l'historique.
- 03/10/2026 — Coquille vide en ligne.
- 04/10/2026 — Fichier privé v2.20 (boutons Imprimer / Envoyer).
