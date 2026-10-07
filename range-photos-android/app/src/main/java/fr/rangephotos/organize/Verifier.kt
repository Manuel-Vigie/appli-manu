package fr.rangephotos.organize

import fr.rangephotos.logic.PlannedMove
import java.io.File

/** Résultat de la vérification complète d'un rangement. */
data class VerifyReport(
    /** Fichiers contrôlés un par un. */
    val checked: Int,
    /** Fichiers bien arrivés (présents, même taille, plus à l'ancienne place). */
    val ok: Int,
    /** Les premiers problèmes, en clair. */
    val problems: List<String>,
    val problemCount: Int,
    /** Photos et vidéos encore à l'ancienne place (non rangées) après le rangement. */
    val leftBehind: Int,
    val leftNames: List<String>,
    /** Nombre de fichiers (photos + vidéos) avant et après, sur tout le stockage analysé. */
    val totalBefore: Int,
    val totalAfter: Int,
    val copiesMade: Int,
) {
    /** Rien n'a disparu : après = avant + copies faites. */
    val countsMatch: Boolean get() = totalAfter == totalBefore + copiesMade
    val allGood: Boolean get() = problemCount == 0 && leftBehind == 0 && countsMatch

    fun lines(): List<String> {
        val result = ArrayList<String>()
        result += "Vérification : $ok fichier(s) sur $checked bien arrivés (présents, même taille)."
        result += if (countsMatch) {
            "Compte total : $totalBefore avant, $totalAfter après" + (if (copiesMade > 0) " (dont $copiesMade copie(s) de proches)." else ".") +
                " Aucun fichier perdu."
        } else {
            "ATTENTION compte total : $totalBefore avant, $totalAfter après" +
                (if (copiesMade > 0) " (avec $copiesMade copie(s) faites)" else "") + ". Il manque ${totalBefore + copiesMade - totalAfter} fichier(s)."
        }
        if (leftBehind > 0) {
            result += "$leftBehind fichier(s) n'ont pas été rangés et sont restés à leur place" +
                (if (leftNames.isNotEmpty()) " : " + leftNames.joinToString(", ") else "") + "."
        }
        problems.forEach { result += it }
        if (problemCount > problems.size) result += "… et ${problemCount - problems.size} autre(s) problème(s)."
        if (allGood) result += "Tout s'est bien passé : aucune photo ni vidéo oubliée ou perdue."
        return result
    }
}

object Verifier {

    private const val MAX_PROBLEMS = 6
    private const val MAX_NAMES = 5

    /** Contrôle un à un les fichiers de [moves] d'après le [journal] (destination présente, même taille, ancienne place vidée). */
    fun checkMoves(moves: List<PlannedMove>, journal: File): Triple<Int, Int, List<String>> {
        val arrived = HashMap<String, String>() // ancien chemin -> nouveau chemin
        val copies = ArrayList<String>()
        if (journal.exists()) {
            for (line in journal.readLines()) {
                val parts = line.split('\t')
                if (parts.size == 3 && parts[0] == "M") arrived[parts[2]] = parts[1]
                if (parts.size == 2 && parts[0] == "C") copies += parts[1]
            }
        }
        var ok = 0
        val problems = ArrayList<String>()
        for (move in moves) {
            val photo = move.photo
            val path = photo.path ?: continue
            val inPlace = photo.currentFolder != null && photo.currentFolder == move.folder
            if (inPlace) {
                if (File(path).isFile) ok++ else problems += "${photo.name} : a disparu de son dossier."
                continue
            }
            val moved = arrived[path]
            if (moved == null) {
                problems += if (File(path).isFile) "${photo.name} : n'a pas été déplacée." else "${photo.name} : introuvable."
                continue
            }
            val target = File(moved)
            when {
                !target.isFile -> problems += "${photo.name} : absente de sa nouvelle place."
                target.length() != photo.size -> problems += "${photo.name} : taille différente (${target.length()} au lieu de ${photo.size})."
                File(path).exists() -> problems += "${photo.name} : existe encore à l'ancienne place."
                else -> ok++
            }
        }
        for (copy in copies) if (!File(copy).isFile) problems += "${File(copy).name} : copie absente."
        return Triple(moves.count { it.photo.path != null } + copies.size, ok + copies.count { File(it).isFile }, problems)
    }

    fun copiesInJournal(journal: File): Int =
        if (journal.exists()) journal.readLines().count { it.startsWith("C\t") } else 0

    fun report(
        checked: Triple<Int, Int, List<String>>,
        leftBehind: List<String>,
        totalBefore: Int,
        totalAfter: Int,
        copiesMade: Int,
    ) = VerifyReport(
        checked = checked.first,
        ok = checked.second,
        problems = checked.third.take(MAX_PROBLEMS),
        problemCount = checked.third.size,
        leftBehind = leftBehind.size,
        leftNames = leftBehind.take(MAX_NAMES),
        totalBefore = totalBefore,
        totalAfter = totalAfter,
        copiesMade = copiesMade,
    )
}
