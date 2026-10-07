package fr.rangephotos.organize

import android.net.Uri
import fr.rangephotos.logic.PlannedMove
import fr.rangephotos.model.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.File

/** Vrais fichiers dans un vrai dossier temporaire : on vérifie que le rangement range, annule et signale les erreurs. */
class OrganizerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(dir: File, name: String, content: String = name): File {
        dir.mkdirs()
        return File(dir, name).also { it.writeText(content) }
    }

    private fun photo(f: File, currentFolder: List<String>? = null) = PhotoInfo(
        uri = mock(Uri::class.java), parentUri = null, name = f.name, mimeType = "image/jpeg",
        takenAt = 0L, lat = null, lon = null, isScreenshot = false, size = f.length(),
        currentFolder = currentFolder, path = f.absolutePath,
    )

    @Test
    fun movesPhotosIntoTheirFoldersAndUndoPutsThemBack() {
        val dcim = tmp.newFolder("DCIM")
        val a = file(dcim, "a.jpg")
        val b = file(File(dcim, "Camera"), "b.jpg")
        val out = File(tmp.root, "Photos rangées")
        val journal = File(tmp.root, "journal.tsv")

        val result = Organizer().execute(
            out,
            listOf(
                PlannedMove(photo(a), listOf("Photos", "2026", "03 - mars")),
                PlannedMove(photo(b), listOf("Portraits", "Solo", "2026")),
            ),
            journal,
        )

        assertEquals(2, result.moved)
        assertEquals(0, result.failed)
        assertTrue(File(out, "Photos/2026/03 - mars/a.jpg").isFile)
        assertTrue(File(out, "Portraits/Solo/2026/b.jpg").isFile)
        assertFalse(a.exists())
        assertFalse(b.exists())

        val undone = Organizer().undo(journal)
        assertEquals(2, undone.moved)
        assertTrue(a.isFile)
        assertTrue(b.isFile)
        assertFalse(File(out, "Photos/2026/03 - mars/a.jpg").exists())
        assertEquals("", journal.readText())
    }

    @Test
    fun missingFileIsReportedWithAReasonAndOthersContinue() {
        val dcim = tmp.newFolder("DCIM")
        val ghost = File(dcim, "disparue.jpg") // n'existe pas
        val real = file(dcim, "vraie.jpg")
        val out = File(tmp.root, "Photos rangées")

        val result = Organizer().execute(
            out,
            listOf(
                PlannedMove(photo(ghost), listOf("Photos", "2026", "01 - janvier")),
                PlannedMove(photo(real), listOf("Photos", "2026", "01 - janvier")),
            ),
            File(tmp.root, "journal.tsv"),
        )

        assertEquals(1, result.moved)
        assertEquals(1, result.failed)
        assertEquals(1, result.failures.size)
        assertTrue(result.failures[0], result.failures[0].contains("disparue.jpg") && result.failures[0].contains("introuvable"))
    }

    @Test
    fun sameNameInTheSameFolderGetsANumber() {
        val one = file(tmp.newFolder("A"), "IMG.jpg", "un")
        val two = file(tmp.newFolder("B"), "IMG.jpg", "deux")
        val out = File(tmp.root, "Photos rangées")

        Organizer().execute(
            out,
            listOf(
                PlannedMove(photo(one), listOf("Photos", "2026", "01 - janvier")),
                PlannedMove(photo(two), listOf("Photos", "2026", "01 - janvier")),
            ),
            File(tmp.root, "journal.tsv"),
        )

        val dir = File(out, "Photos/2026/01 - janvier")
        assertEquals("un", File(dir, "IMG.jpg").readText())
        assertEquals("deux", File(dir, "IMG (2).jpg").readText())
    }

    @Test
    fun copiesAreMadeOnceAndUndoRemovesOnlyCopies() {
        val src = file(tmp.newFolder("DCIM"), "groupe.jpg", "contenu")
        val out = File(tmp.root, "Photos rangées")
        val journal = File(tmp.root, "journal.tsv")
        val move = PlannedMove(photo(src), listOf("Portraits", "Groupe", "2026"), listOf(listOf("Portraits", "Julie", "2026")))

        val first = Organizer().execute(out, listOf(move), journal)
        assertEquals(1, first.copied)
        assertTrue(File(out, "Portraits/Julie/2026/groupe.jpg").isFile)

        // Reclassement : la photo est déjà au bon endroit, la copie existe déjà : rien ne se refait.
        val placed = File(out, "Portraits/Groupe/2026/groupe.jpg")
        val again = Organizer().execute(
            out,
            listOf(PlannedMove(photo(placed, listOf("Portraits", "Groupe", "2026")), move.folder, move.copies)),
            File(tmp.root, "journal2.tsv"),
        )
        assertEquals(0, again.moved)
        assertEquals(0, again.copied)
        assertEquals(1, again.copiesAlreadyThere)
        assertFalse(File(out, "Portraits/Julie/2026/groupe (2).jpg").exists())

        val undone = Organizer().undo(journal)
        assertEquals(1, undone.moved)
        assertEquals(1, undone.copied)
        assertTrue(src.isFile)
        assertFalse(File(out, "Portraits/Julie/2026/groupe.jpg").exists())
    }

    @Test
    fun photoAlreadyInTheRightFolderIsLeftAlone() {
        val out = File(tmp.root, "Photos rangées")
        val placed = file(File(out, "Photos/2026/03 - mars"), "x.jpg")
        val result = Organizer().execute(
            out,
            listOf(PlannedMove(photo(placed, listOf("Photos", "2026", "03 - mars")), listOf("Photos", "2026", "03 - mars"))),
            File(tmp.root, "journal.tsv"),
        )
        assertEquals(0, result.moved)
        assertEquals(0, result.failed)
        assertTrue(placed.isFile)
    }

    @Test
    fun mediaScannerIsToldAboutChangedFiles() {
        val a = file(tmp.newFolder("DCIM"), "a.jpg")
        val seen = ArrayList<String>()
        Organizer { seen += it }.execute(
            File(tmp.root, "Photos rangées"),
            listOf(PlannedMove(photo(a), listOf("Photos", "2026", "03 - mars"))),
            File(tmp.root, "journal.tsv"),
        )
        assertEquals(2, seen.size) // l'ancien et le nouvel emplacement
    }

    // ---- Nettoyage des anciens dossiers ----

    private fun cleanupFor(root: File, out: File) = FolderCleanup(root, setOf(root, out))

    @Test
    fun removesOldFoldersOnceEmptyButNeverOneWithAFileLeft() {
        val root = tmp.newFolder("volume")
        val old = File(root, "Vieux/Sous")
        val a = file(old, "a.jpg")
        val keep = File(root, "Autre")
        val b = file(keep, "b.jpg")
        file(keep, "notes.txt") // un autre fichier : le dossier doit rester
        val out = File(root, "Photos rangées")

        val result = Organizer().execute(
            out,
            listOf(PlannedMove(photo(a), listOf("Journées", "2026")), PlannedMove(photo(b), listOf("Journées", "2026"))),
            File(tmp.root, "journal.tsv"),
            cleanupFor(root, out),
        )

        assertEquals(2, result.moved)
        assertFalse(File(root, "Vieux/Sous").exists()) // vide : supprimé
        assertFalse(File(root, "Vieux").exists())      // son parent devenu vide aussi
        assertTrue(keep.isDirectory)                   // il reste notes.txt
        assertTrue(File(keep, "notes.txt").isFile)
        assertEquals(2, result.foldersRemoved)
        assertEquals(listOf("Autre"), result.foldersKept)
    }

    @Test
    fun keepsAFolderWhenAnotherPhotoIsStillInside() {
        val root = tmp.newFolder("volume")
        val dir = File(root, "Album")
        val a = file(dir, "a.jpg")
        val other = file(dir, "restee.jpg") // pas dans le rangement : doit rester
        val out = File(root, "Photos rangées")
        val result = Organizer().execute(
            out,
            listOf(PlannedMove(photo(a), listOf("X"))),
            File(tmp.root, "journal.tsv"),
            cleanupFor(root, out),
        )
        assertEquals(1, result.moved)
        assertEquals(0, result.foldersRemoved)
        assertEquals(listOf("Album"), result.foldersKept)
        assertTrue(other.isFile)
    }

    @Test
    fun neverRemovesProtectedAndSystemFolders() {
        val root = tmp.newFolder("volume")
        val camera = File(root, "DCIM/Camera")
        val a = file(camera, "a.jpg")
        val out = File(root, "Photos rangées")
        val result = Organizer().execute(
            out,
            listOf(PlannedMove(photo(a), listOf("Y"))),
            File(tmp.root, "journal.tsv"),
            cleanupFor(root, out),
        )
        assertEquals(1, result.moved)
        assertFalse(camera.exists())                 // « Camera » vide : supprimé
        assertTrue(File(root, "DCIM").isDirectory)    // DCIM est un dossier système : gardé
        assertTrue(root.isDirectory)
        assertTrue(out.isDirectory)
    }

    @Test
    fun doesNotRemoveFoldersWhenCleanupIsOff() {
        val root = tmp.newFolder("volume")
        val old = File(root, "Vieux")
        val a = file(old, "a.jpg")
        val out = File(root, "Photos rangées")
        Organizer().execute(out, listOf(PlannedMove(photo(a), listOf("Z"))), File(tmp.root, "journal.tsv"))
        assertTrue(old.isDirectory)
    }

    @Test
    fun undoRecreatesRemovedFolders() {
        val root = tmp.newFolder("volume")
        val a = file(File(root, "Vieux/Sous"), "a.jpg")
        val out = File(root, "Photos rangées")
        val journal = File(tmp.root, "journal.tsv")
        Organizer().execute(out, listOf(PlannedMove(photo(a), listOf("Z"))), journal, cleanupFor(root, out))
        assertFalse(a.parentFile!!.exists())

        val undone = Organizer().undo(journal)
        assertEquals(1, undone.moved)
        assertTrue(a.isFile)
    }
}
