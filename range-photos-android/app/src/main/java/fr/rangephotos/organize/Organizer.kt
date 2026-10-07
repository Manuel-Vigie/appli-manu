package fr.rangephotos.organize

import fr.rangephotos.logic.PlannedMove
import java.io.File
import java.io.IOException

data class OrganizeResult(
    val moved: Int,
    val failed: Int,
    val copied: Int = 0,
    val copyFailed: Int = 0,
    /** Copies non refaites parce qu'elles existent déjà (même nom, même taille). */
    val copiesAlreadyThere: Int = 0,
    /** Les premières erreurs, en clair (nom de la photo : raison). */
    val failures: List<String> = emptyList(),
    /** Anciens dossiers devenus vides, supprimés. */
    val foldersRemoved: Int = 0,
    /** Anciens dossiers gardés parce qu'il reste quelque chose dedans (quelques noms). */
    val foldersKept: List<String> = emptyList(),
)

/**
 * Nettoyage des anciens dossiers après un rangement : [volumeRoot] et [protectedDirs] (dossiers de photos, destination…)
 * ne sont jamais touchés, ni les dossiers système (DCIM, Pictures…) ni les dossiers cachés.
 */
class FolderCleanup(val volumeRoot: File, val protectedDirs: Set<File>)

/**
 * Déplace réellement les photos dans leurs dossiers, avec accès direct aux fichiers.
 *
 * Sécurité : déplacement natif (renommage, instantané, aucune copie) ; si le système le refuse, copie,
 * vérification de la taille, puis seulement suppression de l'original. Chaque déplacement est vérifié
 * (le fichier est bien arrivé, avec la bonne taille) avant d'être compté, et écrit dans un journal qui permet
 * d'annuler. Chaque erreur est conservée avec sa raison : rien n'échoue en silence.
 */
class Organizer(private val onChanged: (List<String>) -> Unit = {}) {

    fun execute(
        outputRoot: File,
        moves: List<PlannedMove>,
        journal: File,
        cleanup: FolderCleanup? = null,
        /** Vrai pour ajouter au journal existant (rangement en plusieurs lots) ; faux = nouveau journal. */
        append: Boolean = false,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): OrganizeResult {
        outputRoot.mkdirs()
        if (!outputRoot.isDirectory) {
            return OrganizeResult(
                0, moves.size,
                failures = listOf("Impossible de créer le dossier « ${outputRoot.name} » (${outputRoot.absolutePath})."),
            )
        }
        journal.parentFile?.mkdirs()
        if (!append) journal.writeText("") // nouveau rangement = nouveau journal

        val failures = ArrayList<String>()
        val touched = ArrayList<String>()
        var moved = 0
        var failed = 0
        var copied = 0
        var copyFailed = 0
        var alreadyThere = 0
        val oldFolders = LinkedHashSet<File>() // dossiers d'où une photo est partie

        fun note(message: String) {
            if (failures.size < MAX_FAILURES) failures += message
        }

        moves.forEachIndexed { index, move ->
            val photo = move.photo
            try {
                val source = File(photo.path ?: throw IOException("chemin inconnu"))
                if (!source.isFile) throw IOException("fichier introuvable")
                val inPlace = photo.currentFolder != null && photo.currentFolder == move.folder

                val current: File = if (inPlace) {
                    source // déjà au bon endroit : on ne la déplace pas
                } else {
                    val destination = uniqueFile(folderOf(outputRoot, move.folder), photo.name)
                    transfer(source, destination)
                    journal.appendText("M\t${destination.absolutePath}\t${source.absolutePath}\n")
                    touched += source.absolutePath
                    touched += destination.absolutePath
                    moved++
                    source.parentFile?.let { oldFolders += it }
                    destination
                }

                for (folder in move.copies) {
                    try {
                        val dir = folderOf(outputRoot, folder)
                        val existing = File(dir, photo.name)
                        if (existing.isFile && existing.length() == current.length()) {
                            alreadyThere++
                            continue
                        }
                        val copy = uniqueFile(dir, photo.name)
                        copyVerified(current, copy)
                        journal.appendText("C\t${copy.absolutePath}\n")
                        touched += copy.absolutePath
                        copied++
                    } catch (e: Exception) {
                        copyFailed++
                        note("Copie de ${photo.name} : ${reason(e)}")
                    }
                }
            } catch (e: Exception) {
                failed++
                note("${photo.name} : ${reason(e)}")
            }
            onProgress(index + 1, moves.size)
            if (touched.size >= 300) flush(touched)
        }
        flush(touched)
        var removed = 0
        var kept: List<String> = emptyList()
        if (cleanup != null) {
            val outcome = removeEmptyFolders(oldFolders, cleanup)
            removed = outcome.first
            kept = outcome.second
        }
        return OrganizeResult(moved, failed, copied, copyFailed, alreadyThere, failures, removed, kept)
    }

