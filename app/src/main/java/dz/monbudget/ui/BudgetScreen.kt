@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dz.monbudget.data.BudgetKeys
import dz.monbudget.data.BudgetLimit
import dz.monbudget.domain.BudgetCalculator
import dz.monbudget.domain.BudgetLevel
import dz.monbudget.domain.BudgetLine
import dz.monbudget.util.Money
import java.time.YearMonth

@Composable
private fun levelColor(level: BudgetLevel): Color = when (level) {
    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
    BudgetLevel.WARN -> MaterialTheme.colorScheme.tertiary
    BudgetLevel.CRITICAL, BudgetLevel.OVER -> MaterialTheme.colorScheme.error
}

private fun levelText(line: BudgetLine, warn: Int, crit: Int) = when (line.level) {
    BudgetLevel.OK -> "Dans le budget"
    BudgetLevel.WARN -> "Seuil de $warn % atteint"
    BudgetLevel.CRITICAL -> "Seuil de $crit % atteint"
    BudgetLevel.OVER -> "Budget dépassé"
}

@Composable
fun BudgetScreen(vm: MainViewModel) {
    val budgets by vm.budgets.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val categories by vm.categories.collectAsState()
    val settings by vm.settings.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    var dialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<BudgetLimit?>(null) }
    fun name(id: String) = if (id == BudgetKeys.GLOBAL) "Budget global du mois" else categories.firstOrNull { it.id == id }?.name ?: id

    val global = BudgetCalculator.globalLine(month, budgets, expenses, settings.warnPct, settings.critPct)
    val lines = BudgetCalculator.categoryLines(month, budgets, expenses, settings.warnPct, settings.critPct)
    val all = listOfNotNull(global) + lines
    val alerts = all.filter { it.level != BudgetLevel.OK }
    fun limitOf(line: BudgetLine) = budgets.firstOrNull { it.month == month.toString() && it.categoryId == line.categoryId }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { MonthStepper("Mois", month) { month = it } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { dialog = true }, modifier = Modifier.weight(1f)) { Text("Définir un budget") }
                OutlinedButton(onClick = { vm.carryOverBudgets(month.minusMonths(1), month) }, modifier = Modifier.weight(1f)) { Text("Reporter le mois précédent") }
            }
        }
        if (alerts.isNotEmpty()) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Alertes", style = MaterialTheme.typography.titleSmall)
                    alerts.forEach { Text("${name(it.categoryId)} : ${levelText(it, settings.warnPct, settings.critPct)} (${it.percent} %)") }
                }
            }
        }
        if (all.isEmpty()) item { Text("Aucun budget défini pour $month. Définissez-en un ou reportez celui du mois précédent.") }
        items(all, key = { it.categoryId }) { line ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(name(line.categoryId), style = MaterialTheme.typography.titleSmall)
                        Text("Budget ${Money.format(line.limitCents)} · dépensé ${Money.format(line.spentCents)}")
                        Text("Restant ${Money.format(line.remainingCents)} · ${line.percent} % consommé")
                        LinearProgressIndicator(progress = { (line.percent / 100f).coerceIn(0f, 1f) }, color = levelColor(line.level), modifier = Modifier.fillMaxWidth())
                        Text(levelText(line, settings.warnPct, settings.critPct), style = MaterialTheme.typography.bodySmall, color = levelColor(line.level))
                    }
                    IconButton(onClick = { dialog = true }) { Icon(Icons.Default.Edit, "Modifier") }
                    IconButton(onClick = { toDelete = limitOf(line) }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }

    if (dialog) {
        var categoryId by remember { mutableStateOf(BudgetKeys.GLOBAL) }
        var amount by remember { mutableStateOf("") }
        val cents = Money.parseAmount(amount)
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text("Budget de $month") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownField("Catégorie", listOf(BudgetKeys.GLOBAL) + categories.map { it.id }, categoryId, { name(it) }) { categoryId = it }
                    AmountField("Budget en DZD", amount, { amount = it }, error = amount.isNotBlank() && cents == null)
                    Text("Si un budget existe déjà pour cette catégorie ce mois-ci, il est remplacé. Les autres mois ne changent pas.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = cents != null, onClick = { cents?.let { vm.setBudget(month, categoryId, it) }; dialog = false }) { Text("Enregistrer") } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text("Annuler") } }
        )
    }
    toDelete?.let { limit ->
        ConfirmDialog("Supprimer ce budget ?", "${name(limit.categoryId)} – ${limit.month}. Les dépenses ne sont pas touchées.",
            onConfirm = { vm.deleteBudget(limit) }, onDismiss = { toDelete = null })
    }
}
