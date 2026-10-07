package fr.rangephotos.logic

import android.net.Uri
import fr.rangephotos.model.Category
import fr.rangephotos.model.CategoryKind
import fr.rangephotos.model.FaceInfo
import fr.rangephotos.model.PhotoInfo
import fr.rangephotos.model.Subjects
import fr.rangephotos.people.CategoryStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import java.time.ZoneId

class CategoryPlannerTest {

    private val paris = ZoneId.of("Europe/Paris")
    private val time = java.time.LocalDateTime.of(2026, 3, 14, 12, 0).atZone(paris).toInstant().toEpochMilli()

    private fun photo(
        labels: Map<String, Float> = emptyMap(),
        barcode: Boolean = false,
        place: String? = null,
        faces: Int = 0,
        screenshot: Boolean = false,
    ) = PhotoInfo(
        uri = mock(Uri::class.java), parentUri = null, name = "p.jpg", mimeType = "image/jpeg",
        takenAt = time, lat = null, lon = null, isScreenshot = screenshot,
        faces = List(faces) { FaceInfo(0.3f, null, null) },
        labels = labels, hasBarcode = barcode, place = place,
    )

    private val barcode = Category("barcode", "Codes-barres", CategoryKind.BARCODE, enabled = true, builtIn = true)
    private val vehicles = Category(
        "vehicles", "Véhicules", CategoryKind.SUBJECTS, Subjects.labelsOf(listOf("Voitures", "Motos")), enabled = true, builtIn = true,
    )
    private val places = Category("places", "Lieux", CategoryKind.PLACE, enabled = true, builtIn = true)

    private fun folderOf(p: PhotoInfo, vararg cats: Category) =
        Planner.plan(listOf(p), emptyList(), emptyMap(), true, paris, cats.toList()).single().folder

    @Test
    fun barcodePhotoGoesToItsFolderByYear() {
        assertEquals(listOf("Codes-barres", "2026"), folderOf(photo(barcode = true), barcode, vehicles))
    }

    @Test
    fun vehiclePhotoNeedsEnoughConfidence() {
        assertEquals(listOf("Véhicules", "2026"), folderOf(photo(labels = mapOf("Car" to 0.9f)), vehicles))
        assertEquals(listOf("Journées", "2026", "03 - mars", "14 mars (sam)"), folderOf(photo(labels = mapOf("Car" to 0.3f)), vehicles))
        assertEquals(listOf("Journées", "2026", "03 - mars", "14 mars (sam)"), folderOf(photo(labels = mapOf("Dog" to 0.95f)), vehicles))
    }

    @Test
    fun placeFolderIsOneSubfolderPerTown() {
        assertEquals(listOf("Lieux", "Castellane"), folderOf(photo(place = "Castellane"), places))
        assertEquals(listOf("Lieux", "Sans nom"), listOf("Lieux", Planner.sanitize("")))
    }

    @Test
    fun portraitsAndScreenshotsRankAsDocumented() {
        // Un portrait garde son dossier Portraits, même s'il contient un code-barres.
        assertEquals(listOf("Portraits", "Solo", "2026"), folderOf(photo(barcode = true, faces = 1), barcode))
        // Une capture d'écran avec un code-barres va dans le dossier de l'utilisateur.
        assertEquals(listOf("Codes-barres", "2026"), folderOf(photo(barcode = true, screenshot = true), barcode))
    }

    @Test
    fun firstMatchingFolderInTheListWins() {
        val both = photo(barcode = true, labels = mapOf("Car" to 0.9f))
        assertEquals(listOf("Véhicules", "2026"), folderOf(both, vehicles, barcode))
        assertEquals(listOf("Codes-barres", "2026"), folderOf(both, barcode, vehicles))
    }

    @Test
    fun storeKeepsChoicesAndCustomFolders() {
        val store = CategoryStore()
        store.setEnabled("barcode", true)
        val custom = store.add("Voitures de collection", CategoryKind.SUBJECTS, Subjects.labelsOf(listOf("Voitures")))
        assertTrue(custom.enabled)

        val copy = CategoryStore()
        copy.importIfEmpty(store.toJson())
        assertTrue(copy.categories.first { it.id == "barcode" }.enabled)
        assertEquals("Voitures de collection", copy.categories.last().name)
        assertEquals(2, copy.enabled().size)

        copy.remove("barcode") // un dossier proposé d'office ne se supprime pas
        assertTrue(copy.categories.any { it.id == "barcode" })
        copy.remove(custom.id)
        assertFalse(copy.categories.any { it.id == custom.id })
    }

    @Test
    fun sameNameGetsANumber() {
        val store = CategoryStore()
        store.add("Motos", CategoryKind.SUBJECTS, setOf("motorcycle"))
        assertEquals("motos (2)", store.add("motos", CategoryKind.SUBJECTS, setOf("motorcycle")).name)
    }
}
