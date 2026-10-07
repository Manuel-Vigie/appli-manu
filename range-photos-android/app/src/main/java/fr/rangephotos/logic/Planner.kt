package fr.rangephotos.logic

import fr.rangephotos.model.PhotoInfo
import java.time.Instant
import java.time.ZoneId

/** Une photo et le chemin du dossier (sous le dossier de sortie) où elle doit aller. */
data class PlannedMove(val photo: PhotoInfo, val folder: List<String>)

/**
 * Décide du dossier de chaque photo, en cascade :
 * 1. Randonnées / <sortie> (avec un sous-dossier Portraits pour les photos de personnes)
 * 2. Portraits / Solo ou Groupe / <année>
 * 3. Captures d'écran / <année>
 * 4. Photos / <année> / <mois>
 */
object Planner {
    const val HIKES = "Randonnées"
    const val PORTRAITS = "Portraits"
    const val SOLO = "Solo"
    const val GROUP = "Groupe"
    const val SCREENSHOTS = "Captures d'écran"
    const val PHOTOS = "Photos"

    private val MONTHS = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )

    fun plan(
        photos: List<PhotoInfo>,
        hikes: List<Hike>,
        hikeFolderNames: Map<Hike, String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<PlannedMove> = photos.map { photo ->
        val date = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
        val year = date.year.toString()
        val hike = HikeDetector.hikeFor(photo, hikes, zone)
        val hasPortrait = photo.faceCount > 0

        val folder = when {
            hike != null -> {
                val base = listOf(HIKES, sanitize(hikeFolderNames[hike] ?: hike.start.toString()))
                if (hasPortrait) base + PORTRAITS else base
            }
            hasPortrait -> listOf(PORTRAITS, if (photo.faceCount == 1) SOLO else GROUP, year)
            photo.isScreenshot -> listOf(SCREENSHOTS, year)
            else -> listOf(PHOTOS, year, "%02d - %s".format(date.monthValue, MONTHS[date.monthValue - 1]))
        }
        PlannedMove(photo, folder)
    }

    /** Retire les caractères interdits dans les noms de dossiers (FAT/exFAT, Android). */
    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().trimEnd('.').ifEmpty { "Sans nom" }
}
