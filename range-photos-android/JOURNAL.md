# Range Photos (appli Android)

Vraie appli Android (fichier APK) qui **range elle-même** les photos de la carte SD dans des dossiers. Une page web ne peut pas le faire sur Android ; c'est pour ça que celle-ci est une appli à installer.
Tout se passe sur le téléphone : aucune photo n'est envoyée, aucune photo dans le dépôt. Détails techniques : [README](README.md).

- **Adresse de téléchargement** : onglet *Releases* du dépôt, version `range-photos-v0.4.0` : https://github.com/Manuel-Vigie/appli-manu/releases (fichier `RangePhotos.apk`).
- **Installer** : sur le téléphone, ouvrir le lien, toucher `RangePhotos.apk`, autoriser l'installation depuis cette source si Android le demande. Une nouvelle version s'installe par-dessus l'ancienne (même clé de signature `app/debug.keystore`, publique, voulue).
- **Utiliser** : ouvrir l'appli, « Choisir le dossier de photos » (carte SD ou son dossier DCIM), regarder l'aperçu, « Ranger maintenant ». Les photos sont déplacées dans `Photos rangées/`. « Annuler le dernier rangement » remet tout en place.
- **Mettre à jour depuis l'appli** : écran d'accueil, carte « Mise à jour » → « Chercher une mise à jour ». Si une version plus récente existe, « Télécharger » ouvre le fichier dans le navigateur ; l'ouvrir puis « Installer » (Android le demande, c'est normal). Seul le numéro de version est demandé à GitHub, aucune photo ne part.
- **Mettre à jour (côté code)** : modifier le code dans ce dossier, changer `versionName` dans `app/build.gradle.kts`, `version.json` et ce journal, puis push. GitHub compile tout seul (`.github/workflows/range-photos-android.yml`, ~5 min) et publie l'APK dans *Releases*.
- **Les proches** : après l'analyse, l'écran « Mes proches » montre les personnes qui reviennent ; taper un prénom les mémorise. Les prénoms sont aussi écrits sur la carte SD (`Photos rangées/mes-proches.json`) : au changement de téléphone, réinstaller l'appli et choisir la même carte. « Gérer mes proches » (écran d'accueil) permet d'oublier un prénom.
- **Piège connu** : un fichier ne peut pas être dans deux dossiers sur une carte SD ; l'option de l'aperçu « Copier aussi… » fait des copies (affichées avec leur poids). L'annulation supprime ces copies.
- **Piège connu** : la reconnaissance a été vérifiée sur ordinateur (modèle SFace, ressemblance ≈ 0,76 même personne, 0,1 à 0,3 deux personnes), jamais encore sur un vrai téléphone.
- **Piège connu** : Android demande de choisir le dossier via son sélecteur ; choisir la carte SD ou son dossier DCIM.

## Historique
- 07/10/2026 : V1 (0.1.0), première version du code ajoutée au dépôt. Jamais compilée avant son arrivée ici ; la première compilation GitHub fait office de premier test.
- 07/10/2026 : V2 (0.2.0), reconnaissance des proches : modèle SFace int8 (9,9 Mo, licence Apache 2.0, `THIRD_PARTY_NOTICES.md`) avec ONNX Runtime, redressement des visages sur 4 repères ML Kit (yeux + coins de la bouche, vérifié contre OpenCV), regroupement, écran « Mes proches », rangement `Portraits/Prénom/année`, copies pour les groupes, journal d'annulation étendu (M = déplacement, C = copie). Tests unitaires ajoutés (regroupement, reconnaissance, alignement, mémoire).
- 07/10/2026 : V3 (0.3.0), nouvel écran d'accueil (couleurs, cartes, liste des proches résumée « X proches reconnus »), nouvelle icône, carte « Mise à jour » avec numéro de version et recherche de la dernière release GitHub (permission internet ajoutée, uniquement pour ce numéro). Test unitaire du comparateur de versions.
- 07/10/2026 : V4 (0.4.0), bouton « Relancer la recherche » (retient le dernier dossier), affichage du nombre de photos déjà rangées dans « Photos rangées » (ignorées par la recherche, c'est pourquoi le total peut être bien plus petit que le nombre de photos de la carte).
