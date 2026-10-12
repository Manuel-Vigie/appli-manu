package fr.mesphotos.logic

import fr.mesphotos.gallery.ImageHash
import fr.mesphotos.gallery.ImageInfo
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
        /** Si fourni : les photos qui se RESSEMBLENT (même image réenregistrée ou redimensionnée) sont aussi regroupées. */
        similarity: ((File) -> ImageInfo?)? = null,
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
        if (similarity == null) return groups.sortedBy { it.keep.name.lowercase() }
        return merge(groups, files, root, similarity, onProgress).sortedBy { it.keep.name.lowercase() }
    }

    /** Regroupe les copies exactes déjà trouvées et les photos qui se ressemblent (même image, enregistrée deux fois). */
    private fun merge(exact: List<DupGroup>, files: List<File>, root: File, similarity: (File) -> ImageInfo?, onProgress: (Int, Int) -> Unit): List<DupGroup> {
        val parent = HashMap<File, File>()
        fun root(f: File): File { var x = f; while (parent[x] != null && parent[x] != x) x = parent[x]!!; return x }
        fun union(a: File, b: File) { val ra = root(a); val rb = root(b); if (ra != rb) parent[ra] = rb }
        for (g in exact) for (f in g.files.drop(1)) union(g.files.first(), f)
        val infos = ArrayList<Pair<File, ImageInfo>>()
        val images = files.filter { it.length() > 0 }
        for ((i, f) in images.withIndex()) {
            similarity(f)?.let { infos += f to it }
            if (i % 20 == 0) onProgress(i, images.size)
        }
        for (i in infos.indices) for (j in i + 1 until infos.size) {
            if (ImageHash.similar(infos[i].second, infos[j].second)) union(infos[i].first, infos[j].first)
        }
        val clusters = LinkedHashMap<File, MutableList<File>>()
        val inGroup = HashSet<File>()
        exact.forEach { inGroup += it.files }
        (infos.map { it.first } + inGroup).distinct().forEach { clusters.getOrPut(root(it)) { ArrayList() } += it }
        return clusters.values.filter { it.size > 1 }.map { DupGroup(it, best(it, root)) }
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
