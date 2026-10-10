package fr.mesphotos.storage

import java.io.File

/** La carte SD. Les photos rangées vont dans « Photos rangées », sur la même carte (déplacement instantané). */
data class Place(
    /** Identifiant de la carte (ex. 1234-ABCD). */
    val key: String,
    val title: String,
    val root: File,
    /** Vrai pour un téléphone SANS carte SD : l'appli travaille alors dans la mémoire du téléphone (voir [Places.detect]). */
    val internal: Boolean = false,
) {
    /**
     * Carte SD : toute la carte est parcourue (sauf « Android » et les dossiers cachés).
     * Mémoire du téléphone : seulement les dossiers de photos habituels (DCIM, Pictures, Movies, Download), pas le reste.
     */
    val scanRoots: List<File>
        get() = if (internal) INTERNAL_SCAN.map { File(root, it) }.filter { it.isDirectory } else listOf(root)
    val outputDir: File get() = File(root, OUTPUT_DIR)

    /**
     * Là où vont les photos prises avec l'appli : dans « Photos rangées », donc visibles tout de suite dans la galerie et la loupe.
     * Le prochain « Ranger » les classe par date, comme toutes les autres.
     */
    val captureDir: File get() = File(outputDir, CAPTURE_DIR)

    /** Où elles allaient avant la V17 (à la racine de la carte, donc invisibles dans l'appli) : on les ramène dans [captureDir]. */
    val legacyCaptureDir: File get() = File(root, CAPTURE_DIR)

    companion object {
        /** Dossiers parcourus dans la mémoire du téléphone (quand il n'y a pas de carte SD). */
        val INTERNAL_SCAN = listOf("DCIM", "Pictures", "Movies", "Download")

        const val OUTPUT_DIR = "Photos rangées"

        const val CAPTURE_DIR = "Photos à trier"

        /** Photos mises à part par la personne : dans « Photos rangées », mais ni dans la galerie, ni dans le rangement. */
        const val ASIDE_DIR = "À l'écart"
    }
}
