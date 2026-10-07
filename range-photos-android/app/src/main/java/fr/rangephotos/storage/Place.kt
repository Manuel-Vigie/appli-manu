package fr.rangephotos.storage

import fr.rangephotos.organize.Organizer
import java.io.File

/**
 * Un endroit où se trouvent des photos : la carte SD ou la mémoire du téléphone.
 * Le dossier « Photos rangées » est créé à la racine de cet endroit (même volume : déplacement instantané).
 */
data class Place(
    /** « primary » pour la mémoire du téléphone, sinon l'identifiant de la carte (ex. 1234-ABCD). */
    val key: String,
    val title: String,
    val isPrimary: Boolean,
    val root: File,
    /** Dossiers parcourus pour trouver les photos. */
    val scanRoots: List<File>,
) {
    val outputDir: File get() = File(root, Organizer.OUTPUT_DIR)
}
