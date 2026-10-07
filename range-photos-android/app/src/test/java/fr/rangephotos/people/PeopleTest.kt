package fr.rangephotos.people

import fr.rangephotos.face.FaceAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

class PeopleTest {

    private val random = Random(42)

    private fun randomUnit(dim: Int = 128): FloatArray =
        FaceMatching.normalize(FloatArray(dim) { random.nextGaussian().toFloat() })

    /** Un visage « de la même personne » : le centre, légèrement bruité. */
    private fun near(center: FloatArray, noise: Float = 0.03f): FloatArray =
        FaceMatching.normalize(FloatArray(center.size) { center[it] + noise * random.nextGaussian().toFloat() })

    // ---- Regroupement et reconnaissance --------------------------------------------------------

    @Test
    fun clusteringSeparatesTwoPeopleAndLeavesStrangersAlone() {
        val julie = randomUnit()
        val maman = randomUnit()
        val faces = List(10) { near(julie) } + List(8) { near(maman) } + List(3) { randomUnit() }

        val groups = FaceMatching.cluster(faces)

        assertEquals(10, groups[0].size)
        assertEquals(8, groups[1].size)
        assertTrue("les 10 premiers sont la même personne", groups[0].all { it < 10 })
        assertTrue("les 8 suivants sont la même personne", groups[1].all { it in 10..17 })
        assertTrue("les inconnus restent seuls", groups.drop(2).all { it.size == 1 })
    }

    @Test
    fun identifyFindsTheRightPersonOrNobody() {
        val julie = randomUnit()
        val maman = randomUnit()
        val known = listOf(
            FaceMatching.Known("Julie", listOf(near(julie), near(julie))),
            FaceMatching.Known("Maman", listOf(near(maman))),
        )
        assertEquals("Julie", FaceMatching.identify(near(julie), known))
        assertEquals("Maman", FaceMatching.identify(near(maman), known))
        assertNull(FaceMatching.identify(randomUnit(), known))
        assertNull(FaceMatching.identify(near(julie), emptyList()))
    }

    @Test
    fun identifyRefusesWhenTwoPeopleAreTooCloseToTell() {
        val base = randomUnit()
        // Trois visages presque identiques : le meilleur et le deuxième sont à égalité, on ne tranche pas.
        val twinA = FaceMatching.Known("A", listOf(near(base, 0.002f)))
        val twinB = FaceMatching.Known("B", listOf(near(base, 0.002f)))
        assertNull(FaceMatching.identify(near(base, 0.002f), listOf(twinA, twinB)))
    }

    @Test
    fun selectExemplarsKeepsAtMostTheLimitAndTheFirst() {
        val all = List(30) { randomUnit() }
        val chosen = FaceMatching.selectExemplars(all, 12)
        assertEquals(12, chosen.size)
        assertTrue(chosen[0] === all[0])
        assertEquals(5, FaceMatching.selectExemplars(all.take(5), 12).size)
    }

    // ---- Alignement du visage ------------------------------------------------------------------

    @Test
    fun alignmentUndoesRotationScaleAndShift() {
        // On déforme le gabarit (zoom ×2, rotation 30°, déplacement) : l'alignement doit le remettre en place.
        val angle = Math.toRadians(30.0)
        val a = 2.0 * cos(angle)
        val b = 2.0 * sin(angle)
        val t = FaceAlign.TEMPLATE
        val moved = FloatArray(t.size)
        for (i in 0 until t.size / 2) {
            val x = t[2 * i].toDouble()
            val y = t[2 * i + 1].toDouble()
            moved[2 * i] = (a * x - b * y + 300.0).toFloat()
            moved[2 * i + 1] = (b * x + a * y + 120.0).toFloat()
        }
        val sim = FaceAlign.estimate(moved)
        assertNotNull(sim)
        for (i in 0 until t.size / 2) {
            assertEquals(t[2 * i].toDouble(), sim!!.mapX(moved[2 * i].toDouble(), moved[2 * i + 1].toDouble()), 0.01)
            assertEquals(t[2 * i + 1].toDouble(), sim.mapY(moved[2 * i].toDouble(), moved[2 * i + 1].toDouble()), 0.01)
        }
    }

    @Test
    fun alignmentRejectsDegeneratePoints() {
        assertNull(FaceAlign.estimate(floatArrayOf(5f, 5f, 5f, 5f, 5f, 5f, 5f, 5f)))
    }

    @Test
    fun landmarksAreOrderedByPositionNotByName() {
        val rightEye = floatArrayOf(200f, 100f)
        val leftEye = floatArrayOf(100f, 100f)
        val mouthRight = floatArrayOf(190f, 200f)
        val mouthLeft = floatArrayOf(110f, 200f)
        val ordered = FaceAlign.orderLandmarks(rightEye, leftEye, mouthRight, mouthLeft)
        assertEquals(listOf(100f, 100f, 200f, 100f, 110f, 200f, 190f, 200f), ordered.toList())
    }

    // ---- Mémoire des prénoms -------------------------------------------------------------------

    @Test
    fun storeRoundTripKeepsNamesAndFaces() {
        val julie = List(3) { randomUnit() }
        val store = PeopleStore()
        store.add("Julie", julie)
        store.add("Maman", listOf(randomUnit()))

        val copy = PeopleStore()
        copy.mergeJson(store.toJson())

        assertEquals(listOf("Julie", "Maman"), copy.people.map { it.name })
        val restored = copy.people[0].exemplars
        assertEquals(3, restored.size)
        for (i in 0 until 3) assertEquals(0f, 1f - FaceMatching.dot(julie[i], restored[i]), 1e-5f)
    }

    @Test
    fun sameNameWithDifferentCaseIsTheSamePerson() {
        val store = PeopleStore()
        store.add("Julie", listOf(randomUnit()))
        store.add("  julie ", listOf(randomUnit()))
        assertEquals(1, store.people.size)
        assertEquals("Julie", store.people[0].name)
        assertEquals(2, store.people[0].exemplars.size)

        store.remove("JULIE")
        assertTrue(store.people.isEmpty())
    }

    @Test
    fun brokenBackupIsIgnored() {
        val store = PeopleStore()
        store.add("Julie", listOf(randomUnit()))
        store.mergeJson("pas du json {{{")
        assertEquals(1, store.people.size)
    }
}
