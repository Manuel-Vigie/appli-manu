package fr.rangephotos.people

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import fr.rangephotos.organize.Organizer

/**
 * Copie de sauvegarde des prénoms dans « Photos rangées/mes-proches.json » sur la carte SD :
 * en changeant de téléphone, il suffit de réinstaller l'appli et de choisir la même carte.
 */
class PeopleSync(private val context: Context) {

    /** Contenu de la sauvegarde présente dans le dossier choisi, ou null s'il n'y en a pas. */
    fun read(root: Uri): String? = try {
        val file = locate(root, create = false)
        file?.let { context.contentResolver.openInputStream(it.uri)?.bufferedReader()?.use { r -> r.readText() } }
    } catch (_: Exception) {
        null
    }

    /** Écrit (ou remplace) la sauvegarde. Retourne true si tout s'est bien passé. */
    fun write(root: Uri, json: String): Boolean = try {
        val file = locate(root, create = true)
        if (file == null) {
            false
        } else {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) } != null
        }
    } catch (_: Exception) {
        false
    }

    private fun locate(root: Uri, create: Boolean): DocumentFile? {
        val top = DocumentFile.fromTreeUri(context, root) ?: return null
        val dir = top.findFile(Organizer.OUTPUT_DIR)?.takeIf { it.isDirectory }
            ?: if (create) top.createDirectory(Organizer.OUTPUT_DIR) else null
        val folder = dir ?: return null
        return folder.findFile(FILE_NAME) ?: if (create) folder.createFile("application/json", FILE_NAME) else null
    }

    private companion object {
        const val FILE_NAME = "mes-proches.json"
    }
}
