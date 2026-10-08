package fr.mesphotos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import fr.mesphotos.logic.Tags
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import android.app.Activity
import android.app.KeyguardManager
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

private fun versionOf(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

@Composable
fun MainScreen(viewModel: MainViewModel, onRequestAccess: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val version = remember { versionOf(context) }
    var updateTick by remember { mutableIntStateOf(0) }

    // Appareil photo du téléphone : la photo est enregistrée sur la carte, dans « Photos à trier ».
    var pendingShot by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val path = pendingShot
        pendingShot = null
        if (path != null) viewModel.photoTaken(File(path), ok)
    }
    var askLabel by remember { mutableStateOf(false) }
    // Renommer plusieurs photos d'un coup : dossiers et fichiers choisis, en attente du mot à ajouter.
    var renaming by remember { mutableStateOf<Pair<Set<String>, List<File>>?>(null) }

    // Coffre-fort : on demande le verrouillage du téléphone (code, schéma, empreinte…) avant de l'ouvrir.
    val unlock = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.unlockVault()
    }
    val openVault: () -> Unit = {
        if (viewModel.isVaultOpen()) {
            viewModel.openVault()
        } else {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent = if (keyguard.isDeviceSecure) keyguard.createConfirmDeviceCredentialIntent("Coffre-fort", "Confirmez que c'est bien vous.") else null
            if (intent == null) {
                viewModel.showNotice(
                    "Pour ouvrir le coffre-fort, le téléphone doit avoir un verrouillage (code, schéma ou empreinte). Activez-le dans les réglages d'Android, « Sécurité », puis réessayez.",
                    isError = true,
                )
            } else {
                try {
                    unlock.launch(intent)
                } catch (e: Exception) {
                    viewModel.showNotice("Impossible de demander le verrouillage du téléphone.", isError = true)
                }
            }
        }
    }

    // Écran protégé : pas de capture d'écran, et l'aperçu des applis récentes est noir, tant qu'on est dans le coffre-fort.
    val inVault = state is UiState.VaultView || (state is UiState.Viewer && (state as UiState.Viewer).fromVault)
    DisposableEffect(inVault) {
        var activity: Context? = context
        while (activity is ContextWrapper && activity !is Activity) activity = activity.baseContext
        val window = (activity as? Activity)?.window
        if (inVault) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    val takePhoto: (String) -> Unit = { label ->
        val file = viewModel.newShotFile(label)
        if (file == null) {
            viewModel.showNotice("Impossible de préparer la photo : vérifiez que la carte SD est bien insérée.", isError = true)
        } else {
            try {
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                pendingShot = file.absolutePath
                camera.launch(uri)
            } catch (e: Exception) {
                pendingShot = null
                file.delete()
                viewModel.showNotice("Impossible d'ouvrir l'appareil photo du téléphone.", isError = true)
            }
        }
    }

    // Les deux onglets du bas : Photos, Outils.
    val tabs: (Int) -> (@Composable () -> Unit) = { selected ->
        {
            AppNavBar(
                selected = selected,
                onPhotos = viewModel::openGallery,
                onTools = viewModel::backToStart,
            )
        }
    }

    MesPhotosTheme {
        if (askLabel) {
            TagDialog(
                choices = viewModel.tagChoices(),
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
                onConfirm = { chosen, _ ->
                    askLabel = false
                    takePhoto(Tags.join(chosen))
                },
                onDismiss = { askLabel = false },
            )
        }
        val sharedBatch by viewModel.shared.collectAsStateWithLifecycle()
        sharedBatch?.let { batch ->
            TagDialog(
                title = "Qui ou quoi est sur ces photos ?",
                confirmText = "Retenir pour ${batch.files.size}",
                help = "${batch.files.size} photo(s) retrouvée(s) sur la carte, sur ${batch.received} partagée(s) depuis la galerie du téléphone. Tapez un nom (Najet, Gilles…) : l'appli le retient pour ces photos, sans les renommer ni les déplacer. Ensuite, la loupe et les raccourcis les retrouvent.",
                shortcutOption = true,
                choices = viewModel.tagChoices(),
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
                onConfirm = { chosen, withShortcut ->
                    viewModel.clearShared()
                    viewModel.tagFiles(batch.files, Tags.join(chosen), withShortcut)
                },
                onDismiss = { viewModel.clearShared() },
            )
        }
        renaming?.let { pending ->
            TagDialog(
                title = "Quel mot ajouter ?",
                confirmText = "Renommer",
                help = "Le mot est ajouté devant le nom de chaque photo choisie (« Immatriculation - IMG_….jpg »). Le reste du nom, donc la date, est gardé. Ensuite, vous verrez les photos renommées.",
                shortcutOption = true,
                choices = viewModel.tagChoices(),
                onAdd = viewModel::addTag,
                onRemove = viewModel::removeTag,
                onConfirm = { chosen, withShortcut ->
                    renaming = null
                    viewModel.renameMany(pending.first, pending.second, Tags.join(chosen), withShortcut)
                },
                onDismiss = { renaming = null },
            )
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = state) {
                is UiState.Home -> {
                    updateTick.let { }
                    HomeScreen(
                        s, version, viewModel.updateMessage, viewModel.updateUrl,
                        onRequestAccess = onRequestAccess,
                        onSort = viewModel::analyze,
                        onUndo = viewModel::undo,
                        onOpenTrash = { viewModel.openTrash(MoveKind.TRASH) },
                        onOpenAside = { viewModel.openTrash(MoveKind.ASIDE) },
                        onOpenVault = openVault,
                        onHealth = viewModel::startHealthCheck,
                        onDuplicates = viewModel::findDuplicates,
                        onTakePhoto = { askLabel = true },
                        onCheckUpdate = { viewModel.checkUpdate(version) { updateTick++ } },
                        onDownload = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
                        tabs = tabs(2),
                    )
                }
                is UiState.Working -> WorkingScreen(s)
                is UiState.Preview -> PreviewScreen(s, onConfirm = viewModel::confirm, onCancel = viewModel::backToStart)
                is UiState.StepDone -> StepDoneScreen(
                    s,
                    onViewResult = viewModel::openGallery,
                    onContinue = viewModel::continueSteps,
                    onStop = viewModel::stopSteps,
                    onCancelAll = viewModel::cancelSteps,
                )
                is UiState.Done -> DoneScreen(s, onClean = viewModel::askClean, onUndo = viewModel::undo, onView = viewModel::openGallery, onBack = viewModel::backToStart)
                is UiState.CleanConfirm -> CleanConfirmScreen(s, onConfirm = viewModel::confirmClean, onCancel = viewModel::backToStart)
                is UiState.Browse -> BrowseScreen(
                    s,
                    onInto = viewModel::browseInto,
                    onUp = viewModel::browseUp,
                    onOpen = viewModel::openViewer,
                    onMove = viewModel::moveSelection,
                    onUndoMove = viewModel::undoMove,
                    onSearch = viewModel::openSearch,
                    onTakePhoto = { askLabel = true },
                    onRename = { folders, files -> renaming = folders to files },
                    onOpenAll = viewModel::openAllPhotos,
                    onOpenShortcut = viewModel::openShortcut,
                    onSaveShortcut = viewModel::saveShortcut,
                    onRemoveShortcut = viewModel::removeShortcut,
                    categories = viewModel::shortcutCategories,
                    tabs = tabs(0),
                )
                is UiState.Viewer -> ViewerScreen(
                    s,
                    onClose = viewModel::closeViewer,
                    onMove = { kind, file -> viewModel.moveSelection(kind, emptySet(), listOf(file)) },
                    onRename = viewModel::renameFile,
                    categories = viewModel::shortcutCategories,
                    onSaveShortcut = { name, category, words -> viewModel.saveShortcut(name, category, words, null) },
                    onRestoreFromVault = { file -> viewModel.restoreFromVaultFile(file) },
                )
                is UiState.HealthView -> HealthScreen(s, onBack = viewModel::backToStart, onRepair = viewModel::repairHealth, onTrashUnusable = viewModel::trashUnusable)
                is UiState.VaultView -> VaultScreen(
                    s,
                    onBack = viewModel::backToStart,
                    onOpen = viewModel::openVaultViewer,
                    onRestore = viewModel::restoreFromVault,
                    onDeleteForever = viewModel::deleteFromVault,
                )
                is UiState.Search -> if (s.query == ALL_QUERY) AllPhotosScreen(
                    s,
                    onBack = viewModel::openGallery,
                    onOrder = viewModel::setAllOrder,
                    onHideRenamed = viewModel::setHideRenamed,
                    onOpen = viewModel::openViewer,
                    onMove = viewModel::moveSelection,
                    onUndoMove = viewModel::undoMove,
                    onRename = { folders, files -> renaming = folders to files },
                ) else SearchScreen(
                    s,
                    onSearch = viewModel::search,
                    onBack = viewModel::openGallery,
                    onOpen = viewModel::openViewer,
                    onMove = viewModel::moveSelection,
                    onUndoMove = viewModel::undoMove,
                    onRename = { folders, files -> renaming = folders to files },
                )
                is UiState.TrashView -> TrashScreen(
                    s,
                    onBack = viewModel::backToStart,
                    onRestore = { items -> viewModel.restoreFromTrash(s.kind, items) },
                    onDeleteForever = viewModel::deleteFromTrash,
                    onToVault = { items -> viewModel.moveStashToVault(s.kind, items) },
                )
                is UiState.DupReview -> DupReviewScreen(
                    s,
                    onBack = viewModel::backToStart,
                    onOpen = viewModel::openViewer,
                    onTrash = { files -> viewModel.moveSelection(MoveKind.TRASH, emptySet(), files) },
                    onUndoMove = viewModel::undoMove,
                )
            }
        }
    }
}

