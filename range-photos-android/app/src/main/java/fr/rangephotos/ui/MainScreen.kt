package fr.rangephotos.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.rangephotos.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = remember { appVersionName(context) }
    var update by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    val checkUpdate: () -> Unit = {
        if (update !is UpdateUi.Checking) {
            update = UpdateUi.Checking
            scope.launch {
                update = try {
                    val latest = withContext(Dispatchers.IO) { UpdateChecker.parseLatest(UpdateChecker.fetch()) }
                    when {
                        latest == null -> UpdateUi.Failed("Aucune version trouvée pour l'instant.")
                        UpdateChecker.isNewer(latest.version, versionName) -> UpdateUi.Available(latest)
                        else -> UpdateUi.UpToDate
                    }
                } catch (e: Exception) {
                    UpdateUi.Failed("Impossible de vérifier : pas d'internet ?")
                }
            }
        }
    }
    val downloadUpdate: (UpdateChecker.Release) -> Unit = { release ->
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            update = UpdateUi.Failed("Impossible d'ouvrir le téléchargement.")
        }
    }

    val openFolder: () -> Unit = {
        val intent = viewModel.openSortedFolder()
        val opened = intent != null && try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
        // Dans tous les cas, l'écran d'accueil explique ce qui existe (ou pas) dans le dossier.
        viewModel.checkSortedFolder(openFailed = intent != null && !opened)
    }

    MaterialTheme(colorScheme = RangeColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(20.dp),
            ) {
                when (val s = state) {
                    is UiState.Start -> StartScreen(
                        s,
                        onPick = { viewModel.armReclassify(false); picker.launch(null) },
                        onUndo = viewModel::undo,
                        onPeople = viewModel::openPeople,
                        onRescan = viewModel::rescan,
                        onOpenFolder = openFolder,
                        onReclassify = {
                            if (s.lastFolder != null) {
                                viewModel.rescanAll()
                            } else {
                                viewModel.armReclassify(true)
                                picker.launch(null)
                            }
                        },
                        versionLabel = "V${versionCode(context)} · $versionName",
                        update = update,
                        onCheckUpdate = checkUpdate,
                        onDownloadUpdate = downloadUpdate,
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
                    is UiState.Done -> DoneScreen(s, onUndo = viewModel::undo, onBack = viewModel::backToStart, onOpen = openFolder)
                }
            }
        }
    }
}

@Composable
private fun StartScreen(
    state: UiState.Start,
    onPick: () -> Unit,
    onUndo: () -> Unit,
    onPeople: () -> Unit,
    onRescan: () -> Unit,
    onOpenFolder: () -> Unit,
    onReclassify: () -> Unit,
    versionLabel: String,
    update: UpdateUi,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (UpdateChecker.Release) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Range Photos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Vos photos rangées toutes seules, sans quitter votre téléphone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        state.message?.let { message ->
            item {
                Text(
                    message,
                    color = if (state.info) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ranger mes photos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Choisissez la carte SD (ou son dossier DCIM). Vous verrez un aperçu : rien ne bouge avant votre accord.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.lastFolder != null) {
                        Button(onClick = onRescan, modifier = Modifier.fillMaxWidth()) {
                            Text("Relancer la recherche (« ${state.lastFolder} »)")
                        }
                        Text(
                            "À utiliser après avoir ajouté de nouvelles photos : seules les photos pas encore rangées sont proposées.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choisir un autre dossier") }
                    } else {
                        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choisir le dossier de photos") }
                    }
                    if (state.lastFolder != null) {
                        OutlinedButton(onClick = onOpenFolder, modifier = Modifier.fillMaxWidth()) {
                            Text("Ouvrir mes photos rangées")
                        }
                    }
                    if (state.hasUndo) {
                        OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                            Text("Annuler le dernier rangement")
                        }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Tout reclasser", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Nouvelle analyse de toutes les photos, même celles déjà rangées, pour les remettre chacune dans le bon dossier " +
                            "(par exemple après avoir nommé de nouveaux proches). Cela peut durer longtemps : gardez l'écran allumé. " +
                            "Rien ne bouge avant votre accord, et vous pourrez annuler.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onReclassify, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.lastFolder != null) "Tout reclasser (« ${state.lastFolder} »)" else "Tout reclasser : choisir le dossier")
                    }
                }
            }
        }
        if (state.people.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            if (state.people.size == 1) "1 proche reconnu" else "${state.people.size} proches reconnus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(peopleSummary(state.people), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onPeople, modifier = Modifier.fillMaxWidth()) { Text("Gérer mes proches") }
                    }
                }
            }
        }
        item { UpdateCard(versionLabel, update, onCheckUpdate, onDownloadUpdate) }
    }
}

