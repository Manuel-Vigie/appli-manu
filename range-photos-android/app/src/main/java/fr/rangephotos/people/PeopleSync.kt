package fr.rangephotos.people

import java.io.File

/**
 * Copie de sauvegarde des prénoms dans « Photos rangées/mes-proches.json » (sur la carte SD) :
 * en changeant de téléphone, il suffit de réinstaller l'appli et de choisir la même carte.
 */
class PeopleSync {

    /** Contenu de la sauvegarde présente dans [outputDir], ou null s'il n'y en a pas. */
    fun read(outputDir: File): String? = try {
        File(outputDir, FILE_NAME).takeIf { it.isFile }?.readText(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    /** Écrit (ou remplace) la sauvegarde. Retourne true si tout s'est bien passé. */
    fun write(outputDir: File, json: String): Boolean = try {
        outputDir.mkdirs()
        File(outputDir, FILE_NAME).writeText(json, Charsets.UTF_8)
        true
    } catch (_: Exception) {
        false
    }

    private companion object {
        const val FILE_NAME = "mes-proches.json"
    }
}
