package fr.rangephotos.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.rangephotos.storage.Place
import fr.rangephotos.storage.Places
import fr.rangephotos.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF1F5C45)
private val GreenLight = Color(0xFF2E7D5B)

private val RangeColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EBDF),
    onPrimaryContainer = Color(0xFF0B2A1F),
    secondary = Color(0xFF8A5A2B),
    background = Color(0xFFF3F6F3),
    surface = Color.White,
    onSurface = Color(0xFF1B1F1C),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF7A8A80),
)

@Composable
fun MainScreen(viewModel: MainViewModel, onRequestAccess: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val versionName = remember { appVersionName(context) }
    val versionLabel = "V${versionCode(context)}"
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
    val openFolder: (Place) -> Unit = { place ->
        val opened = try {
            context.startActivity(Places.openIntent(place))
            true
        } catch (e: Exception) {
            false
        }
        if (!opened) {
            val message = "Ouvrez l'appli Fichiers, puis « ${place.title} », puis « Photos rangées » (${place.outputDir.absolutePath})."
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.showNotice(message)
        }
    }

    MaterialTheme(colorScheme = RangeColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = state) {
                is UiState.Home -> HomeScreen(
                    s,
                    versionLabel = versionLabel,
                    versionName = versionName,
                    update = update,
                    onRequestAccess = onRequestAccess,
                    onMode = viewModel::setReclassifyAll,
                    onAnalyze = viewModel::analyze,
                    onOpenFolder = openFolder,
                    onUndo = viewModel::undo,
                    onPeople = viewModel::openPeople,
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
                is UiState.Done -> DoneScreen(
                    s,
                    onUndo = viewModel::undo,
                    onBack = viewModel::backToStart,
                    onOpen = { viewModel.lastPlace()?.let(openFolder) },
                )
            }
        }
    }
}

// ---- Éléments communs ---------------------------------------------------------------------------

@Composable
private fun Header(title: String, subtitle: String? = null, badge: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(GreenLight, Green)),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Text(
                        badge,
                        color = Green,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD5EBDF))
            }
        }
    }
}

@Composable
private fun SectionCard(
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text(text, fontWeight = FontWeight.Medium)
    }
}

// ---- Accueil -------------------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    state: UiState.Home,
    versionLabel: String,
    versionName: String,
    update: UpdateUi,
    onRequestAccess: () -> Unit,
    onMode: (Boolean) -> Unit,
    onAnalyze: (String) -> Unit,
    onOpenFolder: (Place) -> Unit,
    onUndo: () -> Unit,
    onPeople: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (UpdateChecker.Release) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header("Range Photos", "Vos photos rangées toutes seules, sans quitter votre téléphone.", versionLabel) }

        state.notice?.let { message ->
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard(
                        container = if (state.noticeIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }

        if (!state.access) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard {
                        Text("Une autorisation est nécessaire", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Pour ranger vos photos, Range Photos doit pouvoir les déplacer dans des dossiers. " +
                                "Android va ouvrir une page : activez l'interrupteur pour Range Photos, puis revenez ici. " +
                                "Rien ne sort du téléphone.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        PrimaryButton("Autoriser l'accès aux photos", onRequestAccess)
                    }
                }
            }
        } else {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard {
                        Text("Que voulez-vous faire ?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) {
                                if (!state.reclassifyAll) {
                                    PrimaryButton("Nouvelles photos", { onMode(false) })
                                } else {
                                    SecondaryButton("Nouvelles photos", { onMode(false) })
                                }
                            }
                            Box(Modifier.weight(1f)) {
                                if (state.reclassifyAll) {
                                    PrimaryButton("Tout reclasser", { onMode(true) })
                                } else {
                                    SecondaryButton("Tout reclasser", { onMode(true) })
                                }
                            }
                        }
                        Text(
                            if (state.reclassifyAll) {
                                "Nouvelle analyse de toutes les photos, même celles déjà rangées, pour les remettre chacune dans le bon dossier. " +
                                    "Cela peut durer longtemps : gardez l'écran allumé."
                            } else {
                                "Range les photos qui ne sont pas encore dans « Photos rangées »."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Rien ne bouge avant votre accord : vous verrez d'abord un aperçu.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (state.places.isEmpty()) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        SectionCard {
                            Text("Aucun stockage trouvé", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "L'appli ne voit ni la carte SD ni la mémoire du téléphone. Vérifiez que la carte est bien insérée.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            items(state.places, key = { it.place.key }) { item ->
                Box(Modifier.padding(horizontal = 16.dp)) { PlaceCard(item, state.reclassifyAll, onAnalyze, onOpenFolder) }
            }
        }

        if (state.hasUndo) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard {
                        Text("Dernier rangement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Vous pouvez tout remettre comme avant.", style = MaterialTheme.typography.bodyMedium)
                        SecondaryButton("Annuler le dernier rangement", onUndo)
                    }
                }
            }
        }

        if (state.people.isNotEmpty()) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    SectionCard {
                        Text(
                            if (state.people.size == 1) "1 proche reconnu" else "${state.people.size} proches reconnus",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(peopleSummary(state.people), style = MaterialTheme.typography.bodyMedium)
                        SecondaryButton("Gérer mes proches", onPeople)
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                UpdateCard("$versionLabel · $versionName", update, onCheckUpdate, onDownloadUpdate)
            }
        }
    }
}

