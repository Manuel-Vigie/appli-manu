package fr.mesphotos.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import fr.mesphotos.storage.PhotoFiles
import java.io.File

/** Empreinte « visuelle » d'une photo : deux photos qui se ressemblent (même image réenregistrée, redimensionnée…) ont presque la même. */
class ImageInfo(val hash: Long, val width: Int, val height: Int)

object ImageHash {

    /** Empreinte de différence (dHash 64 bits) : l'image réduite à 9×8 points gris, chaque bit dit si un point est plus clair que son voisin. */
    fun of(file: File): ImageInfo? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
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
                bitmap.recycle()
                ImageInfo(hash, bounds.outWidth, bounds.outHeight)
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun gray(c: Int): Double = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)

    /** Deux photos se ressemblent si leurs empreintes diffèrent de très peu de bits et si leurs proportions sont les mêmes. */
    fun similar(a: ImageInfo, b: ImageInfo, maxBits: Int = 3): Boolean {
        val ra = a.width.toDouble() / a.height
        val rb = b.width.toDouble() / b.height
        if (Math.abs(ra - rb) / ra > 0.03) return false
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
                    if (p.size == 5) map[p[0]] = ImageInfo(p[1].toLong(), p[2].toInt(), p[3].toInt())
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
                store.writeText(map.filterKeys { it in alive }.entries.joinToString("\n") { (k, v) -> "$k\t${v.hash}\t${v.width}\t${v.height}\tv1" } + "\n")
            }
        }
    }
}
