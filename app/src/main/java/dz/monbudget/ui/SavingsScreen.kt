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
import dz.monbudget.data.SavingsGoal
import dz.monbudget.data.TxType
import dz.monbudget.domain.SavingsCalculator
import dz.monbudget.domain.StatsCalculator
import dz.monbudget.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun SavingsScreen(vm: MainViewModel) {
    val goals by vm.goals.collectAsState()
    val txs by vm.savingsTx.collectAsState()
    val settings by vm.settings.collectAsState()
    val today = LocalDate.now()
    var goalDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavingsGoal?>(null) }
    var txFor by remember { mutableStateOf<Pair<SavingsGoal, String>?>(null) }
    var historyFor by remember { mutableStateOf<SavingsGoal?>(null) }
    var toDelete by remember { mutableStateOf<SavingsGoal?>(null) }
    val netThisMonth = StatsCalculator.savingsNet(YearMonth.from(today), txs)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Épargne de ce mois : ${Money.format(netThisMonth)}", style = MaterialTheme.typography.titleSmall)
                    if (settings.monthlySavingsCents > 0) {
                        Text("Objectif mensuel : ${Money.format(settings.monthlySavingsCents)}")
                        LinearProgressIndicator(progress = { (netThisMonth.toFloat() / settings.monthlySavingsCents).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    } else Text("Objectif mensuel non défini (onglet Profil).", style = MaterialTheme.typography.bodySmall)
                    Text("Un versement est un transfert vers l'épargne : il baisse le solde disponible mais n'est pas une dépense.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Button(onClick = { editing = null; goalDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Nouvel objectif d'épargne") } }
        if (goals.isEmpty()) item { Text("Aucun objectif. Exemples : réserve d'urgence, voyage, téléphone.") }
        items(goals, key = { it.id }) { goal ->
            val saved = SavingsCalculator.balance(goal.id, txs)
            val pct = SavingsCalculator.percent(saved, goal.targetCents)
            val needed = SavingsCalculator.monthlyNeeded(goal.targetCents, saved, today, goal.targetEpochDay)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editing = goal; goalDialog = true }) { Icon(Icons.Default.Edit, "Modifier") }
                        IconButton(onClick = { toDelete = goal }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                    Text("${Money.format(saved)} / ${Money.format(goal.targetCents)} ($pct %)")
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                    goal.targetEpochDay?.let { Text("Date cible : ${fmtDay(it)}", style = MaterialTheme.typography.bodySmall) }
                    when {
                        needed == null -> Text("Ajoutez une date cible pour calculer le montant mensuel.", style = MaterialTheme.typography.bodySmall)
                        needed == 0L -> Text("Objectif atteint.", style = MaterialTheme.typography.bodySmall)
                        else -> Text("À épargner par mois : ${Money.format(needed)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { txFor = goal to TxType.DEPOSIT }) { Text("Verser") }
                        OutlinedButton(onClick = { txFor = goal to TxType.WITHDRAW }, enabled = saved > 0) { Text("Retirer") }
                        TextButton(onClick = { historyFor = goal }) { Text("Historique") }
                    }
                }
            }
        }
    }

    if (goalDialog) GoalDialog(editing, onSave = { vm.saveGoal(it); goalDialog = false }, onDismiss = { goalDialog = false })

    txFor?.let { (goal, type) ->
        var amount by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        val cents = Money.parseAmount(amount)
        AlertDialog(
            onDismissRequest = { txFor = null },
            title = { Text((if (type == TxType.DEPOSIT) "Verser vers « " else "Retirer de « ") + goal.name + " »") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("Montant en DZD", amount, { amount = it }, error = amount.isNotBlank() && cents == null)
                    TextField1("Note (facultatif)", note) { note = it }
                }
            },
            confirmButton = { TextButton(enabled = cents != null, onClick = { cents?.let { vm.addSavingsTx(goal.id, type, it, note.trim()) }; txFor = null }) { Text("Valider") } },
            dismissButton = { TextButton(onClick = { txFor = null }) { Text("Annuler") } }
        )
    }

    historyFor?.let { goal ->
        val history = txs.filter { it.goalId == goal.id }
        AlertDialog(
            onDismissRequest = { historyFor = null },
            title = { Text("Historique – ${goal.name}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (history.isEmpty()) Text("Aucun mouvement.")
                    history.forEach { tx ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (tx.type == TxType.DEPOSIT) "+ " else "- ") + Money.format(tx.amountCents))
                                Text(fmtDay(tx.dateEpochDay) + if (tx.note.isBlank()) "" else " · ${tx.note}", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.deleteSavingsTx(tx) }) { Icon(Icons.Default.Delete, "Supprimer") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyFor = null }) { Text("Fermer") } }
        )
    }
    toDelete?.let { goal ->
        ConfirmDialog("Supprimer « ${goal.name} » ?", "Son historique de versements sera supprimé. Le solde disponible sera recalculé.",
            onConfirm = { vm.deleteGoal(goal.id) }, onDismiss = { toDelete = null })
    }
}

@Composable
private fun GoalDialog(initial: SavingsGoal?, onSave: (SavingsGoal) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var target by remember { mutableStateOf(initial?.let { Money.toInput(it.targetCents) } ?: "") }
    var hasDate by remember { mutableStateOf(initial?.targetEpochDay != null) }
    var day by remember { mutableLongStateOf(initial?.targetEpochDay ?: LocalDate.now().plusMonths(6).toEpochDay()) }
    val cents = Money.parseAmount(target)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvel objectif" else "Modifier l'objectif") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField1("Nom (ex. Voyage)", name) { name = it }
                AmountField("Montant visé en DZD", target, { target = it }, error = target.isNotBlank() && cents == null)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = hasDate, onCheckedChange = { hasDate = it }); Spacer(Modifier.width(8.dp)); Text("Date cible")
                }
                if (hasDate) DateField("Date cible", day) { day = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && cents != null, onClick = {
                cents?.let {
                    onSave(SavingsGoal(id = initial?.id ?: UUID.randomUUID().toString(), name = name.trim(), targetCents = it,
                        targetEpochDay = if (hasDate) day else null, createdEpochDay = initial?.createdEpochDay ?: LocalDate.now().toEpochDay()))
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
