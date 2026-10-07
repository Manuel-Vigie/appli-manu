package fr.rangephotos.scan

import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import fr.rangephotos.model.PhotoInfo
import fr.rangephotos.storage.Place
import fr.rangephotos.storage.PhotoFiles
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Lit la date et le lieu (EXIF) de chaque photo trouvée dans un endroit. */
class PhotoScanner {

    fun scan(place: Place, includeSorted: Boolean, onProgress: (Int) -> Unit): List<PhotoInfo> {
        val found = PhotoFiles.list(place, includeSorted, onProgress)
        return found.map { readPhoto(it.file, it.relative) }
    }

    /** (photos à ranger, photos déjà rangées). */
    fun counts(place: Place): Pair<Int, Int> = PhotoFiles.counts(place)

    private fun readPhoto(file: File, currentFolder: List<String>?): PhotoInfo {
        val name = file.name
        var takenAt: Long? = null
        var lat: Double? = null
        var lon: Double? = null

        try {
            val exif = ExifInterface(file.absolutePath)
            takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { parseExifDate(it) }
            exif.latLong?.let {
                lat = it[0]
                lon = it[1]
            }
        } catch (_: Exception) {
            // Fichier sans EXIF lisible : on se rabat sur le nom ou la date du fichier.
        }

        val date = takenAt ?: dateFromFileName(name) ?: file.lastModified()
        return PhotoInfo(
            uri = Uri.fromFile(file),
            parentUri = null,
            name = name,
            mimeType = PhotoFiles.mimeOf(file) ?: "image/jpeg",
            takenAt = date,
            lat = lat,
            lon = lon,
            isScreenshot = looksLikeScreenshot(name),
            size = file.length(),
            currentFolder = currentFolder,
            path = file.absolutePath,
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
        val FILENAME_DATE = Regex("""(20\d{2})(\d{2})(\d{2})(?:[_-]?(\d{2})(\d{2})(\d{2}))?""")
    }
}
