package fr.mesphotos.logic

import java.io.File

/**
 * Les mots (noms de personnes, lieux…) que Manuel a associés à des photos SANS les renommer ni les déplacer.
 * Une photo est reconnue par sa taille et sa date de modification, donc elle reste retrouvée même si elle change de nom ou de dossier.
 * Un fichier texte dans la mémoire privée de l'appli : « taille ⇥ date ⇥ mots ».
 */
class PhotoTags(private val store: File) {
    private val words = HashMap<String, String>()
    private var loaded = false

    private fun key(file: File) = file.length().toString() + "\t" + file.lastModified()

    @Synchronized
    private fun load() {
        if (loaded) return
        loaded = true
        if (!store.isFile) return
        runCatching {
            store.forEachLine { line ->
                val p = line.split('\t')
                if (p.size >= 3) words[p[0] + "\t" + p[1]] = p[2]
            }
        }
    }

    /** Les mots associés à [file] (vide si aucun). */
    @Synchronized
    fun of(file: File): String {
        load()
        return words[key(file)].orEmpty()
    }

    /** Ajoute [newWords] aux mots de chaque photo de [files] (sans doublon). Retourne le nombre de photos touchées. */
    @Synchronized
    fun add(files: List<File>, newWords: List<String>): Int {
        load()
        var touched = 0
        for (file in files) {
            if (!file.isFile) continue
            val k = key(file)
            val current = words[k].orEmpty().split(' ').filter { it.isNotEmpty() }
            val merged = (current + newWords.flatMap { it.split(' ') }.filter { it.isNotEmpty() }).distinctBy { it.lowercase() }
            if (merged.size != current.size || !words.containsKey(k)) {
                words[k] = merged.joinToString(" ")
                touched++
            }
        }
        save()
        return touched
    }

    private fun save() {
        runCatching {
            store.parentFile?.mkdirs()
            store.writeText(words.entries.joinToString("") { it.key + "\t" + it.value.replace('\n', ' ') + "\n" })
        }
    }
}
