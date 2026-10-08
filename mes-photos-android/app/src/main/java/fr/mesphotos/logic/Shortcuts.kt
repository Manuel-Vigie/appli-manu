package fr.mesphotos.logic

import fr.mesphotos.gallery.Gallery
import java.io.File

/** Un raccourci : un nom (« Immatriculation »), un classement (« Véhicule ») et la photo vers laquelle il mène. */
class Shortcut(val name: String, val category: String, val path: String, val fileName: String, val size: Long)

/**
 * Les raccourcis : des signets vers des photos. La photo n'est ni copiée ni déplacée, donc le rangement par date
 * ne les touche jamais ; si la photo change de place ou de nom, on la retrouve (même nom, même taille).
 * Ligne : nom ⇥ catégorie ⇥ chemin ⇥ nom du fichier ⇥ taille. Ce fichier reste dans la mémoire privée de l'appli.
 */
class Shortcuts(private val store: File) {

    private val items = ArrayList<Shortcut>()

    fun load() {
        items.clear()
        if (!store.isFile) return
        store.forEachLine { line ->
            val parts = line.split('\t')
            val size = parts.getOrNull(4)?.toLongOrNull()
            if (parts.size == 5 && size != null && parts[0].isNotBlank()) items += Shortcut(parts[0], parts[1], parts[2], parts[3], size)
        }
    }

    private fun save() {
        store.parentFile?.mkdirs()
        val tmp = File(store.parentFile, store.name + ".tmp")
        tmp.writeText(items.joinToString("") { "${it.name}\t${it.category}\t${it.path}\t${it.fileName}\t${it.size}\n" })
        if (!tmp.renameTo(store)) {
            store.writeText(tmp.readText())
            tmp.delete()
        }
    }

    /** Tous les raccourcis, classés par catégorie puis par nom (sans tenir compte des accents ni des majuscules). */
    fun all(): List<Shortcut> = items.sortedWith(compareBy({ Gallery.normalize(it.category) }, { Gallery.normalize(it.name) }))

    /** Les catégories déjà utilisées, dans l'ordre alphabétique. */
    fun categories(): List<String> = items.map { it.category }.filter { it.isNotEmpty() }.distinctBy { Gallery.normalize(it) }.sortedBy { Gallery.normalize(it) }

    /** Le raccourci de cette photo, s'il y en a un. */
    fun forFile(file: File): Shortcut? = items.firstOrNull { it.path == file.absolutePath }

    /**
     * Crée (ou remplace) le raccourci de [file]. Une photo n'a qu'un raccourci ; un même nom dans la même catégorie
     * ne peut mener qu'à une photo. Retourne null si le nom est vide.
     */
    fun put(file: File, name: String, category: String): Shortcut? {
        val cleanName = clean(name)
        if (cleanName.isEmpty()) return null
        val cleanCategory = clean(category).ifEmpty { OTHER }
        val sameCategory = categories().firstOrNull { Gallery.normalize(it) == Gallery.normalize(cleanCategory) } ?: cleanCategory
        val made = Shortcut(cleanName, sameCategory, file.absolutePath, file.name, file.length())
        items.removeAll { it.path == made.path || (Gallery.normalize(it.name) == Gallery.normalize(made.name) && Gallery.normalize(it.category) == Gallery.normalize(made.category)) }
        items += made
        save()
        return made
    }

    fun remove(shortcut: Shortcut) {
        if (items.removeAll { it === shortcut || (it.path == shortcut.path && it.name == shortcut.name && it.category == shortcut.category) }) save()
    }

    /** La photo [old] a été renommée en [new] : ses raccourcis suivent. */
    fun renamed(old: File, new: File) {
        var changed = false
        for (i in items.indices) {
            val s = items[i]
            if (s.path == old.absolutePath) {
                items[i] = Shortcut(s.name, s.category, new.absolutePath, new.name, new.length())
                changed = true
            }
        }
        if (changed) save()
    }

    /**
     * La photo du raccourci : à sa place habituelle, sinon (rangement refait, photo déplacée) la photo de même nom et de même taille
     * parmi [candidates]. Si on l'a retrouvée ailleurs, le raccourci retient sa nouvelle place. Null si elle est introuvable
     * (mise à la corbeille, à l'écart, effacée).
     */
    fun resolve(shortcut: Shortcut, candidates: () -> List<File>): File? {
        val here = File(shortcut.path)
        if (here.isFile) return here
        val found = candidates().firstOrNull { it.name == shortcut.fileName && it.length() == shortcut.size } ?: return null
        val index = items.indexOfFirst { it.path == shortcut.path && it.name == shortcut.name && it.category == shortcut.category }
        if (index >= 0) {
            items[index] = Shortcut(shortcut.name, shortcut.category, found.absolutePath, found.name, found.length())
            save()
        }
        return found
    }

    /** Les raccourcis dont le nom ou la catégorie contient tous les mots de [terms] (déjà normalisés). */
    fun matching(terms: List<String>): List<Shortcut> =
        if (terms.isEmpty()) emptyList() else all().filter { Gallery.matches(Gallery.normalize(it.name + " " + it.category), terms) }

    private fun clean(text: String): String = text.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(40)

    companion object {
        /** Les mots mis devant « - IMG_… » dans le nom d'un fichier (« Immatriculation, Véhicule - IMG_2026….jpg » → [Immatriculation, Véhicule]). */
        private fun wordsIn(fileName: String): List<String> {
            val cut = fileName.indexOf(" - ")
            if (cut <= 0) return emptyList()
            return fileName.substring(0, cut).split(", ")
                .map { it.replace(Regex("\\s*\\(\\d+\\)$"), "").trim().take(40) }
                .filter { it.isNotEmpty() }
        }

        /** Le nom qu'on propose pour le raccourci d'une photo : le premier mot choisi à la prise de vue. Vide si le nom du fichier n'en dit rien. */
        fun suggestName(fileName: String): String = wordsIn(fileName).firstOrNull() ?: ""

        /** Le classement qu'on propose : le deuxième mot choisi à la prise de vue (« Véhicule »). Vide s'il n'y en a pas. */
        fun suggestCategory(fileName: String): String = wordsIn(fileName).getOrNull(1) ?: ""

        /** Classement quand la personne n'en a pas donné. */
        const val OTHER = "Divers"

        /** Idées de classement proposées d'office. */
        val SUGGESTIONS = listOf("Véhicule", "Papiers", "Maison", "Santé")
    }
}
