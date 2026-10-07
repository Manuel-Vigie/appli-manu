package fr.mesphotos.logic

import java.io.File

/** Des fichiers au contenu identique ; [keep] est celui que l'appli propose de garder. */
class DupGroup(val files: List<File>, val keep: File)

/**
 * Retrouve toutes les copies exactes dans « Photos rangées » (même contenu, quel que soit le nom ou le dossier)
 * et propose pour chaque groupe un exemplaire à garder. La personne vérifie avant que quoi que ce soit parte à la corbeille.
 */
object DuplicateReview {

    private val COPY_SUFFIX = Regex("""\s\(\d+\)(?=\.[^.]*$|$)""")
    private val UNSURE_FOLDERS = setOf(Planner.UNDATED, Planner.NOTHING)

    fun find(
        files: List<File>,
        root: File,
        fingerprint: (File) -> String? = Duplicates::fingerprintOf,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<DupGroup> {
        // Deux fichiers de tailles différentes ne sont jamais identiques : on ne compare que ceux de même taille.
        val sameSize = files.filter { it.length() > 0 }.groupBy { it.length() }.values.filter { it.size > 1 }
        val total = sameSize.sumOf { it.size }
        var done = 0
        val groups = ArrayList<DupGroup>()
        for (candidates in sameSize) {
            val byContent = LinkedHashMap<String, MutableList<File>>()
            for (file in candidates) {
                fingerprint(file)?.let { byContent.getOrPut(it) { ArrayList() } += file }
                done++
                onProgress(done, total)
            }
            for (same in byContent.values) {
                if (same.size > 1) groups += DupGroup(same, best(same, root))
            }
        }
        return groups.sortedBy { it.keep.name.lowercase() }
    }

    /**
     * L'exemplaire le plus « original » : hors du dossier Doublons, dans un dossier de jour (pas « Date incertaine »),
     * sans « (2) » dans le nom, puis le nom le plus court, le plus ancien, et enfin l'ordre alphabétique des chemins.
     */
    fun best(files: List<File>, root: File): File {
        fun first(f: File): String = f.toRelativeString(root).substringBefore('/')
        return files.sortedWith(
            compareBy<File>(
                { if (first(it) == Planner.DUPLICATES) 1 else 0 },
                { if (first(it) == Planner.PHOTOS && it.toRelativeString(root).split('/').getOrNull(1) in UNSURE_FOLDERS) 1 else 0 },
                { if (COPY_SUFFIX.containsMatchIn(it.name)) 1 else 0 },
                { it.name.length },
                { it.lastModified() },
                { it.absolutePath },
            ),
        ).first()
    }
}
