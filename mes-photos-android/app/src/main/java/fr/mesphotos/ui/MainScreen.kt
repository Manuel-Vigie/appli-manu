package fr.mesphotos.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mesphotos.gallery.FolderItem
import fr.mesphotos.gallery.Thumbs
import fr.mesphotos.storage.PhotoFiles
import java.io.File

private val Green = Color(0xFF1F5C45)
private val GreenLight = Color(0xFF2F7D5E)
private val Cream = Color(0xFFF6F3EC)

private val AppColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EBDD),
    onPrimaryContainer = Color(0xFF0E3322),
    background = Cream,
    surface = Color.White,
    errorContainer = Color(0xFFFBE3DD),
)

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

    MaterialTheme(colorScheme = AppColors) {
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
                is UiState.Browse -> BrowseScreen(s, onInto = viewModel::browseInto, onUp = viewModel::browseUp, onOpen = viewModel::openViewer)
                is UiState.Viewer -> ViewerScreen(s, onClose = viewModel::closeViewer)
            }
        }
    }
}

// ---- Éléments communs ----------------------------------------------------------------------------

@Composable
private fun Header(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenLight, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp)
    }
}

@Composable
private fun Section(container: Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(64.dp), shape = RoundedCornerShape(16.dp)) {
        Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SoftButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge)

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
    onCheckUpdate: () -> Unit,
    onDownload: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Header("Mes Photos", "Vos photos et vidéos de la carte SD, rangées par date.") }

        state.notice?.let { message ->
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Section(if (state.noticeIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                        Text(message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                }
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
                        val toSort = state.toSort
                        val sorted = state.sorted
                        if (toSort == null || sorted == null) {
                            Body("Comptage des photos…")
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            Title("${toSort + sorted} photos et vidéos sur la carte")
                            Body("$toSort à ranger  ·  $sorted déjà dans « Photos rangées »")
                        }
                        BigButton("Ranger mes photos", onSort)
                        Text(
                            "Rien ne bouge avant votre accord. Vous voyez un aperçu, puis un petit essai de 10 fichiers.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            item {
                Box(Modifier.padding(horizontal = 16.dp)) { BigButton("Voir mes photos", onView) }
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
private fun WorkingScreen(state: UiState.Working) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("Mes Photos")
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.done.toFloat() / state.total }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("${state.done} / ${state.total}")
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Text("Gardez l'écran allumé.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- Ranger : aperçu, lot test, résultat ---------------------------------------------------------

@Composable
private fun PreviewScreen(state: UiState.Preview, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Header("${state.toMove} fichiers à ranger", "Aperçu : rien n'a encore bougé.")
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section {
                    Title("Où ils iront")
                    Body("Journées / année / mois / jour (avec la ville si internet). Photos et vidéos ensemble.")
                    if (state.alreadyOk > 0) Body("${state.alreadyOk} sont déjà au bon endroit : ils ne bougent pas.")
                }
            }
            if (state.townsUnavailable) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Body("Pas d'internet : les dossiers de jours n'auront pas le nom de la ville. Pour les villes, annulez, connectez-vous et recommencez.")
                    }
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
                                Text(year)
                                Text("$count", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("D'abord un essai de 10 fichiers, pour que vous regardiez. Vous pourrez tout annuler.", style = MaterialTheme.typography.bodySmall)
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
                    state.folders.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    if (state.problems.isEmpty()) Body("Contrôle fichier par fichier : tout est bien arrivé.")
                }
            }
            if (state.problems.isNotEmpty()) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Title("À regarder avant de continuer")
                        state.problems.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
            item { Body("Il reste ${state.remaining} fichier(s) à ranger. « Tout annuler » remet ces fichiers exactement où ils étaient.") }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SoftButton("Voir le résultat", onViewResult)
            BigButton("Continuer : ranger les ${state.remaining} autres", onContinue)
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
                        modifier = Modifier.size(48.dp).clip(CircleShape).background(if (state.success) Green else MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (state.success) "✓" else "!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(state.details, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            if (state.failures.isNotEmpty()) {
                item {
                    Section(MaterialTheme.colorScheme.errorContainer) {
                        Title("Ce qui n'a pas marché")
                        state.failures.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Body("Ces dossiers sont vides (aucun fichier dedans). Si l'un d'eux contient quelque chose au dernier moment, il sera gardé.") }
            items(state.folders.size) { i -> Text("📁  ${state.folders[i]}", style = MaterialTheme.typography.bodyMedium) }
        }
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BigButton("Oui, supprimer ces dossiers vides", onConfirm)
            SoftButton("Non, les garder", onCancel)
        }
    }
}

