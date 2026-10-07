package fr.rangephotos.face

import kotlin.math.hypot

/**
 * Redresse un visage au format attendu par le modèle de reconnaissance (112×112, yeux et bouche
 * à des endroits fixes), à partir de 4 repères : les deux yeux et les deux coins de la bouche.
 * Calcul vérifié contre l'implémentation de référence d'OpenCV (voir JOURNAL.md).
 */
object FaceAlign {
    const val SIZE = 112

    /** Gabarit 112×112 : œil gauche, œil droit, coin gauche de la bouche, coin droit (vus dans l'image). */
    val TEMPLATE = floatArrayOf(
        38.2946f, 51.6963f,
        73.5318f, 51.5014f,
        41.5493f, 92.3655f,
        70.7299f, 92.2041f,
    )

    /** Transformation u = a·x − b·y + tx ; v = b·x + a·y + ty (rotation + zoom + déplacement). */
    class Similarity(val a: Double, val b: Double, val tx: Double, val ty: Double) {
        fun mapX(x: Double, y: Double) = a * x - b * y + tx
        fun mapY(x: Double, y: Double) = b * x + a * y + ty

        /** Les 9 valeurs de la matrice, pour android.graphics.Matrix.setValues. */
        fun matrixValues() = floatArrayOf(
            a.toFloat(), (-b).toFloat(), tx.toFloat(),
            b.toFloat(), a.toFloat(), ty.toFloat(),
            0f, 0f, 1f,
        )
    }

    /** Meilleure transformation (moindres carrés) qui amène les points [src] sur les points [dst] ; null si impossible. */
    fun estimate(src: FloatArray, dst: FloatArray = TEMPLATE): Similarity? {
        if (src.size != dst.size || src.size < 4 || src.size % 2 != 0) return null
        val n = src.size / 2
        var msx = 0.0
        var msy = 0.0
        var mdx = 0.0
        var mdy = 0.0
        for (i in 0 until n) {
            msx += src[2 * i]; msy += src[2 * i + 1]
            mdx += dst[2 * i]; mdy += dst[2 * i + 1]
        }
        msx /= n; msy /= n; mdx /= n; mdy /= n

        var cosSum = 0.0
        var sinSum = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val x = src[2 * i] - msx
            val y = src[2 * i + 1] - msy
            val u = dst[2 * i] - mdx
            val v = dst[2 * i + 1] - mdy
            cosSum += x * u + y * v
            sinSum += x * v - y * u
            den += x * x + y * y
        }
        if (den < 1e-9) return null
        val a = cosSum / den
        val b = sinSum / den
        return Similarity(a, b, mdx - (a * msx - b * msy), mdy - (b * msx + a * msy))
    }

    /**
     * Range les 4 repères comme le modèle les attend : [œil gauche, œil droit, bouche gauche, bouche droite],
     * « gauche » signifiant le plus à gauche dans l'image (on trie par position, pas par nom, pour ne pas dépendre
     * de la convention de gauche/droite du détecteur). Chaque repère est [x, y].
     */
    fun orderLandmarks(eye1: FloatArray, eye2: FloatArray, mouth1: FloatArray, mouth2: FloatArray): FloatArray {
        val eyes = if (eye1[0] <= eye2[0]) listOf(eye1, eye2) else listOf(eye2, eye1)
        val mouth = if (mouth1[0] <= mouth2[0]) listOf(mouth1, mouth2) else listOf(mouth2, mouth1)
        return floatArrayOf(
            eyes[0][0], eyes[0][1], eyes[1][0], eyes[1][1],
            mouth[0][0], mouth[0][1], mouth[1][0], mouth[1][1],
        )
    }

    /** Distance entre les yeux (en pixels) pour des repères rangés par [orderLandmarks]. */
    fun eyeDistance(points: FloatArray): Float = hypot(points[2] - points[0], points[3] - points[1])
}
