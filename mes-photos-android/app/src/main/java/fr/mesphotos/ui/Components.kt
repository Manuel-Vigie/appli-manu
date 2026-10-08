package fr.mesphotos.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.gallery.Thumbs
import fr.mesphotos.storage.PhotoFiles
import java.io.File

/** 12345 → « 12 345 » (espace insécable). */
internal fun spaced(n: Int): String = n.toString().reversed().chunked(3).joinToString(" ").reversed()

// ---- Bandeau, cartes, boutons ---------------------------------------------------------------------

/** Bandeau du haut : dégradé vert, coins arrondis, titre. [top] s'affiche au-dessus du titre (boutons), [content] en dessous. */
@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    top: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(heroColors()))
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f), radius = size.minDimension * 0.9f, center = Offset(size.width * 0.92f, size.height * 0.05f))
                drawCircle(Color.White.copy(alpha = 0.05f), radius = size.minDimension * 0.55f, center = Offset(size.width * 0.04f, size.height))
            }
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (top != null) top()
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
        content()
    }
}

/** Bouton clair posé sur le bandeau (par exemple « Choisir »). */
@Composable
fun HeaderButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = 0.22f), contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Section(container: Color = MaterialTheme.colorScheme.surface, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Gros bouton de la même taille, mais en teinte douce (action importante, mais pas la principale). */
@Composable
fun TonalBigButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(18.dp)) {
        Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

/** Bouton rouge : à réserver à ce qui met des photos de côté ou les efface. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

/** Bouton à contour rouge, pour la suppression définitive. */
@Composable
fun DangerOutlineButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = if (enabled) 1f else 0.4f)),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

/** Message d'information (ou d'erreur) avec, si besoin, un bouton d'action à droite (« Annuler »). */
@Composable
fun NoticeCard(text: String, isError: Boolean, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
    val onContainer = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (isError) Icons.Default.Warning else Icons.Default.Info, contentDescription = null, tint = onContainer)
            Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}

/** Petit bloc de chiffre sur le bandeau (« 1 234 · à ranger »). */
@Composable
fun StatChip(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(value, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp)
    }
}

// ---- Photos : vignettes et sélection --------------------------------------------------------------

/** Rond de sélection : vide, ou rempli avec une coche. [onPhoto] : posé sur une image (contour blanc). */
@Composable
fun SelectMark(selected: Boolean, onPhoto: Boolean, modifier: Modifier = Modifier) {
    val ring = if (onPhoto) Color.White else MaterialTheme.colorScheme.outline
    val empty = if (onPhoto) Color.Black.copy(alpha = 0.25f) else Color.Transparent
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else empty)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
    }
}

/** Vignette d'une photo ou d'une vidéo (lue directement dans le fichier, gardée en mémoire un moment). */
@Composable
fun Thumb(file: File, size: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var unreadable by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        bitmap = Thumbs.load(file, size)
        // Une deuxième tentative règle les échecs passagers (mémoire pleine, carte lente).
        if (bitmap == null) {
            kotlinx.coroutines.delay(600)
            bitmap = Thumbs.load(file, size)
        }
        unreadable = bitmap == null
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize()) }
        if (unreadable && size < 1000) {
            // Le fichier est bien là, mais l'appli ne sait pas en faire une vignette : on le dit au lieu de laisser une case vide.
            Text(
                file.extension.uppercase().take(5).ifEmpty { "?" } + "\nvignette\nillisible",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (PhotoFiles.isVideo(file)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** Case carrée de la grille. En mode choix, un rond de sélection apparaît et la photo se réduit un peu quand elle est choisie. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(file: File, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, label = "inset")
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Thumb(file, 256, Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(if (selected) 8.dp else 0.dp)))
        if (selecting) SelectMark(selected, onPhoto = true, modifier = Modifier.align(Alignment.TopStart).padding(7.dp))
    }
}
