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

    companion object {
        const val OUTPUT_DIR = "Photos rangées"
    }
}
