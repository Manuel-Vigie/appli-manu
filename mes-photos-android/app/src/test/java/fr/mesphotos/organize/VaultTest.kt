package fr.mesphotos.organize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun vault() = Vault(File(tmp.root, "coffre"))

    private fun photo(folder: String, name: String, text: String): File {
        val dir = File(tmp.root, folder).apply { mkdirs() }
        return File(dir, name).apply { writeText(text) }
    }

    @Test
    fun addMovesThePhotoAndKeepsItsContent() {
        val a = photo("sd/Vacances", "a.jpg", "contenu A")
        val result = vault().add(listOf(a))
        assertEquals(1, result.done)
        assertEquals(0, result.failed)
        assertFalse(a.exists())
        val entry = vault().entries().single()
        assertEquals("contenu A", entry.file.readText())
        assertEquals(a.absolutePath, entry.originalPath)
        assertEquals(listOf(a.absolutePath), result.removedPaths)
    }

    @Test
    fun missingFileFailsAndChangesNothing() {
        val result = vault().add(listOf(File(tmp.root, "absent.jpg")))
        assertEquals(0, result.done)
        assertEquals(1, result.failed)
        assertTrue(vault().entries().isEmpty())
    }

    @Test
    fun restorePutsThePhotoBackWhereItWas() {
        val a = photo("sd/Vacances", "a.jpg", "contenu A")
        vault().add(listOf(a))
        val result = vault().restore(vault().entries(), File(tmp.root, "secours"))
        assertEquals(1, result.done)
        assertEquals("contenu A", a.readText())
        assertTrue(vault().entries().isEmpty())
        assertEquals(listOf(a.absolutePath), result.arrivedPaths)
    }

    @Test
    fun restoreUsesFallbackWhenFolderIsGone() {
        val a = photo("sd/Vacances", "a.jpg", "contenu A")
        vault().add(listOf(a))
        a.parentFile!!.deleteRecursively()
        val secours = File(tmp.root, "secours")
        val result = vault().restore(vault().entries(), secours)
        assertEquals(1, result.done)
        assertEquals("contenu A", File(secours, "a.jpg").readText())
    }

    @Test
    fun restoreNeverOverwritesAnExistingFile() {
        val a = photo("sd/Vacances", "a.jpg", "contenu A")
        vault().add(listOf(a))
        photo("sd/Vacances", "a.jpg", "autre photo")
        vault().restore(vault().entries(), File(tmp.root, "secours"))
        assertEquals("autre photo", a.readText())
        assertEquals(2, a.parentFile!!.listFiles()!!.size)
    }

    @Test
    fun deleteForeverRemovesEverything() {
        vault().add(listOf(photo("sd", "a.jpg", "A"), photo("sd", "b.jpg", "B")))
        assertEquals(2, vault().count())
        val result = vault().deleteForever(vault().entries().take(1))
        assertEquals(1, result.done)
        assertEquals(1, vault().count())
    }

    @Test
    fun twoPhotosWithTheSameNameCanBothBeKept() {
        vault().add(listOf(photo("sd/1", "a.jpg", "un"), photo("sd/2", "a.jpg", "deux")))
        assertEquals(setOf("un", "deux"), vault().entries().map { it.file.readText() }.toSet())
    }
}
