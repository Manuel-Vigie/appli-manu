package fr.mesphotos.logic

import java.io.File

/** Les règles pour renommer une photo : on garde son type (.jpg…), on retire les caractères interdits, on ne perd jamais sa date. */
object RenameNames {
    private const val MAX = 120

    /**
     * Nouveau nom complet d'après ce que la personne a tapé ([typed]). Null si c'est vide.
     * [keepOriginal] : le nom d'origine contient la date de la photo et rien d'autre ne la donne ; on le garde à la suite
     * (« Mariage Julie - IMG_20240314_101530.jpg ») pour que le prochain rangement la classe toujours au bon jour.
     */
    fun build(typed: String, original: String, keepOriginal: Boolean): String? {
        val dot = original.lastIndexOf('.')
        val ext = if (dot > 0) original.substring(dot) else ""
        val base = if (dot > 0) original.substring(0, dot) else original
        var text = typed.trim()
        if (ext.isNotEmpty() && text.endsWith(ext, ignoreCase = true)) text = text.dropLast(ext.length)
        text = text.trim().trimStart('.')
        if (text.isEmpty()) return null
        text = Planner.sanitize(text).take(MAX).trim()
        if (text.isEmpty()) return null
        val name = if (keepOriginal && !text.contains(base, ignoreCase = true)) "$text - $base" else text
        return name + ext
    }

    /** Un nom libre dans [dir] : [name], sinon « nom (2).jpg », « nom (3).jpg »… */
    fun unique(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            candidate = File(dir, "$base ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }
}
