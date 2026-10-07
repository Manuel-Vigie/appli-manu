package fr.mesphotos.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    private fun release(tag: String, asset: String? = "MesPhotos.apk", draft: Boolean = false): String {
        val assets = if (asset == null) "[]" else """[{"name":"$asset","browser_download_url":"https://exemple.test/$tag/$asset"}]"""
        return """{"tag_name":"$tag","draft":$draft,"prerelease":false,"html_url":"https://exemple.test/$tag","assets":$assets}"""
    }

    @Test
    fun versionsAreComparedNumerically() {
        assertTrue(UpdateChecker.isNewer("0.3.0", "0.2.0"))
        assertTrue(UpdateChecker.isNewer("0.10.0", "0.9.0"))
        assertTrue(UpdateChecker.isNewer("1.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewer("0.2.0", "0.3.0"))
    }

    @Test
    fun picksTheNewestMesPhotosReleaseAndIgnoresOthers() {
        val json = "[" + listOf(
            release("autre-appli-v9.0.0"),
            release("mes-photos-v0.2.0"),
            release("mes-photos-v0.3.0"),
            release("mes-photos-v0.4.0", draft = true),
            release("mes-photos-v0.5.0", asset = null),
        ).joinToString(",") + "]"
        val latest = UpdateChecker.parseLatest(json)
        assertEquals("0.3.0", latest?.version)
        assertEquals("https://exemple.test/mes-photos-v0.3.0/MesPhotos.apk", latest?.apkUrl)
    }

    @Test
    fun garbageGivesNothing() {
        assertNull(UpdateChecker.parseLatest("pas du json"))
        assertNull(UpdateChecker.parseLatest("[]"))
    }
}
