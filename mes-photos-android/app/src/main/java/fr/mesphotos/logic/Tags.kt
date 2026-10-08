package fr.mesphotos.logic

import fr.mesphotos.gallery.Gallery
import java.io.File

/**
 * Les mots proposés quand on prend une photo (« Immatriculation », « Véhicule », « Montagne »…) : une liste personnelle,
 * qu'on complète quand on veut. Un mot par ligne dans la mémoire privée de l'appli. Sans fichier, on part des mots de [DEFAULTS].
 */
class Tags(private val store: File) {

    private val items = ArrayList<String>()

    fun load() {
        items.clear()
        if (store.isFile) {
            store.forEachLine { line ->
                val tag = clean(line)
                if (tag.isNotEmpty() && items.none { Gallery.normalize(it) == Gallery.normalize(tag) }) items += tag
            }
        } else {
            items += DEFAULTS
        }
    }

    fun all(): List<String> = items.toList()

    /** Ajoute [typed] à la liste (s'il n'y est pas déjà) et retourne le mot tel qu'il est gardé ; null si c'est vide. */
    fun add(typed: String): String? {
        val tag = clean(typed)
        if (tag.isEmpty()) return null
        items.firstOrNull { Gallery.normalize(it) == Gallery.normalize(tag) }?.let { return it }
        items += tag
        save()
        return tag
    }

    /** Retire un mot de la liste (les photos déjà prises gardent leur nom). */
    fun remove(tag: String) {
        if (items.removeAll { Gallery.normalize(it) == Gallery.normalize(tag) }) save()
    }

    private fun save() {
        store.parentFile?.mkdirs()
        store.writeText(items.joinToString("") { it + "\n" })
    }

    companion object {
        val DEFAULTS = listOf("Immatriculation", "Véhicule", "Montagne", "Famille", "Papiers")

        /** Un mot propre : une seule ligne, sans virgule (la virgule sépare les mots dans le nom du fichier), 30 lettres au plus. */
        fun clean(text: String): String = text.replace(Regex("[\\t\\r\\n,]+"), " ").replace(Regex(" {2,}"), " ").trim().take(30).trim()

        /** Les mots choisis, mis bout à bout pour le nom du fichier : « Immatriculation, Véhicule ». Les doublons sont retirés. */
        fun join(tags: List<String>): String =
            tags.map { clean(it) }.filter { it.isNotEmpty() }.distinctBy { Gallery.normalize(it) }.joinToString(", ")
    }
}
