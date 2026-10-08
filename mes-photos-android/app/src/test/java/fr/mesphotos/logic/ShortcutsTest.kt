package fr.mesphotos.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShortcutsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store get() = File(tmp.root, "raccourcis.tsv")

    private fun shortcuts() = Shortcuts(store).apply { load() }

    @Test
    fun aShortcutIsKeptBetweenLaunches() {
        shortcuts().put("Immatriculation", "Véhicule", "plaque immat")
        val s = shortcuts().all().single()
        assertEquals("Immatriculation", s.name)
        assertEquals("Véhicule", s.category)
        assertEquals("plaque immat", s.words)
    }

    @Test
    fun withoutWordsItSearchesItsName() {
        assertEquals("Immatriculation", shortcuts().put("Immatriculation", "Véhicule", "  ")!!.words)
    }

    @Test
    fun emptyNameMakesNoShortcut() {
        val list = shortcuts()
        assertNull(list.put("   ", "Véhicule", "x"))
        assertTrue(list.all().isEmpty())
    }

    @Test
    fun noCategoryGoesToDivers() {
        assertEquals(Shortcuts.OTHER, shortcuts().put("Truc", "", "")!!.category)
    }

    @Test
    fun sameNameInSameCategoryReplaces() {
        val list = shortcuts()
        list.put("Immatriculation", "Véhicule", "plaque")
        list.put("immatriculation", "véhicule", "immat")
        val s = list.all().single()
        assertEquals("immat", s.words)
        assertEquals("Véhicule", s.category)
    }

    @Test
    fun sameNameInAnotherCategoryIsAnotherShortcut() {
        val list = shortcuts()
        list.put("Carte grise", "Véhicule", "")
        list.put("Carte grise", "Papiers", "")
        assertEquals(2, list.all().size)
    }

    @Test
    fun categoriesIgnoreCaseAndAccentsAndAreSorted() {
        val list = shortcuts()
        list.put("Plaque", "Véhicule", "")
        list.put("Carte grise", "vehicule", "")
        list.put("Passeport", "Papiers", "")
        assertEquals(listOf("Papiers", "Véhicule"), list.categories())
        assertEquals(listOf("Passeport", "Carte grise", "Plaque"), list.all().map { it.name })
    }

    @Test
    fun removeDeletesIt() {
        val list = shortcuts()
        val made = list.put("Plaque", "Véhicule", "")!!
        list.remove(made)
        assertTrue(shortcuts().all().isEmpty())
    }

    @Test
    fun oldPhotoShortcutsAreKeptAndSearchTheirName() {
        // Format des V13 à V17 : nom, classement, chemin, nom du fichier, taille.
        store.writeText("Immatriculation\tVéhicule\t/storage/ABCD-1234/Photos rangées/a.jpg\ta.jpg\t123\n")
        val s = shortcuts().all().single()
        assertEquals("Immatriculation", s.name)
        assertEquals("Véhicule", s.category)
        assertEquals("Immatriculation", s.words)
        // Une fois sauvegardé de nouveau, il est au nouveau format.
        shortcuts().put("Autre", "Divers", "")
        assertEquals(2, shortcuts().all().size)
    }

    @Test
    fun matchingLooksAtNameAndCategoryWithoutAccents() {
        val list = shortcuts()
        list.put("Immatriculation", "Véhicule", "")
        list.put("Passeport", "Papiers", "")
        assertEquals(listOf("Immatriculation"), list.matching(listOf("immat")).map { it.name })
        assertEquals(listOf("Immatriculation"), list.matching(listOf("vehicule")).map { it.name })
        assertEquals(listOf("Immatriculation"), list.matching(listOf("vehicule", "immat")).map { it.name })
        assertTrue(list.matching(listOf("rien")).isEmpty())
        assertTrue(list.matching(emptyList()).isEmpty())
    }

    @Test
    fun tabsAndNewlinesCannotBreakTheFile() {
        val list = shortcuts()
        list.put("Plaque\tvoiture\nvieille", "Véhicule\t2", "mot\tun\nmot deux")
        val s = shortcuts().all().single()
        assertEquals("Plaque voiture vieille", s.name)
        assertEquals("Véhicule 2", s.category)
        assertEquals("mot un mot deux", s.words)
    }

    @Test
    fun firstWordIsTheNameAndSecondIsTheCategory() {
        val file = "Immatriculation, Véhicule - IMG_20261008_120509.jpg"
        assertEquals("Immatriculation", Shortcuts.suggestName(file))
        assertEquals("Véhicule", Shortcuts.suggestCategory(file))
        assertEquals("", Shortcuts.suggestCategory("Immatriculation - IMG_20261008_120509.jpg"))
        assertEquals("", Shortcuts.suggestCategory("IMG_20261008_120509.jpg"))
        assertEquals("Immatriculation", Shortcuts.suggestName("Immatriculation (2) - IMG_20261008_120509.jpg"))
        assertEquals("", Shortcuts.suggestName(" - IMG_1.jpg"))
    }
}
