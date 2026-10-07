# Mentions de licence

La recherche automatique de personnes nues utilise le modèle **NudeNet 320n** (version 3.4.2, fichier `app/src/main/assets/nudenet/320n.onnx`),
de notAI-tech (https://github.com/notAI-tech/NudeNet), distribué avec le texte de licence **GNU AGPL-3.0**
(voir `licenses/NudeNet-LICENSE-AGPL-3.0.txt`). Le modèle tourne **sur le téléphone** : aucune photo n'est envoyée nulle part.
Le code source complet de l'appli est public dans ce dépôt, ce qui permet de respecter cette licence.

Moteur d'exécution du modèle : ONNX Runtime (Microsoft), licence MIT.

La recherche par visage utilise deux modèles de l'OpenCV Zoo (https://github.com/opencv/opencv_zoo), qui tournent aussi **sur le téléphone** :
- **YuNet** (détection des visages, `app/src/main/assets/faces/yunet.onnx`), de Shiqi Yu, licence MIT (`licenses/YuNet-LICENSE-MIT.txt`) ;
- **SFace** (reconnaissance, `app/src/main/assets/faces/sface.onnx`, version 2021dec), de Zhong et al., licence Apache-2.0 (`licenses/SFace-OpenCVZoo-LICENSE-Apache-2.0.txt`).
Les empreintes de visages sont gardées seulement dans la mémoire privée de l'appli (fichier `visages.tsv`), jamais envoyées.
