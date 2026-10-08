package fr.mesphotos.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fr.mesphotos.gallery.FolderItem
import fr.mesphotos.gallery.Gallery
import fr.mesphotos.gallery.Thumbs
import fr.mesphotos.logic.Shortcut
import fr.mesphotos.logic.Shortcuts
import fr.mesphotos.organize.TrashEntry
import fr.mesphotos.storage.PhotoFiles
import kotlinx.coroutines.delay
import java.io.File

// ---- Éléments communs : choix multiple, boutons du bas, confirmations ------------------------------

internal fun describeSelection(files: Int, folders: Int, total: Int): String {
    val parts = ArrayList<String>()
    if (folders > 0) parts += if (folders > 1) "$folders dossiers" else "1 dossier"
    if (files > 0) parts += if (files > 1) "$files photos ou vidéos" else "1 photo ou vidéo"
    val text = parts.joinToString(" et ")
    return if (folders > 0) "$text (en tout ${spaced(total)} fichier${if (total > 1) "s" else ""})" else text
}

/** Bandeau du mode « Choisir » : combien de choisis, Annuler, Tout sélectionner. */
@Composable
internal fun SelectionHeader(selectedCount: Int, allSelected: Boolean, onCancel: () -> Unit, onToggleAll: () -> Unit) {
    Header(
        title = if (selectedCount == 0) "Choisissez" else if (selectedCount > 1) "$selectedCount choisis" else "1 choisi",
        subtitle = "Touchez les photos (et les dossiers) à mettre de côté.",
        top = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Annuler", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onToggleAll) {
                    Text(if (allSelected) "Tout désélectionner" else "Tout sélectionner", color = Color.White, fontSize = 16.sp)
                }
            }
        },
    )
}

