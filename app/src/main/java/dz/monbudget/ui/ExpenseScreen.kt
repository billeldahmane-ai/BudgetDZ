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
import dz.monbudget.data.*
import dz.monbudget.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

private enum class Period(val label: String) { DAY("Jour"), WEEK("7 jours"), MONTH("Mois"), ALL("Tout") }

@Composable
fun ExpenseScreen(vm: MainViewModel) {
    val expenses by vm.expenses.collectAsState()
    val categories by vm.categories.collectAsState()
    var query by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(Period.MONTH) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var sortByAmount by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var toDelete by remember { mutableStateOf<Expense?>(null) }
    fun catName(id: String) = categories.firstOrNull { it.id == id }?.name ?: id

    val today = LocalDate.now()
    val filtered = expenses.filter { e ->
        val d = LocalDate.ofEpochDay(e.dateEpochDay)
        val inPeriod = when (period) {
            Period.DAY -> d == today
            Period.WEEK -> !d.isBefore(today.minusDays(6)) && !d.isAfter(today)
            Period.MONTH -> YearMonth.from(d) == YearMonth.from(today)
            Period.ALL -> true
        }
        val q = query.trim()
        inPeriod && (categoryFilter == null || e.categoryId == categoryFilter) &&
            (q.isEmpty() || e.description.contains(q, true) || e.note.contains(q, true))
    }.let { list -> if (sortByAmount) list.sortedByDescending { it.amountCents } else list }

    val paidTotal = filtered.filter { it.status == ExpenseStatus.PAID }.sumOf { it.amountCents }
    val plannedTotal = filtered.filter { it.status == ExpenseStatus.PLANNED }.sumOf { it.amountCents }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Rechercher (description, note)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Period.values().forEach { p -> FilterChip(selected = period == p, onClick = { period = p }, label = { Text(p.label) }) }
            }
        }
        item {
            DropdownField<String?>("Catégorie", listOf(null) + categories.map { it.id }, categoryFilter,
                { id -> if (id == null) "Toutes" else catName(id) }) { categoryFilter = it }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = sortByAmount, onCheckedChange = { sortByAmount = it })
                Spacer(Modifier.width(8.dp))
                Text(if (sortByAmount) "Tri : plus importantes d'abord" else "Tri : plus récentes d'abord")
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Payé : ${Money.format(paidTotal)}", style = MaterialTheme.typography.titleSmall)
                    if (plannedTotal > 0) Text("Prévu (non payé) : ${Money.format(plannedTotal)}")
                }
            }
        }
        if (filtered.isEmpty()) item { Text("Aucune dépense pour ces critères.") }
        items(filtered, key = { it.id }) { e ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.description.ifBlank { catName(e.categoryId) }, style = MaterialTheme.typography.titleSmall)
                        Text("${catName(e.categoryId)} · ${e.paymentMethod} · ${fmtDay(e.dateEpochDay)}", style = MaterialTheme.typography.bodySmall)
                        if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall)
                        Text(Money.format(e.amountCents) + if (e.status == ExpenseStatus.PLANNED) " (prévue)" else "")
                    }
                    IconButton(onClick = { editing = e }) { Icon(Icons.Default.Edit, "Modifier") }
                    IconButton(onClick = { toDelete = e }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }

    editing?.let { e ->
        ExpenseDialog(e, categories, onSave = { vm.saveExpense(it); editing = null }, onDismiss = { editing = null })
    }
    toDelete?.let { e ->
        ConfirmDialog("Supprimer cette dépense ?", "${Money.format(e.amountCents)} – cette action est définitive.",
            onConfirm = { vm.deleteExpense(e) }, onDismiss = { toDelete = null })
    }
}

@Composable
fun ExpenseDialog(initial: Expense?, categories: List<Category>, onSave: (Expense) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(initial?.let { Money.toInput(it.amountCents) } ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: DefaultIds.FOOD) }
    var day by remember { mutableLongStateOf(initial?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var payment by remember { mutableStateOf(initial?.paymentMethod ?: PaymentMethods.all.first()) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var paid by remember { mutableStateOf(initial?.status != ExpenseStatus.PLANNED) }
    val cents = Money.parseAmount(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvelle dépense" else "Modifier la dépense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountField("Montant en DZD", amount, { amount = it }, error = amount.isNotBlank() && cents == null)
                DropdownField("Catégorie", categories.map { it.id }.ifEmpty { listOf(DefaultIds.FOOD) }, categoryId,
                    { id -> categories.firstOrNull { it.id == id }?.name ?: "Alimentation" }) { categoryId = it }
                DateField("Date", day) { day = it }
                TextField1("Description", description) { description = it }
                DropdownField("Paiement", PaymentMethods.all, payment, { it }) { payment = it }
                TextField1("Note (facultatif)", note) { note = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = paid, onCheckedChange = { paid = it })
                    Spacer(Modifier.width(8.dp))
                    Text(if (paid) "Payée" else "Prévue (pas encore payée)")
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null, onClick = {
                cents?.let {
                    onSave(Expense(
                        id = initial?.id ?: UUID.randomUUID().toString(), amountCents = it, dateEpochDay = day,
                        categoryId = categoryId, description = description.trim(), paymentMethod = payment, note = note.trim(),
                        status = if (paid) ExpenseStatus.PAID else ExpenseStatus.PLANNED,
                        shoppingListId = initial?.shoppingListId
                    ))
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
