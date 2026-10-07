package fr.mesphotos.detect

import org.junit.Assert.assertEquals
import org.junit.Test

class NudityModelTest {

    private fun output(vararg hits: Triple<Int, Int, Float>): Array<Array<FloatArray>> {
        val rows = Array(22) { FloatArray(5) }
        for ((cls, anchor, value) in hits) rows[4 + cls][anchor] = value
        return arrayOf(rows)
    }

    @Test
    fun tensorIsRedThenGreenThenBlueScaledToOne() {
        val pixels = intArrayOf(0xFF102030.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF00FF00.toInt())
        val t = NudityModel.toTensor(pixels, size = 2)
        assertEquals(12, t.size)
        assertEquals(0x10 / 255f, t[0], 1e-6f)          // rouge du 1er point
        assertEquals(1f, t[1], 0f)                       // rouge du 2e point
        assertEquals(0x20 / 255f, t[4], 1e-6f)          // vert du 1er point
        assertEquals(1f, t[3 + 4], 0f)                   // vert du 4e point
        assertEquals(0x30 / 255f, t[8], 1e-6f)          // bleu du 1er point
        assertEquals(0f, t[11], 0f)                      // bleu du 4e point
    }

    @Test
    fun scoreIsTheBestExposedBodyPart() {
        // 3 = seins de femme exposés, 14 = parties génitales d'homme exposées
        assertEquals(0.8f, NudityModel.score(output(Triple(3, 2, 0.8f), Triple(14, 0, 0.4f))), 0f)
    }

    @Test
    fun coveredBodyPartsAndMaleChestAreIgnored() {
        // 0 = parties génitales (couvertes), 5 = torse d'homme, 13 = ventre, 12 = visage d'homme, 16 = seins couverts
        val out = output(Triple(0, 1, 0.99f), Triple(5, 1, 0.99f), Triple(13, 1, 0.99f), Triple(12, 1, 0.99f), Triple(16, 1, 0.99f))
        assertEquals(0f, NudityModel.score(out), 0f)
    }

    @Test
    fun everyExposedClassCounts() {
        for (cls in listOf(2, 3, 4, 6, 14)) {
            assertEquals(0.5f, NudityModel.score(output(Triple(cls, 3, 0.5f))), 0f)
        }
    }

    @Test
    fun emptyOutputGivesZero() {
        assertEquals(0f, NudityModel.score(emptyArray()), 0f)
    }
}
