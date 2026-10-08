package fr.mesphotos.storage

import java.io.File

/** La carte SD. Les photos rangées vont dans « Photos rangées », sur la même carte (déplacement instantané). */
data class Place(
    /** Identifiant de la carte (ex. 1234-ABCD). */
    val key: String,
    val title: String,
    val root: File,
) {
    /** Toute la carte est parcourue (sauf « Android » et les dossiers cachés). */
    val scanRoots: List<File> get() = listOf(root)
    val outputDir: File get() = File(root, OUTPUT_DIR)

    /** Là où vont les photos prises avec l'appli : hors de « Photos rangées », donc « à ranger » au prochain rangement par date. */
    val captureDir: File get() = File(root, CAPTURE_DIR)

    companion object {
        const val OUTPUT_DIR = "Photos rangées"

        const val CAPTURE_DIR = "Photos à trier"

        /** Photos mises à part par la personne : dans « Photos rangées », mais ni dans la galerie, ni dans le rangement. */
        const val ASIDE_DIR = "À l'écart"
    }
}
