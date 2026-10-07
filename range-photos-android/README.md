# Range Photos

Application Android qui range automatiquement les photos d'une carte SD (ou de n'importe quel dossier)
dans de vrais dossiers, sans rien envoyer sur internet.

## Ce que fait l'application

Chaque photo est classée, dans cet ordre de priorité :

| Cas | Dossier |
| --- | --- |
| Photo prise pendant une randonnée | `Randonnées/2026-09-14 Castellane/` |
| … et c'est un portrait | `Randonnées/2026-09-14 Castellane/Portraits/Julie/` (ou `Solo`, ou `Groupe`) |
| Portrait d'un proche nommé | `Portraits/Julie/2026/` |
| Portrait d'une personne inconnue | `Portraits/Solo/2026/` |
| Photo de groupe (plusieurs visages) | `Portraits/Groupe/2026/` |
| Capture d'écran | `Captures d'écran/2026/` |
| Tout le reste | `Photos/2026/03 - mars/` |

Tout est créé dans un dossier `Photos rangées` à la racine de la carte SD (ou de la mémoire du téléphone).

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

### Portraits et reconnaissance des proches

Tout se passe sur le téléphone, rien n'est envoyé sur internet :

1. **ML Kit** (modèle embarqué) trouve les visages et leurs repères (yeux, bouche). Les visages minuscules (personnes
   au loin sur un sentier) et ceux de profil sont ignorés pour la reconnaissance.
2. Chaque visage est redressé puis transformé en **128 nombres** (son « empreinte ») par le modèle **SFace**
   (voir `THIRD_PARTY_NOTICES.md`), avec ONNX Runtime. Deux visages de la même personne ont des empreintes proches.
3. Les visages qui se ressemblent sont regroupés. L'écran **« Mes proches »** montre les personnes qui reviennent
   sur au moins 2 photos : vous tapez un prénom (le même prénom pour deux groupes les fusionne), ou vous laissez vide.
4. Les prénoms sont mémorisés (quelques empreintes par personne, jamais de photo) : aux rangements suivants,
   les proches sont reconnus automatiquement. Une copie de cette mémoire est écrite sur la carte SD dans
   `Photos rangées/mes-proches.json` : **en changeant de téléphone, réinstallez l'appli et choisissez la même carte,
   les prénoms sont retrouvés.**
5. Un fichier ne peut pas être à deux endroits sur une carte SD. Pour qu'un dossier `Portraits/Julie` contienne toutes
   ses photos, l'appli propose (activé par défaut, avec la taille en plus affichée dans l'aperçu) de **copier** chaque
   photo où apparaît un proche dans son dossier. L'annulation supprime ces copies et remet les originaux en place.

Fiabilité : mesurée sur ordinateur avec de vrais visages, la ressemblance est d'environ 0,76 pour la même personne et
de 0,10 à 0,29 pour deux personnes différentes. Sur téléphone elle baissera avec les lunettes de soleil, le contre-jour
ou le profil : c'est pourquoi la reconnaissance reste prudente (elle préfère ne rien dire plutôt que se tromper),
et pourquoi vous voyez l'aperçu avant tout déplacement.

### Limites connues

- Seules les photos (JPEG, PNG, WebP, HEIC) sont rangées ; les vidéos restent où elles sont.
- La reconnaissance n'a jamais été essayée sur de vraies photos de téléphone avant cette version : commencez par un petit dossier de test.
- Les photos sans GPS prises un jour de randonnée sont rattachées à cette randonnée ; les photos sans aucune date
  EXIF utilisent la date du nom de fichier, sinon la date du fichier.
- L'appli demande l'autorisation Android « gérer tous les fichiers » (une fois) : c'est ce qui permet de vraiment déplacer les photos de la carte SD. Elle ne s'en sert que pour ranger vos photos.

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
├── storage/                    carte SD / mémoire du téléphone, autorisation, liste des photos
├── scan/PhotoScanner.kt        lecture EXIF (date, GPS)
├── logic/HikeDetector.kt       détection des randonnées
├── logic/HikeNamer.kt          nom du lieu des randonnées
├── logic/Planner.kt            décide du dossier de chaque photo
├── face/FaceAnalyzer.kt        visages (ML Kit) + redressement + empreinte
├── face/FaceAlign.kt           calcul du redressement d'un visage (testé)
├── face/FaceEmbedder.kt        modèle SFace (ONNX Runtime)
├── people/FaceMatching.kt      regroupement et reconnaissance (testés)
├── people/PeopleStore.kt       mémoire des prénoms
├── people/PeopleSync.kt        copie des prénoms sur la carte SD
├── organize/Organizer.kt       déplacement sécurisé + journal + annulation
└── ui/                         écrans (Jetpack Compose) et ViewModel
```

## Feuille de route

- [x] Reconnaissance par personne (`Portraits/Julie/`), écran « Mes proches », mémoire sur la carte SD
- [ ] Corriger une erreur de reconnaissance photo par photo
- [ ] Dossier « À vérifier » : doublons, photos floues
- [ ] Paysages, nourriture, animaux (classification d'images embarquée)
- [ ] Récap par randonnée et carte des sorties
- [ ] Vidéos
