package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.gallery.Thumbs

/** Tri rapide : une photo en grand, et un gros bouton par nom (Manu, Famille, Ex…). Un appui = la photo est rangée, la suivante arrive. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuickSortScreen(
    state: UiState.QuickSort,
    onAssign: (label: String, isNew: Boolean) -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler { onClose() }
    var asking by remember { mutableStateOf(false) }
    val file = state.files.getOrNull(state.index)
    // La photo suivante est lue d'avance : l'enchaînement est immédiat.
    LaunchedEffect(state.index) {
        state.files.getOrNull(state.index + 1)?.let { Thumbs.load(it, 1400) }
    }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("Fermer", fontSize = 16.sp) }
                Text("${state.index + 1} / ${state.files.size}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onUndo, enabled = state.canUndo) { Text("Annuler", fontSize = 16.sp) }
            }
            if (file != null) {
                Thumb(file, 1400, Modifier.fillMaxWidth().weight(1f), scale = ContentScale.Fit)
                Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp))
            }
            Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.targets.forEach { name ->
                        FilledTonalButton(onClick = { onAssign(name, false) }) { Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    }
                    OutlinedButton(onClick = { asking = true }) { Text("+ Autre nom", fontSize = 16.sp) }
                }
                Button(onClick = onSkip, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("Passer cette photo", fontSize = 17.sp) }
            }
        }
    }
    if (asking) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Nouveau nom") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nom du dossier") }) },
            confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { val n = text; asking = false; onAssign(n, true) }) { Text("Ranger", fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Annuler") } },
        )
    }
}
