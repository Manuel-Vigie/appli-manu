package fr.rangephotos.content

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Ce que l'on voit dans une photo : code-barres ? sujets ? (modèles embarqués, rien n'est envoyé sur internet). */
data class Content(val hasBarcode: Boolean, val labels: Map<String, Float>)

class ContentAnalyzer {

    private val barcodeScanner by lazy {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build())
    }
    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
    }
    private var usedBarcode = false
    private var usedLabels = false

    suspend fun analyze(path: String, needBarcode: Boolean, needLabels: Boolean): Content = withContext(Dispatchers.IO) {
        val bitmap = decodeUpright(path) ?: return@withContext Content(false, emptyMap())
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            var barcode = false
            var labels: Map<String, Float> = emptyMap()
            if (needBarcode) {
                usedBarcode = true
                barcode = runCatching { barcodeScanner.process(image).await().isNotEmpty() }.getOrDefault(false)
            }
            if (needLabels) {
                usedLabels = true
                labels = runCatching { labeler.process(image).await().associate { it.text to it.confidence } }.getOrDefault(emptyMap())
            }
            Content(barcode, labels)
        } finally {
            bitmap.recycle()
        }
    }

    fun close() {
        if (usedBarcode) barcodeScanner.close()
        if (usedLabels) labeler.close()
    }

    /** Décode la photo en taille réduite et la remet à l'endroit (orientation EXIF). */
    private fun decodeUpright(path: String): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            null
        } else {
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            val degrees = when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (raw == null || degrees == 0) {
                raw
            } else {
                val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                if (rotated !== raw) raw.recycle()
                rotated
            }
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_SIDE = 1600
    }
}
