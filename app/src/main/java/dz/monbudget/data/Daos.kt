package dz.monbudget.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sortOrder")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM category ORDER BY sortOrder")
    suspend fun getAll(): List<Category>
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM financial_account WHERE id = 'main'")
    fun observeMain(): Flow<FinancialAccount?>

    @Query("SELECT * FROM financial_account WHERE id = 'main'")
    suspend fun getMain(): FinancialAccount?

    @Query("UPDATE financial_account SET initialBalanceCents = :cents WHERE id = 'main'")
    suspend fun setInitialBalance(cents: Long)
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM income ORDER BY dateEpochDay DESC")
    fun observeAll(): Flow<List<Income>>

    @Query("SELECT * FROM income")
    suspend fun getAll(): List<Income>

    @Upsert suspend fun upsert(income: Income)
    @Delete suspend fun delete(income: Income)
    @Insert suspend fun insertAll(incomes: List<Income>)

    @Query("DELETE FROM income") suspend fun deleteAll()
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expense ORDER BY dateEpochDay DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT * FROM expense")
    suspend fun getAll(): List<Expense>

    @Upsert suspend fun upsert(expense: Expense)

    /** ABORT par défaut : un doublon (clé primaire ou index unique) annule toute la transaction. */
    @Insert suspend fun insertAll(expenses: List<Expense>)

    @Delete suspend fun delete(expense: Expense)
    @Query("DELETE FROM expense") suspend fun deleteAll()
}

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_list ORDER BY createdEpochDay DESC")
    fun observeLists(): Flow<List<ShoppingList>>

    @Query("SELECT * FROM shopping_item")
    fun observeAllItems(): Flow<List<ShoppingItem>>

    @Query("SELECT * FROM shopping_list")
    suspend fun getAllLists(): List<ShoppingList>

    @Query("SELECT * FROM shopping_item")
    suspend fun getAllItems(): List<ShoppingItem>

    @Query("SELECT * FROM shopping_list WHERE id = :id")
    suspend fun getList(id: String): ShoppingList?

    @Query("SELECT * FROM shopping_item WHERE listId = :listId")
    suspend fun getItems(listId: String): List<ShoppingItem>

    @Upsert suspend fun upsertList(list: ShoppingList)
    @Upsert suspend fun upsertItem(item: ShoppingItem)
    @Delete suspend fun deleteItem(item: ShoppingItem)
    @Insert suspend fun insertLists(lists: List<ShoppingList>)
    @Insert suspend fun insertItems(items: List<ShoppingItem>)

    @Query("DELETE FROM shopping_list WHERE id = :id") suspend fun deleteList(id: String)
    @Query("DELETE FROM shopping_list") suspend fun deleteAllLists()
    @Query("UPDATE shopping_list SET status = :status WHERE id = :id") suspend fun setListStatus(id: String, status: String)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budget_limit ORDER BY month DESC")
    fun observeAll(): Flow<List<BudgetLimit>>

    @Query("SELECT * FROM budget_limit")
    suspend fun getAll(): List<BudgetLimit>

    @Upsert suspend fun upsert(limit: BudgetLimit)
    @Upsert suspend fun upsertAll(limits: List<BudgetLimit>)
    @Delete suspend fun delete(limit: BudgetLimit)
    @Insert suspend fun insertAll(limits: List<BudgetLimit>)
    @Query("DELETE FROM budget_limit") suspend fun deleteAll()
}

@Dao
interface SavingsDao {
    @Query("SELECT * FROM savings_goal ORDER BY createdEpochDay DESC")
    fun observeGoals(): Flow<List<SavingsGoal>>

    @Query("SELECT * FROM savings_transaction ORDER BY dateEpochDay DESC")
    fun observeTransactions(): Flow<List<SavingsTransaction>>

    @Query("SELECT * FROM savings_goal")
    suspend fun getGoals(): List<SavingsGoal>

    @Query("SELECT * FROM savings_transaction")
    suspend fun getTransactions(): List<SavingsTransaction>

    @Query("SELECT * FROM savings_transaction WHERE goalId = :goalId")
    suspend fun getTransactionsOf(goalId: String): List<SavingsTransaction>

    @Upsert suspend fun upsertGoal(goal: SavingsGoal)
    @Query("DELETE FROM savings_goal WHERE id = :id") suspend fun deleteGoal(id: String)
    @Insert suspend fun insertTransaction(tx: SavingsTransaction)
    @Delete suspend fun deleteTransaction(tx: SavingsTransaction)
    @Insert suspend fun insertGoals(goals: List<SavingsGoal>)
    @Insert suspend fun insertTransactions(txs: List<SavingsTransaction>)
    @Query("DELETE FROM savings_goal") suspend fun deleteAllGoals()
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bill ORDER BY dueEpochDay")
    fun observeAll(): Flow<List<Bill>>

    @Query("SELECT * FROM bill")
    suspend fun getAll(): List<Bill>

    @Query("SELECT * FROM bill WHERE id = :id")
    suspend fun getById(id: String): Bill?

    @Upsert suspend fun upsert(bill: Bill)
    @Query("DELETE FROM bill WHERE id = :id") suspend fun delete(id: String)
    @Query("UPDATE bill SET paid = :paid WHERE id = :id") suspend fun setPaid(id: String, paid: Boolean)
    @Insert suspend fun insertAll(bills: List<Bill>)
    @Query("DELETE FROM bill") suspend fun deleteAll()
}