// ---- Outils (l'ancien accueil) ---------------------------------------------------------------------

private class Tool(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val highlight: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
private fun HomeScreen(
    state: UiState.Home,
    version: String,
    updateMessage: String?,
    updateUrl: String?,
    onRequestAccess: () -> Unit,
    onSort: () -> Unit,
    onUndo: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAside: () -> Unit,
    onOpenVault: () -> Unit,
    onHealth: () -> Unit,
    onDuplicates: () -> Unit,
    onTakePhoto: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: (String) -> Unit,
    tabs: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(
            "Outils",
            if (state.access && state.hasCard) "${state.toSort?.let { spaced(it) } ?: "…"} à ranger  ·  ${state.sorted?.let { spaced(it) } ?: "…"} rangées" else null,
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.notice?.let { message ->
                item { NoticeCard(message, isError = state.noticeIsError) }
            }

            if (!state.access) {
                item {
                    Section {
                        Title("Une autorisation est nécessaire")
                        Body(
                            "Pour ranger vos photos, l'appli doit pouvoir les déplacer dans des dossiers. " +
                                "Android va ouvrir une page : activez l'interrupteur pour Mes Photos, puis revenez ici. Rien ne sort du téléphone.",
                        )
                        BigButton("Autoriser l'accès", onRequestAccess)
                    }
                }
            } else if (!state.hasCard) {
                item {
                    Section {
                        Title("Aucune carte SD trouvée")
                        Body("L'appli ne range que sur la carte SD, jamais dans la mémoire du téléphone. Vérifiez que la carte est bien insérée, puis rouvrez l'appli.")
                    }
                }
            } else {
                item { SortCard(state.toSort, onSort) }

                // Un menu déroulant par catégorie, fermé au départ : des boutons à plat, un par ligne.
                val groups = listOf(
                    ToolGroup(
                        "Prendre une photo",
                        listOf(Tool(AppIcons.Camera, "Prendre une photo", "Enregistrée sur la carte, dans « Photos à trier »", onClick = onTakePhoto)),
                    ),
                    ToolGroup(
                        "Doublons",
                        listOf(
                            Tool(AppIcons.Copy, "Doublons", "Comparer et garder une seule photo", onClick = onDuplicates),
                        ),
                    ),
                    ToolGroup(
                        "Mises de côté",
                        listOf(
                            Tool(AppIcons.Shield, "Coffre-fort", "Photos cachées, avec le verrouillage du téléphone", onClick = onOpenVault),
                            Tool(Icons.Default.Lock, "À l'écart", if (state.asideCount > 0) "${spaced(state.asideCount)} fichier(s)" else "Vide", onClick = onOpenAside),
                            Tool(Icons.Default.Delete, "Corbeille", if (state.trashCount > 0) "${spaced(state.trashCount)} fichier(s)" else "Vide", onClick = onOpenTrash),
                        ),
                    ),
                    ToolGroup(
                        "Réparation",
                        buildList {
                            add(Tool(Icons.Default.Check, "Vérifier les photos", "Repère les fichiers abîmés ou brouillés, et nettoie", onClick = onHealth))
                            if (state.hasUndo) add(Tool(Icons.Default.Refresh, "Annuler le rangement", "Tout remettre comme avant", onClick = onUndo))
                        },
                    ),
                )
                groups.forEach { group -> item { ToolGroupCard(group) } }
            }

            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (updateMessage != null) Text(updateMessage, style = MaterialTheme.typography.bodySmall)
                    if (updateUrl != null) BigButton("Télécharger la nouvelle version", { onDownload(updateUrl) })
                    TextButton(onClick = onCheckUpdate) { Text("Chercher une mise à jour") }
                }
            }
        }
        tabs()
    }
}

