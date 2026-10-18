@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dz.monbudget.domain.StatsCalculator
import dz.monbudget.util.Money
import java.time.YearMonth

/** Courbe d'évolution du solde en fin de mois (dessinée avec Canvas : aucune bibliothèque externe). */
@Composable
fun BalanceChart(series: List<Pair<YearMonth, Long>>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outline
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (series.size < 2) return@Canvas
            val values = series.map { it.second.toFloat() }
            val min = minOf(values.min(), 0f)
            val max = maxOf(values.max(), 0f)
            val range = if (max - min > 0f) max - min else 1f
            val stepX = size.width / (series.size - 1)
            fun y(v: Float) = size.height - (v - min) / range * size.height
            drawLine(axis, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1f)
            val path = Path()
            values.forEachIndexed { i, v -> if (i == 0) path.moveTo(0f, y(v)) else path.lineTo(i * stepX, y(v)) }
            drawPath(path, line, style = Stroke(width = 4f))
            values.forEachIndexed { i, v -> drawCircle(line, 7f, Offset(i * stepX, y(v))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            series.forEach { Text("${it.first.monthValue}/${it.first.year % 100}", style = MaterialTheme.typography.labelSmall) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Début : ${Money.format(series.firstOrNull()?.second ?: 0)}", style = MaterialTheme.typography.labelSmall)
            Text("Fin : ${Money.format(series.lastOrNull()?.second ?: 0)}", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun StatsScreen(vm: MainViewModel) {
    val incomes by vm.incomes.collectAsState()
    val expenses by vm.expenses.collectAsState()
    val txs by vm.savingsTx.collectAsState()
    val categories by vm.categories.collectAsState()
    val account by vm.account.collectAsState()
    var monthA by remember { mutableStateOf(YearMonth.now()) }
    var monthB by remember { mutableStateOf(YearMonth.now().minusMonths(1)) }
    fun catName(id: String) = categories.firstOrNull { it.id == id }?.name ?: id

    val series = StatsCalculator.balanceSeries(YearMonth.now(), 6, account?.initialBalanceCents ?: 0L, incomes, expenses, txs)
    val comparison = StatsCalculator.compareByCategory(monthA, monthB, expenses)
    val totalA = StatsCalculator.expensesPaid(monthA, expenses)
    val totalB = StatsCalculator.expensesPaid(monthB, expenses)
    val top = expenses.filter { it.status == dz.monbudget.data.ExpenseStatus.PAID }.sortedByDescending { it.amountCents }.take(5)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionTitle("Évolution du solde (6 derniers mois)") }
        item { BalanceChart(series) }
        item { SectionTitle("Comparer les dépenses de deux mois") }
        item { MonthStepper("Mois A", monthA) { monthA = it } }
        item { MonthStepper("Mois B", monthB) { monthB = it } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Total A : ${Money.format(totalA)}")
                    Text("Total B : ${Money.format(totalB)}")
                    Text("Écart (A - B) : ${Money.format(totalA - totalB)}", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (comparison.isEmpty()) item { Text("Aucune dépense sur ces deux mois.") }
        items(comparison.size) { i ->
            val (id, a, b) = comparison[i]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(catName(id), Modifier.weight(1f))
                Text("${Money.format(a)} / ${Money.format(b)}")
            }
        }
        item { SectionTitle("Dépenses les plus importantes") }
        if (top.isEmpty()) item { Text("Aucune dépense.") }
        items(top.size) { i ->
            val e = top[i]
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(e.description.ifBlank { catName(e.categoryId) } + " · " + fmtDay(e.dateEpochDay), Modifier.weight(1f))
                Text(Money.format(e.amountCents))
            }
        }
    }
}
