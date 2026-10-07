# Éléments tiers

## Modèle de reconnaissance de visages : SFace (version int8)

- Fichier : `app/src/main/assets/face_recognition_sface_2021dec_int8.onnx` (9,9 Mo)
- Source : OpenCV Zoo, https://github.com/opencv/opencv_zoo (dossier `models/face_recognition_sface`,
  commit `47534e27c9851bb1128ccc0102f1145e27f23f98`), licence Apache 2.0 (copie : `app/src/main/assets/LICENSE_sface_apache2.txt`).
- Empreinte SHA-256 : `2b0e941e6f16cc048c20aee0c8e31f569118f65d702914540f7bfdc14048d78a`
- Rôle : transforme un visage (image 112×112 alignée) en 128 nombres ; deux visages de la même personne donnent des
  nombres proches. Tout est calculé sur le téléphone avec ONNX Runtime.

## Bibliothèques

- ML Kit (détection de visages, modèle embarqué), Google : conditions des services ML Kit.
- ONNX Runtime pour Android, Microsoft : licence MIT.
- Jetpack Compose, AndroidX, Kotlin : licence Apache 2.0.
