package fr.rangephotos.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoMetaTest {

    @Test
    fun readsLocation() {
        val (lat, lon) = VideoMeta.parseLocation("+43.8500+006.3500/")!!
        assertEquals(43.85, lat, 1e-9)
        assertEquals(6.35, lon, 1e-9)
        val (lat2, lon2) = VideoMeta.parseLocation("-12.5-070.25/")!!
        assertEquals(-12.5, lat2, 1e-9)
        assertEquals(-70.25, lon2, 1e-9)
    }

    @Test
    fun ignoresMissingOrEmptyLocation() {
        assertNull(VideoMeta.parseLocation(null))
        assertNull(VideoMeta.parseLocation(""))
        assertNull(VideoMeta.parseLocation("+0.0000+0.0000/"))
        assertNull(VideoMeta.parseLocation("+95.0+006.0/"))
    }

    @Test
    fun readsDate() {
        // 14 septembre 2026, 10h15m30 UTC
        assertEquals(1_789_380_930_000L, VideoMeta.parseDate("20260914T101530.000Z"))
        assertNull(VideoMeta.parseDate(null))
        assertNull(VideoMeta.parseDate("n'importe quoi"))
        assertNull(VideoMeta.parseDate("19040101T000000.000Z")) // date vide des vieux fichiers
    }
}
