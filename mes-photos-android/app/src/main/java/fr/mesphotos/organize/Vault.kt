package fr.mesphotos.organize

import fr.mesphotos.logic.RenameNames
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/** Une photo ou vidéo du coffre-fort : son fichier (dans la mémoire privée de l'appli) et l'endroit d'où elle vient. */
class VaultEntry(val id: String, val file: File, val originalPath: String)

/**
 * Le coffre-fort : des photos et vidéos gardées dans la mémoire PRIVÉE de l'appli (un dossier que ni les autres applis,
 * ni le gestionnaire de fichiers, ni la galerie du téléphone ne peuvent voir). Rien n'y est chiffré : la protection vient
 * de l'isolation d'Android et du verrouillage du téléphone demandé pour l'ouvrir.
 *
 * Chaque photo est d'abord COPIÉE puis contrôlée (même taille, même empreinte SHA-256) ; l'originale n'est effacée
 * qu'ensuite. Si quelque chose cloche, rien ne bouge. Disposition : `index.tsv` (identifiant ⇥ ancien chemin) et un dossier
 * `<identifiant>/<nom d'origine>` par photo.
 */
class Vault(private val dir: File) {

    class Result(
        val done: Int,
        val failed: Int,
        val failures: List<String>,
        /** Les fichiers d'origine qui ont disparu de leur place (pour prévenir la galerie du téléphone). */
        val removedPaths: List<String> = emptyList(),
        /** Les fichiers qui sont arrivés à leur nouvelle place (remise en place). */
        val arrivedPaths: List<String> = emptyList(),
    )

    private val index get() = File(dir, "index.tsv")

    /** Les photos du coffre, les plus récemment ajoutées en dernier. */
    @Synchronized
    fun entries(): List<VaultEntry> {
        if (!index.isFile) return emptyList()
        val result = ArrayList<VaultEntry>()
        index.forEachLine { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2 || parts[0].isEmpty()) return@forEachLine
            val file = File(dir, parts[0]).listFiles()?.firstOrNull { it.isFile } ?: return@forEachLine
            result += VaultEntry(parts[0], file, parts[1])
        }
        return result
    }

    fun count(): Int = entries().size

    /** Met [files] au coffre : copie vérifiée, puis l'original est effacé. [onProgress] : (faits, total). */
    fun add(files: List<File>, onProgress: (Int, Int) -> Unit = { _, _ -> }, originalOf: (File) -> String = { it.absolutePath }): Result {
        if (!dir.isDirectory && !dir.mkdirs()) return Result(0, files.size, listOf("Impossible de préparer le coffre-fort."))
        val needed = files.filter { it.isFile }.sumOf { it.length() }
        if (dir.usableSpace < needed + needed / 10 + 50L * 1024 * 1024) {
            return Result(0, files.size, listOf("Pas assez de place dans la mémoire du téléphone pour ces fichiers. Rien n'a été déplacé."))
        }
        var done = 0
        var failed = 0
        val failures = ArrayList<String>()
        val removed = ArrayList<String>()
        for ((i, source) in files.withIndex()) {
            val problem = addOne(source, originalOf(source))
            if (problem == null) {
                done++
                removed += source.absolutePath
            } else {
                failed++
                if (failures.size < 3) failures += "${source.name} : $problem"
            }
            onProgress(i + 1, files.size)
        }
        return Result(done, failed, failures, removedPaths = removed)
    }

    private fun addOne(source: File, originalPath: String): String? {
        if (!source.isFile) return "fichier introuvable"
        val id = UUID.randomUUID().toString()
        val folder = File(dir, id)
        val target = File(folder, source.name)
        try {
            if (!folder.mkdirs()) return "dossier du coffre impossible à créer"
            val expected = copyWithHash(source, target) ?: return "copie impossible"
            if (target.length() != source.length() || hashOf(target) != expected) {
                folder.deleteRecursively()
                return "la copie ne correspond pas à l'original (rien n'a bougé)"
            }
            target.setLastModified(source.lastModified())
            // On note la photo dans l'index AVANT d'effacer l'original : en cas de coupure, rien n'est perdu.
            synchronized(this) { index.appendText(id + "\t" + originalPath + "\n") }
            if (!source.delete() && source.exists()) {
                forget(id)
                folder.deleteRecursively()
                return "l'original n'a pas pu être retiré de la carte (rien n'a bougé)"
            }
            return null
        } catch (e: Exception) {
            forget(id)
            folder.deleteRecursively()
            return e.message ?: e.javaClass.simpleName
        }
    }

    /**
     * Remet [entries] hors du coffre : à leur ancienne place si ce dossier existe encore, sinon dans [fallbackDir].
     * Copie vérifiée d'abord ; la copie du coffre n'est retirée qu'ensuite.
     */
    fun restore(entries: List<VaultEntry>, fallbackDir: File, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        var done = 0
        var failed = 0
        val failures = ArrayList<String>()
        val arrived = ArrayList<String>()
        for ((i, entry) in entries.withIndex()) {
            val outcome = restoreOne(entry, fallbackDir)
            if (outcome.problem == null) {
                done++
                outcome.arrivedPath?.let { arrived += it }
            } else {
                failed++
                if (failures.size < 3) failures += "${entry.file.name} : ${outcome.problem}"
            }
            onProgress(i + 1, entries.size)
        }
        return Result(done, failed, failures, arrivedPaths = arrived)
    }

    private class Outcome(val problem: String?, val arrivedPath: String? = null)

    private fun restoreOne(entry: VaultEntry, fallbackDir: File): Outcome {
        if (!entry.file.isFile) return Outcome("fichier introuvable")
        val original = File(entry.originalPath).parentFile
        val destDir = if (original != null && original.isDirectory) original else fallbackDir
        try {
            if (!destDir.isDirectory && !destDir.mkdirs()) return Outcome("dossier de destination impossible à créer")
            val target = RenameNames.unique(destDir, entry.file.name)
            val expected = copyWithHash(entry.file, target) ?: return Outcome("copie impossible")
            if (target.length() != entry.file.length() || hashOf(target) != expected) {
                target.delete()
                return Outcome("la copie ne correspond pas (la photo reste dans le coffre)")
            }
            target.setLastModified(entry.file.lastModified())
            forget(entry.id)
            File(dir, entry.id).deleteRecursively()
            return Outcome(null, target.absolutePath)
        } catch (e: Exception) {
            return Outcome(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Efface pour de bon [entries] du coffre. L'écran demande toujours une confirmation avant. */
    fun deleteForever(entries: List<VaultEntry>): Result {
        var done = 0
        var failed = 0
        for (entry in entries) {
            forget(entry.id)
            if (File(dir, entry.id).deleteRecursively()) done++ else failed++
        }
        return Result(done, failed, if (failed > 0) listOf("$failed fichier(s) n'ont pas pu être supprimés.") else emptyList())
    }

    @Synchronized
    private fun forget(id: String) {
        if (!index.isFile) return
        val kept = index.readLines().filter { !it.startsWith(id + "\t") }
        index.writeText(kept.joinToString("") { it + "\n" })
    }

    /** Copie [from] vers [to] en calculant l'empreinte de ce qui est lu. Null si la copie échoue. */
    private fun copyWithHash(from: File, to: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(from).use { input ->
            FileOutputStream(to).use { output ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
                output.flush()
                output.fd.sync()
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        to.delete()
        null
    }

    private fun hashOf(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
