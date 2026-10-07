package fr.rangephotos.model

import android.net.Uri

/** Une photo trouvée sur la carte SD, avec les informations utiles au classement. */
data class PhotoInfo(
    val uri: Uri,
    val parentUri: Uri?,
    val name: String,
    val mimeType: String,
    /** Date de prise de vue en millisecondes (EXIF, sinon nom de fichier, sinon date du fichier). */
    val takenAt: Long,
    val lat: Double?,
    val lon: Double?,
    val isScreenshot: Boolean,
    /** Nombre de visages nets détectés (rempli après la détection de visages). */
    val faceCount: Int = 0,
) {
    val hasGps: Boolean get() = lat != null && lon != null
}