// ---- Galerie -------------------------------------------------------------------------------------

@Composable
private fun Thumb(file: File, size: Int, modifier: Modifier = Modifier) {
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file) { bitmap = Thumbs.load(file, size) }
    Box(modifier.background(Color(0xFFDDE3DF)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        if (PhotoFiles.isVideo(file)) {
            Text("▶", color = Color.White, fontSize = 22.sp, modifier = Modifier.background(Color(0x99000000), CircleShape).padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

@Composable
private fun BrowseScreen(state: UiState.Browse, onInto: (String) -> Unit, onUp: () -> Unit, onOpen: (List<File>, Int) -> Unit) {
    BackHandler { onUp() }
    val title = if (state.path.isEmpty()) "Mes photos" else state.path.last()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GreenLight, RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onUp) { Text(if (state.path.isEmpty()) "← Accueil" else "← Retour", color = Color.White, fontSize = 16.sp) }
            }
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
            if (state.path.size > 1) {
                Text(state.path.dropLast(1).joinToString(" / "), color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        state.message?.let { Text(it, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.folders, key = { "d:" + it.name }, span = { GridItemSpan(maxLineSpan) }) { folder -> FolderRow(folder, onInto) }
            itemsIndexed(state.files, key = { _, f -> "f:" + f.absolutePath }) { index, file ->
                Thumb(file, 256, Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onOpen(state.files, index) })
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderItem, onInto: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onInto(folder.name) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val cover = folder.cover
            if (cover != null) Thumb(cover, 200, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFDDE3DF)))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(if (folder.count > 1) "${folder.count} photos et vidéos" else "1 photo ou vidéo", style = MaterialTheme.typography.bodyMedium)
            }
            Text("›", fontSize = 28.sp)
        }
    }
}

@Composable
private fun ViewerScreen(state: UiState.Viewer, onClose: () -> Unit) {
    BackHandler { onClose() }
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = state.index.coerceIn(0, (state.files.size - 1).coerceAtLeast(0))) { state.files.size }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val file = state.files[page]
            var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(file) { bitmap = Thumbs.load(file, 1600) }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), contentDescription = file.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
                if (bitmap == null) Text("Chargement…", color = Color.White)
                if (PhotoFiles.isVideo(file)) {
                    Button(onClick = { playVideo(context, file) }, modifier = Modifier.height(60.dp)) { Text("▶  Lire la vidéo", fontSize = 18.sp) }
                }
            }
        }
        val current = state.files.getOrNull(pager.currentPage)
        Column(
            modifier = Modifier.fillMaxWidth().background(Color(0x99000000)).statusBarsPadding().padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("✕ Fermer", color = Color.White, fontSize = 16.sp) }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${state.files.size}", color = Color.White)
            }
            if (current != null) {
                Text(current.name, color = Color.White, fontSize = 14.sp)
                Text(dateText(current), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
            }
        }
    }
}

private fun dateText(file: File): String =
    "Fichier du " + java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.FRANCE).format(java.util.Date(file.lastModified()))

private fun playVideo(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, PhotoFiles.mimeOf(file) ?: "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Impossible d'ouvrir la vidéo.", android.widget.Toast.LENGTH_LONG).show()
    }
}
