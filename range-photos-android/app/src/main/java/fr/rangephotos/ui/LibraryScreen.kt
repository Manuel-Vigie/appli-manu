package fr.rangephotos.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fr.rangephotos.library.Album
import fr.rangephotos.library.Library
import fr.rangephotos.library.Shelf
import fr.rangephotos.library.Thumbs
import fr.rangephotos.storage.PhotoFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

// ---- Bibliothèque : des étagères d'albums en 3D ----------------------------------------------------------

private val WallTop = Color(0xFF2A3A33)
private val Night = Color(0xFF14201B)
private val NightDeep = Color(0xFF0B1210)
private val Cream = Color(0xFFF6EBD6)
private val WoodLight = Color(0xFFB98A55)
private val WoodDark = Color(0xFF6E4A28)
private val AlbumW = 124.dp
private val AlbumH = 156.dp

private val SpineColors = listOf(
    Color(0xFFC0392B), Color(0xFF2E86AB), Color(0xFF3B9C6B), Color(0xFFE0A030),
    Color(0xFF8E5BB5), Color(0xFFD9673B), Color(0xFF3F6FB5), Color(0xFF2E8B8B),
)

private fun spineOf(album: Album): Color =
    SpineColors[(album.dir.absolutePath.hashCode() and 0x7fffffff) % SpineColors.size]

/** Image chargée en arrière-plan (null tant qu'elle n'est pas prête). */
@Composable
private fun rememberThumb(file: File?, size: Int): ImageBitmap? {
    var image by remember(file, size) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(file, size) {
        image = if (file == null) null else Thumbs.load(file, size)?.asImageBitmap()
    }
    return image
}

@Composable
fun LibraryScreen(root: File, onOrganize: () -> Unit) {
    var shelves by remember(root) { mutableStateOf<List<Shelf>?>(null) }
    LaunchedEffect(root) { shelves = withContext(Dispatchers.IO) { Library.scan(root) } }
    var open by remember { mutableStateOf<Album?>(null) }
    var viewer by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = viewer != null) { viewer = null }
    BackHandler(enabled = viewer == null && open != null) { open = null }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = open, label = "bibliotheque") { album ->
            if (album == null) {
                ShelvesScreen(shelves, onOrganize, onOpen = { open = it })
            } else {
                AlbumScreen(album, onBack = { open = null }, onPhoto = { viewer = it })
            }
        }
        val album = open
        val index = viewer
        if (album != null && index != null) PhotoViewer(album, index, onClose = { viewer = null })
    }
}

@Composable
private fun ShelvesScreen(shelves: List<Shelf>?, onOrganize: () -> Unit, onOpen: (Album) -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(WallTop, Night, NightDeep)))) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { LibraryHeader(shelves, onOrganize) }
            if (shelves == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Cream)
                    }
                }
            } else if (shelves.isEmpty()) {
                item {
                    Text(
                        "Aucun album pour l'instant. Touchez « Ranger mes photos » : vos albums apparaîtront ici, sur les étagères.",
                        color = Cream,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            } else {
                items(shelves) { shelf -> ShelfRow(shelf, onOpen) }
            }
        }
    }
}

@Composable
private fun LibraryHeader(shelves: List<Shelf>?, onOrganize: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Ma bibliothèque", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Cream)
        if (shelves != null && shelves.isNotEmpty()) {
            val albums = shelves.sumOf { it.albums.size }
            val files = shelves.sumOf { it.fileCount }
            Text(
                "$albums album" + (if (albums > 1) "s" else "") + " · $files photos et vidéos. Faites défiler les étagères, touchez un album.",
                color = Cream.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onOrganize,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Cream),
        ) { Text("Ranger mes photos") }
    }
}

