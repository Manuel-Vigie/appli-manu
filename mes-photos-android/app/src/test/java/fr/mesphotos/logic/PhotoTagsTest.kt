package fr.mesphotos.logic

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTagsTest {
    @Test
    fun retientLesMotsSansDoublonEtSuitLePhotoSiRenommee() {
        val dir = createTempDir()
        val photo = File(dir, "a.jpg").apply { writeText("contenu") }
        val tags = PhotoTags(File(dir, "mots.tsv"))
        assertEquals(1, tags.add(listOf(photo), listOf("Najet")))
        assertEquals(0, tags.add(listOf(photo), listOf("najet")))
        tags.add(listOf(photo), listOf("Gilles"))
        assertEquals("Najet Gilles", tags.of(photo))
        // Renommée : même taille, même date → toujours reconnue, et la mémoire se relit depuis le fichier.
        val moved = File(dir, "autre-nom.jpg")
        val date = photo.lastModified()
        photo.renameTo(moved)
        moved.setLastModified(date)
        assertEquals("Najet Gilles", PhotoTags(File(dir, "mots.tsv")).of(moved))
        dir.deleteRecursively()
    }
}
