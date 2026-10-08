package fr.mesphotos.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mesphotos.logic.Verdict

/** Résultat de « Vérifier les photos » : un bilan en clair, sans rien modifier. */
@Composable
fun HealthScreen(state: UiState.HealthView, onBack: () -> Unit, onRepair: () -> Unit) {
    BackHandler { onBack() }
    val r = state.report
    fun n(v: Verdict) = r.counts[v] ?: 0
    val bad = r.total - n(Verdict.OK)
    Column(Modifier.fillMaxSize()) {
        Header(
            title = "Vérification",
            subtitle = if (bad == 0) "${spaced(r.total)} fichiers vérifiés : tout est sain." else "${spaced(bad)} fichier(s) à problème sur ${spaced(r.total)}.",
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
            r.message?.let { item { NoticeCard(it, isError = false) } }
            item {
                Section {
                    Title("Bilan")
                    Line("Sains", n(Verdict.OK))
                    Line("Brouillés (chiffrés, illisibles pour toute appli)", n(Verdict.SCRAMBLED))
                    Line("Début abîmé, réparables", n(Verdict.REPAIRABLE))
                    Line("Coupés à la fin (souvent visibles en partie)", n(Verdict.TRUNCATED))
                    Line("Remplis de zéros ou vides (rien à récupérer)", n(Verdict.ZEROS) + n(Verdict.EMPTY))
                    Line("Contenu inconnu", n(Verdict.UNKNOWN))
                }
            }
            if (r.byDate.isNotEmpty()) {
                item {
                    Section {
                        Title("Date de modification des fichiers à problème")
                        r.byDate.forEach { Line(it.first, it.second) }
                        Body("Si presque tous ont la même date, quelque chose les a réécrits ce jour-là.")
                    }
                }
                item {
                    Section {
                        Title("Dossiers touchés")
                        r.byFolder.forEach { Line(it.first, it.second) }
                    }
                }
                if (r.unknownKinds.isNotEmpty()) {
                    item {
                        Section {
                            Title("Contenu inconnu : débuts de fichier")
                            r.unknownKinds.forEach { Line(it.first, it.second) }
                            Body("Les 4 premiers octets de chaque fichier. S'ils sont tous différents, le début est brouillé ; s'ils se répètent, c'est un format ou une marque commune.")
                        }
                    }
                }
                if (r.deepKinds.isNotEmpty()) {
                    item {
                        Section {
                            Title("Analyse poussée des fichiers inconnus")
                            r.deepKinds.forEach { Line(it.first, it.second) }
                            Body("« Hasard » = octets sans aucune logique (chiffré). « Structuré » = il reste de l'ordre dans le fichier. « Trace JPEG : oui » = un morceau de la photo est encore reconnaissable, donc peut-être récupérable.")
                        }
                    }
                }
                item {
                    Section {
                        Title("Exemples")
                        Body(r.samples.joinToString("\n"))
                    }
                }
            }
        }
        if (r.repairable.isNotEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp, tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BigButton("Réparer ${r.repairable.size} photo(s) (en copie)", onClick = onRepair)
                    Text(
                        "Les originaux ne sont pas touchés : une copie « (réparée) » est créée à côté.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Line(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(spaced(count), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
