package fr.mesphotos.detect

/**
 * Détection de nudité avec le modèle NudeNet 320n (format ONNX). Tout se passe sur le téléphone.
 *
 * Le modèle regarde une image carrée de 320 × 320 et répond, pour 2100 emplacements, une boîte (4 nombres)
 * puis un score entre 0 et 1 pour chacune de 18 « parties du corps ». Ici on ne garde que les parties
 * découvertes (exposées) : seins de femme, parties génitales, fesses, anus. Cette partie du code est du Kotlin
 * simple, sans Android, pour pouvoir la tester.
 */
object NudityModel {
    const val ASSET = "nudenet/320n.onnx"
    const val SIZE = 320

    /** À partir de ce score, la photo est proposée à la personne (elle décide ensuite). */
    const val THRESHOLD = 0.35f

    /** Rangs des classes « exposées » dans la sortie du modèle (après les 4 nombres de la boîte). */
    private const val BUTTOCKS_EXPOSED = 2
    private const val FEMALE_BREAST_EXPOSED = 3
    private const val FEMALE_GENITALIA_EXPOSED = 4
    private const val ANUS_EXPOSED = 6
    private const val MALE_GENITALIA_EXPOSED = 14
    private val EXPOSED = intArrayOf(BUTTOCKS_EXPOSED, FEMALE_BREAST_EXPOSED, FEMALE_GENITALIA_EXPOSED, ANUS_EXPOSED, MALE_GENITALIA_EXPOSED)

    private const val BOX_VALUES = 4

    /**
     * [pixels] : image [size] × [size] en ARGB, déjà posée en haut à gauche sur fond noir.
     * Résultat : [rouge..., vert..., bleu...] à plat (forme 1 × 3 × size × size), valeurs de 0 à 1.
     */
    fun toTensor(pixels: IntArray, size: Int = SIZE): FloatArray {
        val plane = size * size
        require(pixels.size == plane) { "image de ${pixels.size} points, attendu $plane" }
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = ((p shr 16) and 0xFF) / 255f
            out[plane + i] = ((p shr 8) and 0xFF) / 255f
            out[2 * plane + i] = (p and 0xFF) / 255f
        }
        return out
    }

    /** [output] : sortie du modèle, forme [1][22][emplacements]. Retourne le meilleur score de nudité, entre 0 et 1. */
    fun score(output: Array<Array<FloatArray>>): Float {
        val rows = output.firstOrNull() ?: return 0f
        var best = 0f
        for (c in EXPOSED) {
            val row = rows.getOrNull(BOX_VALUES + c) ?: continue
            for (v in row) if (v > best) best = v
        }
        return best.coerceIn(0f, 1f)
    }
}
