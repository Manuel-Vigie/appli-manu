package fr.mesphotos.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ShortcutsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun photo(name: String, text: String = "contenu de $name"): File = tmp.newFile(name).apply { writeText(text) }

    private fun shortcuts() = Shortcuts(File(tmp.root, "raccourcis.tsv")).apply { load() }

    @Test
    fun aShortcutIsKeptBetweenLaunches() {
        val plate = photo("plaque.jpg")
        shortcuts().put(plate, "Immatriculation", "Véhicule")
        val again = shortcuts()
        val s = again.forFile(plate)
        assertNotNull(s)
        assertEquals("Immatriculation", s!!.name)
        assertEquals("Véhicule", s.category)
    }

    @Test
    fun emptyNameMakesNoShortcut() {
        val plate = photo("plaque.jpg")
        val list = shortcuts()
        assertNull(list.put(plate, "   ", "Véhicule"))
        assertTrue(list.all().isEmpty())
    }

    @Test
    fun noCategoryGoesToDivers() {
        val list = shortcuts()
        assertEquals(Shortcuts.OTHER, list.put(photo("a.jpg"), "Truc", "")!!.category)
    }

    @Test
    fun onePhotoHasOneShortcutAndOneNameLeadsToOnePhoto() {
        val list = shortcuts()
        val a = photo("a.jpg")
        val b = photo("b.jpg")
        list.put(a, "Plaque", "Véhicule")
        list.put(a, "Immatriculation", "Véhicule")
        assertEquals(listOf("Immatriculation"), list.all().map { it.name })
        list.put(b, "immatriculation", "véhicule")
        assertEquals(1, list.all().size)
        assertEquals(b.absolutePath, list.all().single().path)
    }

    @Test
    fun categoriesIgnoreCaseAndAccentsAndAreSorted() {
        val list = shortcuts()
        list.put(photo("a.jpg"), "Plaque", "Véhicule")
        list.put(photo("b.jpg"), "Carte grise", "vehicule")
        list.put(photo("c.jpg"), "Passeport", "Papiers")
        assertEquals(listOf("Papiers", "Véhicule"), list.categories())
        assertEquals(listOf("Passeport", "Carte grise", "Plaque"), list.all().map { it.name })
    }

    @Test
    fun removeDeletesIt() {
        val list = shortcuts()
        val a = photo("a.jpg")
        list.put(a, "Plaque", "Véhicule")
        list.remove(list.forFile(a)!!)
        assertTrue(shortcuts().all().isEmpty())
    }

    @Test
    fun renamingThePhotoMovesTheShortcut() {
        val list = shortcuts()
        val old = photo("a.jpg")
        list.put(old, "Plaque", "Véhicule")
        val new = File(tmp.root, "plaque voiture.jpg")
        assertTrue(old.renameTo(new))
        list.renamed(old, new)
        val s = shortcuts().all().single()
        assertEquals(new.absolutePath, s.path)
        assertEquals("plaque voiture.jpg", s.fileName)
    }

    @Test
    fun resolveFindsThePhotoWhereItWas() {
        val list = shortcuts()
        val a = photo("a.jpg")
        list.put(a, "Plaque", "Véhicule")
        assertEquals(a, list.resolve(list.forFile(a)!!) { emptyList() })
    }

    @Test
    fun resolveFindsAMovedPhotoByNameAndSize() {
        val list = shortcuts()
        val a = photo("a.jpg", "abcdef")
        list.put(a, "Plaque", "Véhicule")
        val sub = tmp.newFolder("2024", "03")
        val moved = File(sub, "a.jpg")
        assertTrue(a.renameTo(moved))
        val decoyOtherSize = File(tmp.newFolder("autre"), "a.jpg").apply { writeText("pas la même taille du tout") }
        val found = list.resolve(list.all().single()) { listOf(decoyOtherSize, moved) }
        assertEquals(moved, found)
        // Le raccourci retient la nouvelle place.
        assertEquals(moved.absolutePath, shortcuts().all().single().path)
    }

    @Test
    fun resolveGivesNullWhenThePhotoIsGone() {
        val list = shortcuts()
        val a = photo("a.jpg")
        list.put(a, "Plaque", "Véhicule")
        assertTrue(a.delete())
        assertNull(list.resolve(list.all().single()) { emptyList() })
    }

    @Test
    fun matchingLooksAtNameAndCategoryWithoutAccents() {
        val list = shortcuts()
        list.put(photo("a.jpg"), "Immatriculation", "Véhicule")
        list.put(photo("b.jpg"), "Passeport", "Papiers")
        assertEquals(listOf("Immatriculation"), list.matching(listOf("immat")).map { it.name })
        assertEquals(listOf("Immatriculation"), list.matching(listOf("vehicule")).map { it.name })
        assertEquals(listOf("Immatriculation"), list.matching(listOf("vehicule", "immat")).map { it.name })
        assertTrue(list.matching(listOf("rien")).isEmpty())
        assertTrue(list.matching(emptyList()).isEmpty())
    }

    @Test
    fun tabsAndNewlinesInNamesCannotBreakTheFile() {
        val list = shortcuts()
        list.put(photo("a.jpg"), "Plaque\tvoiture\nvieille", "Véhicule\t2")
        val s = shortcuts().all().single()
        assertEquals("Plaque voiture vieille", s.name)
        assertEquals("Véhicule 2", s.category)
    }
}
