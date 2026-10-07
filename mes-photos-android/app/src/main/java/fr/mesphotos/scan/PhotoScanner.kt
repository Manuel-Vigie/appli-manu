package fr.mesphotos.scan

import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import fr.mesphotos.model.PhotoInfo
import fr.mesphotos.storage.Place
import fr.mesphotos.storage.PhotoFiles
import java.io.File
import java.text.SimpleDateFormat
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
        var gpsDate: Long? = null
        var lat: Double? = null
        var lon: Double? = null
        val video = PhotoFiles.isVideo(file)

        if (video) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                VideoMeta.parseLocation(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))?.let {
                    lat = it.first
                    lon = it.second
                }
                takenAt = VideoMeta.parseDate(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE))
            } catch (_: Exception) {
                // Vidéo sans information lisible : on se rabat sur le nom ou la date du fichier.
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        } else try {
            val exif = ExifInterface(file.absolutePath)
            takenAt = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { parseExifDate(it) }
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)?.let { parseExifDate(it) }
            gpsDate = exif.getAttribute(ExifInterface.TAG_GPS_DATESTAMP)?.let { parseGpsDate(it) }
            exif.latLong?.let {
                lat = it[0]
                lon = it[1]
            }
        } catch (_: Exception) {
            // Fichier sans EXIF lisible : on se rabat sur le nom ou la date du fichier.
        }

        // Jamais de date inventée : sans date fiable (EXIF, nom, GPS), la date du fichier est utilisée mais signalée.
        val (date, guessed) = DateChoice.pick(video, takenAt, DateChoice.fromFileName(name), gpsDate, file.lastModified())
        return PhotoInfo(
            name = name,
            takenAt = date,
            lat = lat,
            lon = lon,
            size = file.length(),
            currentFolder = currentFolder,
            path = file.absolutePath,
            isVideo = video,
            dateGuessed = guessed,
        )
    }

    private fun parseExifDate(value: String): Long? = try {
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(value.trim())?.time
    } catch (_: Exception) {
        null
    }

    private fun parseGpsDate(value: String): Long? = try {
        SimpleDateFormat("yyyy:MM:dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(value.trim())?.time?.plus(12L * 3600 * 1000)
    } catch (_: Exception) {
        null
    }
}
