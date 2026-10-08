package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Les mots proposés par la reconnaissance : on coche ceux qu'on veut, puis on renomme d'un coup. */
@Composable
fun LabelScreen(state: UiState.LabelView, onBack: () -> Unit, onApply: (Set<String>) -> Unit) {
    BackHandler { onBack() }
    var chosen by remember(state) { mutableStateOf(state.groups.map { it.name }.toSet()) }
    val count = state.groups.filter { it.name in chosen }.sumOf { it.files.size }
    Column(Modifier.fillMaxSize()) {
        Header(
            title = "Classement proposé",
            subtitle = "${spaced(state.scanned)} photo(s) regardée(s) · ${spaced(state.unknown)} non reconnue(s)",
            top = {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Outils", color = Color.White, fontSize = 16.sp)
                    }
                }
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NoticeCard(
                    if (state.groups.isEmpty()) "Rien n'a été reconnu avec assez de certitude. Rien n'a été changé."
                    else "Décochez les mots qui ne vous conviennent pas : les photos de ce mot ne seront pas touchées. Rien n'est renommé avant d'appuyer sur le bouton du bas.",
                    isError = false,
                )
            }
            items(state.groups, key = { it.name }) { group ->
                Section {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = group.name in chosen,
                            onCheckedChange = { on -> chosen = if (on) chosen + group.name else chosen - group.name },
                        )
                        Title("${group.name} (${spaced(group.files.size)})")
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.files.take(4).forEach { Thumb(it, 160, Modifier.size(72.dp)) }
                    }
                }
            }
        }
        if (state.groups.isNotEmpty()) {
            Column(Modifier.padding(16.dp).navigationBarsPadding()) {
                BigButton("Renommer ${spaced(count)} photo(s)", onClick = { onApply(chosen) }, enabled = count > 0)
            }
        }
    }
}
