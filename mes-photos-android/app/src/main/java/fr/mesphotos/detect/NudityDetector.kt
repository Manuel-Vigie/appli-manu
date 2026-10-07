package fr.mesphotos.detect

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import fr.mesphotos.gallery.Thumbs
import java.io.File
import java.nio.FloatBuffer

/** Fait tourner le modèle NudeNet sur une photo, sur le téléphone. À fermer ([close]) après usage. */
class NudityDetector(context: Context) : AutoCloseable {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val bytes = context.assets.open(NudityModel.ASSET).use { it.readBytes() }
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2) // laisse un peu de souffle au téléphone
        session = env.createSession(bytes, options)
        inputName = session.inputNames.iterator().next()
    }

    /** Score de nudité (0 à 1) d'une photo, ou null si la photo n'a pas pu être lue. */
    suspend fun score(file: File): Float? {
        val bitmap = Thumbs.loadUncached(file, NudityModel.SIZE) ?: return null
        val size = NudityModel.SIZE
        val square = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        try {
            // Même préparation que le modèle d'origine : image réduite à 320 de large au plus, posée en haut à gauche sur fond noir.
            val k = size.toFloat() / maxOf(bitmap.width, bitmap.height)
            val w = (bitmap.width * k).toInt().coerceIn(1, size)
            val h = (bitmap.height * k).toInt().coerceIn(1, size)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            val canvas = Canvas(square)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(scaled, 0f, 0f, null)
            if (scaled !== bitmap) scaled.recycle()

            val pixels = IntArray(size * size)
            square.getPixels(pixels, 0, size, 0, 0, size, size)
            val data = NudityModel.toTensor(pixels)
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong()))
            try {
                val result = session.run(mapOf(inputName to tensor))
                try {
                    @Suppress("UNCHECKED_CAST")
                    val output = result[0].value as Array<Array<FloatArray>>
                    return NudityModel.score(output)
                } finally {
                    result.close()
                }
            } finally {
                tensor.close()
            }
        } finally {
            bitmap.recycle()
            square.recycle()
        }
    }

    override fun close() {
        try {
            session.close()
        } catch (_: Exception) {
        }
    }
}