@Composable
private fun PlaceCard(
    item: PlaceUi,
    reclassifyAll: Boolean,
    onAnalyze: (String) -> Unit,
    onOpenFolder: (Place) -> Unit,
) {
    val place = item.place
    val toSort = item.toSort
    val sorted = item.sorted
    SectionCard {
        Text(
            (if (place.isPrimary) "📱  " else "💾  ") + place.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (toSort == null || sorted == null) {
            Text("Comptage des photos…", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                "${toSort + sorted} photos  ·  $toSort à ranger  ·  $sorted déjà rangées",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        PrimaryButton(
            if (reclassifyAll) "Tout reclasser : ${place.title}" else "Ranger : ${place.title}",
            { onAnalyze(place.key) },
        )
        if (place.outputDir.isDirectory) {
            SecondaryButton("Ouvrir mes photos rangées", { onOpenFolder(place) })
        }
    }
}

/** « Manu, Sandrine, Maman et 25 autres » : lisible même avec une longue liste. */
private fun peopleSummary(names: List<String>): String {
    val shown = names.take(4)
    val rest = names.size - shown.size
    return if (rest <= 0) shown.joinToString(", ") else shown.joinToString(", ") + " et $rest autre" + (if (rest > 1) "s" else "")
}

@Composable
private fun UpdateCard(
    versionLabel: String,
    update: UpdateUi,
    onCheck: () -> Unit,
    onDownload: (UpdateChecker.Release) -> Unit,
) {
    SectionCard(
        container = if (update is UpdateUi.Available) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) {
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
            PrimaryButton("Télécharger la version ${update.release.version}", { onDownload(update.release) })
        } else {
            SecondaryButton("Chercher une mise à jour", onCheck, enabled = update !is UpdateUi.Checking)
        }
        Text(
            "Seul le numéro de version est demandé à internet. Aucune photo n'est envoyée.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data object UpToDate : UpdateUi
    data class Available(val release: UpdateChecker.Release) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

private fun appVersionName(context: Context): String =
    try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

@Suppress("DEPRECATION")
private fun versionCode(context: Context): Long =
    try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }

// ---- Travail en cours ----------------------------------------------------------------------------

@Composable
private fun WorkingScreen(state: UiState.Working) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Range Photos")
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth().height(10.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    "${state.done} / ${state.total}  (${state.done * 100 / state.total} %)",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(10.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Gardez l'écran allumé et l'appli ouverte. Cela peut durer quelques minutes pour beaucoup de photos.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---- Mes proches ---------------------------------------------------------------------------------

@Composable
private fun FaceThumb(bytes: ByteArray) {
    val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(68.dp).clip(RoundedCornerShape(12.dp)))
    }
}

@Composable
private fun PeopleScreen(state: UiState.People, onContinue: (Map<Int, String>) -> Unit, onSkip: () -> Unit) {
    val names = remember { mutableStateMapOf<Int, String>() }
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Mes proches", "Donnez un prénom à ceux que vous voulez retrouver : leurs photos iront dans Portraits / Prénom.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.known.isNotEmpty()) {
                item {
                    Text(
                        "Déjà connus : ${peopleSummary(state.known)}. Tapez le même prénom si c'est la même personne.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(state.clusters, key = { it.id }) { cluster ->
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        cluster.thumbs.forEach { FaceThumb(it) }
                    }
                    Text("Sur ${cluster.photos} photos", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = names[cluster.id] ?: "",
                        onValueChange = { names[cluster.id] = it },
                        label = { Text("Prénom (vide = ne pas nommer)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton("Continuer", { onContinue(names.toMap()) })
            SecondaryButton("Passer cette étape", onSkip)
        }
    }
}

@Composable
private fun ManagePeopleScreen(state: UiState.ManagePeople, onForget: (String) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Mes proches", "Les personnes reconnues automatiquement. « Oublier » ne déplace aucune photo.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.names, key = { it }) { name ->
                SectionCard {
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
        }
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding()) { PrimaryButton("Retour", onBack) }
    }
}

// ---- Aperçu --------------------------------------------------------------------------------------

@Composable
private fun PreviewScreen(
    state: UiState.Preview,
    onCopyChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            if (state.reclassify) "${state.total} photos à reclasser" else "${state.total} photos à ranger",
            "Aperçu : rien n'a encore bougé (${state.placeTitle}).",
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (state.reclassify) {
                    Text(
                        "${state.unchanged} photos sont déjà au bon endroit. " +
                            (if (state.leftCopies > 0) "${state.leftCopies} copies de proches existantes sont laissées telles quelles. " else "") +
                            "Aucune photo n'est supprimée.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else if (state.alreadySorted > 0) {
                    Text(
                        "${state.alreadySorted} autres photos sont déjà rangées : elles ne bougent pas.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item {
                SectionCard {
                    Text("Dossiers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    state.topLevel.forEach { (name, count) -> CountRow(name, count) }
                }
            }
            if (state.hikes.isNotEmpty()) {
                item {
                    SectionCard {
                        Text(
                            "Randonnées détectées (${state.hikes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        state.hikes.forEach { (name, count) -> CountRow(name, count) }
                    }
                }
            }
            if (state.hasNamedPeople) {
                item {
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Les photos sont déplacées dans le dossier « Photos rangées ». Vous pourrez annuler ensuite.",
                style = MaterialTheme.typography.bodySmall,
            )
            PrimaryButton("Ranger maintenant", onConfirm)
            SecondaryButton("Annuler", onCancel)
        }
    }
}

@Composable
private fun CountRow(name: String, count: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, modifier = Modifier.weight(1f))
        Text("$count", fontWeight = FontWeight.SemiBold)
    }
}

// ---- Résultat ------------------------------------------------------------------------------------

@Composable
private fun DoneScreen(state: UiState.Done, onUndo: () -> Unit, onBack: () -> Unit, onOpen: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header(state.title)
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (state.success) Green else MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (state.success) "✓" else "!",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(state.details, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            if (state.failures.isNotEmpty()) {
                item {
                    SectionCard(container = MaterialTheme.colorScheme.errorContainer) {
                        Text("Ce qui n'a pas marché", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        state.failures.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.canOpen) PrimaryButton("Ouvrir mes photos rangées", onOpen)
            if (state.hasUndo) SecondaryButton("Annuler ce rangement", onUndo)
            SecondaryButton("Terminer", onBack)
        }
    }
}
