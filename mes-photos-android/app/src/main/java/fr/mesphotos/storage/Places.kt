package fr.mesphotos.storage

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

/** Détection de la carte SD (la mémoire du téléphone n'est jamais utilisée). */
object Places {

    fun detect(context: Context): List<Place> {
        val result = ArrayList<Place>()
        context.getExternalFilesDirs(null).forEachIndexed { index, dir ->
            if (dir == null || index == 0) return@forEachIndexed // le premier est la mémoire du téléphone
            val path = dir.absolutePath
            val cut = path.indexOf("/Android/")
            if (cut <= 0) return@forEachIndexed
            val root = File(path.substring(0, cut))
            if (root.exists()) result += Place(root.name, "Carte SD", root)
        }
        return result
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

    /** Page des réglages Android où l'on autorise Mes Photos (Android 11 et plus). */
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
