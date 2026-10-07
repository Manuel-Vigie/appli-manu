package fr.mesphotos.organize

import java.io.File
import java.io.IOException

/** Un fichier à la corbeille : où il est maintenant, d'où il vient, et quand il y est allé. */
data class TrashEntry(val trashed: File, val original: File, val at: Long)

/** [entries] : les fichiers réellement traités ; [failed] : ceux qui n'ont pas pu l'être (ils n'ont pas bougé). */
data class TrashResult(
    val entries: List<TrashEntry>,
    val failed: Int,
    val failures: List<String> = emptyList(),
) {
    val done: Int get() = entries.size
}

/**
 * Corbeille de l'appli, dans « Photos rangées/.Corbeille » (dossier caché : ni le rangement ni la galerie ne le voient).
 *
 * Sécurité : mettre à la corbeille ne supprime rien, c'est un déplacement vérifié (même taille à l'arrivée).
 * Chaque fichier est noté dans un index (`index.tsv`, sur la carte, avec des chemins relatifs) au moment même
 * où il est déplacé : on peut toujours le remettre exactement où il était. Seul [deleteForever] efface vraiment,
 * et seulement les fichiers qu'on lui donne.
 */
class Trash(private val outputRoot: File, private val onChanged: (List<String>) -> Unit = {}) {

    val dir: File get() = File(outputRoot, DIR_NAME)
    private val index: File get() = File(dir, "index.tsv")

    /** Ce qui est à la corbeille (les fichiers qui ont disparu de la carte sont ignorés), du plus récent au plus ancien. */
    fun entries(): List<TrashEntry> {
        if (!index.isFile) return emptyList()
        val result = ArrayList<TrashEntry>()
        for (line in index.readLines()) {
            val parts = line.split('\t')
            if (parts.size != 4 || parts[0] != "T") continue
            val trashed = resolve(parts[1]) ?: continue
            val original = resolve(parts[2]) ?: continue
            if (!trashed.isFile) continue
            result += TrashEntry(trashed, original, parts[3].toLongOrNull() ?: 0L)
        }
        return result.sortedByDescending { it.at }
    }

    fun count(): Int = entries().size

    /** Met [files] à la corbeille. Seuls les fichiers de « Photos rangées » sont acceptés ; chaque erreur est gardée avec sa raison. */
    fun moveToTrash(files: List<File>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): TrashResult {
        val rootPath = outputRoot.absolutePath
        val trashPath = dir.absolutePath
        dir.mkdirs()
        if (!dir.isDirectory) {
            return TrashResult(emptyList(), files.size, listOf("Impossible de créer la corbeille."))
        }
        val marker = File(dir, ".nomedia") // la galerie du téléphone n'affiche pas ce dossier
        if (!marker.exists()) runCatching { marker.createNewFile() }

        val batch = System.currentTimeMillis()
        val done = ArrayList<TrashEntry>()
        val failures = ArrayList<String>()
        val touched = ArrayList<String>()
        val parents = LinkedHashSet<File>()
        var failed = 0

        files.forEachIndexed { i, file ->
            try {
                val path = file.absolutePath
                if (!path.startsWith("$rootPath/") || path.startsWith("$trashPath/")) throw IOException("ce fichier n'est pas dans « Photos rangées »")
                if (!file.isFile) throw IOException("fichier introuvable")
                val relative = file.toRelativeString(outputRoot)
                val destination = uniqueFile(File(File(dir, batch.toString()), relative))
                destination.parentFile?.mkdirs()
                transfer(file, destination)
                try {
                    index.appendText("T\t${destination.toRelativeString(outputRoot)}\t$relative\t$batch\n")
                } catch (e: Exception) {
                    // Sans trace dans l'index on ne pourrait pas le remettre : on le remet tout de suite à sa place.
                    runCatching { transfer(destination, file) }
                    throw e
                }
                done += TrashEntry(destination, file, batch)
                touched += path
                touched += destination.absolutePath
                file.parentFile?.let { parents += it }
            } catch (e: Exception) {
                failed++
                if (failures.size < MAX_FAILURES) failures += "${file.name} : ${reason(e)}"
            }
            onProgress(i + 1, files.size)
        }
        removeEmptyDirs(parents, outputRoot)
        announce(touched)
        return TrashResult(done, failed, failures)
    }

