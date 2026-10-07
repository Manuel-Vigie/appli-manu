package fr.rangephotos.model

enum class CategoryKind { BARCODE, SUBJECTS, PLACE }

/**
 * Un dossier voulu par l'utilisateur : « Codes-barres », « Véhicules », « Lieux »…
 * [labels] : noms (en minuscules) des sujets reconnus par le modèle d'images qui font entrer une photo ici.
 */
data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val labels: Set<String> = emptySet(),
    val enabled: Boolean = false,
    val builtIn: Boolean = false,
    val minConfidence: Float = 0.6f,
) {
    fun matches(photo: PhotoInfo): Boolean = when (kind) {
        CategoryKind.BARCODE -> photo.hasBarcode
        CategoryKind.SUBJECTS -> photo.labels.any { (label, confidence) ->
            confidence >= minConfidence && label.lowercase() in labels
        }
        CategoryKind.PLACE -> photo.place != null
    }

    /** Court texte pour l'écran « Mes dossiers ». */
    fun describe(): String = when (kind) {
        CategoryKind.BARCODE -> "Photos qui contiennent un code-barres ou un QR code."
        CategoryKind.PLACE -> "Un sous-dossier par ville, d'après le lieu GPS de la photo (nécessite internet)."
        CategoryKind.SUBJECTS -> "Photos où l'appli reconnaît : " + Subjects.namesFor(labels).joinToString(", ") + "."
    }
}