/** Le gros bouton « Ranger » : une seule carte, compacte. */
@Composable
private fun SortCard(toSort: Int?, onSort: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Ranger mes photos", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    when {
                        toSort == null -> "Comptage…"
                        toSort == 0 -> "Tout est rangé par date."
                        else -> "${spaced(toSort)} fichier(s) à ranger. Aperçu et essai avant."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Button(onClick = onSort, shape = RoundedCornerShape(16.dp)) { Text("Ranger", fontWeight = FontWeight.Bold) }
        }
    }
}

/** Une case d'outil : icône, nom, une ligne de détail. */
private class ToolGroup(val title: String, val tools: List<Tool>, val badge: String? = null)

/** Un menu déroulant : le titre de la catégorie, et dessous (une fois ouvert) un grand bouton à plat par outil. */
@Composable
private fun ToolGroupCard(group: ToolGroup) {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (group.badge != null) {
                    Text(group.badge, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (open) "▴" else "▾", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (open) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.tools.forEach { ToolRow(it) }
                }
            }
        }
    }
}

/** Un outil : un bouton à plat sur toute la largeur (icône ronde, nom, petite explication). */
@Composable
private fun ToolRow(tool: Tool) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (tool.highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = tool.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(tool.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkingScreen(state: UiState.Working) {
    val view = LocalView.current
    DisposableEffect(state.onStop != null) {
        if (state.onStop != null) view.keepScreenOn = true
        onDispose { if (state.onStop != null) view.keepScreenOn = false }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Mes Photos")
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.label, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            if (state.total > 0) {
                LinearProgressIndicator(
                    progress = { state.done.toFloat() / state.total },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                )
                Spacer(Modifier.height(10.dp))
                Text("${spaced(state.done)} / ${spaced(state.total)}", style = MaterialTheme.typography.bodyLarge)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
            }
            Spacer(Modifier.height(20.dp))
            Text("Gardez l'écran allumé.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.onStop != null) {
                Spacer(Modifier.height(24.dp))
                TonalBigButton("Arrêter et voir ce qui est trouvé", onClick = state.onStop)
            }
        }
    }
}

@Composable
private fun BottomActions(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun PreviewScreen(state: UiState.Preview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("${spaced(state.toMove)} fichiers à ranger", "Aperçu : rien n'a encore bougé.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section {
                    Title("Où ils iront")
                    Body("Journées / année / mois / jour (avec la ville si internet). Photos et vidéos ensemble.")
                    if (state.alreadyOk > 0) Body("${spaced(state.alreadyOk)} sont déjà au bon endroit : ils ne bougent pas.")
                }
            }
            if (state.townsUnavailable) {
                item {
                    NoticeCard("Pas d'internet : les dossiers de jours n'auront pas le nom de la ville. Pour les villes, annulez, connectez-vous et recommencez.", isError = true)
                }
            }
            if (state.nothing > 0) {
                item {
                    Section(MaterialTheme.colorScheme.secondaryContainer) {
                        Title("${spaced(state.nothing)} fichier(s) sans date ni lieu")
                        Body("Rien ne dit quand ni où ils ont été pris. Ils iront tous ensemble dans un seul dossier, « Journées / Sans date ni lieu », pour que vous les triiez vous-même.")
                    }
                }
            }
            if (state.duplicates > 0) {
                item {
                    Section(MaterialTheme.colorScheme.secondaryContainer) {
                        Title("${spaced(state.duplicates)} doublon(s) exact(s)")
                        Body("Ce sont des copies identiques d'une photo déjà présente. Elles iront toutes dans un seul dossier, « Doublons ». Rien n'est supprimé : vous les comparerez ensuite avec « Chercher les doublons ».")
                    }
                }
            }
            if (state.undated > 0) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Title("${state.undated} fichier(s) sans date fiable")
                        Body("Ni le fichier ni son nom ne donnent la date de prise de vue, mais le lieu est connu. Pour ne pas les mélanger avec vos vrais souvenirs, ils iront à part : « Journées / Date incertaine ».")
                    }
                }
            }
            if (state.years.isNotEmpty()) {
                item {
                    Section {
                        Title("Par année")
                        state.years.forEach { (year, count) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(year, style = MaterialTheme.typography.bodyLarge)
                                Text(spaced(count), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
        BottomActions {
            Text("D'abord un essai de 10 fichiers, pour que vous regardiez. Vous pourrez tout annuler.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BigButton("Commencer par 10 fichiers", onConfirm, enabled = state.toMove > 0)
            SoftButton("Annuler", onCancel)
        }
    }
}

@Composable
private fun StepDoneScreen(
    state: UiState.StepDone,
    onViewResult: () -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    onCancelAll: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Essai terminé", "Rien d'autre n'a bougé. Regardez, puis décidez.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section {
                    Title("${state.moved} fichier(s) rangés")
                    Body("Dossiers créés :")
                    state.folders.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                    if (state.problems.isEmpty()) Body("Contrôle fichier par fichier : tout est bien arrivé.")
                }
            }
            if (state.problems.isNotEmpty()) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Title("À regarder avant de continuer")
                        state.problems.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { Body("Il reste ${spaced(state.remaining)} fichier(s) à ranger. « Tout annuler » remet ces fichiers exactement où ils étaient.") }
        }
        BottomActions {
            SoftButton("Voir le résultat", onViewResult)
            BigButton("Continuer : ranger les ${spaced(state.remaining)} autres", onContinue)
            SoftButton("Arrêter ici (garder ce lot)", onStop)
            SoftButton("Tout annuler", onCancelAll)
        }
    }
}

@Composable
private fun DoneScreen(state: UiState.Done, onClean: () -> Unit, onUndo: () -> Unit, onView: () -> Unit, onBack: () -> Unit) {
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
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(if (state.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (state.success) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(state.details, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            if (state.failures.isNotEmpty()) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Title("Ce qui n'a pas marché")
                        state.failures.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        BottomActions {
            BigButton("Voir mes photos", onView)
            if (state.cleanable > 0) SoftButton("Voir les ${state.cleanable} anciens dossiers vides à supprimer", onClean)
            if (state.hasUndo) SoftButton("Annuler ce rangement", onUndo)
            SoftButton("Terminer", onBack)
        }
    }
}

@Composable
private fun CleanConfirmScreen(state: UiState.CleanConfirm, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Supprimer les anciens dossiers", "${state.folders.size} dossier(s) vides. Aucune photo ne sera supprimée.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Body("Ces dossiers sont vides (aucun fichier dedans). Si l'un d'eux contient quelque chose au dernier moment, il sera gardé.") }
            items(state.folders) { folder -> Text("📁  $folder", style = MaterialTheme.typography.bodyMedium) }
        }
        BottomActions {
            BigButton("Oui, supprimer ces dossiers vides", onConfirm)
            SoftButton("Non, les garder", onCancel)
        }
    }
}

