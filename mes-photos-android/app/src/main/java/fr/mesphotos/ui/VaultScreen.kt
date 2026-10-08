package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.organize.VaultEntry

/**
 * Le coffre-fort ouvert. Toucher une photo l'ouvre en grand ; « Choisir » (appui long) permet de la sortir du coffre
 * ou de la supprimer pour de bon.
 */
@Composable
fun VaultScreen(
    state: UiState.VaultView,
    onBack: () -> Unit,
    onOpen: (List<VaultEntry>, Int) -> Unit,
    onRestore: (List<VaultEntry>) -> Unit,
    onDeleteForever: (List<VaultEntry>) -> Unit,
) {
    BackHandler { onBack() }
    var selected by remember(state) { mutableStateOf(emptySet<String>()) } // identifiants choisis
    var confirm by remember(state) { mutableStateOf(false) }
    val chosen = state.entries.filter { it.id in selected }
    val selecting = selected.isNotEmpty()
    val allSelected = state.entries.isNotEmpty() && chosen.size == state.entries.size

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            title = "Coffre-fort",
            subtitle = if (state.entries.isEmpty()) "Vide." else "${spaced(state.entries.size)} fichier(s), invisibles ailleurs.",
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

        if (state.entries.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Le coffre-fort est vide.\nDans la galerie, touchez « Choisir », cochez des photos, puis « Coffre-fort ».",
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
                items(state.entries, key = { it.id }) { entry ->
                    PhotoTile(
                        entry.file,
                        selecting = selecting,
                        selected = entry.id in selected,
                        onClick = {
                            if (selecting) toggle(entry.id) else onOpen(state.entries, state.entries.indexOf(entry))
                        },
                        onLongClick = { toggle(entry.id) },
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
                            if (chosen.isEmpty()) "Appui long pour choisir des photos" else "${chosen.size} choisi(s)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            selected = if (allSelected) emptySet() else state.entries.map { it.id }.toSet()
                        }) { Text(if (allSelected) "Tout désélectionner" else "Tout sélectionner") }
                    }
                    BigButton("Sortir du coffre-fort", onClick = { onRestore(chosen) }, enabled = chosen.isNotEmpty())
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
