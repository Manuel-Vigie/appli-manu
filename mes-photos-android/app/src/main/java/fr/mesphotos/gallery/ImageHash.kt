package fr.mesphotos.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import fr.mesphotos.storage.PhotoFiles
import java.io.File

/** Empreinte « visuelle » d'une photo : deux photos qui se ressemblent (même image réenregistrée, redimensionnée…) ont presque la même. */
/** [quality] : netteté × bonne exposition (plus grand = meilleure photo) ; 0 si non calculée. */
class ImageInfo(val hash: Long, val width: Int, val height: Int, val quality: Double = 0.0, val taken: Long = 0L, val hist: FloatArray? = null)

object ImageHash {

    /** Empreinte de différence (dHash 64 bits) : l'image réduite à 9×8 points gris, chaque bit dit si un point est plus clair que son voisin. */
    fun of(file: File): ImageInfo? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 400 && bounds.outHeight / (sample * 2) >= 400) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bitmap == null) null else {
                val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
                var hash = 0L
                var bit = 0
                for (y in 0 until 8) for (x in 0 until 8) {
                    if (gray(small.getPixel(x, y)) > gray(small.getPixel(x + 1, y))) hash = hash or (1L shl bit)
                    bit++
                }
                if (small !== bitmap) small.recycle()
                val quality = qualityOf(bitmap)
                val hist = histogramOf(bitmap)
                bitmap.recycle()
                ImageInfo(hash, bounds.outWidth, bounds.outHeight, quality, takenAt(file), hist)
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /**
     * Netteté (variance du laplacien sur une image réduite à 400 px de large, donc comparable d'une photo à l'autre)
     * multipliée par une note d'exposition : une photo trop sombre ou trop claire vaut moins.
     */
    private fun qualityOf(source: Bitmap): Double {
        val w = 400
        val h = (source.height * w / source.width.toDouble()).toInt().coerceAtLeast(3)
        val b = Bitmap.createScaledBitmap(source, w, h, true)
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)
        if (b !== source) b.recycle()
        val g = DoubleArray(w * h) { gray(px[it]) }
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val lap = 4 * g[i] - g[i - 1] - g[i + 1] - g[i - w] - g[i + w]
            sum += lap
            sumSq += lap * lap
            n++
        }
        if (n == 0) return 0.0
        val sharp = sumSq / n - (sum / n) * (sum / n)
        val mean = g.average()
        val exposure = (1.0 - Math.abs(mean - 125.0) / 125.0).coerceIn(0.15, 1.0)
        return sharp * exposure
    }

    /** Répartition des couleurs (64 cases) : reste proche d'une prise de vue à l'autre même si le cadrage change un peu. */
    private fun histogramOf(source: Bitmap): FloatArray {
        val b = Bitmap.createScaledBitmap(source, 48, 48, true)
        val h = FloatArray(64)
        for (y in 0 until 48) for (x in 0 until 48) {
            val c = b.getPixel(x, y)
            h[(Color.red(c) shr 6) * 16 + (Color.green(c) shr 6) * 4 + (Color.blue(c) shr 6)] += 1f / 2304f
        }
        if (b !== source) b.recycle()
        return h
    }

    private val NAME_DATE = Regex("""(20\d{2})(\d{2})(\d{2})[_-]?(\d{2})(\d{2})(\d{2})""")

    /** Heure de la prise de vue : date EXIF, sinon date lue dans le nom du fichier, sinon date du fichier. */
    private fun takenAt(file: File): Long {
        try {
            @Suppress("DEPRECATION")
            val d = android.media.ExifInterface(file.absolutePath).getAttribute(android.media.ExifInterface.TAG_DATETIME_ORIGINAL)
            if (d != null) {
                val t = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US).parse(d)?.time
                if (t != null) return t
            }
        } catch (e: Exception) { }
        NAME_DATE.find(file.name)?.let { m ->
            try {
                val v = m.groupValues.drop(1).map { it.toInt() }
                val cal = java.util.Calendar.getInstance()
                cal.clear(); cal.set(v[0], v[1] - 1, v[2], v[3], v[4], v[5])
                return cal.timeInMillis
            } catch (e: Exception) { }
        }
        return file.lastModified()
    }

    /** Ressemblance des couleurs de 0 à 1. */
    fun colorMatch(a: ImageInfo, b: ImageInfo): Double {
        val x = a.hist ?: return 0.0
        val y = b.hist ?: return 0.0
        var s = 0.0
        for (i in x.indices) s += minOf(x[i], y[i])
        return s
    }

    private fun gray(c: Int): Double = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)

    /**
     * Deux photos se ressemblent si leurs empreintes diffèrent de très peu de bits et (par défaut) si leurs proportions sont les mêmes.
     * Mode « large » ([checkRatio] faux, [maxBits] plus grand) : deux prises de vue d'une même scène, cadrées un peu différemment.
     */
    fun similar(a: ImageInfo, b: ImageInfo, maxBits: Int = 3, checkRatio: Boolean = true): Boolean {
        if (checkRatio) {
            val ra = a.width.toDouble() / a.height
            val rb = b.width.toDouble() / b.height
            if (Math.abs(ra - rb) / ra > 0.03) return false
        }
        return java.lang.Long.bitCount(a.hash xor b.hash) <= maxBits
    }

    /** Les empreintes déjà calculées sont gardées dans un fichier (chemin, taille, date) : la 2e recherche est bien plus rapide. */
    class Cache(private val store: File) {
        private val map = HashMap<String, ImageInfo>()
        private var dirty = false

        init {
            runCatching {
                if (store.isFile) store.forEachLine { line ->
                    val p = line.split('\t')
                    if (p.size == 8 && p[7] == "v3") map[p[0]] = ImageInfo(p[1].toLong(), p[2].toInt(), p[3].toInt(), p[4].toDouble(), p[5].toLong(), p[6].split(',').map { it.toFloat() }.toFloatArray())
                }
            }
        }

        private fun key(f: File) = f.absolutePath + "|" + f.length() + "|" + f.lastModified()

        fun get(file: File): ImageInfo? {
            if (PhotoFiles.isVideo(file)) return null
            val k = key(file)
            map[k]?.let { return it }
            val info = of(file) ?: return null
            map[k] = info
            dirty = true
            return info
        }

        fun save(keep: Collection<File>) {
            if (!dirty) return
            runCatching {
                val alive = keep.map { key(it) }.toHashSet()
                store.writeText(map.filterKeys { it in alive }.entries.joinToString("\n") { (k, v) -> "$k\t${v.hash}\t${v.width}\t${v.height}\t${v.quality}\t${v.taken}\t${v.hist?.joinToString(",") ?: ""}\tv3" } + "\n")
            }
        }
    }
}
