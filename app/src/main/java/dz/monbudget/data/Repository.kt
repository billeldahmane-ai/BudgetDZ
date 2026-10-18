package dz.monbudget.data

import androidx.room.withTransaction
import dz.monbudget.domain.BudgetCalculator
import dz.monbudget.domain.ConvertMode
import dz.monbudget.domain.SavingsCalculator
import dz.monbudget.domain.ShoppingCalculator
import java.time.YearMonth

enum class ConvertResult { OK, ALREADY_DONE, NOTHING_BOUGHT, NOT_FOUND }

class BudgetRepository(private val db: AppDatabase) {
    val categories = db.categoryDao().observeAll()
    val account = db.accountDao().observeMain()
    val incomes = db.incomeDao().observeAll()
    val expenses = db.expenseDao().observeAll()
    val lists = db.shoppingDao().observeLists()
    val items = db.shoppingDao().observeAllItems()
    val budgets = db.budgetDao().observeAll()
    val goals = db.savingsDao().observeGoals()
    val savingsTx = db.savingsDao().observeTransactions()
    val bills = db.billDao().observeAll()

    suspend fun setInitialBalance(cents: Long) = db.accountDao().setInitialBalance(cents)
    suspend fun saveIncome(income: Income) = db.incomeDao().upsert(income)
    suspend fun deleteIncome(income: Income) = db.incomeDao().delete(income)
    suspend fun saveExpense(expense: Expense) = db.expenseDao().upsert(expense)
    suspend fun deleteExpense(expense: Expense) = db.expenseDao().delete(expense)
    suspend fun saveList(list: ShoppingList) = db.shoppingDao().upsertList(list)
    suspend fun deleteList(id: String) = db.shoppingDao().deleteList(id)
    suspend fun saveItem(item: ShoppingItem) = db.shoppingDao().upsertItem(item)
    suspend fun deleteItem(item: ShoppingItem) = db.shoppingDao().deleteItem(item)

    // ---- Budgets
    suspend fun saveBudget(limit: BudgetLimit) = db.budgetDao().upsert(limit)
    suspend fun deleteBudget(limit: BudgetLimit) = db.budgetDao().delete(limit)
    suspend fun carryOverBudgets(from: YearMonth, to: YearMonth): Int {
        val toAdd = BudgetCalculator.carryOver(db.budgetDao().getAll(), from, to)
        if (toAdd.isNotEmpty()) db.budgetDao().upsertAll(toAdd)
        return toAdd.size
    }

    // ---- Épargne
    suspend fun saveGoal(goal: SavingsGoal) = db.savingsDao().upsertGoal(goal)
    suspend fun deleteGoal(id: String) = db.savingsDao().deleteGoal(id)

    /** Un retrait ne peut pas dépasser le solde de l'objectif. Vérifié et inséré dans la même transaction. */
    suspend fun addSavingsTx(tx: SavingsTransaction): Boolean = db.withTransaction<Boolean> {
        val dao = db.savingsDao()
        if (tx.type == TxType.WITHDRAW) {
            val balance = SavingsCalculator.balance(tx.goalId, dao.getTransactionsOf(tx.goalId))
            if (tx.amountCents > balance) return@withTransaction false
        }
        dao.insertTransaction(tx)
        true
    }

    /** Refuse de supprimer un mouvement si le solde de l'objectif deviendrait négatif. */
    suspend fun deleteSavingsTx(tx: SavingsTransaction): Boolean = db.withTransaction<Boolean> {
        val dao = db.savingsDao()
        val remaining = dao.getTransactionsOf(tx.goalId).filter { it.id != tx.id }
        if (SavingsCalculator.balance(tx.goalId, remaining) < 0) return@withTransaction false
        dao.deleteTransaction(tx)
        true
    }

    // ---- Factures
    suspend fun saveBill(bill: Bill) = db.billDao().upsert(bill)
    suspend fun deleteBill(id: String) = db.billDao().delete(id)