    /** Annule le dernier rangement : supprime les copies créées par l'appli, remet chaque photo dans son dossier d'origine. */
    fun undo(journal: File, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): OrganizeResult {
        if (!journal.exists()) return OrganizeResult(0, 0)
        val lines = journal.readLines().filter { it.isNotBlank() }.reversed()
        var restored = 0
        var failed = 0
        var removedCopies = 0
        val remaining = ArrayList<String>()
        val failures = ArrayList<String>()
        val touched = ArrayList<String>()

        lines.forEachIndexed { index, line ->
            val parts = line.split('\t')
            try {
                when {
                    parts[0] == "C" && parts.size == 2 -> {
                        val copy = File(parts[1])
                        if (!copy.isFile) throw IOException("copie introuvable")
                        if (!copy.delete()) throw IOException("suppression impossible")
                        touched += copy.absolutePath
                        removedCopies++
                    }
                    parts[0] == "M" && parts.size == 3 -> {
                        val current = File(parts[1])
                        val original = File(parts[2])
                        if (!current.isFile) throw IOException("fichier introuvable")
                        val folder = original.parentFile ?: throw IOException("dossier d'origine inconnu")
                        folder.mkdirs()
                        val target = if (original.exists()) uniqueFile(folder, original.name) else original
                        transfer(current, target)
                        touched += current.absolutePath
                        touched += target.absolutePath
                        restored++
                    }
                    else -> throw IOException("ligne du journal illisible")
                }
            } catch (e: Exception) {
                failed++
                remaining += line
                if (failures.size < MAX_FAILURES) failures += "${File(parts.getOrElse(1) { "?" }).name} : ${reason(e)}"
            }
            onProgress(index + 1, lines.size)
        }
        flush(touched)
        // On garde dans le journal ce qui n'a pas pu être annulé (ordre d'origine).
        journal.writeText(remaining.reversed().joinToString("\n", postfix = if (remaining.isEmpty()) "" else "\n"))
        return OrganizeResult(restored, failed, removedCopies, 0, 0, failures)
    }

    /**
     * Supprime les anciens dossiers devenus vides, du plus profond au plus haut.
     * Sécurité : on ne supprime qu'un dossier réellement vide (aucun fichier, même caché) ; `File.delete()` refuse
     * d'ailleurs de supprimer un dossier non vide. Jamais de suppression récursive. Retourne (supprimés, gardés).
     */
    fun removeEmptyFolders(starts: Set<File>, cleanup: FolderCleanup): Pair<Int, List<String>> {
        val rootPath = cleanup.volumeRoot.absolutePath
        val candidates = LinkedHashSet<File>()
        for (start in starts) {
            var dir: File? = start
            while (dir != null && dir.absolutePath.startsWith("$rootPath/") && !isProtected(dir, cleanup)) {
                candidates += dir
                dir = dir.parentFile
            }
        }
        var removed = 0
        val kept = ArrayList<String>()
        for (dir in candidates.sortedByDescending { it.absolutePath.length }) {
            if (!dir.isDirectory) continue
            val content = dir.listFiles()
            if (content != null && content.isEmpty() && dir.delete()) {
                removed++
            } else if (dir in starts) {
                kept += dir.name
            }
        }
        return removed to kept.take(5)
    }

