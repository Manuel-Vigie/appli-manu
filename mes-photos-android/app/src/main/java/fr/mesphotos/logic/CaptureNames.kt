package fr.mesphotos.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Le nom des photos prises avec l'appareil photo de l'appli : « IMG_20261008_140512.jpg » (la date est lisible dans le nom, comme sur le téléphone). */
object CaptureNames {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun fileName(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        "IMG_" + FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone)) + ".jpg"
}
