package fr.rangephotos.logic

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Nom de la ville d'une photo d'après son lieu GPS (service de géocodage d'Android, nécessite internet).
 * Les photos proches (environ 2 km) partagent la même réponse : peu de demandes, même pour des milliers de photos.
 * Sans internet, la ville reste inconnue et la photo suit les autres règles.
 */
class PlaceNamer(private val context: Context) {

    private val cache = HashMap<String, String?>()
    private var failures = 0

    suspend fun nameFor(lat: Double, lon: Double): String? {
        val key = "${(lat * 50).roundToInt()}:${(lon * 50).roundToInt()}"
        if (cache.containsKey(key)) return cache[key]
        if (failures >= MAX_FAILURES) return null // pas d'internet : on n'insiste pas
        val name = withContext(Dispatchers.IO) { lookup(lat, lon) }
        cache[key] = name
        return name
    }

    @Suppress("DEPRECATION")
    private fun lookup(lat: Double, lon: Double): String? = try {
        if (!Geocoder.isPresent()) {
            failures = MAX_FAILURES
            null
        } else {
            val found = Geocoder(context, Locale.FRANCE).getFromLocation(lat, lon, 1)?.firstOrNull()
                ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
            failures = 0
            found
        }
    } catch (_: Exception) {
        failures++
        null
    }

    private companion object {
        const val MAX_FAILURES = 3
    }
}
