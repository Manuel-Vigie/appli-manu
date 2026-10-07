package fr.mesphotos.logic

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DuplicateReviewTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val root get() = File(tmp.root, "Photos rangées").apply { mkdirs() }

    private fun file(path: String, content: String): File =
        File(root, path).apply { parentFile!!.mkdirs(); writeText(content) }

    @Test
    fun groupsIdenticalContentWhateverTheName() {
        val a = file("Journées/2026/03 - mars/14 mars (sam)/IMG_1.jpg", "AAAA")
        val b = file("Doublons/WhatsApp-copie.jpg", "AAAA")
        val other = file("Journées/2026/03 - mars/14 mars (sam)/IMG_2.jpg", "BBBB")
        val groups = DuplicateReview.find(listOf(a, b, other), root)
        assertEquals(1, groups.size)
        assertEquals(setOf(a, b), groups[0].files.toSet())
        assertEquals(a, groups[0].keep)
    }

    @Test
    fun sameSizeButDifferentContentIsNotAGroup() {
        val a = file("Journées/a.jpg", "AAAA")
        val b = file("Journées/b.jpg", "AAAB")
        assertEquals(0, DuplicateReview.find(listOf(a, b), root).size)
    }

    @Test
    fun prefersOutsideDoublonsThenSureDateThenNoCopySuffix() {
        val inDoubles = file("Doublons/IMG_1.jpg", "X")
        val unsure = file("Journées/Date incertaine/2026/03 - mars/IMG_1.jpg", "X")
        val sure = file("Journées/2026/03 - mars/14 mars (sam)/IMG_1 (2).jpg", "X")
        val clean = file("Journées/2026/03 - mars/14 mars (sam)/IMG_1.jpg", "X")
        assertEquals(clean, DuplicateReview.best(listOf(inDoubles, unsure, sure, clean), root))
        assertEquals(sure, DuplicateReview.best(listOf(inDoubles, unsure, sure), root))
        assertEquals(unsure, DuplicateReview.best(listOf(inDoubles, unsure), root))
    }

    @Test
    fun reportsProgressAndIgnoresUnreadableFiles() {
        val a = file("Journées/a.jpg", "AAAA")
        val b = file("Journées/b.jpg", "AAAA")
        var last = 0
        val groups = DuplicateReview.find(listOf(a, b), root, fingerprint = { null }, onProgress = { done, _ -> last = done })
        assertEquals(0, groups.size)
        assertEquals(2, last)
    }
}
