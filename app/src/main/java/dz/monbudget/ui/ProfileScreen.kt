@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dz.monbudget.data.NotifType
import dz.monbudget.util.Money
import java.time.YearMonth

@Composable
fun ProfileScreen(vm: MainViewModel, onNavigate: (String) -> Unit) {
    val settings by vm.settings.collectAsState()
    var reportMonth by remember { mutableStateOf(YearMonth.now()) }
    var confirmRestore by remember { mutableStateOf(false) }

    var warn by remember(settings.warnPct) { mutableStateOf(settings.warnPct.toString()) }
    var crit by remember(settings.critPct) { mutableStateOf(settings.critPct.toString()) }
    var salaryDay by remember(settings.salaryDay) { mutableStateOf(settings.salaryDay.toString()) }
    var monthlySavings by remember(settings.monthlySavingsCents) { mutableStateOf(Money.toInput(settings.monthlySavingsCents)) }

    val warnV = warn.toIntOrNull()
    val critV = crit.toIntOrNull()
    val thresholdsOk = warnV != null && critV != null && warnV in 1..99 && critV in 2..100 && warnV < critV
    val dayV = salaryDay.toIntOrNull()
    val dayOk = dayV != null && dayV in 1..31
    val savingsV = Money.parseAmount(monthlySavings, allowZero = true)

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportCsv(it) } }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { vm.exportPdf(it, reportMonth) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { vm.exportBackup(it) } }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.restoreBackup(it) } }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Accès rapide")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onNavigate("income") }, modifier = Modifier.weight(1f)) { Text("Revenus") }
            OutlinedButton(onClick = { onNavigate("savings") }, modifier = Modifier.weight(1f)) { Text("Épargne") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onNavigate("bills") }, modifier = Modifier.weight(1f)) { Text("Factures") }
            OutlinedButton(onClick = { onNavigate("stats") }, modifier = Modifier.weight(1f)) { Text("Statistiques") }
        }

        SectionTitle("Salaire et épargne")
        TextField1("Jour habituel de versement du salaire (1-31)", salaryDay) { salaryDay = it }
        AmountField("Objectif d'épargne mensuel (DZD, 0 = aucun)", monthlySavings, { monthlySavings = it }, error = savingsV == null)
        Button(enabled = dayOk && savingsV != null, onClick = {
            vm.setSalaryDay(dayV ?: 1); vm.setMonthlySavings(savingsV ?: 0L)
        }) { Text("Enregistrer") }

        SectionTitle("Seuils d'alerte du budget (%)")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField1("Alerte", warn, Modifier.weight(1f)) { warn = it }
            TextField1("Critique", crit, Modifier.weight(1f)) { crit = it }
        }
        if (!thresholdsOk) Text("Le seuil d'alerte doit être inférieur au seuil critique (entre 1 et 100).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Button(enabled = thresholdsOk, onClick = { vm.setThresholds(warnV ?: 70, critV ?: 90) }) { Text("Enregistrer les seuils") }

        SectionTitle("Rappels (notifications locales, sans serveur)")
        NotifType.values().forEach { type ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(type.label, Modifier.weight(1f))
                Switch(checked = type in settings.notifications, onCheckedChange = { on ->
                    if (on && Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    vm.setNotification(type, on)
                })
            }
        }
        Text("Les rappels sont vérifiés une fois par jour vers 19 h ; Android peut décaler l'heure pour économiser la batterie.", style = MaterialTheme.typography.bodySmall)

        SectionTitle("Confidentialité")
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Masquer les montants sur l'accueil", Modifier.weight(1f))
            Switch(checked = settings.hideAmounts, onCheckedChange = { vm.setHideAmounts(it) })
        }
        Text("Vos données restent sur ce téléphone, dans la base privée de l'application. Aucun compte, aucun serveur, aucune publicité. " +
            "Elles ne quittent l'appareil que si vous exportez un fichier (CSV, PDF, sauvegarde) et le partagez vous-même.", style = MaterialTheme.typography.bodySmall)

        SectionTitle("Exports et rapports")
        OutlinedButton(onClick = { csvLauncher.launch("mon-budget-operations.csv") }, modifier = Modifier.fillMaxWidth()) { Text("Exporter les opérations (CSV)") }
        MonthStepper("Rapport du mois", reportMonth) { reportMonth = it }
        OutlinedButton(onClick = { pdfLauncher.launch("rapport-$reportMonth.pdf") }, modifier = Modifier.fillMaxWidth()) { Text("Générer le rapport mensuel (PDF)") }

        SectionTitle("Sauvegarde et restauration")
        OutlinedButton(onClick = { backupLauncher.launch("mon-budget-sauvegarde.json") }, modifier = Modifier.fillMaxWidth()) { Text("Créer une sauvegarde") }
        OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restaurer une sauvegarde") }
        Text("Le fichier de sauvegarde n'est pas chiffré : conservez-le en lieu sûr.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRestore) {
        ConfirmDialog("Restaurer une sauvegarde ?",
            "Toutes les données actuelles (revenus, dépenses, courses, budgets, épargne, factures) seront remplacées par celles du fichier.",
            confirmLabel = "Choisir le fichier",
            onConfirm = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onDismiss = { confirmRestore = false })
    }
}
