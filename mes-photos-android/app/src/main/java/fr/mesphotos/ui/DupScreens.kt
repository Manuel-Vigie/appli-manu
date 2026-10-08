package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.logic.DupGroup
import java.io.File

private fun sizeText(file: File): String {
    val mb = file.length() / 1_048_576.0
    return if (mb >= 1) "%.1f Mo".format(mb) else "${(file.length() / 1024).coerceAtLeast(1)} Ko"
}

/** Les doublons par groupes : une photo « à garder » par groupe (proposée par l'appli, modifiable), les autres vont à la corbeille après confirmation. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DupReviewScreen(
    state: UiState.DupReview,
    onBack: () -> Unit,
    onOpen: (List<File>, Int) -> Unit,
    onTrash: (List<File>) -> Unit,
    onUndoMove: () -> Unit,
) {
    var keeps by remember(state) { mutableStateOf(state.groups.map { it.keep.absolutePath }) }
    var confirm by remember(state) { mutableStateOf(false) }
    val toDelete = remember(state, keeps) {
        state.groups.flatMapIndexed { i, g -> g.files.filter { it.absolutePath != keeps[i] } }
    }
    BackHandler { onBack() }

    Column(modifier = Modifier.fillMaxSize()) {
        Header(
            title = "Doublons",
            subtitle = if (state.groups.isEmpty()) "Aucun doublon trouvé." else
                "${spaced(state.groups.size)} groupe(s), ${spaced(toDelete.size)} copie(s) en trop. Touchez la photo à garder ; appui long pour l'agrandir.",
            top = {
                TextButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Outils", color = Color.White, fontSize = 16.sp)
                }
            },
        )

        state.message?.let { message ->
            Box(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                NoticeCard(
                    message,
                    isError = false,
                    actionLabel = if (state.undoTrash > 0) "Annuler" else null,
                    onAction = if (state.undoTrash > 0) onUndoMove else null,
                )
            }
        }

        if (state.groups.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune photo ni vidéo en double dans « Photos rangées ».",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "auto") {
                    val automatic = state.groups.map { it.keep.absolutePath }
                    Section(MaterialTheme.colorScheme.secondaryContainer) {
                        Title("Sélection automatique")
                        Body("L'appli garde la meilleure photo de chaque groupe (l'original) et sélectionne toutes les autres : ${spaced(toDelete.size)} copie(s) marquées « Corbeille ».")
                        SoftButton(
                            "Sélectionner automatiquement tous les doublons",
                            { keeps = automatic },
                            enabled = keeps != automatic,
                        )
                    }
                }
                itemsIndexed(state.groups, key = { _, g -> "g:" + g.keep.absolutePath }) { index, group ->
                    GroupCard(group, keepPath = keeps.getOrNull(index), onKeep = { path ->
                        keeps = keeps.toMutableList().also { it[index] = path }
                    }, onOpen = onOpen)
                }
            }
            DupBar(count = toDelete.size, onTrash = { confirm = true })
        }
    }

    if (confirm) {
        ConfirmMoveDialog(
            kind = MoveKind.TRASH,
            summary = "${spaced(toDelete.size)} copie(s) en double, en gardant une photo par groupe (${spaced(state.groups.size)} photos gardées).",
            onConfirm = {
                confirm = false
                onTrash(toDelete)
            },
            onDismiss = { confirm = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupCard(group: DupGroup, keepPath: String?, onKeep: (String) -> Unit, onOpen: (List<File>, Int) -> Unit) {
    Section {
        Text(
            "${group.files.size} exemplaires identiques · ${sizeText(group.files.first())}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(group.files, key = { _, f -> f.absolutePath }) { index, file ->
                val kept = file.absolutePath == keepPath
                Column(Modifier.width(132.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .size(132.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .then(if (kept) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier)
                            .combinedClickable(
                                onClick = { onKeep(file.absolutePath) },
                                onLongClick = { onOpen(group.files, index) },
                            ),
                    ) {
                        Thumb(file, 256, Modifier.fillMaxSize())
                        Text(
                            if (kept) "À garder" else "Corbeille",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (kept) MaterialTheme.colorScheme.primary else Color(0xCCB3261E))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        file.parentFile?.name.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DupBar(count: Int, onTrash: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DangerButton(
                if (count > 1) "Mettre ${spaced(count)} copies à la corbeille" else "Mettre 1 copie à la corbeille",
                onTrash,
                enabled = count > 0,
            )
            Text(
                "Une photo est gardée par groupe. Rien n'est effacé : tout peut être remis depuis la corbeille.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
