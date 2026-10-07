package fr.mesphotos.faces

import java.io.File

/**
 * Mémoire des visages déjà vus : une ligne par photo, avec pour chaque visage sa place dans l'image et son empreinte
 * (128 nombres). Une photo sans visage est aussi retenue (pour ne pas la regarder deux fois).
 * Ligne : chemin ⇥ date du fichier ⇥ taille ⇥ [x,y,largeur,hauteur,empreinte] ⇥ … Si le fichier change, l'entrée ne compte plus.
 * Ce fichier reste dans la mémoire privée de l'appli.
 */
class FaceCache(private val store: File) {

    class Face(val box: FloatArray, val embedding: FloatArray)

    class Entry(val path: String, val modified: Long, val size: Long, val faces: List<Face>)

    class Hit(val path: String, val score: Float)

    private val entries = LinkedHashMap<String, Entry>()

    fun load() {
        entries.clear()
        if (!store.isFile) return
        store.forEachLine { line ->
            val entry = parse(line)
            if (entry != null) entries[entry.path] = entry
        }
    }

    private fun parse(line: String): Entry? {
        val parts = line.split('\t')
        if (parts.size < 3) return null
        val modified = parts[1].toLongOrNull() ?: return null
        val size = parts[2].toLongOrNull() ?: return null
        val faces = ArrayList<Face>()
        for (field in parts.drop(3)) {
            val f = field.split(',')
            if (f.size != 5) return null
            val box = FloatArray(4)
            for (i in 0 until 4) box[i] = f[i].toFloatOrNull() ?: return null
            val embedding = FaceMath.decodeEmbedding(f[4]) ?: return null
            faces += Face(box, embedding)
        }
        return Entry(parts[0], modified, size, faces)
    }

    /** L'entrée de [file] si elle est encore valable (même date, même taille), sinon null. */
    fun get(file: File): Entry? {
        val e = entries[file.absolutePath] ?: return null
        return if (e.modified == file.lastModified() && e.size == file.length()) e else null
    }

    fun put(file: File, faces: List<Face>) {
        val e = Entry(file.absolutePath, file.lastModified(), file.length(), faces)
        entries[e.path] = e
        store.parentFile?.mkdirs()
        val line = buildString {
            append(e.path).append('\t').append(e.modified).append('\t').append(e.size)
            for (f in faces) {
                append('\t')
                append(f.box.joinToString(",")).append(',').append(FaceMath.encode(f.embedding))
            }
            append('\n')
        }
        store.appendText(line)
    }

    /**
     * Les photos où un visage ressemble au moins à [threshold] à [query] (empreinte normalisée), les plus ressemblantes d'abord.
     * [accept] écarte des chemins (hors « Photos rangées », corbeille…). Seuls les fichiers encore identiques comptent.
     */
    fun search(query: FloatArray, threshold: Float, accept: (String) -> Boolean = { true }): List<Hit> {
        val hits = ArrayList<Hit>()
        for (e in entries.values) {
            var best = -1f
            for (f in e.faces) {
                val s = FaceMath.cosine(query, f.embedding)
                if (s > best) best = s
            }
            if (best >= threshold && accept(e.path)) {
                val file = File(e.path)
                if (file.isFile && file.lastModified() == e.modified && file.length() == e.size) hits += Hit(e.path, best)
            }
        }
        return hits.sortedByDescending { it.score }
    }
}
