# Bibliothèque Photo

Outil pour ranger des photos et vidéos par randonnée et par date (année/mois, jour, appareil, type) et les enregistrer en ZIP.
Tout se passe sur le téléphone : aucune photo n'est envoyée sur le serveur. Fichier unique, sans donnée personnelle.

- 06/10/2026 : ajout au dépôt (version reçue de Manuel, non modifiée).
- 06/10/2026 : rendue installable (manifeste, icônes, service worker `biblio-photo-V1`, balises dans la page).
- 06/10/2026 : icône recentrée, cache biblio-photo-V2.
- 06/10/2026 : manifeste aligné sur celui de carnet-sante (id, description, orientation) ; cache V3. Installation encore refusée sur le téléphone de Manuel (« Impossible d'installer »), cause inconnue.
- 06/10/2026 : copie à une nouvelle adresse (ma-bibliotheque-photo) pour contourner un état bloqué de Chrome sur l'ancienne.
- 06/10/2026 : V4, pastille de mise à jour en haut à droite (version, date, bouton Actualiser) ; `version.json` ajouté ; cache biblio-photo-V4. Mise à jour : modifier le html, `version.json`, puis `python3 outils/poser-pastille.py ma-bibliotheque-photo`.
- 07/10/2026 : V5, nouveau classement « Randonnées + date » (par défaut). La page lit la position GPS des photos (EXIF), repère les randonnées (au moins 5 photos sur plus d'une heure, à plus de 15 km du domicile deviné ; sans domicile connu, plus de 1,5 km de déplacement), fusionne les journées voisines et crée `Randonnées/Rando AAAA-MM-JJ/`. Les photos et vidéos sans position y sont rattachées par la date ; les captures d'écran restent à part. Aucune donnée envoyée (pas de nom de lieu). Piège : sur certains téléphones la galerie retire le GPS, utiliser « Parcourir les fichiers ». Cache biblio-photo-V5. Portraits (visages) : pas encore faits, demandent un modèle de détection de visages.
- 07/10/2026 : V6, correctifs après un essai réel sur téléphone (seulement 47 photos rangées, pas de miniatures) : miniatures avec plusieurs méthodes et délai maximum, affichage du nombre de fichiers reçus, fichier illisible ignoré sans tout bloquer, ZIP qui indique le nombre de fichiers rangés ; cache biblio-photo-V6.
