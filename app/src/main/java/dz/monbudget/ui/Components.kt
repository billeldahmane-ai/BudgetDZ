@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
fun fmtDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)

@Composable
fun AmountField(label: String, value: String, onChange: (String) -> Unit, error: Boolean = false, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, isError = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun TextField1(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth())
}

@Composable
fun <T> DropdownField(label: String, options: List<T>, selected: T, text: (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label : ${text(selected)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(text(option)) }, onClick = { onSelect(option); open = false })
            }
        }
    }
}

@Composable
fun DateField(label: String, epochDay: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            val d = LocalDate.ofEpochDay(epochDay)
            DatePickerDialog(context, { _, y, m, day -> onChange(LocalDate.of(y, m + 1, day).toEpochDay()) },
                d.year, d.monthValue - 1, d.dayOfMonth).show()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("$label : ${fmtDay(epochDay)}") }
}

@Composable
fun MonthStepper(label: String, month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label : $month", modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Mois précédent") }
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Mois suivant") }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmLabel: String = "Supprimer", onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}