// ---- Que photographiez-vous ? --------------------------------------------------------------------------

/**
 * Avant la photo : on coche un ou plusieurs mots (« Immatriculation », « Véhicule », « Montagne »…) ou on en ajoute.
 * Tous vont devant le nom du fichier, donc la loupe retrouve la photo par n'importe lequel. Un appui long retire un mot de la liste.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun TagDialog(
    title: String = "Que photographiez-vous ?",
    confirmText: String = "Prendre la photo",
    help: String? = null,
    /** Vrai : une case « Créer aussi le raccourci » (cochée) est proposée ; sa valeur est donnée à [onConfirm]. */
    shortcutOption: Boolean = false,
    choices: List<String>,
    onAdd: (String) -> String?,
    onRemove: (String) -> Unit,
    onConfirm: (List<String>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var words by remember { mutableStateOf(choices) }
    var picked by remember { mutableStateOf(emptyList<String>()) }
    var typed by remember { mutableStateOf("") }
    var makeShortcut by remember { mutableStateOf(true) }

    fun addTyped() {
        val tag = onAdd(typed) ?: return
        if (tag !in words) words = words + tag
        if (tag !in picked) picked = picked + tag
        typed = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Touchez un ou plusieurs mots.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    words.forEach { word ->
                        val isPicked = picked.contains(word)
                        WordChip(
                            word,
                            isPicked,
                            onClick = { picked = if (isPicked) picked - word else picked + word },
                            // Un appui long retire le mot de la liste (les photos déjà prises gardent leur nom).
                            onLongClick = {
                                onRemove(word)
                                words = words - word
                                picked = picked - word
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        placeholder = { Text("Un autre mot…") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { addTyped() }, enabled = typed.isNotBlank()) { Text("Ajouter") }
                }
                if (shortcutOption) {
                    Row(
                        Modifier.fillMaxWidth().clickable { makeShortcut = !makeShortcut },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = makeShortcut, onCheckedChange = { makeShortcut = it })
                        Text("Créer aussi le raccourci (premier mot = nom, deuxième = classement)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (picked.isNotEmpty()) {
                    Text("Nom de la photo : " + Tags.join(picked) + " - IMG_…", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    help ?: "Les mots sont mis devant le nom de la photo : la loupe la retrouve par n'importe lequel. Le premier mot touché pourra servir de nom de raccourci, le deuxième de classement. Appui long sur un mot : le retirer de la liste. Vous pouvez aussi ne rien choisir.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Un mot tapé mais pas encore ajouté compte aussi.
                val extra = onAdd(typed)
                onConfirm(if (extra != null && extra !in picked) picked + extra else picked, makeShortcut && shortcutOption)
            }) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Un mot à cocher (touché : choisi / pas choisi ; appui long : retiré de la liste). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordChip(text: String, picked: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(if (picked) "✓ $text" else text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (picked) FontWeight.Bold else FontWeight.Normal)
    }
}
