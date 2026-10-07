# Mes Photos (Android) — journal

Appli simple : un bouton pour ranger la carte SD par date, un bouton pour regarder.

## Historique
- V1 (07/10/2026) : création. Rangement par date (Journées / année / mois / jour - ville), « Date incertaine » pour les photos sans date fiable, lot test de 10, vérification complète, nettoyage des dossiers vides à part, galerie intégrée. Carte SD uniquement.
- V2 (07/10/2026) : nouveau look (thème vert en dégradé, mode sombre, écrans retravaillés). Galerie : bouton « Choisir » (ou appui long) pour sélectionner plusieurs photos et/ou dossiers entiers, puis « Mettre à la corbeille » avec confirmation. La corbeille est un dossier caché `Photos rangées/.Corbeille` avec un index : rien n'est effacé, on peut tout remettre à sa place (bouton « Annuler » juste après, ou écran Corbeille depuis l'accueil). Suppression définitive seulement depuis l'écran Corbeille, après une seconde confirmation. Bouton corbeille aussi dans la visionneuse.
- V3 (07/10/2026) : recherche par mots (ville, mois, année, nom de fichier ; sans accents ni majuscules) sur tout « Photos rangées », avec choix multiple dans les résultats. « À l'écart » : photos ou dossiers choisis à la main (aucune détection automatique, l'appli ne regarde jamais le contenu) déplacés dans `Photos rangées/À l'écart` (même arborescence, fichier `.nomedia`, index caché `.index-mes-photos.tsv`) ; ce dossier est ignoré par la galerie, la recherche et le rangement ; remise en place depuis l'accueil ou avec « Annuler ». Corbeille et « À l'écart » partagent la même mécanique (classe `Trash`).
