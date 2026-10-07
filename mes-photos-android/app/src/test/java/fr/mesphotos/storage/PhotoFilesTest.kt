package fr.mesphotos.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PhotoFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, content: String = "x"): File {
        val f = File(tmp.root, path)
        f.parentFile!!.mkdirs()
        f.writeText(content)
        return f
    }

    private fun place() = Place("1234-ABCD", "Carte SD", tmp.root)

    @Test
    fun findsPhotosAndIgnoresHiddenAndAndroidAndOtherFiles() {
        file("DCIM/Camera/a.JPG")
        file("Pictures/b.png")
        file("DCIM/.thumbnails/t.jpg")
        file("Android/data/x/c.jpg")
        file("Musique/morceau.mp3")
        file("DCIM/Camera/vide.jpg", "")
        val names = PhotoFiles.list(place(), includeSorted = false).map { it.file.name }.sorted()
        assertEquals(listOf("a.JPG", "b.png"), names)
    }

    @Test
    fun sortedPhotosAreOnlyListedForAFullReclassify() {
        file("DCIM/new.jpg")
        file("Photos rangées/Photos/2026/03 - mars/old.jpg")

        val normal = PhotoFiles.list(place(), includeSorted = false)
        assertEquals(listOf("new.jpg"), normal.map { it.file.name })

        val all = PhotoFiles.list(place(), includeSorted = true)
        assertEquals(2, all.size)
        val old = all.first { it.file.name == "old.jpg" }
        assertEquals(listOf("Photos", "2026", "03 - mars"), old.relative)
        assertNull(all.first { it.file.name == "new.jpg" }.relative)
    }

    @Test
    fun countsSplitNewAndAlreadySorted() {
        file("DCIM/a.jpg")
        file("DCIM/b.jpg")
        file("Photos rangées/Randonnées/X/c.jpg")
        val (toSort, sorted) = PhotoFiles.counts(place())
        assertEquals(2, toSort)
        assertEquals(1, sorted)
        assertTrue(place().outputDir.isDirectory)
    }

    @Test
    fun findsVideosToo() {
        file("DCIM/Camera/VID_20260914_101530.mp4")
        file("DCIM/Camera/film.MOV")
        file("DCIM/Camera/a.jpg")
        file("DCIM/Camera/son.m4a")
        val found = PhotoFiles.list(place(), includeSorted = false).map { it.file.name }.sorted()
        assertEquals(listOf("VID_20260914_101530.mp4", "a.jpg", "film.MOV"), found)
        assertTrue(PhotoFiles.isVideo(File("x.MP4")))
        assertTrue(!PhotoFiles.isVideo(File("x.jpg")))
    }
}
