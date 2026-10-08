package fr.mesphotos.logic

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Le nom des photos prises avec l'appli : « IMG_20261008_140512.jpg » (la date est lisible dans le nom, comme sur le téléphone).
 * Si la personne a dit ce qu'elle photographie, c'est devant : « Immatriculation - IMG_20261008_140512.jpg ».
 * La loupe retrouve alors la photo par ce nom, et le rangement par date lit toujours la date dans le nom.
 */
object CaptureNames {
    private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun fileName(millis: Long, zone: ZoneId = ZoneId.systemDefault(), label: String = ""): String {
        val plain = "IMG_" + FORMAT.format(Instant.ofEpochMilli(millis).atZone(zone)) + ".jpg"
        if (label.isBlank()) return plain
        return RenameNames.build(label, plain, keepOriginal = true) ?: plain
    }
}