@Composable
private fun ShelfRow(shelf: Shelf, onOpen: (Album) -> Unit) {
    val listState = rememberLazyListState()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shelf.title,
                color = Cream,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                shelf.albums.size.toString() + " album" + (if (shelf.albums.size > 1) "s" else ""),
                color = Cream.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Box(Modifier.fillMaxWidth().height(AlbumH + 34.dp)) {
            // Fond de l'étagère
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0xFF241B13), Color(0xFF3A2A1B)))))
            // Planche de bois
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(20.dp)
                    .background(Brush.verticalGradient(listOf(WoodLight, WoodDark))),
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).fillMaxWidth().height(2.dp).background(Color(0x55FFFFFF)))
            LazyRow(
                state = listState,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 14.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                items(shelf.albums.size) { i ->
                    val album = shelf.albums[i]
                    AlbumCard(album, listState, i) { onOpen(album) }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, listState: LazyListState, index: Int, onClick: () -> Unit) {
    val density = LocalDensity.current.density
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pop by animateFloatAsState(
        targetValue = if (pressed) 1.07f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pop",
    )
    val spine = remember(album) { spineOf(album) }
    val cover = rememberThumb(album.cover, 360)
    val shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)

    Box(
        Modifier
            .size(AlbumW, AlbumH)
            .graphicsLayer {
                // L'album se tourne en 3D selon sa place sur l'étagère (au centre : de face).
                val info = listState.layoutInfo
                val span = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
                val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val me = info.visibleItemsInfo.firstOrNull { it.index == index }
                val d = if (me == null) 0f else (((me.offset + me.size / 2f) - mid) / span).coerceIn(-1f, 1f)
                rotationY = -d * 42f
                cameraDistance = 16f * density
                val s = pop * (1f - abs(d) * 0.1f)
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        // Ombre sur la planche
        Box(
            Modifier.align(Alignment.BottomCenter).offset(y = 6.dp).fillMaxWidth(0.92f).height(10.dp)
                .background(Color(0x66000000), CircleShape),
        )
        // Reliure colorée
        Box(Modifier.fillMaxSize().shadow(6.dp, shape).clip(shape).background(spine)) {
            Box(Modifier.fillMaxHeight().width(9.dp).background(Color(0x33000000)))
            Box(Modifier.padding(start = 12.dp).fillMaxHeight().width(1.dp).background(Color(0x44FFFFFF)))
            // Photo de couverture, bordure blanche
            Box(
                Modifier.fillMaxSize().padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color.White)
                    .padding(3.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFD9DEDB)),
            ) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = album.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text("📷", fontSize = 26.sp, modifier = Modifier.align(Alignment.Center))
                }
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(
                        album.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    album.count.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(spine, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// ---- Un album ouvert : les photos, façon album photo -----------------------------------------------------

@Composable
private fun AlbumScreen(album: Album, onBack: () -> Unit, onPhoto: (Int) -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF1E6D0))) {
        Row(
            Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(WoodLight, WoodDark)))
                .statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Étagères", color = Color.White, fontWeight = FontWeight.Bold) }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    album.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = (if (album.subtitle.isNotEmpty()) album.subtitle + " · " else "") +
                    album.count.toString() + " fichier" + (if (album.count > 1) "s" else "")
                Text(detail, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(album.files.size) { i -> PhotoTile(album.files[i], i, onPhoto) }
        }
    }
}

@Composable
private fun PhotoTile(file: File, index: Int, onPhoto: (Int) -> Unit) {
    val bitmap = rememberThumb(file, 300)
    val tilt = remember(file) { (((file.name.hashCode() and 0xff) / 255f) - 0.5f) * 5f }
    val video = remember(file) { PhotoFiles.isVideo(file) }
    Box(
        Modifier.aspectRatio(1f)
            .graphicsLayer { rotationZ = tilt }
            .shadow(3.dp, RoundedCornerShape(3.dp))
            .background(Color.White)
            .padding(3.dp)
            .background(Color(0xFFD9DEDB))
            .clickable { onPhoto(index) },
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = file.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (video) {
            Text(
                "▶",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0x99000000), CircleShape).padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

// ---- Photo en grand, à feuilleter ------------------------------------------------------------------------

@Composable
private fun PhotoViewer(album: Album, start: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = start.coerceIn(0, album.files.size - 1)) { album.files.size }
    Box(Modifier.fillMaxSize().background(Color(0xF5000000))) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            val file = album.files[page]
            val bitmap = rememberThumb(file, 1600)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = file.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
                if (PhotoFiles.isVideo(file)) {
                    Button(onClick = { playVideo(context, file) }) { Text("▶ Lire la vidéo") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                (pager.currentPage + 1).toString() + " / " + album.files.size,
                color = Color.White,
                modifier = Modifier.weight(1f).padding(start = 12.dp),
            )
            TextButton(onClick = onClose) { Text("✕ Fermer", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

private fun playVideo(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (e: Exception) {
        Toast.makeText(context, "Impossible d'ouvrir cette vidéo.", Toast.LENGTH_LONG).show()
    }
}
