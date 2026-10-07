package fr.mesphotos.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fr.mesphotos.gallery.FolderItem
import fr.mesphotos.gallery.Thumbs
import fr.mesphotos.organize.TrashEntry
import fr.mesphotos.storage.PhotoFiles
import java.io.File

// ---- Galerie : dossiers, photos, choix multiple ---------------------------------------------------

private fun describeSelection(files: Int, folders: Int, total: Int): String {
    val parts = ArrayList<String>()
    if (folders > 0) parts += if (folders > 1) "$folders dossiers" else "1 dossier"
    if (files > 0) parts += if (files > 1) "$files photos ou vidéos" else "1 photo ou vidéo"
    val text = parts.joinToString(" et ")
    return if (folders > 0) "$text (en tout ${spaced(total)} fichier${if (total > 1) "s" else ""})" else text
}

@Composable
fun BrowseScreen(
    state: UiState.Browse,
    onInto: (String) -> Unit,
    onUp: () -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onTrash: (Set<String>, List<File>) -> Unit,
    onUndoTrash: () -> Unit,
) {
    var selecting by remember(state) { mutableStateOf(false) }
    var selectedFolders by remember(state) { mutableStateOf(emptySet<String>()) }
    var selectedFiles by remember(state) { mutableStateOf(emptySet<String>()) }
    var confirm by remember(state) { mutableStateOf(false) }
    val selectedCount = selectedFolders.size + selectedFiles.size
    val itemCount = state.folders.size + state.files.size

    fun stopSelecting() {
        selecting = false
        selectedFolders = emptySet()
        selectedFiles = emptySet()
    }

    fun toggleFolder(name: String) {
        selecting = true
        selectedFolders = if (name in selectedFolders) selectedFolders - name else selectedFolders + name
    }

    fun toggleFile(path: String) {
        selecting = true
        selectedFiles = if (path in selectedFiles) selectedFiles - path else selectedFiles + path
    }

    BackHandler { if (selecting) stopSelecting() else onUp() }

    val title = if (state.path.isEmpty()) "Mes photos" else state.path.last()

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            val allSelected = selectedCount > 0 && selectedCount == itemCount
            Header(
                title = if (selectedCount == 0) "Choisissez" else if (selectedCount > 1) "$selectedCount choisis" else "1 choisi",
                subtitle = "Touchez les photos et les dossiers à mettre à la corbeille.",
                top = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { stopSelecting() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Annuler", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            if (allSelected) {
                                selectedFolders = emptySet()
                                selectedFiles = emptySet()
                            } else {
                                selectedFolders = state.folders.map { it.name }.toSet()
                                selectedFiles = state.files.map { it.absolutePath }.toSet()
                            }
                        }) {
                            Text(if (allSelected) "Tout désélectionner" else "Tout sélectionner", color = Color.White, fontSize = 16.sp)
                        }
                    }
                },
            )
        } else {
            val counts = ArrayList<String>()
            if (state.folders.isNotEmpty()) counts += if (state.folders.size > 1) "${state.folders.size} dossiers" else "1 dossier"
            if (state.files.isNotEmpty()) counts += if (state.files.size > 1) "${spaced(state.files.size)} photos et vidéos" else "1 photo ou vidéo"
            Header(
                title = title,
                subtitle = if (state.path.size > 1) state.path.dropLast(1).joinToString(" / ") else null,
                top = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.path.isEmpty()) "Accueil" else "Retour", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (itemCount > 0) HeaderButton("Choisir") { selecting = true }
                    }
                },
            ) {
                if (counts.isNotEmpty()) Text(counts.joinToString("  ·  "), color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                NoticeCard(
                    message,
                    isError = false,
                    actionLabel = if (state.undoTrash > 0) "Annuler" else null,
                    onAction = if (state.undoTrash > 0) onUndoTrash else null,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(state.folders, key = { "d:" + it.name }, span = { GridItemSpan(maxLineSpan) }) { folder ->
                FolderRow(
                    folder,
                    selecting = selecting,
                    selected = folder.name in selectedFolders,
                    onClick = { if (selecting) toggleFolder(folder.name) else onInto(folder.name) },
                    onLongClick = { toggleFolder(folder.name) },
                )
            }
            itemsIndexed(state.files, key = { _, f -> "f:" + f.absolutePath }) { index, file ->
                PhotoTile(
                    file,
                    selecting = selecting,
                    selected = file.absolutePath in selectedFiles,
                    onClick = { if (selecting) toggleFile(file.absolutePath) else onOpen(state.files, index) },
                    onLongClick = { toggleFile(file.absolutePath) },
                )
            }
        }

        if (selecting) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DangerButton(
                        if (selectedCount > 0) "Mettre à la corbeille ($selectedCount)" else "Mettre à la corbeille",
                        onClick = { confirm = true },
                        enabled = selectedCount > 0,
                        icon = Icons.Default.Delete,
                    )
                    Text(
                        "Rien n'est effacé : tout peut être remis depuis la corbeille.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (confirm) {
        val chosenFolders = state.folders.filter { it.name in selectedFolders }
        val total = selectedFiles.size + chosenFolders.sumOf { it.count }
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Mettre à la corbeille ?") },
            text = {
                Text(
                    describeSelection(selectedFiles.size, chosenFolders.size, total) +
                        "\n\nRien n'est effacé : vous pourrez tout remettre depuis la corbeille.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onTrash(selectedFolders, state.files.filter { it.absolutePath in selectedFiles })
                }) { Text("Mettre à la corbeille", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Garder") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(folder: FolderItem, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Card(
        modifier = Modifier.fillMaxWidth().clip(shape).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val cover = folder.cover
            if (cover != null) {
                Thumb(cover, 200, Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)))
            } else {
                Box(
                    Modifier.size(68.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("📁", fontSize = 28.sp) }
            }
            Column(Modifier.weight(1f)) {
                Text(folder.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (folder.count > 1) "${spaced(folder.count)} photos et vidéos" else "1 photo ou vidéo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selecting) SelectMark(selected, onPhoto = false, modifier = Modifier.padding(end = 6.dp))
            else Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
        }
    }
}

// ---- Visionneuse plein écran ---------------------------------------------------------------------

@Composable
fun ViewerScreen(state: UiState.Viewer, onClose: () -> Unit, onTrash: (File) -> Unit) {
    BackHandler { onClose() }
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = state.index.coerceIn(0, (state.files.size - 1).coerceAtLeast(0))) { state.files.size }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val file = state.files[page]
            var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(file) { bitmap = Thumbs.load(file, 1600) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                if (bitmap == null) Text("Chargement…", color = Color.White)
                if (PhotoFiles.isVideo(file)) {
                    Button(onClick = { playVideo(context, file) }, modifier = Modifier.height(60.dp), shape = RoundedCornerShape(18.dp)) {
                        Text("▶  Lire la vidéo", fontSize = 18.sp)
                    }
                }
            }
        }
        val current = state.files.getOrNull(pager.currentPage)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 22.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Fermer", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${state.files.size}", color = Color.White)
                if (current != null) {
                    IconButton(onClick = { confirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Mettre à la corbeille", tint = Color.White)
                    }
                }
            }
            if (current != null) {
                Text(current.name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Text(dateText(current), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
    if (confirm) {
        val current = state.files.getOrNull(pager.currentPage)
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Mettre à la corbeille ?") },
            text = { Text("Cette photo (ou vidéo) ira à la corbeille. Rien n'est effacé : vous pourrez la remettre.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    if (current != null) onTrash(current)
                }) { Text("Mettre à la corbeille", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Garder") } },
        )
    }
}

private fun dateText(file: File): String =
    "Fichier du " + java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRANCE).format(java.util.Date(file.lastModified()))

private fun playVideo(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, PhotoFiles.mimeOf(file) ?: "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Impossible d'ouvrir la vidéo.", android.widget.Toast.LENGTH_LONG).show()
    }
}

// ---- Corbeille -----------------------------------------------------------------------------------

@Composable
fun TrashScreen(
    state: UiState.TrashView,
    onBack: () -> Unit,
    onRestore: (List<TrashEntry>) -> Unit,
    onDeleteForever: (List<TrashEntry>) -> Unit,
) {
    BackHandler { onBack() }
    var selected by remember(state) { mutableStateOf(emptySet<String>()) } // chemins des fichiers choisis
    var confirm by remember(state) { mutableStateOf(false) }
    val chosen = state.entries.filter { it.trashed.absolutePath in selected }
    val allSelected = state.entries.isNotEmpty() && chosen.size == state.entries.size

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            title = "Corbeille",
            subtitle = if (state.entries.isEmpty()) "Rien à la corbeille."
            else "${spaced(state.entries.size)} fichier(s) mis de côté. Rien n'est effacé tant que vous ne le demandez pas.",
            top = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Accueil", color = Color.White, fontSize = 16.sp)
                    }
                }
            },
        )

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) { NoticeCard(message, isError = false) }
        }

        if (state.entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "La corbeille est vide.\nLes photos que vous y mettez depuis la galerie apparaissent ici.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(state.entries, key = { it.trashed.absolutePath }) { entry ->
                    val path = entry.trashed.absolutePath
                    PhotoTile(
                        entry.trashed,
                        selecting = true,
                        selected = path in selected,
                        onClick = { toggle(path) },
                        onLongClick = { toggle(path) },
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (chosen.isEmpty()) "Touchez ce que vous voulez remettre" else "${chosen.size} choisi(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selected = if (allSelected) emptySet() else state.entries.map { it.trashed.absolutePath }.toSet()
                        }) { Text(if (allSelected) "Tout désélectionner" else "Tout sélectionner") }
                    }
                    BigButton("Remettre à leur place", onClick = { onRestore(chosen) }, enabled = chosen.isNotEmpty())
                    DangerOutlineButton("Supprimer pour de bon…", onClick = { confirm = true }, enabled = chosen.isNotEmpty())
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Supprimer pour de bon ?") },
            text = {
                Text(
                    "${if (chosen.size > 1) "${chosen.size} fichiers seront effacés" else "1 fichier sera effacé"} définitivement. " +
                        "Cette action ne peut pas être annulée : ils ne reviendront plus.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    onDeleteForever(chosen)
                }) { Text("Supprimer pour de bon", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Les garder") } },
        )
    }
}
