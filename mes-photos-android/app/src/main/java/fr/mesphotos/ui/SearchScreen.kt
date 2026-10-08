package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.gallery.Gallery
import kotlinx.coroutines.delay
import java.io.File

// ---- Recherche par mots (nom du fichier, album, ville, mois, année) et raccourcis -------------------

@Composable
fun SearchScreen(
    state: UiState.Search,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onMove: (MoveKind, Set<String>, List<File>) -> Unit,
    onUndoMove: () -> Unit,
) {
    var text by remember { mutableStateOf(state.query) }
    var selecting by remember(state) { mutableStateOf(false) }
    var selected by remember(state) { mutableStateOf(emptySet<String>()) }
    var confirm by remember(state) { mutableStateOf<MoveKind?>(null) }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val hasTerms = Gallery.terms(state.query).isNotEmpty()

    // La recherche part toute seule un instant après la dernière lettre tapée.
    LaunchedEffect(text) {
        if (text != state.query) {
            delay(350)
            onSearch(text)
        }
    }
    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) runCatching { focusRequester.requestFocus() }
    }

    fun stopSelecting() {
        selecting = false
        selected = emptySet()
    }

    // Un raccourci remplit la recherche avec ses mots.
    fun openShortcut(item: ShortcutItem) {
        text = item.shortcut.words
        onSearch(item.shortcut.words)
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
                title = "Rechercher",
                top = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Photos", color = Color.White, fontSize = 16.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        if (state.results.isNotEmpty()) HeaderButton("Choisir") { selecting = true }
                    }
                },
            ) {
                val ink = Color(0xFF1B2723)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    singleLine = true,
                    placeholder = { Text("Nom, ville, mois, année…") },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = ink,
                        unfocusedTextColor = ink,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Color(0xFF14705A),
                        focusedPlaceholderColor = Color(0xFF6B7B74),
                        unfocusedPlaceholderColor = Color(0xFF6B7B74),
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focus.clearFocus()
                        onSearch(text)
                    }),
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Effacer", tint = Color(0xFF4A5A54))
                            }
                        }
                    },
                )
            }
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

        val hasShortcuts = state.shortcuts.isNotEmpty()
        when {
            !hasTerms && !hasShortcuts -> Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Tapez un mot : une ville, un mois, une année ou un bout de nom de fichier.\n\nExemples : « août 2024 », « Nice », « 14 mars », « IMG ».\nPlusieurs mots : on garde les photos qui les contiennent tous.\n\nAstuce : ouvrez une photo et touchez l'étoile pour en faire un raccourci (par exemple « Immatriculation », classé dans « Véhicule »).",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            !hasTerms -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
            ) {
                item { ShortcutRows(state.shortcuts, onOpen = { openShortcut(it) }) }
                item {
                    Text(
                        "Ou tapez un mot : une ville, un mois, une année, un bout de nom de fichier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp, top = 18.dp),
                    )
                }
            }
            state.results.isEmpty() && !hasShortcuts -> Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune photo ne correspond à « ${state.query.trim()} ».",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    if (hasShortcuts) {
                        item(key = "raccourcis", span = { GridItemSpan(maxLineSpan) }) { ShortcutRows(state.shortcuts, onOpen = { openShortcut(it) }) }
                    }
                    if (state.results.isNotEmpty()) {
                        item(key = "compte", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                if (state.results.size > 1) "${spaced(state.results.size)} photos et vidéos trouvées" else "1 photo ou vidéo trouvée",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                            )
                        }
                    }
                    itemsIndexed(state.results, key = { _, f -> "r:" + f.absolutePath }) { index, file ->
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
        }

        if (selecting) MoveBar(selected.size, onAside = { confirm = MoveKind.ASIDE }, onTrash = { confirm = MoveKind.TRASH })
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
