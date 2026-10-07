package fr.rangephotos.library

import fr.rangephotos.storage.PhotoFiles
import java.io.File

/** Un album = un dossier qui contient directement des photos ou des vidéos. */
class Album(
    /** Nom court affiché sur l'album (le dernier dossier du chemin). */
    val title: String,
    /** Chemin au-dessus de l'album (ex. « 2026 · 03 - mars »), vide s'il n'y en a pas. */
    val subtitle: String,
    val dir: File,
    val files: List<File>,
) {
    val count: Int get() = files.size
    val videos: Int get() = files.count { PhotoFiles.isVideo(it) }
    val photos: Int get() = count - videos

    /** Photo de couverture : la première photo (à défaut, la première vidéo). */
    val cover: File? get() = files.firstOrNull { !PhotoFiles.isVideo(it) } ?: files.firstOrNull()
}

/** Une étagère = un groupe d'albums (Randonnées, Journées, Portraits…). */
class Shelf(val title: String, val albums: List<Album>) {
    val fileCount: Int get() = albums.sumOf { it.count }
}

/** Lit le dossier « Photos rangées » et en fait des étagères d'albums. Lecture seule : rien n'est modifié. */
object Library {

    /** Au-delà de ce nombre d'albums, une étagère est découpée par dossier parent (ex. un mois). */
    const val BIG_SHELF = 24
    const val LOOSE = "En vrac"

    private val FIRST = listOf("Randonnées", "Journées", "Portraits")
    private val NEWEST_FIRST = setOf("Randonnées", "Journées")

    private class Raw(val rel: List<String>, val dir: File, val files: List<File>)

    fun scan(root: File): List<Shelf> {
        val raws = ArrayList<Raw>()

        fun walk(dir: File, rel: List<String>, depth: Int) {
            val children = dir.listFiles() ?: return
            val media = ArrayList<File>()
            for (child in children) {
                if (child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth < 12) walk(child, rel + child.name, depth + 1)
                } else if (PhotoFiles.mimeOf(child) != null && child.length() > 0) {
                    media += child
                }
            }
            if (media.isNotEmpty()) raws += Raw(rel, dir, media.sortedWith { a, b -> naturalCompare(a.name, b.name) })
        }
        walk(root, emptyList(), 0)

        val byTop = LinkedHashMap<String, MutableList<Raw>>()
        for (raw in raws) byTop.getOrPut(raw.rel.firstOrNull() ?: LOOSE) { ArrayList() }.add(raw)

        val shelves = ArrayList<Shelf>()
        for ((top, list) in byTop) {
            val newest = top in NEWEST_FIRST
            if (list.size > BIG_SHELF) {
                val bySub = LinkedHashMap<String, MutableList<Raw>>()
                for (raw in list) {
                    val parent = raw.rel.dropLast(1)
                    val key = if (parent.isEmpty()) top else parent.joinToString(" › ")
                    bySub.getOrPut(key) { ArrayList() }.add(raw)
                }
                for ((title, group) in bySub) shelves += Shelf(title, albumsOf(group, newest))
            } else {
                shelves += Shelf(top, albumsOf(list, newest))
            }
        }

        return shelves.sortedWith { a, b ->
            val topA = topOf(a)
            val topB = topOf(b)
            val rankA = FIRST.indexOf(topA).let { if (it < 0) 99 else it }
            val rankB = FIRST.indexOf(topB).let { if (it < 0) 99 else it }
            if (rankA != rankB) {
                rankA.compareTo(rankB)
            } else {
                val c = naturalCompare(a.title, b.title)
                if (topA == topB && topA in NEWEST_FIRST) -c else c
            }
        }
    }

    private fun topOf(shelf: Shelf): String = shelf.title.substringBefore(" › ")

    private fun albumsOf(group: List<Raw>, newestFirst: Boolean): List<Album> {
        val albums = group.map { raw ->
            Album(raw.rel.lastOrNull() ?: LOOSE, raw.rel.dropLast(1).joinToString(" · "), raw.dir, raw.files)
        }
        return albums.sortedWith { a, b ->
            val c = naturalCompare(a.title, b.title)
            if (newestFirst) -c else c
        }
    }

    /** Compare « Rando 2 » et « Rando 10 » comme une personne : les nombres par leur valeur. */
    fun naturalCompare(a: String, b: String): Int {
        val x = chunks(a.lowercase())
        val y = chunks(b.lowercase())
        for (i in 0 until minOf(x.size, y.size)) {
            val p = x[i]
            val q = y[i]
            val c = if (p[0].isDigit() && q[0].isDigit()) {
                val pn = p.trimStart('0')
                val qn = q.trimStart('0')
                if (pn.length != qn.length) pn.length.compareTo(qn.length) else pn.compareTo(qn)
            } else {
                p.compareTo(q)
            }
            if (c != 0) return c
        }
        return x.size.compareTo(y.size)
    }

    private fun chunks(s: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < s.length) {
            val digit = s[i].isDigit()
            var j = i + 1
            while (j < s.length && s[j].isDigit() == digit) j++
            out += s.substring(i, j)
            i = j
        }
        return out
    }
}
