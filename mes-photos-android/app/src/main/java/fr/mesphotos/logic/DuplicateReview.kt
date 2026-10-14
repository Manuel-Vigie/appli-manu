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
        /** Mode « large » : aussi les prises de vue d'une même scène faites à moins de 5 minutes d'écart, même cadrées différemment. */
        loose: Boolean = false,
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
        return merge(groups, files, root, similarity, onProgress, loose).sortedBy { it.keep.name.lowercase() }
    }

    /** Regroupe les copies exactes déjà trouvées et les photos qui se ressemblent (même image, enregistrée deux fois). */
    private fun merge(exact: List<DupGroup>, files: List<File>, root: File, similarity: (File) -> ImageInfo?, onProgress: (Int, Int) -> Unit, loose: Boolean): List<DupGroup> {
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
        // Même date ET même heure (à la seconde) écrites dans la photo par l'appareil : c'est la même prise de vue (copie, réduction, renvoi…).
        // Petite vérification d'image en plus, pour ne pas mélanger deux appareils qui auraient photographié à la même seconde.
        for (same in infos.filter { it.second.exif > 0L }.groupBy { it.second.exif }.values) {
            if (same.size < 2) continue
            for (i in same.indices) for (j in i + 1 until same.size) {
                val a = same[i].second
                val b = same[j].second
                if (ImageHash.similar(a, b, maxBits = 26, checkRatio = false) || ImageHash.colorMatch(a, b) >= 0.6) union(same[i].first, same[j].first)
            }
        }
        if (loose) {
            // Prises de vue rapprochées dans le temps (rafale, deux essais du même cadre) : tolérance plus grande, proportions libres.
            // Photos dont on connaît l'heure de PRISE DE VUE (EXIF ou nom du fichier) : à moins de 5 minutes d'écart.
            val dated = infos.filter { it.second.taken > 0L }.sortedBy { it.second.taken }
            for (i in dated.indices) {
                var j = i + 1
                while (j < dated.size && dated[j].second.taken - dated[i].second.taken <= 300_000L) {
                    val a = dated[i].second
                    val b = dated[j].second
                    if (ImageHash.similar(a, b, maxBits = 22, checkRatio = false) || ImageHash.colorMatch(a, b) >= 0.8) union(dated[i].first, dated[j].first)
                    j++
                }
            }
            // Sans date de prise de vue lisible (la date du fichier n'est que le jour du transfert) : on compare avec toutes les autres, plus strictement.
            val undated = infos.filter { it.second.taken <= 0L }
            for (u in undated) for (o in infos) {
                if (u.first == o.first) continue
                if (ImageHash.similar(u.second, o.second, maxBits = 14, checkRatio = false) || ImageHash.colorMatch(u.second, o.second) >= 0.9) union(u.first, o.first)
            }
        }
        val clusters = LinkedHashMap<File, MutableList<File>>()
        val inGroup = HashSet<File>()
        exact.forEach { inGroup += it.files }
        (infos.map { it.first } + inGroup).distinct().forEach { clusters.getOrPut(root(it)) { ArrayList() } += it }
        val byFile = infos.toMap()
        return clusters.values.filter { it.size > 1 }.map { DupGroup(it, best(it, root, byFile)) }
    }

    /**
     * L'exemplaire le plus « original » : hors du dossier Doublons, dans un dossier de jour (pas « Date incertaine »),
     * sans « (2) » dans le nom, puis le nom le plus court, le plus ancien, et enfin l'ordre alphabétique des chemins.
     */
    fun best(files: List<File>, root: File, infos: Map<File, ImageInfo> = emptyMap()): File {
        fun first(f: File): String = f.toRelativeString(root).substringBefore('/')
        // Photos qui se ressemblent : on garde d'abord la plus nette et la mieux exposée (à 15 % près), puis la plus grande.
        val bestQuality = files.maxOf { infos[it]?.quality ?: 0.0 }
        val sharpEnough = if (bestQuality > 0.0) files.filter { (infos[it]?.quality ?: 0.0) >= bestQuality * 0.85 } else files
        val pool = sharpEnough.ifEmpty { files }
        val biggest = pool.maxOf { infos[it]?.let { i -> i.width.toLong() * i.height } ?: 0L }
        val finalists = if (biggest > 0L) pool.filter { (infos[it]?.let { i -> i.width.toLong() * i.height } ?: 0L) * 100 >= biggest * 95 }.ifEmpty { pool } else pool
        return finalists.sortedWith(
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
