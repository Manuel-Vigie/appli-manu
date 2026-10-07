package fr.rangephotos.storage

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import fr.rangephotos.organize.Organizer
import java.io.File

/** Détection de la carte SD et de la mémoire du téléphone, et autorisation d'accès aux fichiers. */
object Places {

    fun detect(context: Context): List<Place> {
        val result = ArrayList<Place>()
        context.getExternalFilesDirs(null).forEachIndexed { index, dir ->
            if (dir == null) return@forEachIndexed
            val path = dir.absolutePath
            val cut = path.indexOf("/Android/")
            if (cut <= 0) return@forEachIndexed
            val root = File(path.substring(0, cut))
            if (!root.exists()) return@forEachIndexed
            if (index == 0) {
                // Le premier est toujours la mémoire du téléphone : on ne parcourt que DCIM et Pictures.
                val folders = listOf("DCIM", "Pictures").map { File(root, it) }.filter { it.isDirectory }
                result += Place("primary", "Mémoire du téléphone", true, root, folders)
            } else {
                result += Place(root.name, "Carte SD", false, root, listOf(root))
            }
        }
        return result
    }

    /** Ouvre le dossier « Photos rangées » dans l'appli Fichiers du téléphone. */
    fun openIntent(place: Place): Intent {
        val documentId = place.key + ":" + Organizer.OUTPUT_DIR
        val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", documentId)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/** L'autorisation « gérer tous les fichiers » (Android 11 et plus) ou l'accès au stockage (avant). */
object Access {
    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

    /** Page des réglages Android où l'on autorise Range Photos (Android 11 et plus). */
    fun settingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                android.net.Uri.parse("package:" + context.packageName),
            )
        } else {
            null
        }
}
