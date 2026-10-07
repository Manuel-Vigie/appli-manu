package fr.mesphotos.model

/** Une photo ou vidéo trouvée sur la carte SD, avec ce qu'il faut pour la ranger. */
data class PhotoInfo(
    val name: String,
    /** Date de prise de vue en millisecondes. */
    val takenAt: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    /** Taille du fichier en octets. */
    val size: Long = 0L,
    /** Dossier actuel sous « Photos rangées » si le fichier y est déjà (ex. [Journées, 2026, 03 - mars]), sinon null. */
    val currentFolder: List<String>? = null,
    /** Chemin complet du fichier. */
    val path: String? = null,
    val isVideo: Boolean = false,
    /** Aucune date fiable (ni EXIF, ni nom, ni GPS) : la date du fichier est utilisée, elle peut être fausse. */
    val dateGuessed: Boolean = false,
) {
    val hasGps: Boolean get() = lat != null && lon != null
}
