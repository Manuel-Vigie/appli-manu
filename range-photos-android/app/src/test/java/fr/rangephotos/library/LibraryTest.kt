package fr.rangephotos.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun media(dir: File, name: String) {
        dir.mkdirs()
        File(dir, name).writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun albumsAreGroupedOnShelvesByTopFolder() {
        val root = tmp.newFolder("Photos rangées")
        media(File(root, "Randonnées/Rando 2026-08-15"), "a.jpg")
        media(File(root, "Randonnées/Rando 2026-09-02"), "b.jpg")
        media(File(root, "Randonnées/Rando 2026-09-02"), "c.mp4")
        media(File(root, "Portraits/Rémy"), "d.jpg")
        File(root, "Portraits/Vide").mkdirs()

        val shelves = Library.scan(root)

        assertEquals(listOf("Randonnées", "Portraits"), shelves.map { it.title })
        // Les randonnées les plus récentes d'abord.
        assertEquals(listOf("Rando 2026-09-02", "Rando 2026-08-15"), shelves[0].albums.map { it.title })
        assertEquals(2, shelves[0].albums[0].count)
        assertEquals(1, shelves[0].albums[0].videos)
        assertEquals(1, shelves[0].albums[0].photos)
        assertEquals(listOf("Rémy"), shelves[1].albums.map { it.title })
    }

    @Test
    fun hiddenFilesAndEmptyFilesAreIgnored() {
        val root = tmp.newFolder("Rangées")
        media(File(root, "Journées"), "ok.jpg")
        File(root, "Journées/.cache.jpg").writeBytes(byteArrayOf(1))
        File(root, "Journées/vide.jpg").writeBytes(byteArrayOf())
        File(root, "Journées/notes.txt").writeText("x")

        val shelves = Library.scan(root)

        assertEquals(1, shelves.size)
        assertEquals(1, shelves[0].albums[0].count)
    }

    @Test
    fun bigShelfIsSplitByParentFolder() {
        val root = tmp.newFolder("Gros")
        for (day in 1..30) media(File(root, "Journées/2026/03 - mars/" + day.toString().padStart(2, '0') + " mars"), "p.jpg")
        media(File(root, "Journées/2026/04 - avril/01 avril"), "p.jpg")

        val shelves = Library.scan(root)

        assertEquals(listOf("Journées › 2026 › 04 - avril", "Journées › 2026 › 03 - mars"), shelves.map { it.title })
        assertEquals(30, shelves[1].albums.size)
        assertEquals("30 mars", shelves[1].albums[0].title)
    }

    @Test
    fun naturalOrderReadsNumbersByValue() {
        assertTrue(Library.naturalCompare("Rando 2", "Rando 10") < 0)
        assertTrue(Library.naturalCompare("IMG_0100", "IMG_0020") > 0)
        assertEquals(0, Library.naturalCompare("Nice", "nice"))
    }
}
