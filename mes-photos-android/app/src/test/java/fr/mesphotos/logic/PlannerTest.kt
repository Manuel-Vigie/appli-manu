package fr.mesphotos.logic

import fr.mesphotos.model.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class PlannerTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private fun ms(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun everythingGoesByDayWithTheTown() {
        val photo = PhotoInfo("a.jpg", ms(2026, 3, 14))
        val video = PhotoInfo("b.mp4", ms(2026, 3, 14), isVideo = true)
        val moves = Planner.plan(listOf(photo, video), zone, mapOf(LocalDate.of(2026, 3, 14) to "Nice"))
        val expected = listOf("Journées", "2026", "03 - mars", "14 mars (sam) - Nice")
        assertEquals(expected, moves[0].folder)
        assertEquals(expected, moves[1].folder)
    }

    @Test
    fun withoutTownTheDayFolderHasNoSuffix() {
        val moves = Planner.plan(listOf(PhotoInfo("a.jpg", ms(2020, 1, 5))), zone)
        assertEquals(listOf("Journées", "2020", "01 - janvier", "05 janvier (dim)"), moves[0].folder)
    }

    @Test
    fun undatedPhotosWithAPlaceGoApartByMonth() {
        val photo = PhotoInfo("a.jpg", ms(2026, 10, 1), lat = 43.7, lon = 7.26, dateGuessed = true)
        val moves = Planner.plan(listOf(photo), zone)
        assertEquals(listOf("Journées", "Date incertaine", "2026", "10 - octobre"), moves[0].folder)
    }

    @Test
    fun photosWithNeitherDateNorPlaceAllGoInOneFolder() {
        val a = PhotoInfo("a.jpg", ms(2026, 10, 1), dateGuessed = true)
        val b = PhotoInfo("b.jpg", ms(2019, 2, 3), dateGuessed = true)
        val moves = Planner.plan(listOf(a, b), zone)
        assertEquals(listOf("Journées", "Sans date ni lieu"), moves[0].folder)
        assertEquals(moves[0].folder, moves[1].folder)
    }

    @Test
    fun aReliableDateWithoutPlaceStaysByDay() {
        val moves = Planner.plan(listOf(PhotoInfo("a.jpg", ms(2026, 3, 14))), zone)
        assertEquals("Journées", moves[0].folder[0])
        assertEquals("2026", moves[0].folder[1])
    }

    @Test
    fun forbiddenCharactersAreRemoved() {
        assertEquals("Aix-en-Provence", Planner.sanitize("Aix-en-Provence"))
        assertEquals("A-B", Planner.sanitize("A/B"))
    }
}
