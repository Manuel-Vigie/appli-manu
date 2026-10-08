package fr.mesphotos.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TagsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun tags() = Tags(File(tmp.root, "etiquettes.txt")).apply { load() }

    @Test
    fun startsWithTheDefaultWords() {
        assertEquals(Tags.DEFAULTS, tags().all())
    }

    @Test
    fun anAddedWordIsKeptBetweenLaunches() {
        assertEquals("Chalet", tags().add("  Chalet "))
        val again = tags().all()
        assertEquals(Tags.DEFAULTS + "Chalet", again)
    }

    @Test
    fun aWordAlreadyThereIsNotAddedTwiceEvenWithOtherCaseOrAccents() {
        val list = tags()
        assertEquals("Véhicule", list.add("vehicule"))
        assertEquals(Tags.DEFAULTS.size, list.all().size)
    }

    @Test
    fun emptyWordIsRefused() {
        assertNull(tags().add("   "))
        assertNull(tags().add(",,"))
    }

    @Test
    fun commasAndNewlinesCannotBreakTheName() {
        assertEquals("Vue sur le lac", tags().add("Vue, sur\nle lac"))
    }

    @Test
    fun removeKeepsTheOthers() {
        val list = tags()
        list.remove("montagne")
        val again = tags().all()
        assertEquals(Tags.DEFAULTS - "Montagne", again)
    }

    @Test
    fun joinPutsTheWordsTogetherWithoutDuplicates() {
        assertEquals("Immatriculation, Véhicule", Tags.join(listOf("Immatriculation", "Véhicule", "vehicule", "  ")))
        assertEquals("", Tags.join(emptyList()))
    }
}
