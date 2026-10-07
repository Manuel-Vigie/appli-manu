package fr.rangephotos.model

import android.net.Uri

/** Un visage trouvé dans une photo. */
class FaceInfo(
    /** Part de la photo occupée par le visage (0 à 1). */
    val area: Float,
    /** Empreinte du visage (128 nombres normalisés), ou null si le visage est de profil, trop petit ou illisible. */
    val embedding: FloatArray?,
    /** Petite vignette JPEG du visage (112×112), pour l'écran « Mes proches ». */
    val thumb: ByteArray?,
    /** Prénom, si la personne est reconnue ou nommée. */
    val person: String? = null,
) {
    fun withPerson(name: String?) = FaceInfo(area, embedding, thumb, name)
}

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
    /** Taille du fichier en octets. */
    val size: Long = 0L,
    /** Dossier actuel sous « Photos rangées » (ex. [Photos, 2025, 03 - mars]) si la photo est déjà rangée, sinon null. */
    val currentFolder: List<String>? = null,
    /** Chemin complet du fichier sur le téléphone. */
    val path: String? = null,
    /** Sujets reconnus dans la photo (nom ML Kit en anglais -> confiance), si un dossier personnalisé le demande. */
    val labels: Map<String, Float> = emptyMap(),
    /** La photo contient un code-barres ou un QR code. */
    val hasBarcode: Boolean = false,
    /** Nom de la ville (GPS), si un dossier « Lieux » est actif. */
    val place: String? = null,
    /** Vidéo (rangée par date avec les photos, jamais analysée pour les visages). */
    val isVideo: Boolean = false,
    /** Aucune date fiable (ni EXIF, ni nom, ni GPS) : la date du fichier est utilisée, elle peut être fausse. */
    val dateGuessed: Boolean = false,
    /** Visages détectés (rempli après l'analyse des visages). */
    val faces: List<FaceInfo> = emptyList(),
) {
    val hasGps: Boolean get() = lat != null && lon != null
}
