package fr.mesphotos.organize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrashTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var output: File
    private lateinit var trash: Trash

    @Before
    fun setUp() {
        output = File(tmp.root, "Photos rangées").apply { mkdirs() }
        trash = Trash(output)
    }

    private fun photo(vararg path: String, text: String = "contenu"): File {
        val file = path.fold(output) { acc, name -> File(acc, name) }
        file.parentFile!!.mkdirs()
        file.writeText(text)
        return file
    }

    @Test
    fun trashMovesTheFileAndKeepsItsContent() {
        val a = photo("Journées", "2024", "05", "a.jpg", text = "AAA")
        val result = trash.moveToTrash(listOf(a))
        assertEquals(1, result.done)
        assertEquals(0, result.failed)
        assertFalse(a.exists())
        val entry = trash.entries().single()
        assertEquals("AAA", entry.trashed.readText())
        assertEquals(a.absolutePath, entry.original.absolutePath)
        assertTrue(entry.trashed.absolutePath.startsWith(trash.dir.absolutePath + "/"))
    }

    @Test
    fun restoreBringsTheFileBackToItsPlace() {
        val a = photo("Journées", "2024", "05", "a.jpg", text = "AAA")
        trash.moveToTrash(listOf(a))
        val restored = trash.restore(trash.entries())
        assertEquals(1, restored.done)
        assertEquals("AAA", a.readText())
        assertEquals(0, trash.count())
    }

    @Test
    fun restoreNeverOverwritesAnExistingFile() {
        val a = photo("2024", "a.jpg", text = "ancien")
        trash.moveToTrash(listOf(a))
        photo("2024", "a.jpg", text = "nouveau") // la place a été reprise entre-temps
        trash.restore(trash.entries())
        assertEquals("nouveau", File(output, "2024/a.jpg").readText())
        assertEquals("ancien", File(output, "2024/a (2).jpg").readText())
    }

    @Test
    fun twoFilesWithTheSameNameBothSurvive() {
        val a = photo("2024", "01", "x.jpg", text = "un")
        val b = photo("2025", "01", "x.jpg", text = "deux")
        trash.moveToTrash(listOf(a, b))
        assertEquals(setOf("un", "deux"), trash.entries().map { it.trashed.readText() }.toSet())
    }

    @Test
    fun refusesFilesOutsideThePhotosFolder() {
        val outside = File(tmp.root, "autre.jpg").apply { writeText("x") }
        val result = trash.moveToTrash(listOf(outside))
        assertEquals(0, result.done)
        assertEquals(1, result.failed)
        assertTrue(outside.exists())
    }

    @Test
    fun refusesFilesAlreadyInTheTrash() {
        val a = photo("2024", "a.jpg")
        trash.moveToTrash(listOf(a))
        val inTrash = trash.entries().single().trashed
        val result = trash.moveToTrash(listOf(inTrash))
        assertEquals(1, result.failed)
        assertTrue(inTrash.exists())
    }

    @Test
    fun missingFileIsReportedAndNothingElseIsHurt() {
        val a = photo("2024", "a.jpg")
        val ghost = File(output, "2024/fantome.jpg")
        val result = trash.moveToTrash(listOf(ghost, a))
        assertEquals(1, result.done)
        assertEquals(1, result.failed)
        assertTrue(result.failures.single().startsWith("fantome.jpg"))
    }

    @Test
    fun emptyFoldersLeftBehindAreRemovedButNotTheOnesStillUsed() {
        val a = photo("Journées", "2024", "05", "01 - Paris", "a.jpg")
        val b = photo("Journées", "2024", "06", "b.jpg")
        trash.moveToTrash(listOf(a))
        assertFalse(File(output, "Journées/2024/05").exists())
        assertTrue(File(output, "Journées/2024/06/b.jpg").exists())
        assertTrue(b.exists())
        assertTrue(output.isDirectory) // « Photos rangées » elle-même reste toujours
    }

    @Test
    fun aFolderKeepsOtherFilesThatAreNotPhotos() {
        val a = photo("2024", "a.jpg")
        photo("2024", "notes.txt")
        trash.moveToTrash(listOf(a))
        assertTrue(File(output, "2024/notes.txt").exists())
    }

    @Test
    fun deleteForeverOnlyRemovesTheGivenFiles() {
        val a = photo("2024", "a.jpg")
        val b = photo("2024", "b.jpg")
        trash.moveToTrash(listOf(a, b))
        val all = trash.entries()
        val victim = all.first { it.original.name == "a.jpg" }
        val result = trash.deleteForever(listOf(victim))
        assertEquals(1, result.done)
        assertFalse(victim.trashed.exists())
        val left = trash.entries()
        assertEquals(1, left.size)
        assertEquals("b.jpg", left.single().original.name)
    }

    @Test
    fun deleteForeverRefusesFilesThatAreNotInTheTrash() {
        val a = photo("2024", "a.jpg")
        val fake = TrashEntry(a, a, 0L)
        val result = trash.deleteForever(listOf(fake))
        assertEquals(1, result.failed)
        assertTrue(a.exists())
    }

    @Test
    fun entriesIgnoreFilesThatDisappearedAndTamperedPaths() {
        val a = photo("2024", "a.jpg")
        trash.moveToTrash(listOf(a))
        trash.entries().single().trashed.delete()
        assertEquals(0, trash.count())
        File(trash.dir, "index.tsv").appendText("T\t../../secret.jpg\t2024/x.jpg\t1\n")
        assertEquals(0, trash.count())
    }

    @Test
    fun trashFolderIsHiddenFromTheGallery() {
        val a = photo("2024", "a.jpg")
        trash.moveToTrash(listOf(a))
        assertTrue(trash.dir.name.startsWith("."))
        assertTrue(File(trash.dir, ".nomedia").exists())
    }
}
