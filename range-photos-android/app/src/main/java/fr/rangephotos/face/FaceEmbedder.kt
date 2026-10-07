package fr.rangephotos.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import fr.rangephotos.people.FaceMatching
import java.nio.FloatBuffer

/**
 * Transforme un visage aligné (112×112) en empreinte de 128 nombres, avec le modèle SFace (ONNX Runtime).
 * Conventions vérifiées sur ordinateur : entrée RGB, valeurs 0 à 255 telles quelles, forme [1, 3, 112, 112].
 */
class FaceEmbedder(context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val model = context.assets.open(MODEL_FILE).use { it.readBytes() }
        val options = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
        session = env.createSession(model, options)
        inputName = session.inputNames.first()
    }

    /** [chip] doit faire exactement 112×112 pixels. */
    @Suppress("UNCHECKED_CAST")
    fun embed(chip: Bitmap): FloatArray {
        val size = FaceAlign.SIZE
        val pixels = IntArray(size * size)
        chip.getPixels(pixels, 0, size, 0, 0, size, size)

        val plane = size * size
        val data = FloatArray(3 * plane)
        for (i in pixels.indices) {
            val p = pixels[i]
            data[i] = ((p shr 16) and 0xFF).toFloat()
            data[plane + i] = ((p shr 8) and 0xFF).toFloat()
            data[2 * plane + i] = (p and 0xFF).toFloat()
        }

        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
            session.run(mapOf(inputName to input)).use { result ->
                val out = (result[0].value as Array<FloatArray>)[0]
                return FaceMatching.normalize(out)
            }
        }
    }

    override fun close() {
        session.close()
    }

    private companion object {
        const val MODEL_FILE = "face_recognition_sface_2021dec_int8.onnx"
    }
}
