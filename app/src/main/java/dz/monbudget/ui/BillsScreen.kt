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
import dz.monbudget.data.Bill
import dz.monbudget.util.Money
import java.time.LocalDate
import java.util.UUID

@Composable
fun BillsScreen(vm: MainViewModel) {
    val bills by vm.bills.collectAsState()
    val categories by vm.categories.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Bill?>(null) }
    var toDelete by remember { mutableStateOf<Bill?>(null) }
    val today = LocalDate.now().toEpochDay()
    val sorted = bills.sortedWith(compareBy({ it.paid }, { it.dueEpochDay }))

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { editing = null; dialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter une facture") } }
        item { Text("Payer une facture crée une dépense (une seule fois) : elle n'est jamais comptée deux fois.", style = MaterialTheme.typography.bodySmall) }
        if (sorted.isEmpty()) item { Text("Aucune facture.") }
        items(sorted, key = { it.id }) { bill ->
            val overdue = !bill.paid && bill.dueEpochDay < today
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(bill.name, style = MaterialTheme.typography.titleSmall)
                        Text("${Money.format(bill.amountCents)} · échéance ${fmtDay(bill.dueEpochDay)}")
                        Text(if (bill.paid) "Payée" else if (overdue) "En retard" else "À payer",
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (!bill.paid) {
                        TextButton(onClick = { vm.payBill(bill.id) }) { Text("Payer") }
                        IconButton(onClick = { editing = bill; dialog = true }) { Icon(Icons.Default.Edit, "Modifier") }
                    }
                    IconButton(onClick = { toDelete = bill }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }

    if (dialog) {
        val initial = editing
        var name by remember { mutableStateOf(initial?.name ?: "") }
        var amount by remember { mutableStateOf(initial?.let { Money.toInput(it.amountCents) } ?: "") }
        var day by remember { mutableLongStateOf(initial?.dueEpochDay ?: LocalDate.now().plusDays(7).toEpochDay()) }
        var categoryId by remember { mutableStateOf(initial?.categoryId ?: "cat_energie") }
        val cents = Money.parseAmount(amount)
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(if (initial == null) "Nouvelle facture" else "Modifier la facture") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField1("Nom (ex. Électricité)", name) { name = it }
                    AmountField("Montant en DZD", amount, { amount = it }, error = amount.isNotBlank() && cents == null)
                    DateField("Échéance", day) { day = it }
                    DropdownField("Catégorie", categories.map { it.id }.ifEmpty { listOf("cat_energie") }, categoryId,
                        { id -> categories.firstOrNull { it.id == id }?.name ?: id }) { categoryId = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && cents != null, onClick = {
                    cents?.let { vm.saveBill(Bill(id = initial?.id ?: UUID.randomUUID().toString(), name = name.trim(), amountCents = it, dueEpochDay = day, categoryId = categoryId, paid = false)) }
                    dialog = false
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Annuler") } }
        )
    }
    toDelete?.let { bill ->
        ConfirmDialog("Supprimer cette facture ?", "${bill.name} – la dépense déjà enregistrée, si elle existe, est conservée.",
            onConfirm = { vm.deleteBill(bill.id) }, onDismiss = { toDelete = null })
    }
}
