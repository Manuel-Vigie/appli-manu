package fr.mesphotos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Demande le jour (jour, mois, année) où les photos ou vidéos choisies ont vraiment été prises. */
@Composable
internal fun DateDialog(count: Int, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = remember { LocalDate.now() }
    var day by remember { mutableStateOf(today.dayOfMonth.toString()) }
    var month by remember { mutableStateOf(today.monthValue.toString()) }
    var year by remember { mutableStateOf(today.year.toString()) }
    val date = runCatching { LocalDate.of(year.trim().toInt(), month.trim().toInt(), day.trim().toInt()) }.getOrNull()
    val valid = date != null && date.year >= 1990 && !date.isAfter(today)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quel jour ont-elles été prises ?", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "$count fichier(s) choisi(s). Ils seront rangés dans le dossier de ce jour (Journées / année / mois / jour) et leur date sera corrigée.",
                    fontSize = 15.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2) }, label = { Text("Jour") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                    OutlinedTextField(month, { month = it.filter(Char::isDigit).take(2) }, label = { Text("Mois") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                    OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Année") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(100.dp))
                }
                if (!valid) Text("Cette date n'existe pas (ou elle est dans le futur).", fontSize = 14.sp)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { date?.let(onConfirm) }) { Text("Changer la date", fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
