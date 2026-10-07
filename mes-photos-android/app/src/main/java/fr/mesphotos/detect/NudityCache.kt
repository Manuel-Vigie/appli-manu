package fr.mesphotos.detect

import java.io.File

/**
 * Mémoire des photos déjà regardées : on ne refait pas le travail à chaque recherche, et on retrouve les suggestions
 * si l'appli a été fermée. Une ligne par photo : chemin, date du fichier, taille, score (0 à 1) ; -1 = « pas concernée »
 * (la personne a retiré la photo des suggestions). Si le fichier change, l'entrée ne compte plus.
 * Ce fichier reste dans la mémoire privée de l'appli.
 */
class NudityCache(private val store: File) {

    class Entry(val path: String, val modified: Long, val size: Long, val score: Float)

    private val entries = LinkedHashMap<String, Entry>()

    fun load() {
        entries.clear()
        if (!store.isFile) return
        for (line in store.readLines()) {
            val parts = line.split('\t')
            if (parts.size != 4) continue
            val modified = parts[1].toLongOrNull() ?: continue
            val size = parts[2].toLongOrNull() ?: continue
            val score = parts[3].toFloatOrNull() ?: continue
            entries[parts[0]] = Entry(parts[0], modified, size, score)
        }
    }

    /** L'entrée de [file] si elle est encore valable (même date, même taille), sinon null. */
    fun get(file: File): Entry? {
        val e = entries[file.absolutePath] ?: return null
        return if (e.modified == file.lastModified() && e.size == file.length()) e else null
    }

    fun put(file: File, score: Float) = write(file, score)

    /** La personne dit « ce n'est pas ça » : la photo ne sera plus proposée (tant qu'elle ne change pas). */
    fun dismiss(files: List<File>) {
        for (f in files) write(f, DISMISSED)
    }

    /** Les photos dont le score atteint [threshold], les plus probables d'abord. Seules les entrées encore valables comptent. */
    fun hits(threshold: Float): List<Entry> =
        entries.values
            .filter { it.score >= threshold }
            .filter { e -> File(e.path).let { it.isFile && it.lastModified() == e.modified && it.length() == e.size } }
            .sortedByDescending { it.score }

    private fun write(file: File, score: Float) {
        val e = Entry(file.absolutePath, file.lastModified(), file.length(), score)
        entries[e.path] = e
        store.parentFile?.mkdirs()
        store.appendText("${e.path}\t${e.modified}\t${e.size}\t${e.score}\n")
    }

    companion object {
        const val DISMISSED = -1f
    }
}
