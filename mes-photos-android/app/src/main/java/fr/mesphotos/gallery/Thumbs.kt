package fr.mesphotos.gallery

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import fr.mesphotos.storage.PhotoFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** Petites images pour la bibliothèque : lues directement dans les fichiers, gardées en mémoire un moment. */
object Thumbs {

    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt().coerceIn(8 * 1024 * 1024, 96 * 1024 * 1024),
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /** Quelques lectures à la fois : le défilement reste fluide et la mémoire du téléphone aussi. */
    private val gate = Semaphore(3)

    suspend fun load(file: File, maxSize: Int): Bitmap? {
        val key = file.absolutePath + "|" + maxSize + "|" + file.lastModified()
        cache.get(key)?.let { return it }
        val bitmap = gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    decode(file, maxSize)
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
        }
        if (bitmap != null) cache.put(key, bitmap)
        return bitmap
    }

    /** Comme [load], mais sans garder l'image en mémoire : pour parcourir des milliers de photos d'affilée. À recycler par l'appelant. */
    suspend fun loadUncached(file: File, maxSize: Int): Bitmap? =
        gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    decode(file, maxSize)
                } catch (e: Exception) {
                    null
                } catch (e: OutOfMemoryError) {
                    null
                }
            }
        }

    /** Essaie plusieurs lecteurs, du plus simple au plus tolérant : un format que l'un ne lit pas est souvent lu par l'autre. */
    private fun decode(file: File, maxSize: Int): Bitmap? {
        if (PhotoFiles.isVideo(file)) {
            return attempt { videoFrame(file, maxSize) } ?: attempt { videoThumbnail(file, maxSize) }
        }
        return attempt { decodeClassic(file, maxSize) }
            ?: attempt { decodeModern(file, maxSize) }
            ?: attempt { embeddedThumbnail(file) }
            ?: attempt { systemThumbnail(file, maxSize) }
    }

    private inline fun attempt(block: () -> Bitmap?): Bitmap? =
        try {
            block()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }

    private fun decodeClassic(file: File, maxSize: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val degrees = rotationOf(file)
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Lecteur plus récent d'Android (HEIC, AVIF, WebP animé, formats bizarres) ; il tourne lui-même les photos. */
    private fun decodeModern(file: File, maxSize: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val k = maxSize.toFloat() / maxOf(info.size.width, info.size.height)
            if (k < 1f) decoder.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
        }
    }

    /** La petite image cachée dans beaucoup de fichiers JPEG (utile si le reste du fichier est abîmé). */
    private fun embeddedThumbnail(file: File): Bitmap? {
        val exif = ExifInterface(file.absolutePath)
        if (!exif.hasThumbnail()) return null
        return exif.thumbnailBitmap
    }

    private fun systemThumbnail(file: File, maxSize: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return ThumbnailUtils.createImageThumbnail(file, Size(maxSize, maxSize), null)
    }

    private fun videoThumbnail(file: File, maxSize: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return ThumbnailUtils.createVideoThumbnail(file, Size(maxSize, maxSize), null)
    }

    private fun rotationOf(file: File): Int =
        try {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }

    private fun videoFrame(file: File, maxSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime()
                ?: return null
            val k = maxSize.toFloat() / maxOf(frame.width, frame.height)
            if (k >= 1f) return frame
            return Bitmap.createScaledBitmap(
                frame,
                (frame.width * k).toInt().coerceAtLeast(1),
                (frame.height * k).toInt().coerceAtLeast(1),
                true,
            )
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // rien à faire
            }
        }
    }
}
