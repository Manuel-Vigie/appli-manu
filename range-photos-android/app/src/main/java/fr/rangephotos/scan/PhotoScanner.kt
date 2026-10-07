package fr.rangephotos.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import fr.rangephotos.model.PhotoInfo
import fr.rangephotos.organize.Organizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Parcourt le dossier choisi par l'utilisateur (via le sélecteur de dossier Android, qui est
 * la seule façon fiable d'accéder à une carte SD) et lit les métadonnées de chaque photo.
 */
class PhotoScanner(private val context: Context) {

    suspend fun scan(rootUri: Uri, includeSorted: Boolean = false, onProgress: (Int) -> Unit): List<PhotoInfo> =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
            val result = ArrayList<PhotoInfo>()

            // [relative] : chemin sous « Photos rangées » si on y est déjà, sinon null.
            fun walk(dir: DocumentFile, relative: List<String>?) {
                for (file in dir.listFiles()) {
                    if (file.isDirectory) {
                        val name = file.name ?: continue
                        if (relative == null && name == Organizer.OUTPUT_DIR) {
                            // Par défaut les photos déjà rangées restent en place ; « Tout reclasser » les reprend.
                            if (includeSorted) walk(file, emptyList())
                        } else {
                            walk(file, relative?.plus(name))
                        }
                    } else {
                        val mime = file.type ?: continue
                        if (mime !in SUPPORTED_TYPES) continue
                        result += readPhoto(file, dir, mime, relative)
                        if (result.size % 25 == 0) onProgress(result.size)
                    }
                }
            }

            walk(root, null)
            onProgress(result.size)
            result
        }

    /** Nombre de photos déjà rangées plus tôt (dans « Photos rangées ») : elles ne sont jamais retouchées. */
    suspend fun countAlreadySorted(rootUri: Uri): Int =
        withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext 0
            val sorted = root.findFile(Organizer.OUTPUT_DIR) ?: return@withContext 0
            var count = 0
            fun walk(dir: DocumentFile) {
                for (file in dir.listFiles()) {
                    if (file.isDirectory) walk(file) else if (file.type?.let { it in SUPPORTED_TYPES } == true) count++
                }
            }
            walk(sorted)
            count
        }

    private fun readPhoto(file: DocumentFile, parent: DocumentFile, mime: String, currentFolder: List<String>?): PhotoInfo {
        val name = file.name ?: "photo"
        var takenAt: Long? = null
        var lat: Double? = null
        var lon: Double? = null

        try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                val exif = ExifInterface(stream)
                takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?.let { parseExifDate(it) }
                exif.latLong?.let {
                    lat = it[0]
                    lon = it[1]
                }
            }
        } catch (_: Exception) {
            // Fichier sans EXIF lisible : on se rabat sur le nom ou la date du fichier.
        }

        val date = takenAt ?: dateFromFileName(name) ?: file.lastModified()
        return PhotoInfo(
            uri = file.uri,
            parentUri = parent.uri,
            name = name,
            mimeType = mime,
            takenAt = date,
            lat = lat,
            lon = lon,
            isScreenshot = looksLikeScreenshot(name),
            size = file.length(),
            currentFolder = currentFolder,
        )
    }

    private fun parseExifDate(value: String): Long? = try {
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(value.trim())?.time
    } catch (_: Exception) {
        null
    }

    /** Reconnaît les noms du type IMG_20260914_101530.jpg ou 20260914-101530.jpg. */
    private fun dateFromFileName(name: String): Long? {
        val m = FILENAME_DATE.find(name) ?: return null
        val (y, mo, d) = m.destructured
        val h = m.groupValues[4].ifEmpty { "12" }
        val mi = m.groupValues[5].ifEmpty { "0" }
        val s = m.groupValues[6].ifEmpty { "0" }
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), s.toInt())
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeScreenshot(name: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        return "screenshot" in n || "capture d" in n || "screen_shot" in n || n.startsWith("capture")
    }

    private companion object {
        val SUPPORTED_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
        val FILENAME_DATE = Regex("""(20\d{2})(\d{2})(\d{2})(?:[_-]?(\d{2})(\d{2})(\d{2}))?""")
    }
}