/** Barre du bas en mode « Choisir » : « À l'écart » (rangées à part) ou « Corbeille ». */
@Composable
internal fun MoveBar(count: Int, onAside: () -> Unit, onTrash: () -> Unit, onNotThat: (() -> Unit)? = null, onVault: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onAside,
                    enabled = count > 0,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("À l'écart", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onTrash,
                    enabled = count > 0,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Corbeille", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onVault != null) {
                FilledTonalButton(
                    onClick = onVault,
                    enabled = count > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Icon(AppIcons.Shield, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Coffre-fort (photos cachées)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (onNotThat != null) SoftButton("Ce n'est pas ça : retirer de la liste", onNotThat, enabled = count > 0)
            Text(
                "Rien n'est effacé : tout peut être remis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun ConfirmMoveDialog(kind: MoveKind, summary: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val aside = kind == MoveKind.ASIDE
    val vault = kind == MoveKind.VAULT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vault) "Mettre au coffre-fort ?" else if (aside) "Mettre à l'écart ?" else "Mettre à la corbeille ?") },
        text = {
            Text(
                summary + "\n\n" + (
                    if (vault) "Ils sont copiés (et vérifiés) dans la mémoire privée de l'appli, puis retirés de la carte et de la galerie du téléphone. Personne ne les voit sans le verrouillage de votre téléphone. Attention : si vous désinstallez l'appli, ils sont perdus."
                    else if (aside) "Ils quittent la galerie (celle de l'appli et celle du téléphone) mais restent sur la carte, dans « Photos rangées / À l'écart ». Vous pourrez les remettre."
                    else "Rien n'est effacé : vous pourrez tout remettre depuis la corbeille."
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(if (vault) "Mettre au coffre" else if (aside) "Mettre à l'écart" else "Mettre à la corbeille", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Garder") } },
    )
}

// ---- Galerie : dossiers, photos ------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(
    state: UiState.Browse,
    onInto: (String) -> Unit,
    onUp: () -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onMove: (MoveKind, Set<String>, List<File>) -> Unit,
    onUndoMove: () -> Unit,
    onSearch: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenShortcut: (String) -> Unit,
    onSaveShortcut: (name: String, category: String, words: String, replacing: Shortcut?) -> Unit,
    onRemoveShortcut: (Shortcut) -> Unit,
    categories: () -> List<String>,
    tabs: @Composable () -> Unit,
) {
    var editing by remember { mutableStateOf<ShortcutItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    // Le menu des raccourcis est fermé à chaque retour sur la page (il se referme derrière nous).
    var drawerOpen by remember { mutableStateOf(false) }
    var selecting by remember(state) { mutableStateOf(false) }
    var selectedFolders by remember(state) { mutableStateOf(emptySet<String>()) }
    var selectedFiles by remember(state) { mutableStateOf(emptySet<String>()) }
    var confirm by remember(state) { mutableStateOf<MoveKind?>(null) }
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

    // À la racine, « retour » quitte l'appli comme dans toute appli photo.
    BackHandler(enabled = selecting || state.path.isNotEmpty()) { if (selecting) stopSelecting() else onUp() }

    val title = if (state.path.isEmpty()) "Photos" else state.path.last()

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            val allSelected = selectedCount > 0 && selectedCount == itemCount
            SelectionHeader(
                selectedCount = selectedCount,
                allSelected = allSelected,
                onCancel = { stopSelecting() },
                onToggleAll = {
                    if (allSelected) {
                        selectedFolders = emptySet()
                        selectedFiles = emptySet()
                    } else {
                        selectedFolders = state.folders.map { it.name }.toSet()
                        selectedFiles = state.files.map { it.absolutePath }.toSet()
                    }
                },
            )
        } else {
            val counts = ArrayList<String>()
            if (state.folders.isNotEmpty()) counts += if (state.folders.size > 1) "${state.folders.size} albums" else "1 album"
            if (state.files.isNotEmpty()) counts += if (state.files.size > 1) "${spaced(state.files.size)} photos et vidéos" else "1 photo ou vidéo"
            TopBar(
                title = title,
                subtitle = counts.joinToString("  ·  ").ifEmpty { null },
                onBack = if (state.path.isEmpty()) null else onUp,
            ) {
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Rechercher") }
                if (itemCount > 0) TopAction("Choisir") { selecting = true }
            }
        }

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)) {
                NoticeCard(
                    message,
                    isError = false,
                    actionLabel = if (state.undoTrash > 0) "Annuler" else null,
                    onAction = if (state.undoTrash > 0) onUndoMove else null,
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // En tête de la page d'accueil des photos : l'appareil photo, puis le tiroir des raccourcis.
            if (!selecting && state.path.isEmpty()) {
                item(key = "appareil", span = { GridItemSpan(maxLineSpan) }) { CameraCard(onTakePhoto) }
                item(key = "raccourcis", span = { GridItemSpan(maxLineSpan) }) {
                    ShortcutDrawer(
                        state.shortcuts,
                        open = drawerOpen,
                        onToggle = { drawerOpen = !drawerOpen },
                        onOpen = { onOpenShortcut(it.shortcut.words) },
                        onEdit = { editing = it },
                        onCreate = { creating = true },
                    )
                }
                if (state.folders.isNotEmpty()) {
                    item(key = "albums", span = { GridItemSpan(maxLineSpan) }) {
                        Text("Albums", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
                    }
                }
            }
            items(state.folders, key = { "d:" + it.name }, span = { GridItemSpan(3) }) { folder ->
                AlbumCard(
                    folder,
                    selecting = selecting,
                    selected = folder.name in selectedFolders,
                    onClick = { if (selecting) toggleFolder(folder.name) else onInto(folder.name) },
                    onLongClick = { toggleFolder(folder.name) },
                )
            }
            itemsIndexed(state.files, key = { _, f -> "f:" + f.absolutePath }, span = { _, _ -> GridItemSpan(2) }) { index, file ->
                PhotoTile(
                    file,
                    selecting = selecting,
                    selected = file.absolutePath in selectedFiles,
                    onClick = { if (selecting) toggleFile(file.absolutePath) else onOpen(state.files, index) },
                    onLongClick = { toggleFile(file.absolutePath) },
                )
            }
        }

        if (selecting) MoveBar(selectedCount, onAside = { confirm = MoveKind.ASIDE }, onTrash = { confirm = MoveKind.TRASH }, onVault = { confirm = MoveKind.VAULT })
        else tabs()
    }

    confirm?.let { kind ->
        val chosenFolders = state.folders.filter { it.name in selectedFolders }
        val total = selectedFiles.size + chosenFolders.sumOf { it.count }
        ConfirmMoveDialog(
            kind = kind,
            summary = describeSelection(selectedFiles.size, chosenFolders.size, total),
            onConfirm = {
                confirm = null
                onMove(kind, selectedFolders, state.files.filter { it.absolutePath in selectedFiles })
            },
            onDismiss = { confirm = null },
        )
    }

    if (creating) {
        ShortcutDialog(
            initial = null,
            categories = categories(),
            onSave = { name, category, words ->
                creating = false
                onSaveShortcut(name, category, words, null)
            },
            onDismiss = { creating = false },
        )
    }
    editing?.let { item ->
        ShortcutDialog(
            initial = item.shortcut,
            categories = categories(),
            onSave = { name, category, words ->
                editing = null
                onSaveShortcut(name, category, words, item.shortcut)
            },
            onRemove = {
                editing = null
                onRemoveShortcut(item.shortcut)
            },
            onDismiss = { editing = null },
        )
    }
}

/** Le grand bouton « Prendre une photo », tout en haut de la page d'accueil des photos. */
@Composable
private fun CameraCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(23.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Camera, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("Prendre une photo", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    "Choisissez ce que vous photographiez : elle sera facile à retrouver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/**
 * Le menu déroulant des raccourcis : un appui sur l'en-tête l'ouvre ou le ferme. Dedans, un grand bouton coloré par raccourci
 * avec le nombre de photos à droite ; appui = voir les photos, appui long = modifier ou retirer. En bas : « Nouveau raccourci ».
 */
@Composable
private fun ShortcutDrawer(
    items: List<ShortcutItem>,
    open: Boolean,
    onToggle: () -> Unit,
    onOpen: (ShortcutItem) -> Unit,
    onEdit: (ShortcutItem) -> Unit,
    onCreate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Raccourcis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (items.isNotEmpty()) {
                    Text(items.size.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (items.isEmpty()) {
                        Text(
                            "Aucun raccourci pour l'instant. Un raccourci est une recherche enregistrée : par exemple « Immatriculation » montre toutes les photos qui portent ce mot, avec leur nombre.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    } else {
                        ShortcutRows(items, onOpen = onOpen, onLongClick = onEdit)
                        Text(
                            "Appui long sur un raccourci : le modifier ou le retirer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    TextButton(onClick = onCreate) { Text("+ Nouveau raccourci", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

/** Un album (dossier) : sa photo de couverture en grand, le nom et le nombre de photos par-dessus. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(folder: FolderItem, selecting: Boolean, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        folder.cover?.let { Thumb(it, 400, Modifier.fillMaxSize()) }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(folder.name, color = Color.White, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(
                if (folder.count > 1) "${spaced(folder.count)} photos" else "1 photo",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
        }
        if (selecting) SelectMark(selected, onPhoto = true, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}

// ---- Suggestions de la recherche automatique -------------------------------------------------------

@Composable
fun ReviewScreen(
    state: UiState.Review,
    onBack: () -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onMove: (MoveKind, Set<String>, List<File>) -> Unit,
    onUndoMove: () -> Unit,
    onDismiss: (List<File>) -> Unit,
    onRescan: () -> Unit,
) {
    var selecting by remember(state) { mutableStateOf(false) }
    var selected by remember(state) { mutableStateOf(emptySet<String>()) }
    var confirm by remember(state) { mutableStateOf<MoveKind?>(null) }

    fun stopSelecting() {
        selecting = false
        selected = emptySet()
    }

    fun toggle(path: String) {
        selecting = true
        selected = if (path in selected) selected - path else selected + path
    }

    BackHandler { if (selecting) stopSelecting() else onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            val allSelected = selected.isNotEmpty() && selected.size == state.files.size
            SelectionHeader(
                selectedCount = selected.size,
                allSelected = allSelected,
                onCancel = { stopSelecting() },
                onToggleAll = { selected = if (allSelected) emptySet() else state.files.map { it.absolutePath }.toSet() },
            )
        } else {
            Header(
                title = "Suggestions",
                subtitle = when {
                    state.files.isEmpty() -> "Aucune photo à regarder."
                    state.files.size > 1 -> "${spaced(state.files.size)} photos semblent montrer des personnes nues. L'appli peut se tromper : regardez avant de décider."
                    else -> "1 photo semble montrer des personnes nues. L'appli peut se tromper : regardez avant de décider."
                },
                top = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Outils", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.files.isNotEmpty()) HeaderButton("Choisir") { selecting = true }
                    }
                },
            )
        }

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                NoticeCard(
                    message,
                    isError = false,
                    actionLabel = if (state.undoTrash > 0) "Annuler" else null,
                    onAction = if (state.undoTrash > 0) onUndoMove else null,
                )
            }
        }

        if (state.files.isEmpty()) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Rien à proposer pour l'instant.\nSi vous avez ajouté des photos depuis, relancez la recherche : seules les nouvelles sont regardées.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                BigButton("Relancer la recherche", onRescan)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                itemsIndexed(state.files, key = { _, f -> "s:" + f.absolutePath }) { index, file ->
                    val path = file.absolutePath
                    PhotoTile(
                        file,
                        selecting = selecting,
                        selected = path in selected,
                        onClick = { if (selecting) toggle(path) else onOpen(state.files, index) },
                        onLongClick = { toggle(path) },
                    )
                }
            }
        }

        if (selecting) {
            MoveBar(
                selected.size,
                onAside = { confirm = MoveKind.ASIDE },
                onTrash = { confirm = MoveKind.TRASH },
                onVault = { confirm = MoveKind.VAULT },
                onNotThat = {
                    val chosen = state.files.filter { it.absolutePath in selected }
                    stopSelecting()
                    onDismiss(chosen)
                },
            )
        }
    }

    confirm?.let { kind ->
        ConfirmMoveDialog(
            kind = kind,
            summary = describeSelection(selected.size, 0, selected.size),
            onConfirm = {
                confirm = null
                onMove(kind, emptySet(), state.files.filter { it.absolutePath in selected })
            },
            onDismiss = { confirm = null },
        )
    }
}

// ---- Recherche par visage ------------------------------------------------------------------------

@Composable
fun FaceResultsScreen(
    state: UiState.FaceResults,
    onBack: () -> Unit,
    onLevel: (Int) -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onMove: (MoveKind, Set<String>, List<File>) -> Unit,
    onUndoMove: () -> Unit,
) {
    var selecting by remember(state) { mutableStateOf(false) }
    var selected by remember(state) { mutableStateOf(emptySet<String>()) }
    var confirm by remember(state) { mutableStateOf<MoveKind?>(null) }

    fun stopSelecting() {
        selecting = false
        selected = emptySet()
    }

    BackHandler { if (selecting) stopSelecting() else onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selecting) {
            val allSelected = selected.isNotEmpty() && selected.size == state.results.size
            SelectionHeader(
                selectedCount = selected.size,
                allSelected = allSelected,
                onCancel = { stopSelecting() },
                onToggleAll = { selected = if (allSelected) emptySet() else state.results.map { it.absolutePath }.toSet() },
            )
        } else {
            Header(
                title = "Même personne",
                subtitle = when {
                    state.results.isEmpty() -> "Aucune photo trouvée."
                    state.results.size > 1 -> "${spaced(state.results.size)} photos. Les plus ressemblantes d'abord. L'appli peut se tromper : regardez."
                    else -> "1 photo. L'appli peut se tromper : regardez."
                },
                top = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Visages", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.results.isNotEmpty()) HeaderButton("Choisir") { selecting = true }
                    }
                },
            )
        }

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                NoticeCard(
                    message,
                    isError = false,
                    actionLabel = if (state.undoTrash > 0) "Annuler" else null,
                    onAction = if (state.undoTrash > 0) onUndoMove else null,
                )
            }
        }

        if (!selecting) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("Sûr", "Normal", "Large").forEachIndexed { level, label ->
                    FilterChip(selected = state.level == level, onClick = { onLevel(level) }, label = { Text(label) })
                }
            }
        }

        if (state.results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune photo ne ressemble à ce visage" + if (state.level < 2) ".\n\nEssayez « Large » pour voir plus de photos." else ".\n\nSi l'analyse n'a pas été faite sur toutes vos photos, relancez-la depuis l'écran des visages.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                itemsIndexed(state.results, key = { _, f -> "f:" + f.absolutePath }) { index, file ->
                    val path = file.absolutePath
                    PhotoTile(
                        file,
                        selecting = selecting,
                        selected = path in selected,
                        onClick = {
                            if (selecting) selected = if (path in selected) selected - path else selected + path
                            else onOpen(state.results, index)
                        },
                        onLongClick = {
                            selecting = true
                            selected = if (path in selected) selected - path else selected + path
                        },
                    )
                }
            }
        }

        if (selecting) MoveBar(selected.size, onAside = { confirm = MoveKind.ASIDE }, onTrash = { confirm = MoveKind.TRASH }, onVault = { confirm = MoveKind.VAULT })
    }

    confirm?.let { kind ->
        ConfirmMoveDialog(
            kind = kind,
            summary = describeSelection(selected.size, 0, selected.size),
            onConfirm = {
                confirm = null
                onMove(kind, emptySet(), state.results.filter { it.absolutePath in selected })
            },
            onDismiss = { confirm = null },
        )
    }
}

// ---- Visionneuse plein écran ---------------------------------------------------------------------

@Composable
fun ViewerScreen(
    state: UiState.Viewer,
    onClose: () -> Unit,
    onMove: (MoveKind, File) -> Unit,
    onRename: (File, String) -> Unit,
    categories: () -> List<String>,
    onSaveShortcut: (name: String, category: String, words: String) -> Unit,
    onRestoreFromVault: (File) -> Unit = {},
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    var confirm by remember { mutableStateOf<MoveKind?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var shortcutDialog by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = state.index.coerceIn(0, (state.files.size - 1).coerceAtLeast(0))) { state.files.size }
    // Photo agrandie : le doigt fait glisser la photo au lieu de passer à la suivante.
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1, userScrollEnabled = !zoomed) { page ->
            val file = state.files[page]
            var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
            var finished by remember(file) { mutableStateOf(false) }
            LaunchedEffect(file) {
                bitmap = Thumbs.load(file, 1600)
                if (bitmap == null) {
                    delay(600)
                    bitmap = Thumbs.load(file, 1600)
                }
                finished = true
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bitmap?.let { ZoomableImage(it, file.name) { isZoomed -> if (pager.currentPage == page) zoomed = isZoomed } }
                if (bitmap == null && !finished) Text("Chargement…", color = Color.White)
                if (PhotoFiles.isVideo(file)) {
                    Button(onClick = { playVideo(context, file) }, modifier = Modifier.height(60.dp), shape = RoundedCornerShape(18.dp)) {
                        Text("▶  Lire la vidéo", fontSize = 18.sp)
                    }
                } else if (bitmap == null && finished) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Text(
                            "L'appli n'arrive pas à afficher ce fichier (${file.extension.uppercase()}). Il est bien là, rien n'est perdu.",
                            color = Color.White,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { playVideo(context, file) }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(18.dp)) {
                            Text("Ouvrir avec une autre appli", fontSize = 16.sp)
                        }
                        var diag by remember(file) { mutableStateOf<String?>(null) }
                        LaunchedEffect(file) { diag = Thumbs.diagnose(file) }
                        diag?.let {
                            Spacer(Modifier.height(20.dp))
                            Text("Ce que l'appli a constaté :", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 17.sp)
                        }
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
                if (current != null && state.fromVault) {
                    IconButton(onClick = { onRestoreFromVault(current) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sortir du coffre-fort", tint = Color.White)
                    }
                }
                if (current != null && !state.fromVault) {
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Renommer", tint = Color.White)
                    }
                    // Étoile : créer un raccourci (une recherche enregistrée) d'après cette photo.
                    IconButton(onClick = { shortcutDialog = true }) {
                        Icon(Icons.Default.Star, contentDescription = "Créer un raccourci", tint = Color.White)
                    }
                    IconButton(onClick = { confirm = MoveKind.VAULT }) {
                        Icon(AppIcons.Shield, contentDescription = "Mettre au coffre-fort", tint = Color.White)
                    }
                    IconButton(onClick = { confirm = MoveKind.ASIDE }) {
                        Icon(Icons.Default.Lock, contentDescription = "Mettre à l'écart", tint = Color.White)
                    }
                    IconButton(onClick = { confirm = MoveKind.TRASH }) {
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
    var messageHidden by remember(state.message) { mutableStateOf(false) }
    LaunchedEffect(state.message) {
        delay(5000)
        messageHidden = true
    }
    state.message?.takeIf { !messageHidden }?.let { message ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                message,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    if (renaming) {
        val target = state.files.getOrNull(pager.currentPage)
        if (target != null) {
            var text by remember(target) { mutableStateOf(target.nameWithoutExtension) }
            AlertDialog(
                onDismissRequest = { renaming = false },
                title = { Text("Renommer la photo") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(
                            "Le type du fichier (« .${target.extension} ») est gardé. Vous pourrez retrouver la photo avec la loupe de l'onglet Photos.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        renaming = false
                        onRename(target, text)
                    }) { Text("Renommer", fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } },
            )
        }
    }
    if (shortcutDialog) {
        val target = state.files.getOrNull(pager.currentPage)
        if (target != null) {
            ShortcutDialog(
                initial = null,
                suggestedName = Shortcuts.suggestName(target.name),
                suggestedCategory = Shortcuts.suggestCategory(target.name),
                categories = categories(),
                onSave = { name, category, words ->
                    shortcutDialog = false
                    onSaveShortcut(name, category, words)
                },
                onDismiss = { shortcutDialog = false },
            )
        }
    }
    confirm?.let { kind ->
        val target = state.files.getOrNull(pager.currentPage)
        ConfirmMoveDialog(
            kind = kind,
            summary = "Cette photo (ou vidéo)",
            onConfirm = {
                confirm = null
                if (target != null) onMove(kind, target)
            },
            onDismiss = { confirm = null },
        )
    }
}

private fun dateText(file: File): String =
    "Fichier du " + java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRANCE).format(java.util.Date(file.lastModified()))

private fun playVideo(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, PhotoFiles.mimeOf(file) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Aucune appli du téléphone ne peut ouvrir ce fichier.", android.widget.Toast.LENGTH_LONG).show()
    }
}

// ---- Corbeille et « À l'écart » ------------------------------------------------------------------

@Composable
fun TrashScreen(
    state: UiState.TrashView,
    onBack: () -> Unit,
    onRestore: (List<TrashEntry>) -> Unit,
    onDeleteForever: (List<TrashEntry>) -> Unit,
    onToVault: (List<TrashEntry>) -> Unit = {},
) {
    BackHandler { onBack() }
    val aside = state.kind == MoveKind.ASIDE
    var selected by remember(state) { mutableStateOf(emptySet<String>()) } // chemins des fichiers choisis
    var confirm by remember(state) { mutableStateOf(false) }
    val chosen = state.entries.filter { it.trashed.absolutePath in selected }
    val allSelected = state.entries.isNotEmpty() && chosen.size == state.entries.size

    fun toggle(path: String) {
        selected = if (path in selected) selected - path else selected + path
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            title = if (aside) "À l'écart" else "Corbeille",
            subtitle = when {
                state.entries.isEmpty() -> if (aside) "Rien à l'écart." else "Rien à la corbeille."
                aside -> "${spaced(state.entries.size)} fichier(s) rangés à part, hors de la galerie."
                else -> "${spaced(state.entries.size)} fichier(s) mis de côté. Rien n'est effacé tant que vous ne le demandez pas."
            },
            top = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Outils", color = Color.White, fontSize = 16.sp)
                    }
                }
            },
        )

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) { NoticeCard(message, isError = false) }
        }
        if (aside && state.entries.isNotEmpty()) {
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                NoticeCard(
                    "Pour les récupérer : dossier « Photos rangées / À l'écart » de la carte SD (gestionnaire de fichiers, ou téléphone branché à l'ordinateur).",
                    isError = false,
                )
            }
        }

        if (state.entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (aside) "Rien n'est à l'écart.\nDans la galerie, touchez « Choisir », cochez des photos, puis « À l'écart »."
                    else "La corbeille est vide.\nLes photos que vous y mettez depuis la galerie apparaissent ici.",
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
                    BigButton(
                        if (aside) "Remettre dans la galerie" else "Remettre à leur place",
                        onClick = { onRestore(chosen) },
                        enabled = chosen.isNotEmpty(),
                    )
                    SoftButton("Mettre au coffre-fort", onClick = { onToVault(chosen) }, enabled = chosen.isNotEmpty())
                    if (!aside) DangerOutlineButton("Supprimer pour de bon…", onClick = { confirm = true }, enabled = chosen.isNotEmpty())
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
