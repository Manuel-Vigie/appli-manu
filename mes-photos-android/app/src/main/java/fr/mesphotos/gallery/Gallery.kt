package fr.mesphotos.gallery

import fr.mesphotos.logic.Planner
import fr.mesphotos.storage.PhotoFiles
import java.io.File

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
            .filter { it.isDirectory && !it.name.startsWith(".") }
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
