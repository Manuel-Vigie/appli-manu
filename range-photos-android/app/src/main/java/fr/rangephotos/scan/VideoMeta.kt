package fr.rangephotos.scan

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Lecture des informations d'une vidéo (date de prise de vue, lieu) à partir du texte donné par Android. */
object VideoMeta {

    private val LOCATION = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""")

    /** « +43.8500+006.3500/ » -> (43.85, 6.35) ; null si absent ou invalide. */
    fun parseLocation(raw: String?): Pair<Double, Double>? {
        val match = LOCATION.find(raw ?: return null) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        return lat to lon
    }

    /** « 20260914T101530.000Z » -> millisecondes ; null si absent, illisible ou avant 2000 (date vide). */
    fun parseDate(raw: String?): Long? {
        val text = raw?.trim()?.take(15) ?: return null
        return try {
            val format = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
            format.parse(text)?.time?.takeIf { it >= 946_684_800_000L } // 1er janvier 2000
        } catch (_: Exception) {
            null
        }
    }
}
