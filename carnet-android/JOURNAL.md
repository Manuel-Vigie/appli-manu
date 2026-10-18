# Carnet de rando (Android) — journal

## Identité
Petite appli Android (Kotlin, une seule activité) qui ouvre le carnet en ligne (`carnet-randonnee/carnet-randonnee.html`, GitHub Pages) dans une fenêtre web, mais installée pour de bon sur le téléphone. Créée parce que, sur le téléphone de Manuel (Oppo), « Installer l'application » dans Chrome ou Edge ne créait qu'un raccourci : le carnet n'apparaissait donc pas dans « Partager ».
Version actuelle : **V4 · 15/10/2026** (`versionName 1.0.3`, `versionCode 4`).

## Ce qu'elle fait
- Charge `https://manuel-vigie.github.io/appli-manu/carnet-randonnee/carnet-randonnee.html` : toujours la dernière version du carnet, hors connexion grâce au service worker du carnet.
- Reçoit « Partager » (une ou plusieurs photos / vidéos, `SEND` et `SEND_MULTIPLE`) : copie les fichiers dans son espace privé (`files/recu/`), ouvre le carnet avec `?partage=N`. Le carnet (`window.CarnetAndroid`, voir `recevoirPartage()`) lit les fichiers par `fetch("__recu/<n>/<nom>")` ; l'appli les sert dans `shouldInterceptRequest` (fenêtre et service worker). Le carnet propose album + sortie, 50 / 100 / toutes, et range par date.
- Sélecteur de fichiers du carnet (« Choisir des photos », « Restaurer ») : `onShowFileChooser`.
- Sauvegardes / albums téléchargés : un clic sur un lien `download` de type `blob:` est intercepté par un petit script et écrit par morceaux dans **Téléchargements** (`MediaStore.Downloads`).
- Liens vers d'autres sites : ouverts dans le navigateur.

## Pièges connus
- **Les albums ne sont pas partagés avec Chrome** : l'appli a son propre stockage (IndexedDB de sa fenêtre web). Au premier lancement elle est vide : utiliser « 📥 Restaurer » avec la sauvegarde faite dans Chrome (« 💾 Sauvegarder toute la bibliothèque »). Désinstaller l'appli efface ses albums : sauvegarder avant.
- Signée avec la clé publique `app/debug.keystore` (la même que Mes Photos) : une nouvelle version s'installe par-dessus l'ancienne.
- Android 15 dessine sous les barres : la marge du haut / bas / clavier est ajoutée par `setOnApplyWindowInsetsListener`.
- Compilée seulement par GitHub (`.github/workflows/carnet-android.yml`, déclenchée par un changement dans `carnet-android/`) ; APK publié dans Releases (`carnet-rando-v<version>`, fichier `CarnetRando.apk`). Rien n'a pu être compilé ni essayé sur téléphone au moment de l'écriture.
- Le carnet doit rester en V69 ou plus (pont `CarnetAndroid`).

## Historique
- V1 (15/10/2026) : création.
- V2 / 1.0.1 (15/10/2026) : fenêtres du carnet (`alert`, `confirm`, `prompt`) affichées par l'appli (`onJsAlert/Confirm/Prompt`) ; sans elles, Android les refuse en silence et par exemple « Remplacer la bibliothèque ? » de la restauration n'aboutissait jamais.
- V3 / 1.0.2 (15/10/2026) : fermeture propre. Quand l'appli quitte l'écran, la musique se met en pause (`onPause` appelle `__sonPause`, elle repart à `onResume`) ; à la fermeture (`onDestroy`) la fenêtre web est détruite, donc plus aucun son, sans « forcer l'arrêt ». Le bouton Retour revient en arrière dans le carnet s'il y a un historique, sinon quitte l'appli.
- V4 / 1.0.3 (15/10/2026) : pont `CarnetAndroid.quitter()` (`finishAndRemoveTask`) pour le bouton « ⏻ Quitter l'application » du carnet ; appui long sur la fenêtre web : ni menu de sélection ni vibration d'Android (`setOnLongClickListener`), pour que le carnet passe en mode Retoucher.
- V5 / 1.0.4 (18/10/2026) : réception des fichiers partagés plus bavarde : erreurs de copie relevées (`Throwable`, flux nul) et résumées dans `diag` (« N annoncé(s), M copié(s) ; erreurs : … »), affiché par le carnet via `CarnetAndroid.diag()` et dans le message « Impossible de lire ces fichiers ». Aucun changement de comportement sinon. Cause de l'échec sur le téléphone de Manuel (Samsung ancien) encore inconnue.
- V6 / 1.0.5 (18/10/2026) : le nom de l'appli devient « Carnet de rando (appli) » (manifeste), pour la distinguer dans la liste « Partager » d'un éventuel raccourci ou WebAPK « Carnet de rando » installé depuis Chrome, qui ne sait pas recevoir les photos (message « Aucun souvenir reçu » sans détail). Hypothèse non confirmée.
- V7 / 1.0.6 (18/10/2026) : à la réception d'un « Partager » le carnet s'affiche tout de suite (avant : écran noir pendant la copie de tous les fichiers, très long pour 192 photos sur un Samsung S9+), un message « Réception… N sur M » tous les 25 fichiers, copie par blocs de 256 Ko. Le carnet se recharge à la fin de la copie.
- V8 / 1.0.7 (18/10/2026) : vrai plein écran. Le bouton ⛶ du carnet appelle `CarnetAndroid.plein(true/false)` : la barre du haut et la barre de navigation d'Android se cachent (`WindowInsetsController` dès Android 11, sinon `systemUiVisibility` immersif ; un glissement depuis le bord les montre un instant). Réappliqué à `onResume`. Avant, `requestFullscreen` de la page n'avait aucun effet dans la fenêtre web et la bande du bas restait.
- V9 / 1.0.8 (18/10/2026) : le bouton Retour d'Android demande d'abord au carnet (`window.__retour`) de fermer ce qui est ouvert (vidéo/photo plein écran, réglages, plein écran, Retoucher, puis l'album → bibliothèque) ; si le carnet répond non, comportement d'avant (page précédente ou fermeture).
- V10 / 1.0.9 (18/10/2026) : bande blanche en bas en plein écran : fond de fenêtre et barres d'état/navigation en vert sombre, `setDecorFitsSystemWindows(false)` + marges à 0 pendant le plein écran (ancien Android : drapeaux LAYOUT_HIDE_NAVIGATION / LAYOUT_FULLSCREEN), zone d'encoche autorisée. Non testé sur téléphone (pas d'Android SDK ici).