/** « Manu, Sandrine, Maman et 25 autres » : lisible même avec une longue liste. */
private fun peopleSummary(names: List<String>): String {
    val shown = names.take(4)
    val rest = names.size - shown.size
    return when {
        rest <= 0 -> shown.joinToString(", ")
        else -> shown.joinToString(", ") + " et $rest autre" + (if (rest > 1) "s" else "")
    }
}

@Composable
private fun UpdateCard(
    versionLabel: String,
    update: UpdateUi,
    onCheck: () -> Unit,
    onDownload: (UpdateChecker.Release) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (update is UpdateUi.Available) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mise à jour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Version installée : $versionLabel", style = MaterialTheme.typography.bodyMedium)
            when (update) {
                UpdateUi.Idle -> Unit
                UpdateUi.Checking -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                UpdateUi.UpToDate -> Text("Vous avez la dernière version.", style = MaterialTheme.typography.bodyMedium)
                is UpdateUi.Available -> Text(
                    "Nouvelle version disponible : ${update.release.version}. Touchez « Télécharger », puis ouvrez le fichier " +
                        "et choisissez « Installer » : vos proches et vos réglages sont conservés.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                is UpdateUi.Failed -> Text(update.message, color = MaterialTheme.colorScheme.error)
            }
            if (update is UpdateUi.Available) {
                Button(onClick = { onDownload(update.release) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Télécharger la version ${update.release.version}")
                }
            } else {
                OutlinedButton(onClick = onCheck, enabled = update !is UpdateUi.Checking, modifier = Modifier.fillMaxWidth()) {
                    Text("Chercher une mise à jour")
                }
            }
            Text(
                "Seul le numéro de version est demandé à internet. Aucune photo n'est envoyée.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val release: UpdateChecker.Release) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

private val RangeColors = lightColorScheme(
    primary = Color(0xFF1F5C45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE8DA),
    onPrimaryContainer = Color(0xFF0B2A1F),
    secondary = Color(0xFF8A5A2B),
    background = Color(0xFFF4F7F4),
    surface = Color.White,
    onSurface = Color(0xFF1B1F1C),
    outline = Color(0xFF7A8A80),
)

private fun appVersionName(context: android.content.Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

@Suppress("DEPRECATION")
private fun versionCode(context: android.content.Context): Long =
    try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
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
        Text(
            if (state.reclassify) "${state.total} photos vont changer de dossier" else "${state.total} photos prêtes à être rangées",
            style = MaterialTheme.typography.headlineSmall,
        )
        if (state.reclassify) {
            Text(
                "${state.unchanged} photos sont déjà au bon endroit. " +
                    (if (state.leftCopies > 0) "${state.leftCopies} copies de proches existantes sont laissées telles quelles. " else "") +
                    "Aucune photo n'est supprimée.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.alreadySorted > 0) {
            Text(
                "${state.alreadySorted} autres photos sont déjà rangées dans « Photos rangées » : elles ne bougent pas.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
private fun DoneScreen(state: UiState.Done, onUndo: () -> Unit, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(state.title, style = MaterialTheme.typography.headlineMedium)
        Text(state.details, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir mes photos rangées") }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Terminer") }
        if (state.hasUndo) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) {
                Text("Annuler ce rangement")
            }
        }
    }
}
