package fr.mesphotos.gallery

import org.junit.Assert.assertEquals
import org.junit.Test

class GalleryTest {
    @Test
    fun foldersAreSortedRecentFirstAndUncertainLast() {
        val sorted = listOf("Date incertaine", "2019", "2026", "2021").sortedWith { a, b -> Gallery.compareFolders(a, b) }
        assertEquals(listOf("2026", "2021", "2019", "Date incertaine"), sorted)
    }

    @Test
    fun journeesComesFirst() {
        val sorted = listOf("Autre", "Journées").sortedWith { a, b -> Gallery.compareFolders(a, b) }
        assertEquals(listOf("Journées", "Autre"), sorted)
    }
}