    /** Remet [items] exactement où ils étaient (sous un autre nom si la place est prise : rien n'est écrasé). */
    fun restore(items: List<TrashEntry>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): TrashResult {
        val done = ArrayList<TrashEntry>()
        val failures = ArrayList<String>()
        val touched = ArrayList<String>()
        val trashParents = LinkedHashSet<File>()
        var failed = 0

        items.forEachIndexed { i, item ->
            try {
                if (!item.trashed.isFile) throw IOException("fichier introuvable dans la corbeille")
                val folder = item.original.parentFile ?: throw IOException("dossier d'origine inconnu")
                folder.mkdirs()
                val target = if (item.original.exists()) uniqueFile(item.original) else item.original
                transfer(item.trashed, target)
                done += item
                touched += item.trashed.absolutePath
                touched += target.absolutePath
                item.trashed.parentFile?.let { trashParents += it }
            } catch (e: Exception) {
                failed++
                if (failures.size < MAX_FAILURES) failures += "${item.original.name} : ${reason(e)}"
            }
            onProgress(i + 1, items.size)
        }
        dropFromIndex(done.map { it.trashed })
        removeEmptyDirs(trashParents, dir)
        announce(touched)
        return TrashResult(done, failed, failures)
    }

    /** Efface pour de bon [items] (et eux seuls). À n'appeler qu'après une confirmation claire de la personne. */
    fun deleteForever(items: List<TrashEntry>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): TrashResult {
        val done = ArrayList<TrashEntry>()
        val failures = ArrayList<String>()
        val touched = ArrayList<String>()
        val trashParents = LinkedHashSet<File>()
        var failed = 0
        val trashPath = dir.absolutePath

        items.forEachIndexed { i, item ->
            try {
                if (!item.trashed.absolutePath.startsWith("$trashPath/")) throw IOException("ce fichier n'est pas dans la corbeille")
                if (!item.trashed.isFile) throw IOException("fichier introuvable")
                if (!item.trashed.delete()) throw IOException("suppression impossible")
                done += item
                touched += item.trashed.absolutePath
                item.trashed.parentFile?.let { trashParents += it }
            } catch (e: Exception) {
                failed++
                if (failures.size < MAX_FAILURES) failures += "${item.original.name} : ${reason(e)}"
            }
            onProgress(i + 1, items.size)
        }
        dropFromIndex(done.map { it.trashed })
        removeEmptyDirs(trashParents, dir)
        announce(touched)
        return TrashResult(done, failed, failures)
    }

    // ---- Détails -------------------------------------------------------------------------------

    /** Chemin relatif de l'index → fichier, sauf s'il sort de « Photos rangées » (index abîmé ou modifié). */
    private fun resolve(relative: String): File? {
        if (relative.isBlank() || relative.startsWith("/")) return null
        if (relative.split('/').any { it == ".." }) return null
        return File(outputRoot, relative)
    }

    /** Retire de l'index les fichiers [trashed] (et les lignes dont le fichier n'existe plus). */
    private fun dropFromIndex(trashed: Collection<File>) {
        if (!index.isFile) return
        val gone = trashed.map { it.toRelativeString(outputRoot) }.toSet()
        val kept = index.readLines().filter { line ->
            val parts = line.split('\t')
            parts.size == 4 && parts[0] == "T" && parts[1] !in gone && resolve(parts[1])?.isFile == true
        }
        index.writeText(kept.joinToString("\n", postfix = if (kept.isEmpty()) "" else "\n"))
    }

    /** Supprime les dossiers devenus vides, en remontant jusqu'à [stop] (jamais [stop] lui-même). Ne touche à rien de non vide. */
    private fun removeEmptyDirs(starts: Set<File>, stop: File) {
        val stopPath = stop.absolutePath
        for (start in starts.sortedByDescending { it.absolutePath.length }) {
            var current: File? = start
            while (current != null && current.absolutePath.startsWith("$stopPath/")) {
                val content = current.listFiles()
                if (!current.isDirectory || content == null || content.isNotEmpty() || !current.delete()) break
                current = current.parentFile
            }
        }
    }

    private fun announce(touched: List<String>) {
        if (touched.isEmpty()) return
        try {
            onChanged(touched.toList())
        } catch (_: Exception) {
            // La galerie du téléphone se mettra à jour plus tard : ce n'est pas grave.
        }
    }

    private fun reason(e: Exception) = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    private fun uniqueFile(wanted: File): File {
        if (!wanted.exists()) return wanted
        val name = wanted.name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            val candidate = File(wanted.parentFile, "$base ($n)$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    /** Déplace [source] vers [destination] et vérifie que le fichier est bien arrivé avec la même taille. */
    private fun transfer(source: File, destination: File) {
        val size = source.length()
        if (source.renameTo(destination)) {
            if (!destination.isFile || destination.length() != size || source.exists()) throw IOException("déplacement non confirmé")
            return
        }
        // Le renommage direct a été refusé : copie vérifiée, puis suppression de l'original.
        try {
            source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            destination.setLastModified(source.lastModified())
            if (destination.length() != size) throw IOException("copie incomplète")
        } catch (e: Exception) {
            destination.delete() // copie incomplète : on la retire, l'original n'a pas bougé
            throw e
        }
        if (!source.delete()) {
            destination.delete()
            throw IOException("l'original n'a pas pu être supprimé")
        }
    }

    companion object {
        const val DIR_NAME = ".Corbeille"
        private const val MAX_FAILURES = 8
    }
}
