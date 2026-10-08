package fr.mesphotos.labels

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Reconnaissance des photos par Google ML Kit, avec le modèle embarqué : tout se passe sur le téléphone, rien n'est envoyé. */
class Labeler : AutoCloseable {
    private val client = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.55f).build())

    /** Les étiquettes (en anglais) et leur confiance pour [bitmap] ; liste vide si la reconnaissance échoue. */
    suspend fun labels(bitmap: Bitmap): List<Pair<String, Float>> = suspendCancellableCoroutine { cont ->
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result.map { it.text to it.confidence }) }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }

    override fun close() = client.close()
}
