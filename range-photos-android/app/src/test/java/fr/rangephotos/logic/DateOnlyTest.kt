package fr.rangephotos.logic

import android.net.Uri
import fr.rangephotos.model.FaceInfo
import fr.rangephotos.model.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Mode « tout par date » : chaque photo ou vidéo va dans le dossier de son jour, quoi qu'il y ait dessus. */
class DateOnlyTest {

    private val paris = ZoneId.of("Europe/Paris")

    private fun photo(
        time: LocalDateTime,
        faces: Int = 0,
        screenshot: Boolean = false,
        video: Boolean = false,
        lat: Double? = null,
        lon: Double? = null,
    ) = PhotoInfo(
        uri = mock(Uri::class.java), parentUri = null, name = if (video) "v.mp4" else "p.jpg",
        mimeType = if (video) "video/mp4" else "image/jpeg",
        takenAt = time.atZone(paris).toInstant().toEpochMilli(), lat = lat, lon = lon,
        isScreenshot = screenshot, isVideo = video,
        faces = List(faces) { FaceInfo(0.3f, null, null, "Julie") },
    )

    @Test
    fun everythingGoesInItsDayFolder() {
        val t = LocalDateTime.of(2026, 3, 14, 10, 0)
        val photos = listOf(
            photo(t),
            photo(t.plusHours(1), faces = 2),
            photo(t.plusHours(2), screenshot = true),
            photo(t.plusHours(3), video = true),
            photo(t.plusHours(4), lat = 43.85, lon = 6.35),
        )
        val plan = Planner.plan(
            photos, emptyList(), emptyMap(), copyToPeople = true, zone = paris,
            dayPlaces = mapOf(LocalDate.of(2026, 3, 14) to "Nice"), dateOnly = true,
        )
        val expected = listOf("Journées", "2026", "03 - mars", "14 mars (sam) - Nice")
        assertEquals(5, plan.size)
        plan.forEach {
            assertEquals(expected, it.folder)
            assertTrue(it.copies.isEmpty())
        }
    }

    @Test
    fun otherDaysGoElsewhere() {
        val plan = Planner.plan(
            listOf(photo(LocalDateTime.of(2026, 3, 14, 10, 0)), photo(LocalDateTime.of(2026, 3, 15, 10, 0))),
            emptyList(), emptyMap(), zone = paris, dateOnly = true,
        )
        assertEquals(listOf("Journées", "2026", "03 - mars", "14 mars (sam)"), plan[0].folder)
        assertEquals(listOf("Journées", "2026", "03 - mars", "15 mars (dim)"), plan[1].folder)
    }
}
