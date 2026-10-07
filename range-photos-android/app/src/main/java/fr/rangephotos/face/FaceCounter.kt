package fr.rangephotos.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Compte les visages bien visibles d'une photo, entièrement sur le téléphone
 * (le modèle ML Kit est embarqué, aucune photo n'est envoyée sur internet).
 *
 * Les visages minuscules (personnes au loin sur un sentier) sont ignorés grâce à [MIN_FACE_SIZE] :
 * seules les vraies photos de portrait comptent.
 */
class FaceCounter(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build()
    )

    suspend fun count(uri: Uri): Int = withContext(Dispatchers.IO) {
        val (bitmap, rotation) = decode(uri) ?: return@withContext 0
        try {
            detector.process(InputImage.fromBitmap(bitmap, rotation)).await().size
        } catch (_: Exception) {
            0
        } finally {
            bitmap.recycle()
        }
    }

    fun close() = detector.close()

    /** Décode l'image en réduisant sa taille (suffisant pour détecter les visages, bien plus rapide). */
    private fun decode(uri: Uri): Pair<Bitmap, Int>? {
        val resolver = context.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2

            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null

            val rotation = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            bitmap to rotation
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val MIN_FACE_SIZE = 0.15f
        const val MAX_SIDE = 1024
    }
}
