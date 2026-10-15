# Carnet de rando (Android) — journal

## Identité
Petite appli Android (Kotlin, une seule activité) qui ouvre le carnet en ligne (`carnet-randonnee/carnet-randonnee.html`, GitHub Pages) dans une fenêtre web, mais installée pour de bon sur le téléphone. Créée parce que, sur le téléphone de Manuel (Oppo), « Installer l'application » dans Chrome ou Edge ne créait qu'un raccourci : le carnet n'apparaissait donc pas dans « Partager ».
Version actuelle : **V2 · 15/10/2026** (`versionName 1.0.1`, `versionCode 2`).

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
