package fr.mesphotos.logic

/** Ce qu'on constate sur un fichier photo ou vidéo d'après son début et sa fin (sans l'ouvrir en entier). */
enum class Verdict {
    /** Le début du fichier est celui d'un vrai fichier de ce type (ou le type n'est pas vérifié). */
    OK,
    EMPTY,

    /** Rempli de zéros : copie ratée. Rien à récupérer. */
    ZEROS,

    /** JPEG dont la fin manque : coupé en route. Souvent encore affichable en partie. */
    TRUNCATED,

    /** Image intacte derrière quelques octets en trop ou en moins au début : réparable en copie. */
    REPAIRABLE,

    /** Contenu entièrement brouillé (chiffré) : sans la clé, rien à faire. */
    SCRAMBLED,

    /** Pas le début d'un fichier de ce type, et pas non plus brouillé : cause inconnue. */
    UNKNOWN,
}

/**
 * [offset] et [addSoi] (réparation seulement) : la copie réparée = [offset] premiers octets retirés ;
 * si [addSoi], on remet devant les deux octets FF D8 qui ouvrent un JPEG.
 */
class Health(val verdict: Verdict, val offset: Int = 0, val addSoi: Boolean = false)

object FileHealth {
    const val HEAD = 65536
    const val TAIL = 32

    private val CHECKED = setOf("jpg", "jpeg", "jpe", "jfif", "png", "gif", "webp", "heic", "heif", "avif", "mp4", "m4v", "mov", "3gp")
    private val JPEG = setOf("jpg", "jpeg", "jpe", "jfif")
    private val ATOMS = setOf("ftyp", "moov", "mdat", "free", "wide", "skip", "pnot", "junk")

    fun classify(extension: String, head: ByteArray, tail: ByteArray, length: Long): Health {
        val ext = extension.lowercase()
        if (length == 0L || head.isEmpty()) return Health(Verdict.EMPTY)
        if (ext !in CHECKED) return Health(Verdict.OK)
        if (head.all { it == 0.toByte() }) return Health(Verdict.ZEROS)
        if (hasKnownSignature(head)) {
            val jpeg = at(head, 0) == 0xFF && at(head, 1) == 0xD8
            if (jpeg && ext in JPEG && !endsLikeJpeg(tail)) return Health(Verdict.TRUNCATED)
            return Health(Verdict.OK)
        }
        if (ext in JPEG || ext == "heic") {
            // Un JPEG peut être intact derrière des octets en trop ou abîmés devant.
            findSoi(head)?.let { return Health(Verdict.REPAIRABLE, it, addSoi = false) }
            findBody(head)?.let { return Health(Verdict.REPAIRABLE, it, addSoi = true) }
        }
        return if (looksRandom(head)) Health(Verdict.SCRAMBLED) else Health(Verdict.UNKNOWN)
    }

    private fun at(b: ByteArray, i: Int): Int = if (i in b.indices) b[i].toInt() and 0xFF else -1

    private fun hasKnownSignature(h: ByteArray): Boolean {
        if (at(h, 0) == 0xFF && at(h, 1) == 0xD8 && at(h, 2) == 0xFF) return true
        if (at(h, 0) == 0x89 && at(h, 1) == 0x50 && at(h, 2) == 0x4E && at(h, 3) == 0x47) return true
        val text = String(h.copyOf(minOf(h.size, 16)), Charsets.ISO_8859_1)
        if (text.startsWith("GIF8")) return true
        if (text.startsWith("RIFF") && text.length >= 12 && text.substring(8, 12) == "WEBP") return true
        if (text.length >= 8 && text.substring(4, 8) in ATOMS) return true
        // Un autre vrai format (TIFF/RAW, BMP…) reconnu avec une mauvaise extension : le fichier n'est pas brouillé.
        if (text.startsWith("II*") || text.startsWith("MM\u0000*") || text.startsWith("BM")) return true
        return false
    }

    private fun endsLikeJpeg(tail: ByteArray): Boolean {
        for (i in 0 until tail.size - 1) if (at(tail, i) == 0xFF && at(tail, i + 1) == 0xD9) return true
        return false
    }

    private fun matches(h: ByteArray, pos: Int, pattern: IntArray): Boolean {
        for (k in pattern.indices) if (at(h, pos + k) != pattern[k]) return false
        return true
    }

    /** Début d'un JPEG (FF D8 FF + un repère connu) plus loin que l'octet 0. */
    private fun findSoi(h: ByteArray): Int? {
        val next = setOf(0xE0, 0xE1, 0xE2, 0xDB, 0xEE, 0xED, 0xC0, 0xC2, 0xC4, 0xFE)
        for (i in 1 until h.size - 3) {
            if (at(h, i) == 0xFF && at(h, i + 1) == 0xD8 && at(h, i + 2) == 0xFF && at(h, i + 3) in next) return i
        }
        return null
    }

    /** Premier morceau reconnaissable de l'intérieur d'un JPEG (tables, en-tête de l'image) : si le tout début est abîmé, c'est là qu'on repart. */
    private fun findBody(h: ByteArray): Int? {
        val patterns = listOf(
            intArrayOf(0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46), // JFIF
            intArrayOf(0xFF, 0xE1, -2, -2, 0x45, 0x78, 0x69, 0x66),     // Exif (longueur quelconque)
            intArrayOf(0xFF, 0xDB, 0x00, 0x43, 0x00),                    // table de quantification
            intArrayOf(0xFF, 0xDB, 0x00, 0x84, 0x00),
            intArrayOf(0xFF, 0xC0, 0x00, 0x11, 0x08),                    // en-tête de l'image
            intArrayOf(0xFF, 0xC2, 0x00, 0x11, 0x08),
            intArrayOf(0xFF, 0xC4, 0x00, 0x1F, 0x00),                    // table de Huffman
            intArrayOf(0xFF, 0xC4, 0x00, 0xB5, 0x10),
        )
        var best: Int? = null
        for (p in patterns) {
            for (i in 1 until h.size - p.size) {
                var ok = true
                for (k in p.indices) {
                    if (p[k] >= 0 && at(h, i + k) != p[k]) { ok = false; break }
                }
                if (ok) {
                    if (best == null || i < best) best = i
                    break
                }
            }
        }
        return best
    }

    /** Octets répartis presque parfaitement au hasard (khi-deux proche de 255) : typique d'un chiffrement. */
    private fun looksRandom(h: ByteArray): Boolean {
        if (h.size < 4096) return false
        val counts = IntArray(256)
        for (b in h) counts[b.toInt() and 0xFF]++
        val expected = h.size / 256.0
        var chi = 0.0
        for (c in counts) chi += (c - expected) * (c - expected) / expected
        return chi < 400.0
    }
}
