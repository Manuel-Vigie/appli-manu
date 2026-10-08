package fr.mesphotos.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class FileHealthTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private val jpegHead = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1)
    private val jpegTail = ByteArray(32) { 0 }.also { it[10] = 0xFF.toByte(); it[11] = 0xD9.toByte() }
    private fun random(n: Int, seed: Long = 1) = ByteArray(n).also { Random(seed).nextBytes(it) }

    @Test
    fun healthyJpegIsOk() = assertEquals(Verdict.OK, FileHealth.classify("jpg", jpegHead, jpegTail, 1000).verdict)

    @Test
    fun jpegWithoutEndIsTruncated() =
        assertEquals(Verdict.TRUNCATED, FileHealth.classify("JPG", jpegHead, ByteArray(32) { 7 }, 1000).verdict)

    @Test
    fun emptyAndZeros() {
        assertEquals(Verdict.EMPTY, FileHealth.classify("jpg", ByteArray(0), ByteArray(0), 0).verdict)
        assertEquals(Verdict.ZEROS, FileHealth.classify("jpg", ByteArray(5000), ByteArray(32), 5000).verdict)
    }

    @Test
    fun randomBytesAreScrambled() =
        assertEquals(Verdict.SCRAMBLED, FileHealth.classify("jpg", random(65536), random(32, 2), 500000).verdict)

    @Test
    fun extraBytesBeforeJpegAreRepairable() {
        val head = random(100, 3) + jpegHead + random(5000, 4)
        val h = FileHealth.classify("jpg", head.copyOf(minOf(head.size, FileHealth.HEAD)), jpegTail, 9000)
        assertEquals(Verdict.REPAIRABLE, h.verdict)
        // Le hasard peut contenir lui-même un début plausible : on exige seulement que la coupe tombe sur le vrai début ou avant.
        assertTrue(h.offset in 1..100)
        assertFalse(h.addSoi)
    }

    @Test
    fun damagedFirstBytesAreRepairableByAddingSoi() {
        val body = bytes(0xFF, 0xDB, 0x00, 0x43, 0x00) + random(2000, 5)
        val head = random(16, 6) + body
        val h = FileHealth.classify("jpg", head, jpegTail, 5000)
        assertEquals(Verdict.REPAIRABLE, h.verdict)
        assertEquals(16, h.offset)
        assertTrue(h.addSoi)
    }

    @Test
    fun otherTypesAreNotJudged() =
        assertEquals(Verdict.OK, FileHealth.classify("dng", random(65536), random(32), 1000).verdict)

    @Test
    fun realMp4IsOk() {
        val head = bytes(0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6F, 0x6D, 0, 0, 2, 0)
        assertEquals(Verdict.OK, FileHealth.classify("mp4", head, ByteArray(32), 10000).verdict)
    }

    private fun ints(vararg v: Int) = bytes(*v)

    @Test
    fun jpegWithEndAndTrailingDataIsComplete() {
        // SOI, un segment APP0 de longueur 4, des données avec FF00, EOI, puis des données du constructeur.
        val data = ints(0xFF, 0xD8, 0xFF, 0xE0, 0, 4, 1, 2, 0xFF, 0xDA, 0, 2, 5, 6, 0xFF, 0, 7, 0xFF, 0xD9, 9, 9, 9)
        assertTrue(FileHealth.jpegReachesEnd(data.inputStream()))
    }

    @Test
    fun jpegCutBeforeEndIsTruncated() {
        val data = ints(0xFF, 0xD8, 0xFF, 0xE0, 0, 4, 1, 2, 0xFF, 0xDA, 0, 2, 5, 6, 0xFF, 0, 7, 8)
        assertFalse(FileHealth.jpegReachesEnd(data.inputStream()))
    }

    @Test
    fun endMarkerInsideEmbeddedThumbnailDoesNotCount() {
        // L'EOI du segment APP1 (petite image cachée) est dans un segment sauté : il ne doit pas compter.
        val data = ints(0xFF, 0xD8, 0xFF, 0xE1, 0, 6, 0xFF, 0xD9, 1, 2, 0xFF, 0xDA, 0, 2, 5, 6)
        assertFalse(FileHealth.jpegReachesEnd(data.inputStream()))
    }

    @Test
    fun jpegTraceIsFoundAnywhereAndNotInNoise() {
        val inside = ByteArray(300000) { (it * 7).toByte() } // sans le motif cherché
        val withTrace = inside.copyOf().also { val m = ints(0xFF, 0xDB, 0x00, 0x43, 0x00); m.copyInto(it, 270000) }
        assertEquals(270000L, FileHealth.firstJpegTrace(withTrace.inputStream()))
        assertEquals(null, FileHealth.firstJpegTrace(inside.inputStream()))
    }

    @Test
    fun blockRandomnessSeparatesNoiseFromText() {
        assertTrue(FileHealth.blockLooksRandom(random(8192), 0, 8192))
        assertFalse(FileHealth.blockLooksRandom(ByteArray(8192) { (it % 3).toByte() }, 0, 8192))
    }
}
