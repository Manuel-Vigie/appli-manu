package fr.mesphotos.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateChoiceTest {

    private fun ms(y: Int, m: Int, d: Int, h: Int = 12) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, 0, 0) }.timeInMillis

    private val now = ms(2026, 10, 7)

    @Test
    fun readsDatesInFileNames() {
        assertEquals(ms(2026, 9, 14, 10).let { it + 15 * 60_000 + 30_000 }, DateChoice.fromFileName("IMG_20260914_101530.jpg"))
        assertEquals(ms(2019, 2, 3), DateChoice.fromFileName("IMG-20190203-WA0001.jpg"))
        assertEquals(ms(2021, 12, 25), DateChoice.fromFileName("Photo 2021-12-25.jpg"))
    }

    @Test
    fun ignoresNumbersThatAreNotDates() {
        assertNull(DateChoice.fromFileName("1620123456789.jpg"))      // heure Unix en millisecondes
        assertNull(DateChoice.fromFileName("IMG_20261340_1.jpg"))     // mois 13
        assertNull(DateChoice.fromFileName("photo.jpg"))
        assertNull(DateChoice.fromFileName("scan_123456789012.jpg"))
    }

    @Test
    fun insideDateWinsForPhotosAndNameWinsForVideos() {
        val inside = ms(2015, 6, 1)
        val name = ms(2016, 7, 2)
        assertEquals(inside to false, DateChoice.pick(false, inside, name, null, now, now))
        assertEquals(name to false, DateChoice.pick(true, inside, name, null, now, now))
    }

    @Test
    fun fileDateIsOnlyALastResortAndIsFlagged() {
        val modified = ms(2026, 10, 1)
        assertEquals(modified to true, DateChoice.pick(false, null, null, null, modified, now))
        assertTrue(DateChoice.pick(false, null, null, null, modified, now).second)
    }

    @Test
    fun implausibleDatesAreRejected() {
        val modified = ms(2026, 10, 1)
        val future = ms(2031, 1, 1)
        val ancient = ms(1971, 1, 1)
        val r = DateChoice.pick(false, future, ancient, null, modified, now)
        assertEquals(modified, r.first)
        assertTrue(r.second)
        assertFalse(DateChoice.pick(false, ms(2010, 5, 5), null, null, modified, now).second)
        assertNotNull(DateChoice.fromFileName("20100505.jpg"))
    }
}
