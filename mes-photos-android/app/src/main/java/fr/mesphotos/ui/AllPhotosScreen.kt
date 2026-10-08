package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * « Toutes les photos » : toutes les photos et vidéos de « Photos rangées » à la suite, en petites vignettes, dans l'ordre choisi
 * (plus récentes, plus anciennes, nom, plus grosses). Un appui ouvre la photo ; « Choisir » (ou un appui long) permet d'en
 * sélectionner autant qu'on veut, puis de les mettre au coffre-fort, à l'écart ou à la corbeille. Rien n'est déplacé tant
 * qu'on ne l'a pas demandé.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllPhotosScreen(
    state: UiState.Search,
    onBack: () -> Unit,
    onOrder: (AllOrder) -> Unit,
    onHideRenamed: (Boolean) -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onMove: (MoveKind, Set<String>, List<File>) -> Unit,
    onUndoMove: () -> Unit,
    onRename: (Set<String>, List<File>) -> Unit,
    tabs: @Composable () -> Unit,
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

    Column(Modifier.fillMaxSize()) {
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
                title = "Toutes les photos",
                subtitle = "${spaced(state.results.size)} photos et vidéos",
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
            )
        }

        // L'ordre : un appui sur une puce change tout de suite la liste.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.hideRenamed,
                onClick = { onHideRenamed(!state.hideRenamed) },
                label = { Text(if (state.hideRenamed) "Renommées cachées (${state.renamedCount})" else "Cacher les renommées (${state.renamedCount})") },
            )
            AllOrder.values().forEach { order ->
                FilterChip(selected = state.order == order, onClick = { onOrder(order) }, label = { Text(order.label) })
            }
        }

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 6.dp)) {
                NoticeCard(
                    message,
                    isError = false,
                    actionLabel = if (state.undoTrash > 0) "Annuler" else null,
                    onAction = if (state.undoTrash > 0) onUndoMove else null,
                )
            }
        }

        if (state.results.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(if (state.hideRenamed && state.renamedCount > 0) "Bravo : toutes les photos sont renommées. Touchez la puce en haut pour les revoir." else "Aucune photo pour l'instant : rangez d'abord vos photos.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(78.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(state.results, key = { _, f -> "t:" + f.absolutePath }) { index, file ->
                    val path = file.absolutePath
                    PhotoTile(
                        file,
                        selecting = selecting,
                        selected = path in selected,
                        onClick = { if (selecting) toggle(path) else onOpen(state.results, index) },
                        onLongClick = { toggle(path) },
                    )
                }
            }
        }

        if (selecting) MoveBar(
            selected.size,
            onAside = { confirm = MoveKind.ASIDE },
            onTrash = { confirm = MoveKind.TRASH },
            onVault = { confirm = MoveKind.VAULT },
            onRename = { onRename(emptySet(), state.results.filter { it.absolutePath in selected }) },
        )
        else tabs()
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
