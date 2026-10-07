package fr.rangephotos.logic

import fr.rangephotos.model.PhotoInfo
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/** Une sortie détectée : une ou plusieurs journées consécutives loin de chez soi. */
data class Hike(
    val start: LocalDate,
    val end: LocalDate,
    val centerLat: Double,
    val centerLon: Double,
    val photoCount: Int,
)

/**
 * Détecte les randonnées à partir des positions GPS et des heures des photos.
 *
 * Principe : on déduit d'abord le « domicile » (la zone où l'on prend le plus de photos, jour après
 * jour). Une journée est ensuite considérée comme une sortie si elle contient assez de photos
 * prises loin du domicile, réparties sur au moins une heure. Les journées voisines et proches
 * géographiquement sont fusionnées (randonnée de plusieurs jours).
 */
object HikeDetector {
    const val MIN_DISTANCE_KM = 15.0
    const val MIN_PHOTOS = 5
    const val MIN_SPAN_MINUTES = 60
    const val MERGE_RADIUS_KM = 40.0
    const val MATCH_RADIUS_KM = 60.0
    private const val HOME_CELL_DEGREES = 0.1

    fun detect(photos: List<PhotoInfo>, zone: ZoneId = ZoneId.systemDefault()): List<Hike> {
        val gps = photos.filter { it.hasGps }
        val home = findHome(gps, zone) ?: return emptyList()

        data class DayGroup(val date: LocalDate, val photos: List<PhotoInfo>, val lat: Double, val lon: Double)

        val days = gps
            .groupBy { toDate(it.takenAt, zone) }
            .mapNotNull { (date, list) ->
                val far = list.filter { distanceKm(it.lat!!, it.lon!!, home.first, home.second) > MIN_DISTANCE_KM }
                if (far.size < MIN_PHOTOS) return@mapNotNull null
                val spanMinutes = (far.maxOf { it.takenAt } - far.minOf { it.takenAt }) / 60_000
                if (spanMinutes < MIN_SPAN_MINUTES) return@mapNotNull null
                DayGroup(date, far, far.map { it.lat!! }.average(), far.map { it.lon!! }.average())
            }
            .sortedBy { it.date }

        val hikes = ArrayList<Hike>()
        var current = ArrayList<DayGroup>()

        fun flush() {
            if (current.isEmpty()) return
            hikes += Hike(
                start = current.first().date,
                end = current.last().date,
                centerLat = current.map { it.lat }.average(),
                centerLon = current.map { it.lon }.average(),
                photoCount = current.sumOf { it.photos.size },
            )
            current = ArrayList()
        }

        for (day in days) {
            val last = current.lastOrNull()
            val continues = last != null &&
                last.date.plusDays(1) >= day.date &&
                distanceKm(last.lat, last.lon, day.lat, day.lon) <= MERGE_RADIUS_KM
            if (!continues) flush()
            current += day
        }
        flush()
        return hikes
    }

    /** Retourne la randonnée à laquelle appartient la photo, ou null. */
    fun hikeFor(photo: PhotoInfo, hikes: List<Hike>, zone: ZoneId = ZoneId.systemDefault()): Hike? {
        val date = toDate(photo.takenAt, zone)
        return hikes.firstOrNull { h ->
            date >= h.start && date <= h.end &&
                (!photo.hasGps || distanceKm(photo.lat!!, photo.lon!!, h.centerLat, h.centerLon) <= MATCH_RADIUS_KM)
        }
    }

    /** Domicile = la zone (cellule d'environ 10 km) où l'on prend des photos le plus de jours différents. */
    internal fun findHome(gps: List<PhotoInfo>, zone: ZoneId): Pair<Double, Double>? {
        if (gps.isEmpty()) return null
        fun cell(p: PhotoInfo) =
            Pair((p.lat!! / HOME_CELL_DEGREES).roundToLong(), (p.lon!! / HOME_CELL_DEGREES).roundToLong())

        val best = gps.groupBy { cell(it) }
            .maxByOrNull { (_, list) -> list.map { toDate(it.takenAt, zone) }.toSet().size }
            ?.value ?: return null
        return best.map { it.lat!! }.average() to best.map { it.lon!! }.average()
    }

    private fun toDate(millis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Distance à vol d'oiseau en kilomètres (formule de haversine). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
