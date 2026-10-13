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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    onPick: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by remember { mutableStateOf<List<String>>(emptyList()) }
    var folders by remember { mutableStateOf<List<FolderItem>?>(null) }
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
                Text("Dossier choisi : $where", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Touchez un dossier pour l'ouvrir, puis « Traiter ce dossier ».", fontSize = 14.sp)
                val list = folders
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    if (path.isNotEmpty()) item {
                        Text("↑ Dossier précédent", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth().clickable { path = path.dropLast(1) }.padding(vertical = 12.dp))
                    }
                    if (list == null) item { Text("Chargement…", fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                    else if (list.isEmpty()) item { Text("Pas d'autre dossier à l'intérieur.", fontSize = 15.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                    else items(list, key = { it.name }) { f ->
                        Row(Modifier.fillMaxWidth().clickable { path = path + f.name }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Gallery.displayName(f.name), fontSize = 17.sp)
                            Text("${f.count}", fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(path) }) { Text("Traiter ce dossier", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
