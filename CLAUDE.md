# Instructions pour Claude — dépôt appli-manu

Dépôt GitHub Pages de Manuel (compte Manuel-Vigie). Une conversation = une appli.
Manuel n'est pas développeur : il travaille surtout depuis son téléphone Android. Parler simplement, en français.
C'est Claude qui publie (commit + push sur `main`). Manuel ne doit rien avoir à télécharger ni à envoyer.

## En début de conversation
1. Lire `README.md` (liste des applis et règles), puis le `JOURNAL.md` et le `version.json` du dossier de l'appli concernée.
2. Dire à Manuel en une phrase où on en est (version actuelle, dernière modification).

## Pour chaque modification d'une appli
1. Modifier le html de l'appli (dans son dossier uniquement).
2. Dans `<dossier>/version.json` : version suivante (V3 → V4), date du jour (JJ/MM/AAAA), nouvelle ligne **en tête** de `nouveautes` (phrase courte, compréhensible par Manuel ; en garder 4 au plus), et nom de cache dans `sw.cache`.
3. Lancer `python3 outils/poser-pastille.py <dossier>` : met à jour la pastille (version, date, nouveautés) dans les pages et le nom de cache du service worker. Ne jamais modifier le bloc `<!-- MAJ:debut … MAJ:fin -->` à la main : le modèle est `outils/pastille-maj.html`.
4. Ajouter une ligne à l'**Historique** du `JOURNAL.md` de l'appli.
5. Tester en local (`python3 -m http.server`, Chromium/Playwright) à 360 px de large : pas d'erreur, la pastille ne cache aucun bouton.
6. Commit en français (« Rando 3D V4 : … ») puis push sur `main`. Vérifier en ligne après ~1 min (`…/<dossier>/version.json`).
7. Dire à Manuel : « Sur le téléphone, touchez la pastille ↻ en haut puis Actualiser ».

## La pastille de mise à jour
En haut à droite de chaque appli : « ↻ V3 · 06/10 ». Elle lit `version.json` en ligne ; si la version diffère, elle devient verte « Nouvelle version ». Un appui ouvre un panneau (nom, version, date, nouveautés, bouton Actualiser qui vide les caches de l'appli et recharge).
- Carnet de rando a sa propre pastille (`id="version-appli"`) : dans son `version.json`, `"pastille": false` ; le script met juste à jour son étiquette et son cache.
- Carnet de santé : quand le carnet est ouvert, la pastille passe en bas à gauche à côté de ⚙ (réglage `css` du `version.json`).
- Rando 3D : `rando-3d.html` et `index.html` doivent rester identiques (le script traite les deux). Son `sw.js` laisse passer `version.json` sans cache : à garder.

## Règles absolues
- **Dépôt public : jamais de données personnelles** (carnet de santé privé, sauvegardes, photos personnelles).
- Ne rien supprimer sans l'accord de Manuel (voir « À ranger » dans le README).
- L'appli Vigie est dans un autre dépôt privé (`Vigie-depo`) : ne pas y toucher depuis ici.
