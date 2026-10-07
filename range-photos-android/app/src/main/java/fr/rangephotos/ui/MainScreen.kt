package fr.rangephotos.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
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
                    is UiState.Start -> StartScreen(
                        s,
                        onPick = { picker.launch(null) },
                        onUndo = viewModel::undo,
                        onPeople = viewModel::openPeople,
                    )
                    is UiState.Working -> WorkingScreen(s)
                    is UiState.People -> PeopleScreen(s, onContinue = viewModel::applyNames, onSkip = viewModel::skipPeople)
                    is UiState.ManagePeople -> ManagePeopleScreen(s, onForget = viewModel::forget, onBack = viewModel::backToStart)
                    is UiState.Preview -> PreviewScreen(
                        s,
                        onCopyChange = viewModel::setCopyToPeople,
                        onConfirm = viewModel::confirm,
                        onCancel = viewModel::backToStart,
                    )
                    is UiState.Done -> DoneScreen(s, onUndo = viewModel::undo, onBack = viewModel::backToStart)
                }
            }
        }
    }
}

@Composable
private fun StartScreen(state: UiState.Start, onPick: () -> Unit, onUndo: () -> Unit, onPeople: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Range Photos", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Range automatiquement vos photos dans des dossiers : randonnées, portraits de vos proches, " +
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
        if (state.people.isNotEmpty()) {
            Text("Mes proches : " + state.people.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onPeople, modifier = Modifier.fillMaxWidth()) { Text("Gérer mes proches") }
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
        if (state.label.startsWith("Reconnaissance")) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Cette étape peut durer quelques minutes pour beaucoup de photos. Gardez l'écran allumé.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun FaceThumb(bytes: ByteArray) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}

@Composable
private fun PeopleScreen(state: UiState.People, onContinue: (Map<Int, String>) -> Unit, onSkip: () -> Unit) {
    val names = remember { mutableStateMapOf<Int, String>() }
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Mes proches", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ces personnes reviennent dans vos photos. Donnez un prénom à celles qui vous intéressent : " +
                "leurs photos iront dans Portraits / Prénom. Laissez vide pour ne pas les nommer.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (state.known.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "Déjà connus : ${state.known.joinToString(", ")}. Tapez le même prénom si c'est la même personne.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(state.clusters, key = { it.id }) { cluster ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        cluster.thumbs.forEach { FaceThumb(it) }
                    }
                    Text("Sur ${cluster.photos} photos", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = names[cluster.id] ?: "",
                        onValueChange = { names[cluster.id] = it },
                        label = { Text("Prénom") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onContinue(names.toMap()) }, modifier = Modifier.fillMaxWidth()) { Text("Continuer") }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Passer cette étape") }
    }
}

@Composable
private fun ManagePeopleScreen(state: UiState.ManagePeople, onForget: (String) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text("Mes proches", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "L'appli reconnaît ces personnes dans vos nouvelles photos. « Oublier » ne déplace aucune photo.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.names, key = { it }) { name ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onForget(name) }) { Text("Oublier") }
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Retour") }
    }
}

@Composable
private fun PreviewScreen(
    state: UiState.Preview,
    onCopyChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
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
        if (state.hasNamedPeople) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(checked = state.copyToPeople, onCheckedChange = onCopyChange)
                Text(
                    if (state.copyToPeople) {
                        "Copier aussi chaque photo dans le dossier de ses proches : ${state.copyCount} copies, " +
                            "environ ${state.copyMegabytes} Mo de plus."
                    } else {
                        "Pas de copie : une photo de groupe n'apparaît que dans « Groupe »."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
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
