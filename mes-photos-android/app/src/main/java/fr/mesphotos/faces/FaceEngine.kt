package fr.mesphotos.faces

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import java.nio.FloatBuffer

/** Trouve les visages d'une image et calcule leur empreinte, avec YuNet et SFace, sur le téléphone. À fermer ([close]) après usage. */
class FaceEngine(context: Context) : AutoCloseable {

    /** [box] : x, y, largeur, hauteur en fractions de l'image (0 à 1). [crop] : visage redressé (seulement si demandé). */
    class Found(val box: FloatArray, val embedding: FloatArray, val crop: Bitmap?)

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val detector: OrtSession
    private val recognizer: OrtSession
    private val detectorInput: String
    private val recognizerInput: String

    init {
        val options = OrtSession.SessionOptions()
        options.setIntraOpNumThreads(2)
        detector = env.createSession(context.assets.open(DETECTOR).use { it.readBytes() }, options)
        recognizer = env.createSession(context.assets.open(RECOGNIZER).use { it.readBytes() }, options)
        detectorInput = detector.inputNames.iterator().next()
        recognizerInput = recognizer.inputNames.iterator().next()
    }

    /** Les visages de [bitmap] (déjà dans le bon sens), du plus sûr au moins sûr. À appeler hors du fil principal. */
    fun analyze(bitmap: Bitmap, withCrops: Boolean = false): List<Found> {
        val size = FaceMath.INPUT
        val k = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        val w = Math.round(bitmap.width * k).coerceIn(1, size)
        val h = Math.round(bitmap.height * k).coerceIn(1, size)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== bitmap) scaled.recycle()

        val detections = detect(FaceMath.toDetectorInput(pixels, w, h))
            .filter { it.w >= MIN_FACE && it.h >= MIN_FACE }
            .take(MAX_FACES)

        val found = ArrayList<Found>()
        for (d in detections) {
            // Repères ramenés aux points de l'image d'origine (plus grande, donc plus nette).
            val src = DoubleArray(10) { (d.points[it] / k).toDouble() }
            val m = FaceMath.alignment(src) ?: continue
            val crop = Bitmap.createBitmap(FaceMath.CROP, FaceMath.CROP, Bitmap.Config.ARGB_8888)
            try {
                val matrix = Matrix()
                matrix.setValues(floatArrayOf(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(), m[5].toFloat(), 0f, 0f, 1f))
                Canvas(crop).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
                val embedding = embed(crop)
                val box = floatArrayOf(
                    (d.x / k / bitmap.width).coerceIn(0f, 1f), (d.y / k / bitmap.height).coerceIn(0f, 1f),
                    (d.w / k / bitmap.width).coerceIn(0f, 1f), (d.h / k / bitmap.height).coerceIn(0f, 1f),
                )
                found += Found(box, embedding, if (withCrops) crop else null)
            } finally {
                if (!withCrops) crop.recycle()
            }
        }
        return found
    }

    private fun detect(input: FloatArray): List<Detection> {
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 3, FaceMath.INPUT.toLong(), FaceMath.INPUT.toLong()))
        try {
            val result = detector.run(mapOf(detectorInput to tensor))
            try {
                val all = ArrayList<Detection>()
                for (stride in FaceMath.STRIDES) {
                    val cls = column(result, "cls_$stride")
                    val obj = column(result, "obj_$stride")
                    val bbox = rows(result, "bbox_$stride")
                    val kps = rows(result, "kps_$stride")
                    all += FaceMath.decode(stride, cls, obj, bbox, kps, DETECTION_THRESHOLD)
                }
                return FaceMath.nms(all)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun rows(result: OrtSession.Result, name: String): Array<FloatArray> =
        (result.get(name).get().value as Array<Array<FloatArray>>)[0]

    private fun column(result: OrtSession.Result, name: String): FloatArray {
        val r = rows(result, name)
        return FloatArray(r.size) { r[it][0] }
    }

    private fun embed(crop: Bitmap): FloatArray {
        val pixels = IntArray(FaceMath.CROP * FaceMath.CROP)
        crop.getPixels(pixels, 0, FaceMath.CROP, 0, 0, FaceMath.CROP, FaceMath.CROP)
        val data = FaceMath.toRecognizerInput(pixels)
        val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(data), longArrayOf(1, 3, FaceMath.CROP.toLong(), FaceMath.CROP.toLong()))
        try {
            val result = recognizer.run(mapOf(recognizerInput to tensor))
            try {
                @Suppress("UNCHECKED_CAST")
                val out = (result[0].value as Array<FloatArray>)[0]
                return FaceMath.normalized(out)
            } finally {
                result.close()
            }
        } finally {
            tensor.close()
        }
    }

    override fun close() {
        try {
            detector.close()
            recognizer.close()
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val DETECTOR = "faces/yunet.onnx"
        const val RECOGNIZER = "faces/sface.onnx"
        const val DETECTION_THRESHOLD = 0.75f

        /** Visages plus petits que ça (en points sur 640) : trop flous pour être reconnus. */
        const val MIN_FACE = 28f
        const val MAX_FACES = 20
    }
}
