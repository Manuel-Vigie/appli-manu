package fr.mesphotos.labels

import java.io.File

/** Mémoire « fichier → réponse » : une photo déjà analysée (même date, même taille) n'est pas ré-analysée. */
class SimpleCache(private val store: File) {
    private val entries = HashMap<String, String>()
    private var loaded = false

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        if (!store.isFile) return
        runCatching {
            store.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 4) entries[p[0] + "\t" + p[1] + "\t" + p[2]] = p[3]
            }
        }
    }

    private fun key(file: File) = file.absolutePath + "\t" + file.lastModified() + "\t" + file.length()

    @Synchronized
    fun get(file: File): String? {
        load()
        return entries[key(file)]
    }

    @Synchronized
    fun put(file: File, value: String) {
        load()
        entries[key(file)] = value
        runCatching {
            store.parentFile?.mkdirs()
            store.appendText(file.absolutePath + "\t" + file.lastModified() + "\t" + file.length() + "\t" + value.replace('\n', ' ') + "\n")
        }
    }
}
