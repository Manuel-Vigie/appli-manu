package fr.mesphotos.gallery

import fr.mesphotos.logic.Planner
import fr.mesphotos.storage.PhotoFiles
import fr.mesphotos.storage.Place
import java.io.File
import java.text.Normalizer
import java.util.Locale

/** Un sous-dossier affiché dans la galerie : son nom, le nombre de photos/vidéos qu'il contient, et une photo de couverture. */
class FolderItem(val name: String, val count: Int, val cover: File?)

/** Contenu d'un dossier : ses sous-dossiers et les photos/vidéos qui sont directement dedans. */
class Listing(val path: List<String>, val folders: List<FolderItem>, val files: List<File>)

/** Parcourt « Photos rangées » en lecture seule : rien n'est jamais modifié. */
object Gallery {

    fun list(root: File, path: List<String>): Listing {
        val dir = path.fold(root) { acc, name -> File(acc, name) }
        val children = dir.listFiles() ?: emptyArray()
        val files = children
            .filter { it.isFile && !it.name.startsWith(".") && PhotoFiles.mimeOf(it) != null && it.length() > 0 }
            .sortedWith(compareBy({ it.name.lowercase() }))
        val folders = children
            .filter { it.isDirectory && !it.name.startsWith(".") && !(path.isEmpty() && it.name == Place.ASIDE_DIR) }
            .map { child ->
                val media = media(child, 0)
                FolderItem(child.name, media.size, media.firstOrNull { !PhotoFiles.isVideo(it) } ?: media.firstOrNull())
            }
            .filter { it.count > 0 }
            .sortedWith(Comparator { a, b -> compareFolders(a.name, b.name) })
        return Listing(path, folders, files)
    }

    /** Tous les fichiers photo/vidéo sous [dir] (parcours limité en profondeur, dossiers cachés ignorés). */
    fun media(dir: File, depth: Int = 0): List<File> {
        if (depth > 12) return emptyList()
        val result = ArrayList<File>()
        for (child in dir.listFiles() ?: return emptyList()) {
            if (child.name.startsWith(".")) continue
            if (child.isDirectory) result += media(child, depth + 1)
            else if (PhotoFiles.mimeOf(child) != null && child.length() > 0) result += child
        }
        return result.sortedBy { it.name.lowercase() }
    }

    /** Toutes les photos et vidéos de « Photos rangées » (sans « À l'écart » ni dossiers cachés), pour la recherche. */
    fun allMedia(root: File): List<File> {
        val result = ArrayList<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 12) return
            for (child in dir.listFiles() ?: return) {
                if (child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth == 0 && child.name == Place.ASIDE_DIR) continue
                    walk(child, depth + 1)
                } else if (PhotoFiles.mimeOf(child) != null && child.length() > 0) {
                    result += child
                }
            }
        }
        walk(root, 0)
        return result
    }

    /** Minuscules et sans accents : « Août » et « aout » se retrouvent. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Les mots cherchés (« août 2024 » → [aout, 2024]). */
    fun terms(query: String): List<String> = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Vrai si tous les mots se trouvent dans le chemin (déjà normalisé) de la photo. */
    fun matches(haystack: String, terms: List<String>): Boolean = terms.isNotEmpty() && terms.all { haystack.contains(it) }

    /** Journées d'abord ; les années, mois et jours du plus récent au plus ancien ; « Date incertaine » à la fin. */
    fun compareFolders(a: String, b: String): Int {
        fun group(name: String) = when {
            name == Planner.PHOTOS -> 0
            name == Planner.UNDATED -> 3
            name.firstOrNull()?.isDigit() == true -> 1
            else -> 2
        }
        val ga = group(a)
        val gb = group(b)
        if (ga != gb) return ga.compareTo(gb)
        return if (ga == 1) b.compareTo(a) else a.compareTo(b)
    }
}
