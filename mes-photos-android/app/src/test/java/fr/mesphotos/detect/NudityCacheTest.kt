package fr.mesphotos.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NudityCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun photo(name: String, text: String = "contenu"): File = File(tmp.root, name).apply { writeText(text) }

    private fun cache() = NudityCache(File(tmp.root, "cache.tsv"))

    @Test
    fun scoresSurviveAReload() {
        val a = photo("a.jpg")
        val c = cache()
        c.put(a, 0.9f)
        val again = cache().apply { load() }
        assertEquals(0.9f, again.get(a)!!.score, 0f)
    }

    @Test
    fun aChangedFileIsLookedAtAgain() {
        val a = photo("a.jpg", "un")
        val c = cache()
        c.put(a, 0.9f)
        a.writeText("un contenu bien plus long")
        assertNull(c.get(a))
        assertEquals(0, c.hits(0.35f).size)
    }

    @Test
    fun hitsAreSortedAndOnlyAboveTheThreshold() {
        val a = photo("a.jpg")
        val b = photo("b.jpg")
        val c = photo("c.jpg")
        val cache = cache()
        cache.put(a, 0.4f)
        cache.put(b, 0.95f)
        cache.put(c, 0.1f)
        assertEquals(listOf("b.jpg", "a.jpg"), cache.hits(0.35f).map { File(it.path).name })
    }

    @Test
    fun dismissedPhotosAreNotProposedAnymoreEvenAfterReload() {
        val a = photo("a.jpg")
        val b = photo("b.jpg")
        val cache = cache()
        cache.put(a, 0.9f)
        cache.put(b, 0.8f)
        cache.dismiss(listOf(a))
        assertEquals(listOf("b.jpg"), cache.hits(0.35f).map { File(it.path).name })
        val again = cache().apply { load() }
        assertEquals(listOf("b.jpg"), again.hits(0.35f).map { File(it.path).name })
        assertNotNull(again.get(a)) // retenue comme « déjà vue » : elle n'est pas refaite
    }

    @Test
    fun missingFilesAreNotProposed() {
        val a = photo("a.jpg")
        val cache = cache()
        cache.put(a, 0.9f)
        a.delete()
        assertEquals(0, cache.hits(0.35f).size)
    }

    @Test
    fun brokenLinesAreIgnored() {
        File(tmp.root, "cache.tsv").writeText("pas une ligne\nx\ty\tz\tw\n")
        val c = cache().apply { load() }
        assertEquals(0, c.hits(0f).size)
    }
}
