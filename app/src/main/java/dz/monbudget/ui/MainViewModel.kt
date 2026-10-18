package dz.monbudget.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dz.monbudget.data.*
import dz.monbudget.domain.ConvertMode
import dz.monbudget.domain.StatsCalculator
import dz.monbudget.export.CsvExporter
import dz.monbudget.export.PdfReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = BudgetRepository(AppDatabase.get(app))
    private val settingsRepo = SettingsRepository(app)

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val categories = repo.categories.state(emptyList())
    val account = repo.account.state(null)
    val incomes = repo.incomes.state(emptyList())
    val expenses = repo.expenses.state(emptyList())
    val lists = repo.lists.state(emptyList())
    val items = repo.items.state(emptyList())
    val budgets = repo.budgets.state(emptyList())
    val goals = repo.goals.state(emptyList())
    val savingsTx = repo.savingsTx.state(emptyList())
    val bills = repo.bills.state(emptyList())
    val settings = settingsRepo.settings.state(AppSettings())

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()
    private fun say(message: String) { _events.tryEmit(message) }

    fun setInitialBalance(cents: Long) = viewModelScope.launch { repo.setInitialBalance(cents) }

    // ---- Revenus / dépenses
    fun saveIncome(i: Income) = viewModelScope.launch { repo.saveIncome(i) }
    fun deleteIncome(i: Income) = viewModelScope.launch { repo.deleteIncome(i) }
    fun toggleIncomeReceived(i: Income) = viewModelScope.launch {
        val next = if (i.status == IncomeStatus.RECEIVED) IncomeStatus.PLANNED else IncomeStatus.RECEIVED
        repo.saveIncome(i.copy(status = next))
    }
    fun saveExpense(e: Expense) = viewModelScope.launch {
        try {
            repo.saveExpense(e)
        } catch (ex: Exception) {
            // Ex. : index unique (liste, catégorie) violé en modifiant une dépense issue d'une liste de courses.
            say("Enregistrement impossible : une dépense de cette liste existe déjà dans cette catégorie.")
        }
    }
    fun deleteExpense(e: Expense) = viewModelScope.launch { repo.deleteExpense(e) }

    // ---- Courses
    fun createList(name: String, budgetCents: Long) = viewModelScope.launch {
        repo.saveList(ShoppingList(name = name, createdEpochDay = LocalDate.now().toEpochDay(), plannedBudgetCents = budgetCents))
    }
    fun deleteList(id: String) = viewModelScope.launch { repo.deleteList(id) }
    fun saveItem(item: ShoppingItem) = viewModelScope.launch { repo.saveItem(item) }
    fun deleteItem(item: ShoppingItem) = viewModelScope.launch { repo.deleteItem(item) }
    fun toggleBought(item: ShoppingItem) = viewModelScope.launch { repo.saveItem(item.copy(bought = !item.bought)) }
    fun setRealPrice(item: ShoppingItem, cents: Long?) = viewModelScope.launch { repo.saveItem(item.copy(realUnitCents = cents)) }
    fun archiveList(id: String) = viewModelScope.launch { repo.archiveList(id); say("Liste archivée, aucune dépense créée.") }
    fun convertList(id: String, mode: ConvertMode) = viewModelScope.launch {
        val message = try {
            when (repo.convertList(id, mode, LocalDate.now().toEpochDay())) {
                ConvertResult.OK -> "Achats enregistrés comme dépenses."
                ConvertResult.ALREADY_DONE -> "Cette liste a déjà été traitée."
                ConvertResult.NOTHING_BOUGHT -> "Aucun article acheté : rien à enregistrer."
                ConvertResult.NOT_FOUND -> "Liste introuvable."
            }
        } catch (e: Exception) { "Erreur : conversion annulée, aucune dépense créée." }
        say(message)
    }

    // ---- Budgets
    fun setBudget(month: YearMonth, categoryId: String, cents: Long) = viewModelScope.launch {
        repo.saveBudget(BudgetLimit(BudgetKeys.id(month.toString(), categoryId), month.toString(), categoryId, cents))
    }
    fun deleteBudget(limit: BudgetLimit) = viewModelScope.launch { repo.deleteBudget(limit) }
    fun carryOverBudgets(from: YearMonth, to: YearMonth) = viewModelScope.launch {
        val n = repo.carryOverBudgets(from, to)
        say(if (n == 0) "Rien à reporter (budgets déjà définis ou mois précédent vide)." else "$n budget(s) reporté(s) sur $to.")
    }

    // ---- Épargne
    fun saveGoal(g: SavingsGoal) = viewModelScope.launch { repo.saveGoal(g) }
    fun deleteGoal(id: String) = viewModelScope.launch { repo.deleteGoal(id) }
    fun addSavingsTx(goalId: String, type: String, cents: Long, note: String) = viewModelScope.launch {
        val ok = repo.addSavingsTx(SavingsTransaction(goalId = goalId, amountCents = cents, type = type, dateEpochDay = LocalDate.now().toEpochDay(), note = note))
        if (!ok) say("Retrait refusé : il dépasse le montant épargné pour cet objectif.")
    }
    fun deleteSavingsTx(tx: SavingsTransaction) = viewModelScope.launch {
        if (!repo.deleteSavingsTx(tx)) say("Suppression refusée : le solde de l'objectif deviendrait négatif.")
    }

    // ---- Factures
    fun saveBill(b: Bill) = viewModelScope.launch { repo.saveBill(b) }
    fun deleteBill(id: String) = viewModelScope.launch { repo.deleteBill(id) }
    fun payBill(id: String) = viewModelScope.launch {
        val message = try {
            if (repo.payBill(id, LocalDate.now().toEpochDay())) "Facture payée : dépense enregistrée." else "Cette facture est déjà payée."
        } catch (e: Exception) { "Erreur : facture non marquée payée." }
        say(message)
    }

    // ---- Paramètres (DataStore)
    fun setThresholds(warn: Int, crit: Int) = viewModelScope.launch { settingsRepo.setThresholds(warn, crit) }
    fun setSalaryDay(day: Int) = viewModelScope.launch { settingsRepo.setSalaryDay(day) }
    fun setMonthlySavings(cents: Long) = viewModelScope.launch { settingsRepo.setMonthlySavings(cents) }
    fun setHideAmounts(hide: Boolean) = viewModelScope.launch { settingsRepo.setHideAmounts(hide) }
    fun setNotification(type: NotifType, enabled: Boolean) = viewModelScope.launch { settingsRepo.setNotification(type, enabled) }

    // ---- Exports, sauvegarde, restauration (fichiers choisis par l'utilisateur via le sélecteur Android : aucune permission)
    private fun writeTo(uri: Uri, bytes: ByteArray) {
        val stream = getApplication<Application>().contentResolver.openOutputStream(uri) ?: error("Fichier inaccessible")
        stream.use { it.write(bytes) }
    }

    fun exportCsv(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        say(try {
            writeTo(uri, CsvExporter.build(repo.snapshot(), repo.categoryNames()).toByteArray(Charsets.UTF_8)); "Export CSV enregistré."
        } catch (e: Exception) { "Échec de l'export CSV." })
    }

    fun exportPdf(uri: Uri, month: YearMonth) = viewModelScope.launch(Dispatchers.IO) {
        say(try {
            val s = repo.snapshot()
            val report = StatsCalculator.report(month, s.initialBalanceCents, s.incomes, s.expenses, s.savingsTx)
            writeTo(uri, PdfReport.build(report, repo.categoryNames())); "Rapport PDF enregistré."
        } catch (e: Exception) { "Échec de la création du PDF." })
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        say(try {
            writeTo(uri, BackupCodec.encode(repo.snapshot()).toByteArray(Charsets.UTF_8)); "Sauvegarde enregistrée."
        } catch (e: Exception) { "Échec de la sauvegarde." })
    }

    fun restoreBackup(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        say(try {
            val text = getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Fichier inaccessible")
            val snapshot = BackupCodec.decode(text)      // validation complète AVANT de toucher aux données
            repo.restore(snapshot)                       // transaction : tout ou rien
            "Données restaurées."
        } catch (e: Exception) { "Restauration impossible : fichier invalide ou incompatible. Vos données n'ont pas été modifiées." })
    }
}
