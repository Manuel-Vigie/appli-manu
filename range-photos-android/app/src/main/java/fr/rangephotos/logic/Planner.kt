package fr.rangephotos.logic

import fr.rangephotos.model.Category
import fr.rangephotos.model.CategoryKind
import fr.rangephotos.model.PhotoInfo
import java.time.Instant
import java.time.ZoneId

/**
 * Une photo, le dossier où elle va (sous « Photos rangées »), et les dossiers où en mettre une copie
 * (photos où apparaît un proche : copie dans son dossier Portraits/<Prénom>/<année>).
 */
data class PlannedMove(
    val photo: PhotoInfo,
    val folder: List<String>,
    val copies: List<List<String>> = emptyList(),
)

/**
 * Décide du dossier de chaque photo, en cascade :
 * 1. Randonnées / <sortie> (avec un sous-dossier Portraits / <Prénom, Solo ou Groupe> pour les photos de personnes)
 * 2. Portraits / <Prénom, Solo ou Groupe> / <année>
 * 3. Dossiers de l'utilisateur (Codes-barres, Véhicules, Lieux…) : <nom> / <année> (ou <nom> / <ville>)
 * 4. Captures d'écran / <année>
 * 5. Photos / <année> / <mois>
 *
 * Une photo avec un seul visage reconnu va dans le dossier de cette personne ; avec plusieurs visages, dans « Groupe ».
 * Si [copyToPeople] est vrai, chaque proche reconnu a en plus une copie dans Portraits/<Prénom>/<année>
 * (un fichier ne peut pas être à deux endroits sur une carte SD : on le copie).
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
        copyToPeople: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
        categories: List<Category> = emptyList(),
    ): List<PlannedMove> = photos.map { photo ->
        val date = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
        val year = date.year.toString()
        val hike = HikeDetector.hikeFor(photo, hikes, zone)
        val hasPortrait = photo.faces.isNotEmpty()
        // Les dossiers de l'utilisateur passent après les randonnées et les portraits, avant captures d'écran et dates.
        val category = if (hike == null && !hasPortrait) categories.firstOrNull { it.matches(photo) } else null

        val folder = when {
            hike != null -> {
                val base = listOf(HIKES, sanitize(hikeFolderNames[hike] ?: hike.start.toString()))
                if (hasPortrait) base + PORTRAITS + portraitFolder(photo) else base
            }
            hasPortrait -> listOf(PORTRAITS, portraitFolder(photo), year)
            category != null -> categoryFolder(category, photo, year)
            photo.isScreenshot -> listOf(SCREENSHOTS, year)
            else -> listOf(PHOTOS, year, "%02d - %s".format(date.monthValue, MONTHS[date.monthValue - 1]))
        }

        val copies = if (copyToPeople) {
            photo.faces.mapNotNull { it.person }.distinct()
                .map { listOf(PORTRAITS, sanitize(it), year) }
                .filter { it != folder }
        } else {
            emptyList()
        }
        PlannedMove(photo, folder, copies)
    }

    private fun categoryFolder(category: Category, photo: PhotoInfo, year: String): List<String> {
        val name = sanitize(category.name)
        return if (category.kind == CategoryKind.PLACE) listOf(name, sanitize(photo.place ?: "Sans nom")) else listOf(name, year)
    }

    /** Un visage reconnu : le prénom ; un visage inconnu : « Solo » ; plusieurs visages : « Groupe ». */
    private fun portraitFolder(photo: PhotoInfo): String {
        if (photo.faces.size >= 2) return GROUP
        return photo.faces.first().person?.let { sanitize(it) } ?: SOLO
    }

    /** Retire les caractères interdits dans les noms de dossiers (FAT/exFAT, Android). */
    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().trimEnd('.').ifEmpty { "Sans nom" }
}
