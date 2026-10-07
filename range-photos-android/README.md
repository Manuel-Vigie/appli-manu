# Range Photos

Application Android qui range automatiquement les photos d'une carte SD (ou de n'importe quel dossier)
dans de vrais dossiers, sans rien envoyer sur internet.

## Ce que fait l'application

Chaque photo est classée, dans cet ordre de priorité :

| Cas | Dossier |
| --- | --- |
| Photo prise pendant une randonnée | `Randonnées/2026-09-14 Castellane/` |
| … et c'est un portrait | `Randonnées/2026-09-14 Castellane/Portraits/` |
| Portrait (un visage bien visible) | `Portraits/Solo/2026/` |
| Photo de groupe (plusieurs visages) | `Portraits/Groupe/2026/` |
| Capture d'écran | `Captures d'écran/2026/` |
| Tout le reste | `Photos/2026/03 - mars/` |

Tout est créé dans un dossier `Photos rangées` à l'intérieur du dossier choisi.

- **Aperçu avant déplacement** : rien ne bouge tant que vous n'avez pas appuyé sur « Ranger maintenant ».
- **Sécurité** : les photos sont *déplacées* par le système (aucune copie, aucun risque de perte en cas
  d'interruption). Si le système ne le permet pas, l'appli copie, vérifie la taille, puis seulement supprime l'original.
- **Annulation** : chaque rangement est journalisé, le bouton « Annuler le dernier rangement » remet tout en place.
- **Les photos déjà rangées** (dossier `Photos rangées`) ne sont jamais retouchées : on peut relancer l'appli
  après chaque sortie pour ranger les nouvelles photos.

### Comment une randonnée est détectée

1. L'appli devine votre « domicile » : la zone où vous prenez des photos le plus de jours différents.
2. Une journée est une sortie si elle contient **au moins 5 photos géolocalisées à plus de 15 km du domicile**,
   réparties sur **au moins 1 heure**.
3. Les journées consécutives proches (moins de 40 km) sont fusionnées en une seule randonnée.
4. Le nom du lieu vient du service de géocodage d'Android (nécessite internet ; sinon le dossier porte juste la date).

Les seuils sont des constantes en haut de `HikeDetector.kt`, faciles à ajuster.

### Portraits

La détection des visages utilise ML Kit, modèle embarqué dans l'APK : tout se passe sur le téléphone.
Les visages minuscules (personnes au loin) sont ignorés.

**Limite actuelle :** l'appli distingue photos *solo* et *groupe*, mais ne reconnaît pas encore *qui* est sur la photo.
Le classement par personne (`Portraits/Julie/`) est la prochaine étape, voir la feuille de route.

### Limites connues

- Seules les photos (JPEG, PNG, WebP, HEIC) sont rangées ; les vidéos restent où elles sont.
- Les photos sans GPS prises un jour de randonnée sont rattachées à cette randonnée ; les photos sans aucune date
  EXIF utilisent la date du nom de fichier, sinon la date du fichier.
- Android impose de choisir le dossier via le sélecteur système : choisissez la carte SD (ou son dossier `DCIM`).

## Installer l'application (aussi sur un futur téléphone)

L'APK est compilé automatiquement par GitHub à chaque modification de ce dossier
(`.github/workflows/range-photos-android.yml`, à la racine du dépôt) et publié dans les *Releases*.

1. Sur GitHub : onglet **Releases** du dépôt → `range-photos-v…` → téléchargez `RangePhotos.apk`
   (ou onglet **Actions** → dernier lancement → **Artifacts**).
2. Ouvrez le fichier sur le téléphone et autorisez l'installation depuis cette source si Android le demande.

Tous les APK sont signés avec la même clé (`app/debug.keystore`, volontairement publique : c'est un projet
personnel installé hors Play Store). Une nouvelle version s'installe donc par-dessus l'ancienne.

## Compiler soi-même

Prérequis : Android Studio (ou JDK 17 + Android SDK).

```bash
./gradlew testDebugUnitTest   # tests du moteur de classement
./gradlew assembleRelease     # APK dans app/build/outputs/apk/release/
```

Ou simplement ouvrir le dossier dans Android Studio et lancer l'appli sur le téléphone.

## Structure du code

```
app/src/main/java/fr/rangephotos/
├── scan/PhotoScanner.kt        parcours du dossier + lecture EXIF (date, GPS)
├── logic/HikeDetector.kt       détection des randonnées
├── logic/HikeNamer.kt          nom du lieu des randonnées
├── logic/Planner.kt            décide du dossier de chaque photo
├── face/FaceCounter.kt         détection de visages (ML Kit, hors ligne)
├── organize/Organizer.kt       déplacement sécurisé + journal + annulation
└── ui/                         écrans (Jetpack Compose) et ViewModel
```

## Feuille de route

- [ ] Reconnaissance par personne (`Portraits/Julie/`) : modèle d'empreinte de visage (TFLite) + écran « Mes proches »
      pour nommer et corriger
- [ ] Dossier « À vérifier » : doublons, photos floues
- [ ] Paysages, nourriture, animaux (classification d'images embarquée)
- [ ] Récap par randonnée et carte des sorties
- [ ] Vidéos
