package fr.mesphotos.logic

import fr.mesphotos.model.PhotoInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Un fichier et le dossier (sous « Photos rangées ») où il doit aller. */
data class PlannedMove(val photo: PhotoInfo, val folder: List<String>)

/**
 * Une seule règle, simple : tout est rangé par date.
 * Journées / année / mois / jour (avec la ville si on la connaît), photos et vidéos ensemble.
 * Sans date fiable : Journées / Date incertaine / année / mois (pour ne jamais mal dater un souvenir).
 * Sans date fiable ET sans lieu (pas de GPS) : tous dans un seul dossier, Journées / Sans date ni lieu,
 * pour que la personne les trie elle-même.
 */
object Planner {
    const val PHOTOS = "Journées"
    const val UNDATED = "Date incertaine"
    const val NOTHING = "Sans date ni lieu"

    /** Copies exactes d'une photo déjà présente : rangées à part, jamais supprimées. */
    const val DUPLICATES = "Doublons"

    // Anciens dossiers de proches (ancienne appli) : leurs copies ne sont pas reclassées une seconde fois.
    const val PORTRAITS = "Portraits"
    const val SOLO = "Solo"
    const val GROUP = "Groupe"

    private val DAYS = listOf("lun", "mar", "mer", "jeu", "ven", "sam", "dim")

    private val MONTHS = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre",
    )

    fun plan(
        photos: List<PhotoInfo>,
        zone: ZoneId = ZoneId.systemDefault(),
        /** Ville où l'on était chaque jour. */
        dayPlaces: Map<LocalDate, String> = emptyMap(),
    ): List<PlannedMove> = photos.map { photo ->
        val date = Instant.ofEpochMilli(photo.takenAt).atZone(zone).toLocalDate()
        val month = "%02d - %s".format(date.monthValue, MONTHS[date.monthValue - 1])
        val folder = when {
            isOwnFolder(photo.currentFolder) -> photo.currentFolder!!
            photo.dateGuessed && !photo.hasGps -> listOf(PHOTOS, NOTHING)
            photo.dateGuessed -> listOf(PHOTOS, UNDATED, date.year.toString(), month)
            else -> listOf(PHOTOS, date.year.toString(), month, dayFolder(date, dayPlaces[date]))
        }
        PlannedMove(photo, folder)
    }

    /**
     * Un dossier choisi par la personne sous « Journées » (ex. Journées / Ex) : tout ce qui n'est pas une année,
     * « Date incertaine » ou « Sans date ni lieu ». Ce qu'elle y a rangé n'est plus jamais remis dans les dossiers par date.
     */
    fun isOwnFolder(current: List<String>?): Boolean {
        if (current == null || current.size < 2 || current[0] != PHOTOS) return false
        val top = current[1]
        return top != UNDATED && top != NOTHING && !top.matches(Regex("""\d{4}"""))
    }

    /** Toutes les copies exactes vont dans un seul dossier, « Doublons », pour les comparer facilement. */
    fun duplicateFolder(@Suppress("UNUSED_PARAMETER") photo: PhotoInfo): List<String> = listOf(DUPLICATES)

    /** « 10 - octobre » : le dossier du mois, comme le range « Ranger ». */
    fun monthFolder(date: LocalDate): String = "%02d - %s".format(date.monthValue, MONTHS[date.monthValue - 1])

    /** Début du nom d'un dossier de jour, sans la ville : « 10 octobre (sam) ». */
    fun dayPrefix(date: LocalDate): String = "%02d %s (%s)".format(date.dayOfMonth, MONTHS[date.monthValue - 1], DAYS[date.dayOfWeek.value - 1])

    /** « 14 mars (sam) - Nice » : classé par date dans l'explorateur de fichiers, avec la ville si on la connaît. */
    fun dayFolder(date: LocalDate, town: String?): String {
        val label = "%02d %s (%s)".format(date.dayOfMonth, MONTHS[date.monthValue - 1], DAYS[date.dayOfWeek.value - 1])
        return if (town.isNullOrBlank()) label else "$label - ${sanitize(town)}"
    }

    /** Retire les caractères interdits dans les noms de dossiers (FAT/exFAT, Android). */
    fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|]"""), "-").trim().trimEnd('.').ifEmpty { "Sans nom" }
}
