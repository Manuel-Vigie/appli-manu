package fr.mesphotos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Photo qu'on peut agrandir : pincer avec deux doigts, ou toucher deux fois. Zoomée, on la fait glisser avec un doigt.
 * Pas zoomée, un doigt seul laisse la main au défilement d'une photo à l'autre. [onZoomChange] dit si la photo est agrandie.
 */
@Composable
fun ZoomableImage(bitmap: Bitmap, contentDescription: String?, onZoomChange: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    /** Garde l'image dans le cadre : on ne peut pas la tirer au-delà de ses bords. */
    fun limited(o: Offset, s: Float): Offset {
        if (s <= 1f || !o.isFinite) return Offset.Zero
        val maxX = size.width * (s - 1f) / 2f
        val maxY = size.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { tap ->
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        val c = tap - Offset(size.width / 2f, size.height / 2f)
                        scale = DOUBLE_TAP_ZOOM
                        offset = limited(c - c * DOUBLE_TAP_ZOOM, DOUBLE_TAP_ZOOM)
                    }
                    onZoomChange(scale > 1f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // Deux doigts : on zoome. Un doigt : on ne déplace que si la photo est déjà agrandie.
                        if (fingers >= 1 && (fingers >= 2 || scale > 1f)) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            // Le centre n'existe pas quand un doigt vient d'être levé : on ne touche alors à rien.
                            val centroid = event.calculateCentroid(useCurrent = false)
                            if (centroid.isSpecified && zoom.isFinite() && pan.isFinite) {
                                val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                val c = centroid - Offset(size.width / 2f, size.height / 2f)
                                offset = limited(c - (c - offset) * (newScale / scale) + pan, newScale)
                                scale = newScale
                                onZoomChange(scale > 1f)
                                if (scale > 1f || zoom != 1f) {
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale <= 1.01f) {
                        scale = 1f
                        offset = Offset.Zero
                        onZoomChange(false)
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    ) {
        Image(bitmap.asImageBitmap(), contentDescription = contentDescription, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    }
}
