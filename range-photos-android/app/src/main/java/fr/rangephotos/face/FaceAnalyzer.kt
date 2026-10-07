package fr.rangephotos.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import fr.rangephotos.model.FaceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Trouve les visages d'une photo et calcule l'empreinte de chacun, entièrement sur le téléphone
 * (modèles embarqués, aucune photo n'est envoyée sur internet).
 *
 * Les visages minuscules (personnes au loin sur un sentier) sont ignorés. Les visages de profil
 * ou sans repères clairs sont comptés mais n'ont pas d'empreinte (trop peu fiables pour reconnaître).
 */
class FaceAnalyzer(private val context: Context) {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build()
    )
    private val embedder by lazy { FaceEmbedder(context) }

    suspend fun analyze(uri: Uri): List<FaceInfo> = withContext(Dispatchers.IO) {
        val bitmap = decodeUpright(uri) ?: return@withContext emptyList()
        try {
            val found = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
            found.map { describe(bitmap, it) }
        } catch (_: Exception) {
            emptyList()
        } finally {
            bitmap.recycle()
        }
    }

    fun close() {
        detector.close()
        runCatching { embedder.close() }
    }

    private fun describe(bitmap: Bitmap, face: Face): FaceInfo {
        val box = face.boundingBox
        val area = (box.width().toFloat() * box.height()) / (bitmap.width.toFloat() * bitmap.height)

        fun point(type: Int): FloatArray? =
            face.getLandmark(type)?.position?.let { floatArrayOf(it.x, it.y) }

        val eye1 = point(FaceLandmark.LEFT_EYE)
        val eye2 = point(FaceLandmark.RIGHT_EYE)
        val mouth1 = point(FaceLandmark.MOUTH_LEFT)
        val mouth2 = point(FaceLandmark.MOUTH_RIGHT)
        if (eye1 == null || eye2 == null || mouth1 == null || mouth2 == null || abs(face.headEulerAngleY) > MAX_YAW) {
            return FaceInfo(area, null, null)
        }

        val points = FaceAlign.orderLandmarks(eye1, eye2, mouth1, mouth2)
        if (FaceAlign.eyeDistance(points) < MIN_EYE_DISTANCE) return FaceInfo(area, null, null)
        val similarity = FaceAlign.estimate(points) ?: return FaceInfo(area, null, null)

        val chip = Bitmap.createBitmap(FaceAlign.SIZE, FaceAlign.SIZE, Bitmap.Config.ARGB_8888)
        return try {
            val matrix = Matrix().apply { setValues(similarity.matrixValues()) }
            Canvas(chip).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
            val embedding = embedder.embed(chip)
            val jpeg = ByteArrayOutputStream().also { chip.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
            FaceInfo(area, embedding, jpeg)
        } catch (_: Exception) {
            FaceInfo(area, null, null)
        } finally {
            chip.recycle()
        }
    }

    /** Décode la photo en taille réduite et la remet à l'endroit (orientation EXIF). */
    private fun decodeUpright(uri: Uri): Bitmap? = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            val degrees = resolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
            if (raw == null || degrees == 0) {
                raw
            } else {
                val rotated = Bitmap.createBitmap(
                    raw, 0, 0, raw.width, raw.height,
                    Matrix().apply { postRotate(degrees.toFloat()) }, true,
                )
                if (rotated !== raw) raw.recycle()
                rotated
            }
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MIN_FACE_SIZE = 0.15f
        const val MAX_SIDE = 1600
        const val MAX_YAW = 40f
        const val MIN_EYE_DISTANCE = 20f
    }
}