    /** Payer une facture : crée UNE dépense "bill-<id>" + marque payée, en une transaction (pas de doublon possible). */
    suspend fun payBill(billId: String, dateEpochDay: Long): Boolean = db.withTransaction<Boolean> {
        val bill = db.billDao().getById(billId) ?: return@withTransaction false
        if (bill.paid) return@withTransaction false
        db.expenseDao().insertAll(listOf(Expense(
            id = "bill-${bill.id}", amountCents = bill.amountCents, dateEpochDay = dateEpochDay, categoryId = bill.categoryId,
            description = "Facture : ${bill.name}", paymentMethod = "Autre", status = ExpenseStatus.PAID
        )))
        db.billDao().setPaid(bill.id, true)
        true
    }

    // ---- Courses
    /** Option 1 de la validation : archiver sans créer de dépense. */
    suspend fun archiveList(id: String) {
        val list = db.shoppingDao().getList(id) ?: return
        if (list.status == ListStatus.ACTIVE) db.shoppingDao().setListStatus(id, ListStatus.ARCHIVED)
    }

    /**
     * Option 2 : enregistrer les achats comme dépenses réelles. TOUT ou RIEN dans une seule transaction.
     * Anti-doublon : (1) liste ACTIVE, (2) ids déterministes, (3) index unique (liste, catégorie).
     */
    suspend fun convertList(listId: String, mode: ConvertMode, dateEpochDay: Long): ConvertResult =
        db.withTransaction<ConvertResult> {
            val dao = db.shoppingDao()
            val list = dao.getList(listId) ?: return@withTransaction ConvertResult.NOT_FOUND
            if (list.status != ListStatus.ACTIVE) return@withTransaction ConvertResult.ALREADY_DONE
            val bought = dao.getItems(listId).filter { it.bought }
            val expenses = ShoppingCalculator.buildExpenses(list, bought, mode, dateEpochDay)
            if (expenses.isEmpty()) return@withTransaction ConvertResult.NOTHING_BOUGHT
            db.expenseDao().insertAll(expenses)
            dao.setListStatus(listId, ListStatus.CONVERTED)
            ConvertResult.OK
        }

    // ---- Export / sauvegarde / restauration
    suspend fun categoryNames(): Map<String, String> = db.categoryDao().getAll().associate { it.id to it.name }

    suspend fun snapshot(): BackupSnapshot = db.withTransaction<BackupSnapshot> {
        BackupSnapshot(
            initialBalanceCents = db.accountDao().getMain()?.initialBalanceCents ?: 0L,
            incomes = db.incomeDao().getAll(), expenses = db.expenseDao().getAll(),
            lists = db.shoppingDao().getAllLists(), items = db.shoppingDao().getAllItems(),
            budgets = db.budgetDao().getAll(), goals = db.savingsDao().getGoals(),
            savingsTx = db.savingsDao().getTransactions(), bills = db.billDao().getAll()
        )
    }

    /** Remplace TOUTES les données par celles de la sauvegarde, en une transaction : en cas d'erreur, rien n'est modifié. */
    suspend fun restore(s: BackupSnapshot) = db.withTransaction {
        db.expenseDao().deleteAll()
        db.incomeDao().deleteAll()
        db.billDao().deleteAll()
        db.budgetDao().deleteAll()
        db.savingsDao().deleteAllGoals()      // supprime aussi les mouvements (CASCADE)
        db.shoppingDao().deleteAllLists()     // supprime aussi les articles (CASCADE)
        db.accountDao().setInitialBalance(s.initialBalanceCents)
        db.incomeDao().insertAll(s.incomes)
        db.shoppingDao().insertLists(s.lists)
        db.shoppingDao().insertItems(s.items)
        db.savingsDao().insertGoals(s.goals)
        db.savingsDao().insertTransactions(s.savingsTx)
        db.budgetDao().insertAll(s.budgets)
        db.billDao().insertAll(s.bills)
        db.expenseDao().insertAll(s.expenses)
    }
}
