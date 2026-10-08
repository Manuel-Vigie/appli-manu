package fr.mesphotos.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RenameNamesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun keepsTheFileType() {
        assertEquals("Mariage Julie.jpg", RenameNames.build("Mariage Julie", "IMG_0001.jpg", false))
        assertEquals("Mariage Julie.JPG", RenameNames.build("Mariage Julie", "IMG_0001.JPG", false))
    }

    @Test
    fun doesNotDoubleTheExtensionTypedByThePerson() {
        assertEquals("Chat.jpg", RenameNames.build("Chat.jpg", "a.jpg", false))
        assertEquals("Chat.png", RenameNames.build("Chat.PNG", "a.png", false))
    }

    @Test
    fun removesForbiddenCharactersAndLeadingDots() {
        assertEquals("a-b-c.jpg", RenameNames.build("a/b:c", "x.jpg", false))
        assertEquals("secret.jpg", RenameNames.build("..secret", "x.jpg", false))
    }

    @Test
    fun emptyNameIsRefused() {
        assertNull(RenameNames.build("   ", "x.jpg", false))
        assertNull(RenameNames.build("...", "x.jpg", false))
    }

    @Test
    fun keepsTheOriginalNameWhenItCarriesTheDate() {
        assertEquals("Mariage - IMG_20240314_101530.jpg", RenameNames.build("Mariage", "IMG_20240314_101530.jpg", true))
        // déjà contenu dans ce que la personne a tapé : pas de doublon
        assertEquals("IMG_20240314_101530 Mariage.jpg", RenameNames.build("IMG_20240314_101530 Mariage", "IMG_20240314_101530.jpg", true))
    }

    @Test
    fun uniqueAddsANumberWhenTheNameIsTaken() {
        File(tmp.root, "Chat.jpg").writeText("x")
        File(tmp.root, "Chat (2).jpg").writeText("x")
        assertEquals("Chat (3).jpg", RenameNames.unique(tmp.root, "Chat.jpg").name)
        assertEquals("Chien.jpg", RenameNames.unique(tmp.root, "Chien.jpg").name)
    }
}
