package fr.mesphotos.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    MesPhotosTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val s = state) {
                is UiState.Home -> {
                    updateTick.let { }
                    HomeScreen(
                        s, version, viewModel.updateMessage, viewModel.updateUrl,
                        onRequestAccess = onRequestAccess,
                        onSort = viewModel::analyze,
                        onView = viewModel::openGallery,
                        onUndo = viewModel::undo,
                        onOpenTrash = { viewModel.openTrash(MoveKind.TRASH) },
                        onOpenAside = { viewModel.openTrash(MoveKind.ASIDE) },
                        onSearch = viewModel::openSearch,
                        onCheckUpdate = { viewModel.checkUpdate(version) { updateTick++ } },
                        onDownload = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        },
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
                )
                is UiState.Viewer -> ViewerScreen(
                    s,
                    onClose = viewModel::closeViewer,
                    onMove = { kind, file -> viewModel.moveSelection(kind, emptySet(), listOf(file)) },
                )
                is UiState.TrashView -> TrashScreen(
                    s,
                    onBack = viewModel::backToStart,
                    onRestore = { items -> viewModel.restoreFromTrash(s.kind, items) },
                    onDeleteForever = viewModel::deleteFromTrash,
                )
                is UiState.Search -> SearchScreen(
                    s,
                    onSearch = viewModel::search,
                    onBack = viewModel::backToStart,
                    onOpen = viewModel::openViewer,
                    onMove = viewModel::moveSelection,
                    onUndoMove = viewModel::undoMove,
                )
            }
        }
    }
}

// ---- Accueil -------------------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    state: UiState.Home,
    version: String,
    updateMessage: String?,
    updateUrl: String?,
    onRequestAccess: () -> Unit,
    onSort: () -> Unit,
    onView: () -> Unit,
    onUndo: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenAside: () -> Unit,
    onSearch: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Header("Mes Photos", "Votre carte SD, rangée par date.") {
                if (state.access && state.hasCard) {
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatChip(state.toSort?.let { spaced(it) } ?: "…", "à ranger", Modifier.weight(1f))
                        StatChip(state.sorted?.let { spaced(it) } ?: "…", "déjà rangées", Modifier.weight(1f))
                    }
                }
            }
        }

        state.notice?.let { message ->
            item {
                Box(Modifier.padding(horizontal = 16.dp)) { NoticeCard(message, isError = state.noticeIsError) }
            }
        }

        if (!state.access) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Section {
                        Title("Une autorisation est nécessaire")
                        Body(
                            "Pour ranger vos photos, l'appli doit pouvoir les déplacer dans des dossiers. " +
                                "Android va ouvrir une page : activez l'interrupteur pour Mes Photos, puis revenez ici. Rien ne sort du téléphone.",
                        )
                        BigButton("Autoriser l'accès", onRequestAccess)
                    }
                }
            }
        } else if (!state.hasCard) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Section {
                        Title("Aucune carte SD trouvée")
                        Body("L'appli ne range que sur la carte SD, jamais dans la mémoire du téléphone. Vérifiez que la carte est bien insérée, puis rouvrez l'appli.")
                    }
                }
            }
        } else {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Section {
                        Title("Ranger la carte")
                        if (state.toSort == null || state.sorted == null) {
                            Body("Comptage des photos…")
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            Body("Chaque photo ira dans le dossier de son jour.")
                        }
                        BigButton("Ranger mes photos", onSort)
                        Text(
                            "Rien ne bouge avant votre accord. Vous voyez un aperçu, puis un petit essai de 10 fichiers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) { TonalBigButton("Voir mes photos", onView) }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) { SoftButton("Rechercher une photo", onSearch) }
            }
            if (state.asideCount > 0) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) { StashTile(MoveKind.ASIDE, state.asideCount, onOpenAside) }
                }
            }
            if (state.trashCount > 0) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) { StashTile(MoveKind.TRASH, state.trashCount, onOpenTrash) }
                }
            }
        }

        if (state.hasUndo) {
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Section {
                        Title("Dernier rangement")
                        Body("Vous pouvez tout remettre comme avant.")
                        SoftButton("Annuler le dernier rangement", onUndo)
                    }
                }
            }
        }

        item {
            Box(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
                Section {
                    Text("Version $version", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    if (updateMessage != null) Text(updateMessage, style = MaterialTheme.typography.bodyMedium)
                    if (updateUrl != null) BigButton("Télécharger la nouvelle version", { onDownload(updateUrl) })
                    TextButton(onClick = onCheckUpdate) { Text("Chercher une mise à jour") }
                }
            }
        }
    }
}

@Composable
private fun StashTile(kind: MoveKind, count: Int, onClick: () -> Unit) {
    val aside = kind == MoveKind.ASIDE
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (aside) Icons.Default.Lock else Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
            }
            Column(Modifier.weight(1f)) {
                Text(if (aside) "À l'écart" else "Corbeille", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    when {
                        aside && count > 1 -> "${spaced(count)} fichiers rangés à part"
                        aside -> "1 fichier rangé à part"
                        count > 1 -> "${spaced(count)} fichiers à remettre ou à supprimer"
                        else -> "1 fichier à remettre ou à supprimer"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Text("›", fontSize = 28.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun WorkingScreen(state: UiState.Working) {
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
        }
    }
}

// ---- Ranger : aperçu, lot test, résultat ---------------------------------------------------------

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
            if (state.undated > 0) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Title("${state.undated} fichier(s) sans date fiable")
                        Body("Ni le fichier ni son nom ne donnent la date de prise de vue. Pour ne pas les mélanger avec vos vrais souvenirs, ils iront à part : « Journées / Date incertaine ».")
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
