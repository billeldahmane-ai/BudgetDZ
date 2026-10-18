@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dz.monbudget.data.ExpenseStatus
import dz.monbudget.data.IncomeCategories
import dz.monbudget.data.IncomeStatus
import dz.monbudget.domain.BalanceCalculator
import dz.monbudget.domain.BudgetCalculator
import dz.monbudget.domain.SavingsCalculator
import dz.monbudget.domain.StatsCalculator
import dz.monbudget.util.Money
import java.time.LocalDate
import java.time.YearMonth

private data class Op(val title: String, val cents: Long, val day: Long, val isIncome: Boolean)

@Composable
fun HomeScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val categories by vm.categories.collectAsState()
    val account by vm.account.collectAsState()
    val txs by vm.savingsTx.collectAsState()
    val bills by vm.bills.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val settings by vm.settings.collectAsState()
    var editInitial by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val thisMonth = YearMonth.from(today)
    val initial = account?.initialBalanceCents ?: 0L
    // Solde = initial + revenus reçus - dépenses payées + retraits d'épargne - versements d'épargne (transferts, pas des dépenses)
    val balance = BalanceCalculator.currentBalance(initial, incomes, expenses,
        transfersInCents = SavingsCalculator.totalWithdrawals(txs), transfersOutCents = SavingsCalculator.totalDeposits(txs))
    val month = BalanceCalculator.summarize(thisMonth, today, incomes, expenses)
    val percent = BalanceCalculator.percentSpent(month.expensesPaid, month.incomeReceived)
    val savedTotal = SavingsCalculator.totalDeposits(txs) - SavingsCalculator.totalWithdrawals(txs)
    val monthBills = bills.filter { YearMonth.from(LocalDate.ofEpochDay(it.dueEpochDay)) == thisMonth }
    val globalBudget = BudgetCalculator.globalLine(thisMonth, budgets, expenses, settings.warnPct, settings.critPct)
    val series = StatsCalculator.balanceSeries(thisMonth, 6, initial, incomes, expenses, txs)

    fun show(cents: Long) = if (settings.hideAmounts) "••••••" else Money.format(cents)
    fun catName(id: String) = categories.firstOrNull { it.id == id }?.name ?: id

    val ops = (incomes.filter { it.status == IncomeStatus.RECEIVED }.map {
        Op(IncomeCategories.label(it.category) + if (it.description.isBlank()) "" else " – ${it.description}", it.amountCents, it.dateEpochDay, true)
    } + expenses.filter { it.status == ExpenseStatus.PAID }.map {
        Op(it.description.ifBlank { catName(it.categoryId) }, it.amountCents, it.dateEpochDay, false)
    }).sortedByDescending { it.day }.take(5)

    val byCategory = StatsCalculator.expensesByCategory(thisMonth, expenses).map { (id, total) -> catName(id) to total }.sortedByDescending { it.second }
    val maxCat = byCategory.firstOrNull()?.second ?: 1L

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Solde disponible", style = MaterialTheme.typography.labelLarge)
                    Text(show(balance), style = MaterialTheme.typography.headlineMedium)
                    Text("Solde initial : ${show(initial)}", style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editInitial = true }) { Text("Modifier le solde initial") }
                        TextButton(onClick = { vm.setHideAmounts(!settings.hideAmounts) }) { Text(if (settings.hideAmounts) "Afficher" else "Masquer") }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate("income") }, modifier = Modifier.weight(1f)) { Text("Revenus") }
                    OutlinedButton(onClick = { onNavigate("savings") }, modifier = Modifier.weight(1f)) { Text("Épargne") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onNavigate("bills") }, modifier = Modifier.weight(1f)) { Text("Factures") }
                    OutlinedButton(onClick = { onNavigate("stats") }, modifier = Modifier.weight(1f)) { Text("Statistiques") }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Salaire reçu", show(month.salaryReceived), Modifier.weight(1f))
                    StatCard("Salaire prévu (non reçu)", show(month.salaryPlanned), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Autres revenus reçus", show(month.otherReceived), Modifier.weight(1f))
                    StatCard("Dépenses du mois", show(month.expensesPaid), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Courses du mois", show(month.shoppingPaid), Modifier.weight(1f))
                    StatCard("Dépenses du jour", show(month.todayPaid), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Factures du mois (impayées : ${show(monthBills.filter { !it.paid }.sumOf { it.amountCents })})",
                        show(monthBills.sumOf { it.amountCents }), Modifier.weight(1f))
                    StatCard("Épargne totale", show(savedTotal), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Budget restant (global)", globalBudget?.let { show(it.remainingCents) } ?: "Non défini", Modifier.weight(1f))
                    StatCard("Dépenses prévues, non payées", show(month.expensesPlanned), Modifier.weight(1f))
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Revenus du mois déjà dépensés : $percent %", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { (percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        item { SectionTitle("Évolution du solde") }
        item { BalanceChart(series) }
        item { SectionTitle("Dépenses par catégorie (ce mois)") }
        if (byCategory.isEmpty()) item { Text("Aucune dépense ce mois-ci.") }
        items(byCategory.size) { index ->
            val (name, total) = byCategory[index]
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(name); Text(show(total)) }
                LinearProgressIndicator(progress = { total.toFloat() / maxCat.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { SectionTitle("Dernières opérations") }
        if (ops.isEmpty()) item { Text("Aucune opération pour le moment.") }
        items(ops.size) { index ->
            val op = ops[index]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(op.title); Text(fmtDay(op.day), style = MaterialTheme.typography.bodySmall) }
                Text((if (op.isIncome) "+ " else "- ") + show(op.cents))
            }
        }
    }

    if (editInitial) {
        var text by remember { mutableStateOf(Money.toInput(initial)) }
        val cents = Money.parseAmount(text, allowZero = true)
        AlertDialog(
            onDismissRequest = { editInitial = false },
            title = { Text("Solde initial") },
            text = { AmountField("Montant en DZD", text, { text = it }, error = cents == null) },
            confirmButton = {
                TextButton(enabled = cents != null, onClick = { cents?.let { vm.setInitialBalance(it) }; editInitial = false }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editInitial = false }) { Text("Annuler") } }
        )
    }
}
