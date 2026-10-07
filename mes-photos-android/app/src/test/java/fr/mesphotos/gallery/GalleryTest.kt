package fr.mesphotos.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GalleryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun searchIgnoresCaseAndAccents() {
        val terms = Gallery.terms("  AOÛT   2024 ")
        assertEquals(listOf("aout", "2024"), terms)
        assertTrue(Gallery.matches(Gallery.normalize("Journées 2024 08 - août 14 août (sam) - Nice IMG_1.jpg"), terms))
    }

    @Test
    fun everyWordMustMatch() {
        val path = Gallery.normalize("Journées 2024 08 - août 14 août (sam) - Nice a.jpg")
        assertTrue(Gallery.matches(path, Gallery.terms("nice 2024")))
        assertFalse(Gallery.matches(path, Gallery.terms("nice 2023")))
    }

    @Test
    fun emptyQueryMatchesNothing() {
        assertFalse(Gallery.matches("abc", Gallery.terms("   ")))
    }

    @Test
    fun asideFolderIsInvisibleToTheGalleryAndTheSearch() {
        val root = tmp.newFolder("Photos rangées")
        File(root, "Journées/2024/a.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
        File(root, "À l'écart/Journées/2024/b.jpg").apply { parentFile!!.mkdirs(); writeText("x") }
        assertEquals(listOf("Journées"), Gallery.list(root, emptyList()).folders.map { it.name })
        assertEquals(listOf("a.jpg"), Gallery.allMedia(root).map { it.name })
    }

    @Test
    fun foldersAreSortedRecentFirstAndUncertainLast() {
        val sorted = listOf("Date incertaine", "2019", "2026", "2021").sortedWith { a, b -> Gallery.compareFolders(a, b) }
        assertEquals(listOf("2026", "2021", "2019", "Date incertaine"), sorted)
    }

    @Test
    fun noDateNoPlaceFolderComesLast() {
        val sorted = listOf("Sans date ni lieu", "Date incertaine", "2024").sortedWith { a, b -> Gallery.compareFolders(a, b) }
        assertEquals(listOf("2024", "Date incertaine", "Sans date ni lieu"), sorted)
    }

    @Test
    fun journeesComesFirst() {
        val sorted = listOf("Autre", "Journées").sortedWith { a, b -> Gallery.compareFolders(a, b) }
        assertEquals(listOf("Journées", "Autre"), sorted)
    }
}
