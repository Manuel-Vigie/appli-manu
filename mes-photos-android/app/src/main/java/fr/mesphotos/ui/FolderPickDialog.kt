package fr.mesphotos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.gallery.FolderItem
import fr.mesphotos.gallery.Gallery

/** Choisir un dossier de la galerie (année, mois, jour…) : on entre dans les dossiers, puis on touche « Traiter ce dossier ». */
@Composable
internal fun FolderPickDialog(
    title: String,
    load: suspend (List<String>) -> List<FolderItem>,
    onPick: (List<List<String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FolderItem>?>(null) }
    var chosen by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    LaunchedEffect(path) {
        folders = null
        folders = runCatching { load(path) }.getOrDefault(emptyList())
    }
    val where = if (path.isEmpty()) "Toute la galerie" else path.joinToString(" / ") { Gallery.displayName(it) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Je suis dans : $where", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                if (chosen.isNotEmpty()) Text("${chosen.size} dossier(s) coché(s)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Cochez les dossiers à traiter (vous pouvez en cocher plusieurs, même dans des mois différents). Touchez le nom d'un dossier pour l'ouvrir.", fontSize = 14.sp)
                val list = folders
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (path.isNotEmpty()) item {
                        Text("↑ Dossier précédent", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { path = path.dropLast(1) }.padding(vertical = 12.dp))
                    }
                    if (list == null) item { Text("Chargement…", fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                    else if (list.isEmpty()) item { Text("Pas d'autre dossier à l'intérieur.", fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                    else items(list, key = { it.name }) { f ->
                        val full = path + f.name
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = full in chosen, onCheckedChange = { on -> chosen = if (on) chosen + listOf(full) else chosen - listOf(full) })
                            Text(Gallery.displayName(f.name), fontSize = 17.sp, modifier = Modifier.weight(1f).clickable { path = full }.padding(vertical = 12.dp))
                            Text("${f.count}", fontSize = 15.sp, modifier = Modifier.clickable { path = full }.padding(vertical = 12.dp, horizontal = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(if (chosen.isNotEmpty()) chosen else listOf(path)) }) { Text(if (chosen.isNotEmpty()) "Traiter les cochés" else "Traiter ce dossier", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
