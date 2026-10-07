package fr.mesphotos.faces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FaceCacheTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun vec(first: Float, second: Float): FloatArray {
        val v = FloatArray(FaceMath.EMBEDDING)
        v[0] = first
        v[1] = second
        return FaceMath.normalized(v)
    }

    private fun face(v: FloatArray) = FaceCache.Face(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), v)

    private fun photo(name: String, text: String = name): File = tmp.newFile(name).apply { writeText(text) }

    @Test
    fun searchFindsSimilarFacesBestFirst() {
        val cache = FaceCache(File(tmp.root, "visages.tsv"))
        val close = photo("close.jpg")
        val far = photo("far.jpg")
        val medium = photo("medium.jpg")
        cache.put(close, listOf(face(vec(1f, 0.05f))))
        cache.put(far, listOf(face(vec(0f, 1f))))
        cache.put(medium, listOf(face(vec(1f, 0.6f)), face(vec(0f, 1f))))
        val hits = cache.search(vec(1f, 0f), 0.5f)
        assertEquals(listOf(close.absolutePath, medium.absolutePath), hits.map { it.path })
    }

    @Test
    fun searchHonoursTheAcceptFilter() {
        val cache = FaceCache(File(tmp.root, "visages.tsv"))
        val a = photo("a.jpg")
        cache.put(a, listOf(face(vec(1f, 0f))))
        assertEquals(0, cache.search(vec(1f, 0f), 0.5f) { false }.size)
    }

    @Test
    fun cacheSurvivesReloadAndKeepsPhotosWithoutFace() {
        val store = File(tmp.root, "visages.tsv")
        val a = photo("a.jpg")
        val none = photo("none.jpg")
        FaceCache(store).apply {
            put(a, listOf(face(vec(1f, 0f))))
            put(none, emptyList())
        }
        val again = FaceCache(store).apply { load() }
        assertNotNull(again.get(a))
        assertEquals(1, again.get(a)!!.faces.size)
        assertEquals(0.2f, again.get(a)!!.faces[0].box[1], 1e-6f)
        assertNotNull(again.get(none)) // vue, sans visage : pas à refaire
        assertEquals(1, again.search(vec(1f, 0f), 0.5f).size)
    }

    @Test
    fun changedFileIsNoLongerValid() {
        val store = File(tmp.root, "visages.tsv")
        val a = photo("a.jpg", "un")
        val cache = FaceCache(store)
        cache.put(a, listOf(face(vec(1f, 0f))))
        a.writeText("contenu différent et plus long")
        assertNull(cache.get(a))
        assertEquals(0, cache.search(vec(1f, 0f), 0.5f).size)
    }

    @Test
    fun brokenLinesAreIgnored() {
        val store = File(tmp.root, "visages.tsv")
        store.writeText("n'importe quoi\n/x\tpas un nombre\t3\n/y\t1\t2\t0.1,0.2,0.3,0.4,pasUneEmpreinte\n")
        val cache = FaceCache(store)
        cache.load()
        assertNull(cache.get(File("/x")))
        assertNull(cache.get(File("/y")))
    }
}