    /** Les dossiers d'où des fichiers sont partis lors du dernier rangement (d'après le journal). */
    fun oldFoldersFromJournal(journal: File): Set<File> {
        if (!journal.exists()) return emptySet()
        return journal.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size == 3 && parts[0] == "M") File(parts[2]).parentFile else null
        }.toCollection(LinkedHashSet())
    }

    /**
     * Simulation, sans rien supprimer : les dossiers qui seraient supprimés (vides, ou ne contenant que des dossiers
     * eux-mêmes supprimés), du plus profond au plus haut. Mêmes protections que le nettoyage réel.
     */
    fun foldersThatWouldBeRemoved(starts: Set<File>, cleanup: FolderCleanup): List<File> {
        val rootPath = cleanup.volumeRoot.absolutePath
        val candidates = LinkedHashSet<File>()
        for (start in starts) {
            var dir: File? = start
            while (dir != null && dir.absolutePath.startsWith("$rootPath/") && !isProtected(dir, cleanup)) {
                candidates += dir
                dir = dir.parentFile
            }
        }
        val removable = LinkedHashSet<File>()
        val ordered = ArrayList<File>()
        for (dir in candidates.sortedByDescending { it.absolutePath.length }) {
            if (!dir.isDirectory) continue
            val content = dir.listFiles() ?: continue
            if (content.all { it in removable }) {
                removable += dir
                ordered += dir
            }
        }
        return ordered
    }

    private fun isProtected(dir: File, cleanup: FolderCleanup): Boolean =
        dir in cleanup.protectedDirs ||
            dir.name.startsWith(".") ||
            (dir.parentFile == cleanup.volumeRoot && dir.name.lowercase() in SYSTEM_FOLDERS)

    private fun flush(touched: MutableList<String>) {
        if (touched.isEmpty()) return
        try {
            onChanged(touched.toList())
        } catch (_: Exception) {
            // La galerie se mettra à jour plus tard : ce n'est pas grave.
        }
        touched.clear()
    }

    private fun reason(e: Exception) = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    private fun folderOf(outputRoot: File, folder: List<String>): File {
        val dir = File(outputRoot, folder.joinToString(File.separator))
        dir.mkdirs()
        if (!dir.isDirectory) throw IOException("impossible de créer le dossier « ${folder.last()} »")
        return dir
    }

    private fun uniqueFile(dir: File, name: String): File {
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

    /** Déplace [source] vers [destination] et vérifie que le fichier est bien arrivé avec la même taille. */
    private fun transfer(source: File, destination: File) {
        val size = source.length()
        if (source.renameTo(destination)) {
            if (!destination.isFile || destination.length() != size || source.exists()) {
                throw IOException("déplacement non confirmé")
            }
            return
        }
        // Le renommage direct a été refusé : copie vérifiée, puis suppression de l'original.
        copyVerified(source, destination)
        if (!source.delete()) {
            destination.delete()
            throw IOException("l'original n'a pas pu être supprimé")
        }
    }

    private fun copyVerified(source: File, destination: File) {
        try {
            source.inputStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            destination.setLastModified(source.lastModified()) // garde la date d'origine du fichier
            if (destination.length() != source.length()) throw IOException("copie incomplète")
        } catch (e: Exception) {
            destination.delete() // copie incomplète : on la retire, l'original n'a pas bougé
            throw e
        }
    }

    companion object {
        const val OUTPUT_DIR = "Photos rangées"
        private const val MAX_FAILURES = 8
        private val SYSTEM_FOLDERS = setOf(
            "dcim", "pictures", "movies", "download", "downloads", "documents", "music", "android",
            "alarms", "notifications", "podcasts", "ringtones", "audiobooks", "recordings",
        )
    }
}
