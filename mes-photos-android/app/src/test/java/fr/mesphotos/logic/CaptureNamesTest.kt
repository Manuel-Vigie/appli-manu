package fr.mesphotos.logic

import fr.mesphotos.scan.DateChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.time.ZoneId

class CaptureNamesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val moment = Instant.parse("2026-10-08T12:05:09Z").toEpochMilli()

    @Test
    fun nameCarriesTheDate() {
        assertEquals("IMG_20261008_120509.jpg", CaptureNames.fileName(moment, ZoneId.of("UTC")))
    }

    @Test
    fun nameUsesTheLocalTime() {
        assertEquals("IMG_20261008_140509.jpg", CaptureNames.fileName(moment, ZoneId.of("Europe/Paris")))
    }

    @Test
    fun theSorterCanReadTheDateInTheName() {
        assertNotNull(DateChoice.fromFileName(CaptureNames.fileName(moment, ZoneId.of("UTC"))))
    }

    @Test
    fun twoPhotosInTheSameSecondKeepBothNames() {
        val first = File(tmp.root, CaptureNames.fileName(moment, ZoneId.of("UTC"))).apply { writeText("a") }
        val second = RenameNames.unique(tmp.root, CaptureNames.fileName(moment, ZoneId.of("UTC")))
        assertEquals("IMG_20261008_120509 (2).jpg", second.name)
        assertEquals(true, first.isFile)
    }
}
