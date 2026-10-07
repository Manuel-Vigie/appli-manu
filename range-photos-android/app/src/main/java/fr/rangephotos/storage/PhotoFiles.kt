package fr.rangephotos.storage

import java.io.File

/** Liste les fichiers photo d'un endroit (sans toucher à leur contenu). */
object PhotoFiles {

    private val MIME_BY_EXTENSION = mapOf(
        "jpg" to "image/jpeg", "jpeg" to "image/jpeg", "png" to "image/png",
        "webp" to "image/webp", "heic" to "image/heic", "heif" to "image/heif",
    )

    /** [relative] : chemin sous « Photos rangées » si la photo y est déjà, sinon null. */
    class Found(val file: File, val relative: List<String>?)

    fun mimeOf(file: File): String? = MIME_BY_EXTENSION[file.extension.lowercase()]

    /**
     * Par défaut, les photos déjà rangées ne sont pas reprises ; avec [includeSorted] elles le sont,
     * pour un reclassement complet. Les dossiers cachés (nom commençant par un point) et « Android » sont ignorés.
     */
    fun list(place: Place, includeSorted: Boolean, onFound: (Int) -> Unit = {}): List<Found> {
        val result = ArrayList<Found>()
        val output = place.outputDir

        fun walk(dir: File, relative: List<String>?, depth: Int) {
            if (depth > 30) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                val name = child.name
                if (name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (relative == null && child == output) continue
                    if (dir == place.root && name == "Android") continue
                    walk(child, relative?.plus(name), depth + 1)
                } else if (mimeOf(child) != null && child.length() > 0) {
                    result += Found(child, relative)
                    if (result.size % 50 == 0) onFound(result.size)
                }
            }
        }

        for (root in place.scanRoots) walk(root, null, 0)
        if (includeSorted && output.isDirectory) walk(output, emptyList(), 0)
        onFound(result.size)
        return result
    }

    /** (photos à ranger, photos déjà rangées). */
    fun counts(place: Place): Pair<Int, Int> {
        val all = list(place, includeSorted = true)
        val sorted = all.count { it.relative != null }
        return (all.size - sorted) to sorted
    }
}
