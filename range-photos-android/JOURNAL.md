# Range Photos (appli Android)

Vraie appli Android (fichier APK) qui **range elle-même** les photos de la carte SD dans des dossiers. Une page web ne peut pas le faire sur Android ; c'est pour ça que celle-ci est une appli à installer.
Tout se passe sur le téléphone : aucune photo n'est envoyée, aucune photo dans le dépôt. Détails techniques : [README](README.md).

- **Adresse de téléchargement** : onglet *Releases* du dépôt, version `range-photos-v0.1.0` : https://github.com/Manuel-Vigie/appli-manu/releases (fichier `RangePhotos.apk`).
- **Installer** : sur le téléphone, ouvrir le lien, toucher `RangePhotos.apk`, autoriser l'installation depuis cette source si Android le demande. Une nouvelle version s'installe par-dessus l'ancienne (même clé de signature `app/debug.keystore`, publique, voulue).
- **Utiliser** : ouvrir l'appli, « Choisir le dossier de photos » (carte SD ou son dossier DCIM), regarder l'aperçu, « Ranger maintenant ». Les photos sont déplacées dans `Photos rangées/`. « Annuler le dernier rangement » remet tout en place.
- **Mettre à jour** : modifier le code dans ce dossier, changer `versionName` dans `app/build.gradle.kts`, `version.json` et ce journal, puis push. GitHub compile tout seul (`.github/workflows/range-photos-android.yml`, ~5 min) et publie l'APK dans *Releases*.
- **Piège connu** : la reconnaissance par personne (`Portraits/Julie/`) n'existe pas encore ; les portraits sont séparés en Solo et Groupe.
- **Piège connu** : Android demande de choisir le dossier via son sélecteur ; choisir la carte SD ou son dossier DCIM.

## Historique
- 07/10/2026 : V1 (0.1.0), première version du code ajoutée au dépôt. Jamais compilée avant son arrivée ici ; la première compilation GitHub fait office de premier test.
