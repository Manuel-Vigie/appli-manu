package fr.rangephotos.logic

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Donne un nom lisible à chaque randonnée : « 2026-09-14 Castellane ».
 * Le nom du lieu vient du service de géocodage d'Android (nécessite internet) ;
 * hors connexion, le dossier s'appelle simplement « 2026-09-14 ».
 */
class HikeNamer(private val context: Context) {

    suspend fun name(hike: Hike): String = withContext(Dispatchers.IO) {
        val dates = if (hike.start == hike.end) {
            hike.start.format(DATE)
        } else {
            "${hike.start.format(DATE)} au ${hike.end.format(DATE)}"
        }
        val place = lookupPlace(hike)
        if (place.isNullOrBlank()) dates else "$dates $place"
    }

    @Suppress("DEPRECATION")
    private fun lookupPlace(hike: Hike): String? = try {
        if (!Geocoder.isPresent()) null
        else Geocoder(context, Locale.FRANCE)
            .getFromLocation(hike.centerLat, hike.centerLon, 1)
            ?.firstOrNull()
            ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    } catch (_: Exception) {
        null
    }

    private companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
