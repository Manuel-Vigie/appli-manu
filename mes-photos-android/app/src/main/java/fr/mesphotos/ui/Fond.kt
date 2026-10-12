package fr.mesphotos.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File

/** La photo choisie comme fond du haut de la page d'accueil : une copie réduite gardée dans l'appli (la photo d'origine ne bouge pas). */
object Fond {
    fun file(context: Context): File? = File(context.filesDir, "fond.jpg").takeIf { it.isFile && it.length() > 0 }

    fun remove(context: Context) {
        File(context.filesDir, "fond.jpg").delete()
    }

    /** Copie réduite (1600 px au plus) de la photo [uri], tournée dans le bon sens. Retourne vrai si ça a marché. */
    fun save(context: Context, uri: Uri): Boolean = try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1600 && bounds.outHeight / (sample * 2) >= 1600) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        if (bitmap == null) false else {
            val degrees = resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            val upright = if (degrees == 0f) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
            val tmp = File(context.filesDir, "fond.tmp")
            tmp.outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val target = File(context.filesDir, "fond.jpg")
            target.delete()
            tmp.renameTo(target)
        }
    } catch (e: Throwable) {
        false
    }
}
