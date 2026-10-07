package fr.rangephotos.logic

import android.net.Uri
import fr.rangephotos.model.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock

class DuplicatesTest {

    private fun photo(name: String, size: Long, folder: List<String>?) = PhotoInfo(
        uri = mock(Uri::class.java), parentUri = null, name = name, mimeType = "image/jpeg",
        takenAt = 0L, lat = null, lon = null, isScreenshot = false, size = size, currentFolder = folder,
    )

    @Test
    fun keepsTheOriginalAndLeavesThePersonCopy() {
        val copy = photo("IMG_1.jpg", 100, listOf("Portraits", "Julie", "2026"))
        val original = photo("IMG_1.jpg", 100, listOf("Portraits", "Groupe", "2026"))
        val other = photo("IMG_2.jpg", 100, null)
        val (kept, left) = Duplicates.keepOnePerPhoto(listOf(copy, original, other))
        assertEquals(2, kept.size)
        assertEquals(1, left)
        assertEquals(listOf("Portraits", "Groupe", "2026"), kept[0].currentFolder)
    }

    @Test
    fun renamedCopyCountsAsTheSamePhoto() {
        val a = photo("IMG_1.jpg", 100, listOf("Photos", "2026", "03 - mars"))
        val b = photo("IMG_1 (2).jpg", 100, listOf("Portraits", "Julie", "2026"))
        assertEquals(1, Duplicates.keepOnePerPhoto(listOf(a, b)).first.size)
    }

    @Test
    fun sameNameButDifferentSizeAreDifferentPhotos() {
        val a = photo("IMG_1.jpg", 100, null)
        val b = photo("IMG_1.jpg", 200, null)
        assertEquals(2, Duplicates.keepOnePerPhoto(listOf(a, b)).first.size)
    }
}
