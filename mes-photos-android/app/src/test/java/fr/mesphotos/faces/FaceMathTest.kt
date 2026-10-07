package fr.mesphotos.faces

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

class FaceMathTest {

    @Test
    fun alignmentBringsPointsBackToTheTemplate() {
        // Visage vu plus grand, penché de 30° et décalé : l'alignement doit le remettre au modèle.
        val angle = 30 * PI / 180
        val scale = 2.5
        val src = DoubleArray(10)
        for (i in 0 until 5) {
            val x = FaceMath.TEMPLATE[2 * i]
            val y = FaceMath.TEMPLATE[2 * i + 1]
            src[2 * i] = scale * (cos(angle) * x - sin(angle) * y) + 120
            src[2 * i + 1] = scale * (sin(angle) * x + cos(angle) * y) + 75
        }
        val m = FaceMath.alignment(src)!!
        for (i in 0 until 5) {
            val x = m[0] * src[2 * i] + m[1] * src[2 * i + 1] + m[2]
            val y = m[3] * src[2 * i] + m[4] * src[2 * i + 1] + m[5]
            assertEquals(FaceMath.TEMPLATE[2 * i], x, 1e-6)
            assertEquals(FaceMath.TEMPLATE[2 * i + 1], y, 1e-6)
        }
    }

    @Test
    fun alignmentOfIdenticalPointsIsNull() {
        assertNull(FaceMath.alignment(DoubleArray(10) { 5.0 }))
    }

    @Test
    fun decodeReadsBoxAndPoints() {
        val stride = 32
        val n = (FaceMath.INPUT / stride) * (FaceMath.INPUT / stride)
        val cls = FloatArray(n)
        val obj = FloatArray(n)
        val bbox = Array(n) { FloatArray(4) }
        val kps = Array(n) { FloatArray(10) }
        val i = 21 // ligne 1, colonne 1
        cls[i] = 0.9f
        obj[i] = 0.9f
        bbox[i] = floatArrayOf(0.5f, 0.5f, ln(2f), ln(2f))
        kps[i][0] = 1f
        kps[i][1] = 2f
        val found = FaceMath.decode(stride, cls, obj, bbox, kps, 0.5f)
        assertEquals(1, found.size)
        val d = found[0]
        assertEquals(0.9f, d.score, 1e-5f)
        assertEquals(16f, d.x, 1e-3f) // centre 48, largeur 64
        assertEquals(16f, d.y, 1e-3f)
        assertEquals(64f, d.w, 1e-3f)
        assertEquals(64f, d.points[0], 1e-3f) // (colonne 1 + 1) × 32
        assertEquals(96f, d.points[1], 1e-3f) // (ligne 1 + 2) × 32
    }

    @Test
    fun decodeIgnoresWeakScores() {
        val n = (FaceMath.INPUT / 8) * (FaceMath.INPUT / 8)
        val found = FaceMath.decode(8, FloatArray(n) { 0.1f }, FloatArray(n) { 0.1f }, Array(n) { FloatArray(4) }, Array(n) { FloatArray(10) }, 0.5f)
        assertEquals(0, found.size)
    }

    @Test
    fun nmsKeepsTheBestOfOverlappingBoxes() {
        val pts = FloatArray(10)
        val a = Detection(10f, 10f, 100f, 100f, 0.9f, pts)
        val b = Detection(15f, 12f, 100f, 100f, 0.8f, pts)
        val c = Detection(400f, 400f, 50f, 50f, 0.7f, pts)
        val kept = FaceMath.nms(listOf(b, c, a))
        assertEquals(2, kept.size)
        assertEquals(0.9f, kept[0].score, 1e-6f)
        assertEquals(0.7f, kept[1].score, 1e-6f)
    }

    @Test
    fun cosineOfNormalizedVectors() {
        val a = FaceMath.normalized(floatArrayOf(3f, 4f))
        assertEquals(1f, FaceMath.cosine(a, a), 1e-6f)
        assertEquals(0f, FaceMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(0.6f, a[0], 1e-6f)
    }

    @Test
    fun embeddingSurvivesTextRoundTrip() {
        val v = FloatArray(FaceMath.EMBEDDING) { it / 100f - 0.5f }
        assertArrayEquals(v, FaceMath.decodeEmbedding(FaceMath.encode(v))!!, 0f)
        assertNull(FaceMath.decodeEmbedding("pasUneEmpreinte"))
        assertNull(FaceMath.decodeEmbedding(FaceMath.encode(FloatArray(3))))
    }

    @Test
    fun detectorInputIsBlueGreenRedOnBlackSquare() {
        val pixels = intArrayOf(0xFF102030.toInt(), 0xFF405060.toInt())
        val out = FaceMath.toDetectorInput(pixels, 2, 1)
        val plane = FaceMath.INPUT * FaceMath.INPUT
        assertEquals(0x30.toFloat(), out[0], 0f) // bleu du 1er point
        assertEquals(0x20.toFloat(), out[plane], 0f) // vert
        assertEquals(0x10.toFloat(), out[2 * plane], 0f) // rouge
        assertEquals(0x60.toFloat(), out[1], 0f)
        assertEquals(0f, out[2], 0f) // fond noir
        assertNotNull(out)
    }
}
