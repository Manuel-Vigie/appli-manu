package fr.mesphotos.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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

// Six verts, assortis au vert de l'appli (émeraude, menthe, sauge, olive, sapin, jade), en clair et en sombre.
private val LIGHT_PALETTE = listOf(
    ShortcutColors(Color(0xFFD3EFE3), Color(0xFF7CC5A8), Color(0xFF0E5C49)), // émeraude
    ShortcutColors(Color(0xFFE2F5EA), Color(0xFF9ED3B3), Color(0xFF1F6B3F)), // menthe
    ShortcutColors(Color(0xFFE6EFE0), Color(0xFFA9C79A), Color(0xFF3F6B2A)), // sauge
    ShortcutColors(Color(0xFFEDF2D9), Color(0xFFC0CC8A), Color(0xFF566B16)), // olive
    ShortcutColors(Color(0xFFD8EAE4), Color(0xFF7DB0A2), Color(0xFF0F5448)), // sapin
    ShortcutColors(Color(0xFFD6EEDC), Color(0xFF6FBF8B), Color(0xFF14663A)), // jade
)

private val DARK_PALETTE = listOf(
    ShortcutColors(Color(0xFF0F4F40), Color(0xFF2E8F73), Color(0xFFC3F2E0)),
    ShortcutColors(Color(0xFF14472D), Color(0xFF3A9B63), Color(0xFFCDF3DB)),
    ShortcutColors(Color(0xFF2A3F20), Color(0xFF6A9A55), Color(0xFFD6EBC9)),
    ShortcutColors(Color(0xFF3A4112), Color(0xFF8E9B2E), Color(0xFFE6EDB5)),
    ShortcutColors(Color(0xFF0D3F38), Color(0xFF3F8E7D), Color(0xFFC4EBE2)),
    ShortcutColors(Color(0xFF0F4A2A), Color(0xFF3FAE6A), Color(0xFFC8F0D6)),
)

/** Un vert par catégorie : tous les raccourcis d'une même catégorie ont le même. */
private fun colorsFor(category: String, dark: Boolean): ShortcutColors {
    val palette = if (dark) DARK_PALETTE else LIGHT_PALETTE
    return palette[Math.floorMod(Gallery.normalize(category).hashCode(), palette.size)]
}

/**
 * Les raccourcis, un grand bouton par raccourci : son nom et, à droite, le nombre de photos. Groupés par catégorie.
 * Un appui ouvre les photos ; un appui long ([onLongClick]) permet de modifier ou retirer le raccourci.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShortcutRows(items: List<ShortcutItem>, onOpen: (ShortcutItem) -> Unit, onLongClick: ((ShortcutItem) -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
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
                val colors = colorsFor(item.shortcut.category, dark)
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
