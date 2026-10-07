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

class ClassificationTest {

    private val paris = ZoneId.of("Europe/Paris")

    // Marseille (domicile) et un point de randonnée à environ 100 km (Verdon).
    private val homeLat = 43.30
    private val homeLon = 5.37
    private val hikeLat = 43.85
    private val hikeLon = 6.35

    private fun photo(
        time: LocalDateTime,
        lat: Double? = null,
        lon: Double? = null,
        faceCount: Int = 0,
        screenshot: Boolean = false,
        people: List<String?> = emptyList(),
    ) = PhotoInfo(
        uri = mock(Uri::class.java),
        parentUri = null,
        name = "photo.jpg",
        mimeType = "image/jpeg",
        takenAt = time.atZone(paris).toInstant().toEpochMilli(),
        lat = lat,
        lon = lon,
        isScreenshot = screenshot,
        faces = if (people.isNotEmpty()) people.map { FaceInfo(0.3f, null, null, it) } else List(faceCount) { FaceInfo(0.3f, null, null) },
    )

    /** Une photo à la maison sur 6 jours différents : c'est ce qui définit le « domicile ». */
    private fun homePhotos() = (1..6).map {
        photo(LocalDateTime.of(2026, 8, it, 19, 0), homeLat, homeLon)
    }

    /** [count] photos réparties régulièrement entre 9h et 9h+[spanMinutes] à la montagne. */
    private fun hikeDay(day: LocalDate, count: Int = 8, spanMinutes: Long = 240) =
        (0 until count).map {
            photo(
                day.atTime(9, 0).plusMinutes(it * spanMinutes / (count - 1)),
                hikeLat, hikeLon,
            )
        }

    @Test
    fun detectsHikeFarFromHome() {
        val photos = homePhotos() + hikeDay(LocalDate.of(2026, 9, 14))
        val hikes = HikeDetector.detect(photos, paris)
        assertEquals(1, hikes.size)
        assertEquals(LocalDate.of(2026, 9, 14), hikes[0].start)
        assertEquals(LocalDate.of(2026, 9, 14), hikes[0].end)
    }

    @Test
    fun ignoresShortStopFarFromHome() {
        // 6 photos en 10 minutes : une pause en voiture, pas une randonnée.
        val photos = homePhotos() + hikeDay(LocalDate.of(2026, 9, 14), count = 6, spanMinutes = 10)
        assertTrue(HikeDetector.detect(photos, paris).isEmpty())
    }

    @Test
    fun ignoresDaysNearHome() {
        val photos = homePhotos() + (0 until 10).map {
            photo(LocalDateTime.of(2026, 9, 14, 9, 0).plusMinutes(it * 30L), homeLat, homeLon)
        }
        assertTrue(HikeDetector.detect(photos, paris).isEmpty())
    }

    @Test
    fun mergesConsecutiveDaysIntoOneHike() {
        val photos = homePhotos() +
            hikeDay(LocalDate.of(2026, 9, 14)) +
            hikeDay(LocalDate.of(2026, 9, 15))
        val hikes = HikeDetector.detect(photos, paris)
        assertEquals(1, hikes.size)
        assertEquals(LocalDate.of(2026, 9, 14), hikes[0].start)
        assertEquals(LocalDate.of(2026, 9, 15), hikes[0].end)
    }

    @Test
    fun separateWeeksAreSeparateHikes() {
        val photos = homePhotos() +
            hikeDay(LocalDate.of(2026, 9, 14)) +
            hikeDay(LocalDate.of(2026, 9, 28))
        assertEquals(2, HikeDetector.detect(photos, paris).size)
    }

    @Test
    fun plannerBuildsTheExpectedFolders() {
        val hikePhotos = hikeDay(LocalDate.of(2026, 9, 14))
        val portraitInHike = photo(LocalDateTime.of(2026, 9, 14, 11, 0), hikeLat, hikeLon, faceCount = 1)
        val groupAtHome = photo(LocalDateTime.of(2026, 3, 5, 20, 0), homeLat, homeLon, faceCount = 2)
        val soloAtHome = photo(LocalDateTime.of(2026, 4, 5, 20, 0), homeLat, homeLon, faceCount = 1)
        val plain = photo(LocalDateTime.of(2026, 3, 5, 12, 0))
        val screenshot = photo(LocalDateTime.of(2026, 1, 2, 12, 0), screenshot = true)

        val all = homePhotos() + hikePhotos + portraitInHike + groupAtHome + soloAtHome + plain + screenshot
        val hikes = HikeDetector.detect(all, paris)
        assertEquals(1, hikes.size)
        val names = mapOf(hikes[0] to "2026-09-14 Verdon")

        val plan = Planner.plan(all, hikes, names, zone = paris).associateBy { it.photo }

        assertEquals(listOf("Randonnées", "2026-09-14 Verdon"), plan.getValue(hikePhotos[0]).folder)
        assertEquals(listOf("Randonnées", "2026-09-14 Verdon", "Portraits", "Solo"), plan.getValue(portraitInHike).folder)
        assertEquals(listOf("Portraits", "Groupe", "2026"), plan.getValue(groupAtHome).folder)
        assertEquals(listOf("Portraits", "Solo", "2026"), plan.getValue(soloAtHome).folder)
        assertEquals(listOf("Journées", "2026", "03 - mars"), plan.getValue(plain).folder.take(3))
        assertEquals("05 mars (jeu)", plan.getValue(plain).folder.last())
        assertEquals(listOf("Captures d'écran", "2026"), plan.getValue(screenshot).folder)
    }

