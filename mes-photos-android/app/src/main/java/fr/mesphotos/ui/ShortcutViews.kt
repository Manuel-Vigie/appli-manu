package fr.mesphotos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.gallery.Gallery
import fr.mesphotos.logic.Shortcut

// ---- Raccourcis : grands boutons colorés avec le nombre de photos --------------------------------------

/** Fond, bord et texte d'un bouton raccourci (tons pastel : lisibles en clair comme en sombre). */
private class ShortcutColors(val background: Color, val border: Color, val text: Color)

private val PALETTE = listOf(
    ShortcutColors(Color(0xFFFBE9E9), Color(0xFFD9A0A0), Color(0xFF9B2226)), // rouge
    ShortcutColors(Color(0xFFFDEBD6), Color(0xFFE3A55B), Color(0xFF8A4B08)), // orange
    ShortcutColors(Color(0xFFE4F0F6), Color(0xFF8DB4C9), Color(0xFF1E5F7A)), // bleu clair
    ShortcutColors(Color(0xFFE8EEF7), Color(0xFF9DB1CF), Color(0xFF1F4E79)), // bleu
    ShortcutColors(Color(0xFFEEE9F7), Color(0xFFB5A5D6), Color(0xFF4B3A8A)), // violet
    ShortcutColors(Color(0xFFE6F3E8), Color(0xFF93C49B), Color(0xFF1F6B34)), // vert
)

/** Une couleur par catégorie : tous les raccourcis d'une même catégorie ont la même. */
private fun colorsFor(category: String): ShortcutColors =
    PALETTE[Math.floorMod(Gallery.normalize(category).hashCode(), PALETTE.size)]

/**
 * Les raccourcis, un grand bouton par raccourci : son nom et, à droite, le nombre de photos. Groupés par catégorie.
 * Un appui ouvre les photos ; un appui long ([onLongClick]) permet de modifier ou retirer le raccourci.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutRows(items: List<ShortcutItem>, onOpen: (ShortcutItem) -> Unit, onLongClick: ((ShortcutItem) -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.groupBy { it.shortcut.category }.forEach { (category, list) ->
            Text(
                category,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            )
            list.forEach { item ->
                val colors = colorsFor(item.shortcut.category)
                val shape = RoundedCornerShape(18.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.background)
                        .border(1.5.dp, colors.border, shape)
                        .combinedClickable(onClick = { onOpen(item) }, onLongClick = onLongClick?.let { handler -> { handler(item) } })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        item.shortcut.name,
                        color = colors.text,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    CountPill(item.count)
                }
            }
        }
    }
}

/** Le petit rond (en fait une pastille) avec le nombre de photos. */
@Composable
private fun CountPill(count: Int) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Color.White)
            .border(2.dp, Color(0xFF1B2723), shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), color = Color(0xFF1B2723), fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Créer ou modifier un raccourci --------------------------------------------------------------------

/**
 * Fenêtre pour créer ([initial] = null) ou modifier un raccourci : un nom, un classement, et les mots à chercher
 * (facultatif : sans mots, il cherche son nom). [onRemove] : bouton « Retirer ce raccourci » (modification seulement).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShortcutDialog(
    initial: Shortcut?,
    suggestedName: String = "",
    suggestedCategory: String = "",
    categories: List<String>,
    onSave: (name: String, category: String, words: String) -> Unit,
    onRemove: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: suggestedName) }
    var category by remember { mutableStateOf(initial?.category ?: suggestedCategory) }
    // Les mots ne sont montrés que s'ils diffèrent du nom : par défaut, le raccourci cherche son nom.
    var words by remember { mutableStateOf(if (initial != null && initial.words != initial.name) initial.words else "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial != null) "Modifier le raccourci" else "Créer un raccourci") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Nom du raccourci") },
                    placeholder = { Text("Immatriculation") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    singleLine = true,
                    label = { Text("Classer dans") },
                    placeholder = { Text("Véhicule") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c ->
                        val picked = Gallery.normalize(category.trim()) == Gallery.normalize(c)
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { category = c }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) { Text(if (picked) "✓ $c" else c, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                OutlinedTextField(
                    value = words,
                    onValueChange = { words = it },
                    singleLine = true,
                    label = { Text("Mots à chercher (facultatif)") },
                    placeholder = { Text("Comme le nom") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Le raccourci montre toutes les photos dont le nom, l'album, la ville, le mois… contient ces mots, et leur nombre. Aucune photo n'est déplacée.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) { Text("Retirer ce raccourci") }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, category, words) }) {
                Text("Enregistrer", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
