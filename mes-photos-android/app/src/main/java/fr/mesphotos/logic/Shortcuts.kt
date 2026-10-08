package fr.mesphotos.logic

import fr.mesphotos.gallery.Gallery
import java.io.File

/**
 * Un raccourci : une recherche enregistrée. Un nom (« Immatriculation »), un classement (« Véhicule »)
 * et les mots cherchés ([words], par défaut le nom). Il montre toutes les photos dont le nom, l'album, la ville, le mois…
 * contient tous ces mots.
 */
class Shortcut(val name: String, val category: String, val words: String)

/**
 * Les raccourcis. Rien n'est copié ni déplacé : un raccourci ne fait que chercher, donc le rangement par date ne le touche pas
 * et il retrouve aussi les photos prises plus tard.
 * Ligne : nom ⇥ classement ⇥ mots. Ce fichier reste dans la mémoire privée de l'appli.
 * Les raccourcis des versions V13 à V17 (nom ⇥ classement ⇥ chemin ⇥ fichier ⇥ taille) sont repris : ils cherchent leur nom.
 */
class Shortcuts(private val store: File) {

    private val items = ArrayList<Shortcut>()

    fun load() {
        items.clear()
        if (!store.isFile) return
        store.forEachLine { line ->
            val parts = line.split('\t')
            val name = parts.getOrNull(0)?.let { clean(it) } ?: ""
            if (name.isEmpty()) return@forEachLine
            val category = clean(parts.getOrNull(1) ?: "").ifEmpty { OTHER }
            // Ancien format (5 colonnes) : le raccourci cherche son nom. Nouveau format (3 colonnes) : les mots sont dans la 3e.
            val words = if (parts.size == 3) clean(parts[2]).ifEmpty { name } else name
            if (items.none { sameKey(it, name, category) }) items += Shortcut(name, category, words)
        }
    }

    private fun save() {
        store.parentFile?.mkdirs()
        val tmp = File(store.parentFile, store.name + ".tmp")
        tmp.writeText(items.joinToString("") { "${it.name}\t${it.category}\t${it.words}\n" })
        if (!tmp.renameTo(store)) {
            store.writeText(tmp.readText())
            tmp.delete()
        }
    }

    private fun sameKey(s: Shortcut, name: String, category: String) =
        Gallery.normalize(s.name) == Gallery.normalize(name) && Gallery.normalize(s.category) == Gallery.normalize(category)

    /** Tous les raccourcis, classés par catégorie puis par nom (sans tenir compte des accents ni des majuscules). */
    fun all(): List<Shortcut> = items.sortedWith(compareBy({ Gallery.normalize(it.category) }, { Gallery.normalize(it.name) }))

    /** Les catégories déjà utilisées, dans l'ordre alphabétique. */
    fun categories(): List<String> = items.map { it.category }.distinctBy { Gallery.normalize(it) }.sortedBy { Gallery.normalize(it) }

    /**
     * Crée (ou remplace, s'il existe déjà avec le même nom dans la même catégorie) un raccourci.
     * [words] vide : il cherche son nom. Retourne null si le nom est vide.
     */
    fun put(name: String, category: String, words: String): Shortcut? {
        val cleanName = clean(name)
        if (cleanName.isEmpty()) return null
        val typedCategory = clean(category).ifEmpty { OTHER }
        val cleanCategory = categories().firstOrNull { Gallery.normalize(it) == Gallery.normalize(typedCategory) } ?: typedCategory
        val made = Shortcut(cleanName, cleanCategory, clean(words).ifEmpty { cleanName })
        items.removeAll { sameKey(it, made.name, made.category) }
        items += made
        save()
        return made
    }

    fun remove(shortcut: Shortcut) {
        if (items.removeAll { sameKey(it, shortcut.name, shortcut.category) }) save()
    }

    /** Les raccourcis dont le nom ou la catégorie contient tous les mots de [terms] (déjà normalisés). */
    fun matching(terms: List<String>): List<Shortcut> =
        if (terms.isEmpty()) emptyList() else all().filter { Gallery.matches(Gallery.normalize(it.name + " " + it.category), terms) }

    private fun clean(text: String): String = text.replace(Regex("[\\t\\r\\n]+"), " ").replace(Regex(" {2,}"), " ").trim().take(60).trim()

    companion object {
        /** Les mots mis devant « - IMG_… » dans le nom d'un fichier (« Immatriculation, Véhicule - IMG_2026….jpg » → [Immatriculation, Véhicule]). */
        private fun wordsIn(fileName: String): List<String> {
            val cut = fileName.indexOf(" - ")
            if (cut <= 0) return emptyList()
            return fileName.substring(0, cut).split(", ")
                .map { it.replace(Regex("\\s*\\(\\d+\\)$"), "").trim().take(40) }
                .filter { it.isNotEmpty() }
        }

        /** Le nom qu'on propose pour un raccourci d'après une photo : le premier mot choisi à la prise de vue. Vide si le nom du fichier n'en dit rien. */
        fun suggestName(fileName: String): String = wordsIn(fileName).firstOrNull() ?: ""

        /** Le classement qu'on propose : le deuxième mot choisi à la prise de vue (« Véhicule »). Vide s'il n'y en a pas. */
        fun suggestCategory(fileName: String): String = wordsIn(fileName).getOrNull(1) ?: ""

        /** Classement quand la personne n'en a pas donné. */
        const val OTHER = "Divers"

        /** Idées de classement proposées d'office. */
        val SUGGESTIONS = listOf("Véhicule", "Papiers", "Maison", "Santé")
    }
}
