package fr.rangephotos.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
            ) {
                when (val s = state) {
                    is UiState.Start -> StartScreen(s, onPick = { picker.launch(null) }, onUndo = viewModel::undo)
                    is UiState.Working -> WorkingScreen(s)
                    is UiState.Preview -> PreviewScreen(s, onConfirm = viewModel::confirm, onCancel = viewModel::backToStart)
                    is UiState.Done -> DoneScreen(s, onUndo = viewModel::undo, onBack = viewModel::backToStart)
                }
            }
        }
    }
}

@Composable
private fun StartScreen(state: UiState.Start, onPick: () -> Unit, onUndo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Range Photos", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Range automatiquement vos photos dans des dossiers : randonnées, portraits, " +
                "captures d'écran, puis par année et par mois. Tout reste sur votre téléphone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            "Choisissez le dossier à ranger (par exemple la carte SD, ou son dossier DCIM). " +
                "Vous verrez un aperçu avant que quoi que ce soit ne bouge.",
            style = MaterialTheme.typography.bodyMedium,
        )
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choisir le dossier de photos") }
        if (state.hasUndo) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Text("Annuler le dernier rangement")
            }
        }
    }
}

@Composable
private fun WorkingScreen(state: UiState.Working) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(state.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        if (state.total > 0) {
            LinearProgressIndicator(
                progress = { state.done.toFloat() / state.total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text("${state.done} / ${state.total}", style = MaterialTheme.typography.bodyMedium)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PreviewScreen(state: UiState.Preview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("${state.total} photos prêtes à être rangées", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("Dossiers", style = MaterialTheme.typography.titleMedium) }
            items(state.topLevel) { (name, count) -> CountRow(name, count) }
            if (state.hikes.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    Text("Randonnées détectées (${state.hikes.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(state.hikes) { (name, count) -> CountRow(name, count) }
            }
        }
        Text(
            "Les photos sont déplacées dans le dossier « Photos rangées ». Vous pourrez annuler ensuite.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Ranger maintenant") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
    }
}

@Composable
private fun CountRow(name: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, modifier = Modifier.weight(1f))
        Text("$count", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DoneScreen(state: UiState.Done, onUndo: () -> Unit, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(state.title, style = MaterialTheme.typography.headlineMedium)
        Text(state.details, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Terminer") }
        if (state.hasUndo) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Text("Annuler ce rangement")
            }
        }
    }
}
