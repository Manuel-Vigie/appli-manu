package fr.mesphotos.logic

import fr.mesphotos.model.PhotoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DuplicatesTest {

    private fun photo(name: String, size: Long, folder: List<String>?, path: String = "/sd/$name") = PhotoInfo(
        name = name, takenAt = 0L, size = size, currentFolder = folder, path = path,
    )

    /** Empreinte de test : le contenu est donné par le nom du dossier. */
    private val byParent: (PhotoInfo) -> String? = { it.path?.substringBeforeLast('/') }

    @Test
    fun identicalCopyBecomesDuplicateAndOriginalStays() {
        val a = photo("IMG_1.jpg", 100, null, "/x/IMG_1.jpg")
        val b = photo("IMG_1 (2).jpg", 100, null, "/x/IMG_1 (2).jpg")
        val r = Duplicates.split(listOf(a, b), fingerprint = byParent)
        assertEquals(listOf(a), r.originals)
        assertEquals(listOf(b), r.duplicates)
    }

    @Test
    fun sameNameAndSizeButDifferentContentAreTwoPhotos() {
        val a = photo("IMG_1.jpg", 100, null, "/x/IMG_1.jpg")
        val b = photo("IMG_1.jpg", 100, null, "/y/IMG_1.jpg")
        val r = Duplicates.split(listOf(a, b), fingerprint = byParent)
        assertEquals(2, r.originals.size)
        assertEquals(0, r.duplicates.size)
    }

    @Test
    fun differentSizeAreDifferentPhotos() {
        val a = photo("IMG_1.jpg", 100, null, "/x/IMG_1.jpg")
        val b = photo("IMG_1.jpg", 200, null, "/x/IMG_1.jpg")
        assertEquals(2, Duplicates.split(listOf(a, b), fingerprint = byParent).originals.size)
    }

    @Test
    fun unreadableFileIsNeverTreatedAsDuplicate() {
        val a = photo("IMG_1.jpg", 100, null, "/x/IMG_1.jpg")
        val b = photo("IMG_1.jpg", 100, null, "/x/IMG_1.jpg")
        assertEquals(2, Duplicates.split(listOf(a, b), fingerprint = { null }).originals.size)
    }

    @Test
    fun alreadySortedPhotoIsPreferredAsOriginal() {
        val loose = photo("IMG_1.jpg", 100, null, "/x/IMG_1.jpg")
        val sorted = photo("IMG_1.jpg", 100, listOf("Journées", "2026", "03 - mars", "14 mars (sam)"), "/x/IMG_1.jpg")
        val inDoubles = photo("IMG_1.jpg", 100, listOf("Doublons", "DCIM"), "/x/IMG_1.jpg")
        val r = Duplicates.split(listOf(inDoubles, loose, sorted), fingerprint = byParent)
        assertEquals(1, r.originals.size)
        assertSame(sorted, r.originals[0])
        assertEquals(2, r.duplicates.size)
    }

    @Test
    fun allDuplicatesGoToOneSingleFolder() {
        val a = photo("IMG_1.jpg", 100, null, "/sd/WhatsApp/IMG_1.jpg")
        val b = photo("IMG_2.jpg", 100, listOf("Doublons", "DCIM"), "/sd/Photos rangées/Doublons/DCIM/IMG_2.jpg")
        assertEquals(listOf("Doublons"), Planner.duplicateFolder(a))
        assertEquals(listOf("Doublons"), Planner.duplicateFolder(b))
    }

    @Test
    fun realFingerprintTellsSameContentFromDifferent() {
        val dir = java.nio.file.Files.createTempDirectory("dup").toFile()
        try {
            val a = java.io.File(dir, "a.jpg").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
            val b = java.io.File(dir, "b.jpg").apply { writeBytes(ByteArray(1000) { it.toByte() }) }
            val c = java.io.File(dir, "c.jpg").apply { writeBytes(ByteArray(1000) { (it + 1).toByte() }) }
            fun info(f: java.io.File) = photo("x.jpg", 1000, null, f.path)
            val same = Duplicates.fingerprintOf(info(a)) == Duplicates.fingerprintOf(info(b))
            val diff = Duplicates.fingerprintOf(info(a)) == Duplicates.fingerprintOf(info(c))
            assertEquals(true, same)
            assertEquals(false, diff)
        } finally {
            dir.deleteRecursively()
        }
    }
}