    @Test
    fun photosWithoutGpsJoinTheHikeOnlyDuringTheOuting() {
        val day = LocalDate.of(2026, 9, 14)
        val during = photo(day.atTime(11, 0)) // sans GPS, en pleine sortie
        val evening = photo(day.atTime(20, 0)) // sans GPS, le soir à la maison
        val all = homePhotos() + hikeDay(day) + during + evening
        val hikes = HikeDetector.detect(all, paris)
        val names = mapOf(hikes[0] to "2026-09-14 Verdon")

        val plan = Planner.plan(all, hikes, names, zone = paris).associateBy { it.photo }

        assertEquals(listOf("Randonnées", "2026-09-14 Verdon"), plan.getValue(during).folder)
        assertEquals("sortie=${hikes[0]} soir=${evening.takenAt} gps=${evening.hasGps}", listOf("Journées", "2026", "09 - septembre"), plan.getValue(evening).folder.take(3))
        assertEquals("14 septembre (lun)", plan.getValue(evening).folder.last())
    }

    @Test
    fun dayFolderShowsTheTownWhenKnown() {
        val plain = photo(LocalDateTime.of(2026, 3, 5, 12, 0))
        val day = LocalDate.of(2026, 3, 5)
        val withTown = Planner.plan(listOf(plain), emptyList(), emptyMap(), zone = paris, dayPlaces = mapOf(day to "Nice")).single()
        assertEquals("05 mars (jeu) - Nice", withTown.folder.last())
        val without = Planner.plan(listOf(plain), emptyList(), emptyMap(), zone = paris).single()
        assertEquals("05 mars (jeu)", without.folder.last())
    }

    @Test
    fun namedPeopleGetTheirOwnFoldersAndCopies() {
        val soloJulie = photo(LocalDateTime.of(2026, 4, 5, 20, 0), homeLat, homeLon, people = listOf("Julie"))
        val groupe = photo(LocalDateTime.of(2026, 4, 6, 20, 0), homeLat, homeLon, people = listOf("Julie", "Maman", null))
        val all = homePhotos() + soloJulie + groupe
        val plan = Planner.plan(all, emptyList(), emptyMap(), true, paris).associateBy { it.photo }

        assertEquals(listOf("Portraits", "Julie", "2026"), plan.getValue(soloJulie).folder)
        assertTrue(plan.getValue(soloJulie).copies.isEmpty())

        assertEquals(listOf("Portraits", "Groupe", "2026"), plan.getValue(groupe).folder)
        assertEquals(
            listOf(listOf("Portraits", "Julie", "2026"), listOf("Portraits", "Maman", "2026")),
            plan.getValue(groupe).copies,
        )

        val sansCopie = Planner.plan(all, emptyList(), emptyMap(), false, paris).associateBy { it.photo }
        assertTrue(sansCopie.getValue(groupe).copies.isEmpty())
    }

    @Test
    fun namedPersonOnAHikeIsFiledInTheHikeAndCopiedToTheirFolder() {
        val hikePhotos = hikeDay(LocalDate.of(2026, 9, 14))
        val julie = photo(LocalDateTime.of(2026, 9, 14, 11, 0), hikeLat, hikeLon, people = listOf("Julie"))
        val all = homePhotos() + hikePhotos + julie
        val hikes = HikeDetector.detect(all, paris)
        val names = mapOf(hikes[0] to "2026-09-14 Verdon")

        val move = Planner.plan(all, hikes, names, true, paris).first { it.photo === julie }
        assertEquals(listOf("Randonnées", "2026-09-14 Verdon", "Portraits", "Julie"), move.folder)
        assertEquals(listOf(listOf("Portraits", "Julie", "2026")), move.copies)
    }

    @Test
    fun sanitizeRemovesForbiddenCharacters() {
        assertEquals("a-b-c", Planner.sanitize("a/b:c"))
        assertEquals("Sans nom", Planner.sanitize("..."))
    }
}
