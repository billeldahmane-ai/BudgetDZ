@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dz.monbudget.data.Income
import dz.monbudget.data.IncomeCategories
import dz.monbudget.data.IncomeStatus
import dz.monbudget.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun IncomeScreen(vm: MainViewModel) {
    val incomes by vm.incomes.collectAsState()
    var dialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Income?>(null) }
    var toDelete by remember { mutableStateOf<Income?>(null) }

    // Comparaison des revenus entre mois (par mois de référence)
    val perMonth = incomes.groupBy { it.referenceMonth }
        .map { (m, list) ->
            Triple(
                m,
                list.filter { it.status == IncomeStatus.RECEIVED }.sumOf { it.amountCents },
                list.filter { it.status == IncomeStatus.PLANNED }.sumOf { it.amountCents }
            )
        }.sortedByDescending { it.first }.take(6)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = { editing = null; dialogOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter un revenu") }
        }
        if (perMonth.isNotEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Revenus par mois", style = MaterialTheme.typography.titleSmall)
                        perMonth.forEach { (month, received, planned) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(month)
                                Text("Reçu ${Money.format(received)}" + if (planned > 0) " · prévu ${Money.format(planned)}" else "")
                            }
                        }
                    }
                }
            }
        }
        if (incomes.isEmpty()) item { Text("Aucun revenu enregistré.") }
        items(incomes, key = { it.id }) { income ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(IncomeCategories.label(income.category) + " – ${income.referenceMonth}", style = MaterialTheme.typography.titleSmall)
                        if (income.description.isNotBlank()) Text(income.description, style = MaterialTheme.typography.bodySmall)
                        Text("${Money.format(income.amountCents)} · ${fmtDay(income.dateEpochDay)}")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = income.status == IncomeStatus.RECEIVED, onCheckedChange = { vm.toggleIncomeReceived(income) })
                            Text(if (income.status == IncomeStatus.RECEIVED) "Reçu" else "Prévu (non reçu)")
                        }
                    }
                    IconButton(onClick = { editing = income; dialogOpen = true }) { Icon(Icons.Default.Edit, "Modifier") }
                    IconButton(onClick = { toDelete = income }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }

    if (dialogOpen) {
        IncomeDialog(initial = editing, onSave = { vm.saveIncome(it); dialogOpen = false }, onDismiss = { dialogOpen = false })
    }
    toDelete?.let { income ->
        ConfirmDialog("Supprimer ce revenu ?", "${Money.format(income.amountCents)} – cette action est définitive.",
            onConfirm = { vm.deleteIncome(income) }, onDismiss = { toDelete = null })
    }
}

@Composable
fun IncomeDialog(initial: Income?, onSave: (Income) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(initial?.let { Money.toInput(it.amountCents) } ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "SALARY") }
    var day by remember { mutableLongStateOf(initial?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var month by remember { mutableStateOf(initial?.let { YearMonth.parse(it.referenceMonth) } ?: YearMonth.now()) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var received by remember { mutableStateOf(initial?.status == IncomeStatus.RECEIVED) }
    val cents = Money.parseAmount(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouveau revenu" else "Modifier le revenu") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField("Montant en DZD", amount, { amount = it }, error = amount.isNotBlank() && cents == null)
                DropdownField("Catégorie", IncomeCategories.all.map { it.first }, category, { IncomeCategories.label(it) }) { category = it }
                DateField("Date", day) { day = it }
                MonthStepper("Mois de référence", month) { month = it }
                TextField1("Description (facultatif)", description) { description = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = received, onCheckedChange = { received = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Versement déjà reçu")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null, onClick = {
                cents?.let {
                    onSave(Income(
                        id = initial?.id ?: UUID.randomUUID().toString(), amountCents = it, dateEpochDay = day,
                        category = category, description = description.trim(), referenceMonth = month.toString(),
                        status = if (received) IncomeStatus.RECEIVED else IncomeStatus.PLANNED
                    ))
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
