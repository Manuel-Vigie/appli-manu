package fr.rangephotos.organize

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import fr.rangephotos.logic.PlannedMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class OrganizeResult(
    val moved: Int,
    val failed: Int,
    val copied: Int = 0,
    val copyFailed: Int = 0,
    /** Copies non refaites parce qu'elles existent déjà (même nom, même taille). */
    val copiesAlreadyThere: Int = 0,
)

/**
 * Déplace réellement les photos dans leurs dossiers.
 *
 * Sécurité : on utilise le déplacement natif du système (instantané, aucune copie, aucune photo
 * perdue en cas d'interruption). Si le système ne le permet pas, on copie, on vérifie que la taille
 * est identique, et seulement ensuite on supprime l'original.
 *
 * Les copies demandées (photos de proches dans leur dossier) sont faites après le déplacement, avec la
 * même vérification de taille. Chaque déplacement et chaque copie est écrit dans un journal, ce qui permet
 * d'annuler tout le rangement : les photos reviennent à leur place et seules les copies créées par l'appli
 * sont supprimées.
 */
class Organizer(private val context: Context) {

    private val resolver get() = context.contentResolver

    suspend fun execute(
        rootUri: Uri,
        moves: List<PlannedMove>,
        journal: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ): OrganizeResult = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri)
            ?: return@withContext OrganizeResult(0, moves.size)
        val outputRoot = root.findFile(OUTPUT_DIR)?.takeIf { it.isDirectory }
            ?: root.createDirectory(OUTPUT_DIR)
            ?: return@withContext OrganizeResult(0, moves.size)

        journal.parentFile?.mkdirs()
        journal.writeText("") // nouveau rangement = nouveau journal

        val dirCache = HashMap<List<String>, DocumentFile>()
        val namesInDir = HashMap<String, MutableSet<String>>()
        var moved = 0
        var failed = 0
        var copied = 0
        var copyFailed = 0
        var copiesAlreadyThere = 0

        fun namesOf(dir: DocumentFile): MutableSet<String> =
            namesInDir.getOrPut(dir.uri.toString()) { dir.listFiles().mapNotNull { it.name }.toMutableSet() }

        moves.forEachIndexed { index, move ->
            try {
                val inPlace = move.photo.currentFolder != null && move.photo.currentFolder == move.folder
                val target = targetDir(outputRoot, move.folder, dirCache)
                val names = namesOf(target)
                val finalName = uniqueName(move.photo.name, names)
                val parentUri = move.photo.parentUri
                val newUri: Uri? = if (inPlace) {
                    move.photo.uri // déjà au bon endroit : on ne la déplace pas
                } else if (parentUri != null) {
                    moveOrCopy(move.photo.uri, parentUri, target, move.photo.mimeType, move.photo.name, finalName)
                } else {
                    null
                }

                if (newUri != null) {
                    if (!inPlace) {
                        names += finalName
                        journal.appendText("M\t$newUri\t${target.uri}\t$parentUri\n")
                        moved++
                    }

                    for (folder in move.copies) {
                        try {
                            val dir = targetDir(outputRoot, folder, dirCache)
                            val dirNames = namesOf(dir)
                            if (move.photo.size > 0 && move.photo.name in dirNames &&
                                dir.findFile(move.photo.name)?.length() == move.photo.size
                            ) {
                                copiesAlreadyThere++
                                continue
                            }
                            val copyName = uniqueName(move.photo.name, dirNames)
                            val copyUri = copyFile(newUri, dir, move.photo.mimeType, copyName)
                            if (copyUri != null) {
                                dirNames += copyName
                                journal.appendText("C\t$copyUri\n")
                                copied++
                            } else {
                                copyFailed++
                            }
                        } catch (_: Exception) {
                            copyFailed++
                        }
                    }
                } else {
                    failed++
                }
            } catch (_: Exception) {
                failed++
            }
            onProgress(index + 1, moves.size)
        }
        OrganizeResult(moved, failed, copied, copyFailed, copiesAlreadyThere)
    }

    /** Annule le dernier rangement : supprime les copies créées par l'appli, remet chaque photo dans son dossier d'origine. */
    suspend fun undo(journal: File, onProgress: (done: Int, total: Int) -> Unit): OrganizeResult =
        withContext(Dispatchers.IO) {
            if (!journal.exists()) return@withContext OrganizeResult(0, 0)
            val lines = journal.readLines().filter { it.isNotBlank() }.reversed()
            var restored = 0
            var failed = 0
            var removedCopies = 0
            val remaining = ArrayList<String>()

            lines.forEachIndexed { index, line ->
                val parts = line.split('\t')
                var ok = false
                try {
                    when {
                        parts[0] == "C" && parts.size == 2 -> {
                            ok = DocumentFile.fromSingleUri(context, Uri.parse(parts[1]))?.delete() == true
                            if (ok) removedCopies++
                        }
                        parts[0] == "M" && parts.size == 4 && parts[3] != "null" -> {
                            ok = moveBack(parts[1], parts[2], parts[3])
                            if (ok) restored++
                        }
                        parts.size == 3 && parts[2] != "null" -> { // ancien format de journal
                            ok = moveBack(parts[0], parts[1], parts[2])
                            if (ok) restored++
                        }
                    }
                } catch (_: Exception) {
                }
                if (!ok) {
                    failed++
                    remaining += line
                }
                onProgress(index + 1, lines.size)
            }
            // On garde dans le journal ce qui n'a pas pu être annulé (ordre d'origine).
            journal.writeText(remaining.reversed().joinToString("\n", postfix = if (remaining.isEmpty()) "" else "\n"))
            OrganizeResult(restored, failed, removedCopies, 0)
        }

    private fun moveBack(document: String, currentParent: String, originalParent: String): Boolean =
        DocumentsContract.moveDocument(
            resolver, Uri.parse(document), Uri.parse(currentParent), Uri.parse(originalParent),
        ) != null

    private fun targetDir(
        outputRoot: DocumentFile,
        folder: List<String>,
        cache: MutableMap<List<String>, DocumentFile>,
    ): DocumentFile {
        cache[folder]?.let { return it }
        var current = outputRoot
        for (i in folder.indices) {
            val key = folder.subList(0, i + 1)
            current = cache.getOrPut(key) {
                current.findFile(folder[i])?.takeIf { it.isDirectory }
                    ?: current.createDirectory(folder[i])
                    ?: error("Impossible de créer le dossier ${folder[i]}")
            }
        }
        return current
    }

    private fun uniqueName(name: String, existing: Set<String>): String {
        if (name !in existing) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while ("$base ($n)$ext" in existing) n++
        return "$base ($n)$ext"
    }

    /** Retourne l'URI de la photo à son nouvel emplacement, ou null en cas d'échec. */
    private fun moveOrCopy(
        source: Uri,
        sourceParent: Uri,
        target: DocumentFile,
        mime: String,
        originalName: String,
        finalName: String,
    ): Uri? {
        // 1) Déplacement natif (le plus sûr et le plus rapide).
        try {
            var moved = DocumentsContract.moveDocument(resolver, source, sourceParent, target.uri)
            if (moved != null) {
                if (finalName != originalName) {
                    moved = DocumentsContract.renameDocument(resolver, moved, finalName) ?: moved
                }
                return moved
            }
        } catch (_: Exception) {
            // Non supporté par ce fournisseur : on passe à la copie vérifiée.
        }

        // 2) Copie, vérification de la taille, puis suppression de l'original.
        val sourceFile = DocumentFile.fromSingleUri(context, source) ?: return null
        val copy = copyFile(source, target, mime, finalName) ?: return null
        return if (sourceFile.delete()) {
            copy
        } else {
            DocumentFile.fromSingleUri(context, copy)?.delete()
            null
        }
    }

    /** Copie [source] dans [target] et vérifie que la taille est identique. Retourne l'URI de la copie, ou null. */
    private fun copyFile(source: Uri, target: DocumentFile, mime: String, name: String): Uri? {
        val expected = DocumentFile.fromSingleUri(context, source)?.length() ?: return null
        val copy = target.createFile(mime, name) ?: return null
        return try {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(copy.uri)?.use { output -> input.copyTo(output) }
                    ?: throw IllegalStateException("écriture impossible")
            } ?: throw IllegalStateException("lecture impossible")

            if (copy.length() == expected) {
                copy.uri
            } else {
                copy.delete()
                null
            }
        } catch (_: Exception) {
            copy.delete() // copie incomplète : on la retire, l'original n'a pas bougé
            null
        }
    }

    companion object {
        const val OUTPUT_DIR = "Photos rangées"
    }
}
