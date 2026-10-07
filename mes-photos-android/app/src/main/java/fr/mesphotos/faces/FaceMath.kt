package fr.mesphotos.faces

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Un visage trouvé par le détecteur. Coordonnées en points de l'image réduite à 640 × 640. [points] : 5 repères (x, y) = œil, œil, nez, bouche, bouche. */
class Detection(val x: Float, val y: Float, val w: Float, val h: Float, val score: Float, val points: FloatArray)

/**
 * Calculs de la recherche par visage, en Kotlin simple (sans Android) pour pouvoir les tester.
 * Détecteur : YuNet (640 × 640, 3 échelles). Reconnaissance : SFace (visage redressé de 112 × 112 -> 128 nombres).
 */
object FaceMath {
    const val INPUT = 640
    const val CROP = 112
    const val EMBEDDING = 128
    val STRIDES = intArrayOf(8, 16, 32)

    /** Position des 5 repères dans un visage redressé de 112 × 112 (x, y, x, y, …). */
    val TEMPLATE = doubleArrayOf(
        38.2946, 51.6963, 73.5318, 51.5014, 56.0252, 71.7366, 41.5493, 92.3655, 70.7299, 92.2041,
    )

    /** Deux visages d'une même personne ont en général une ressemblance (cosinus) d'au moins 0,363. */
    const val SAME_PERSON = 0.363f

    /** Image [width] × [height] (ARGB) posée en haut à gauche d'un carré noir de [INPUT], rangée en bleu, vert, rouge (valeurs 0 à 255). */
    fun toDetectorInput(pixels: IntArray, width: Int, height: Int): FloatArray {
        require(pixels.size == width * height && width <= INPUT && height <= INPUT)
        val plane = INPUT * INPUT
        val out = FloatArray(3 * plane)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val p = pixels[y * width + x]
                val i = y * INPUT + x
                out[i] = (p and 0xFF).toFloat()
                out[plane + i] = ((p shr 8) and 0xFF).toFloat()
                out[2 * plane + i] = ((p shr 16) and 0xFF).toFloat()
            }
        }
        return out
    }

    /** Visage redressé de 112 × 112 (ARGB) -> entrée de SFace : bleu, vert, rouge (valeurs 0 à 255). */
    fun toRecognizerInput(pixels: IntArray): FloatArray {
        val plane = CROP * CROP
        require(pixels.size == plane)
        val out = FloatArray(3 * plane)
        for (i in 0 until plane) {
            val p = pixels[i]
            out[i] = (p and 0xFF).toFloat()
            out[plane + i] = ((p shr 8) and 0xFF).toFloat()
            out[2 * plane + i] = ((p shr 16) and 0xFF).toFloat()
        }
        return out
    }

    /** Lit une des trois échelles de sortie du détecteur. [bbox] : n × 4, [kps] : n × 10. */
    fun decode(
        stride: Int,
        cls: FloatArray,
        obj: FloatArray,
        bbox: Array<FloatArray>,
        kps: Array<FloatArray>,
        threshold: Float,
    ): List<Detection> {
        val cols = INPUT / stride
        val result = ArrayList<Detection>()
        for (i in cls.indices) {
            val score = sqrt(cls[i].coerceIn(0f, 1f) * obj[i].coerceIn(0f, 1f))
            if (score < threshold) continue
            val r = i / cols
            val c = i % cols
            val cx = (c + bbox[i][0]) * stride
            val cy = (r + bbox[i][1]) * stride
            val w = exp(bbox[i][2].coerceAtMost(10f)) * stride
            val h = exp(bbox[i][3].coerceAtMost(10f)) * stride
            val points = FloatArray(10) { n -> if (n % 2 == 0) (c + kps[i][n]) * stride else (r + kps[i][n]) * stride }
            result += Detection(cx - w / 2, cy - h / 2, w, h, score, points)
        }
        return result
    }

    /** Garde le meilleur de chaque groupe de boîtes qui se chevauchent. */
    fun nms(list: List<Detection>, maxOverlap: Float = 0.3f): List<Detection> {
        val kept = ArrayList<Detection>()
        for (d in list.sortedByDescending { it.score }) {
            if (kept.none { overlap(it, d) > maxOverlap }) kept += d
        }
        return kept
    }

    private fun overlap(a: Detection, b: Detection): Float {
        val w = min(a.x + a.w, b.x + b.w) - max(a.x, b.x)
        val h = min(a.y + a.h, b.y + b.h) - max(a.y, b.y)
        if (w <= 0f || h <= 0f) return 0f
        val inter = w * h
        return inter / (a.w * a.h + b.w * b.h - inter)
    }

    /**
     * Rotation + zoom + décalage qui amènent les 5 repères [src] (x, y, x, y…) sur [dst] (par défaut le modèle de 112 × 112).
     * Résultat : [a, b, tx, c, d, ty] avec x' = a·x + b·y + tx et y' = c·x + d·y + ty. Null si les repères sont tous au même point.
     */
    fun alignment(src: DoubleArray, dst: DoubleArray = TEMPLATE): DoubleArray? {
        val n = src.size / 2
        var msx = 0.0; var msy = 0.0; var mdx = 0.0; var mdy = 0.0
        for (i in 0 until n) {
            msx += src[2 * i]; msy += src[2 * i + 1]; mdx += dst[2 * i]; mdy += dst[2 * i + 1]
        }
        msx /= n; msy /= n; mdx /= n; mdy /= n
        var numA = 0.0; var numB = 0.0; var den = 0.0
        for (i in 0 until n) {
            val sx = src[2 * i] - msx
            val sy = src[2 * i + 1] - msy
            val dx = dst[2 * i] - mdx
            val dy = dst[2 * i + 1] - mdy
            numA += sx * dx + sy * dy
            numB += sx * dy - sy * dx
            den += sx * sx + sy * sy
        }
        if (den < 1e-9) return null
        val a = numA / den
        val b = numB / den
        val tx = mdx - (a * msx - b * msy)
        val ty = mdy - (b * msx + a * msy)
        return doubleArrayOf(a, -b, tx, b, a, ty)
    }

    fun normalized(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x.toDouble() * x
        val norm = sqrt(sum).toFloat()
        return if (norm < 1e-9f) v.copyOf() else FloatArray(v.size) { v[it] / norm }
    }

    /** Ressemblance entre deux empreintes déjà normalisées : de -1 à 1 (1 = identiques). */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        var sum = 0f
        for (i in a.indices) sum += a[i] * b[i]
        return sum
    }

    fun encode(v: FloatArray): String {
        val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (x in v) buffer.putFloat(x)
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    fun decodeEmbedding(text: String): FloatArray? = try {
        val bytes = Base64.getDecoder().decode(text)
        if (bytes.size != EMBEDDING * 4) null
        else {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            FloatArray(EMBEDDING) { buffer.getFloat() }
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}
