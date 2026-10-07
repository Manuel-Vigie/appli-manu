package fr.mesphotos.logic

import fr.mesphotos.model.PhotoInfo

/**
 * Lors d'un reclassement complet, les copies de proches faites par l'appli (même nom, même taille)
 * seraient reprises comme de nouvelles photos. On ne garde qu'un exemplaire par photo ;
 * les autres exemplaires ne sont ni déplacés ni supprimés.
 */
object Duplicates {
    private val SUFFIX = Regex("""\s\(\d+\)(?=\.[^.]*$|$)""")

    /** Retourne les photos à classer et le nombre d'exemplaires laissés en place. */
    fun keepOnePerPhoto(photos: List<PhotoInfo>): Pair<List<PhotoInfo>, Int> {
        val kept = LinkedHashMap<String, PhotoInfo>()
        val result = ArrayList<PhotoInfo>(photos.size)
        var left = 0
        val indexOf = HashMap<String, Int>()
        for (photo in photos) {
            if (photo.size <= 0L) {
                result += photo
                continue
            }
            val key = photo.name.replace(SUFFIX, "").lowercase() + "|" + photo.size
            val previous = kept[key]
            if (previous == null) {
                kept[key] = photo
                indexOf[key] = result.size
                result += photo
            } else if (rank(photo) < rank(previous)) {
                // Mieux placé comme « original » : il remplace l'exemplaire déjà retenu.
                kept[key] = photo
                result[indexOf.getValue(key)] = photo
                left++
            } else {
                left++
            }
        }
        return result to left
    }

    /** Plus petit = plus probablement l'original. Une copie de proche est dans Portraits/<Prénom>/…, hors Solo et Groupe. */
    private fun rank(photo: PhotoInfo): Int {
        val folder = photo.currentFolder ?: return 0 // pas encore rangée : c'est l'original
        val isPersonCopy = folder.size >= 2 && folder[0] == Planner.PORTRAITS &&
            folder[1] != Planner.SOLO && folder[1] != Planner.GROUP
        return if (isPersonCopy) 2 else 1
    }
}
