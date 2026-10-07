package fr.mesphotos.organize

import fr.mesphotos.logic.PlannedMove
import fr.mesphotos.model.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VerifierTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, content: String = name): File {
        dir.mkdirs()
        return File(dir, name).also { it.writeText(content) }
    }

    private fun photo(f: File) = PhotoInfo(
        name = f.name, takenAt = 0L, size = f.length(), path = f.absolutePath,
    )

    @Test
    fun aCleanRunIsAllGood() {
        val root = tmp.newFolder("volume")
        val a = file(File(root, "Vieux"), "a.jpg")
        val b = file(File(root, "Vieux"), "b.mp4", "video")
        val moves = listOf(PlannedMove(photo(a), listOf("J", "1")), PlannedMove(photo(b), listOf("J", "1")))
        val journal = File(tmp.root, "j.tsv")
        Organizer().execute(File(root, "Rangées"), moves, journal)

        val report = Verifier.report(Verifier.checkMoves(moves, journal), emptyList(), 2, 2, Verifier.copiesInJournal(journal))
        assertEquals(2, report.ok)
        assertTrue(report.allGood)
    }

    @Test
    fun detectsAMissingFileAndALostCount() {
        val root = tmp.newFolder("volume")
        val a = file(File(root, "Vieux"), "a.jpg")
        val moves = listOf(PlannedMove(photo(a), listOf("J")))
        val journal = File(tmp.root, "j.tsv")
        Organizer().execute(File(root, "Rangées"), moves, journal)
        File(root, "Rangées/J/a.jpg").delete() // une photo disparaît

        val report = Verifier.report(Verifier.checkMoves(moves, journal), emptyList(), 1, 0, 0)
        assertFalse(report.allGood)
        assertEquals(1, report.problemCount)
        assertFalse(report.countsMatch)
    }

    @Test
    fun aLeftBehindPhotoIsReported() {
        val report = Verifier.report(Triple(1, 1, emptyList()), listOf("oubliee.jpg"), 2, 2, 0)
        assertFalse(report.allGood)
        assertEquals(1, report.leftBehind)
    }

    @Test
    fun batchesAppendToTheSameJournalAndUndoRestoresEverything() {
        val root = tmp.newFolder("volume")
        val a = file(File(root, "Vieux"), "a.jpg")
        val b = file(File(root, "Vieux"), "b.jpg")
        val out = File(root, "Rangées")
        val journal = File(tmp.root, "j.tsv")
        Organizer().execute(out, listOf(PlannedMove(photo(a), listOf("J"))), journal)
        Organizer().execute(out, listOf(PlannedMove(photo(b), listOf("J"))), journal, null, append = true)
        assertEquals(2, journal.readLines().count { it.startsWith("M\t") })

        assertEquals(2, Organizer().undo(journal).moved)
        assertTrue(a.isFile)
        assertTrue(b.isFile)
    }

    @Test
    fun dryRunListsEmptyOldFoldersWithoutDeletingAnything() {
        val root = tmp.newFolder("volume")
        val a = file(File(root, "Vieux/Sous"), "a.jpg")
        val keep = File(root, "Garde")
        val b = file(keep, "b.jpg")
        file(keep, "reste.jpg")
        val out = File(root, "Rangées")
        val journal = File(tmp.root, "j.tsv")
        val organizer = Organizer()
        organizer.execute(out, listOf(PlannedMove(photo(a), listOf("J")), PlannedMove(photo(b), listOf("J"))), journal)

        val cleanup = FolderCleanup(root, setOf(root, out))
        val starts = organizer.oldFoldersFromJournal(journal)
        val list = organizer.foldersThatWouldBeRemoved(starts, cleanup)

        assertEquals(listOf(File(root, "Vieux/Sous"), File(root, "Vieux")), list)
        assertTrue(File(root, "Vieux/Sous").isDirectory) // rien n'a été supprimé
        assertTrue(File(keep, "reste.jpg").isFile)

        val (removed, kept) = organizer.removeEmptyFolders(starts, cleanup)
        assertEquals(2, removed)
        assertEquals(listOf("Garde"), kept)
        assertTrue(keep.isDirectory)
    }
}
