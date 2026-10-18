package dz.monbudget.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base locale (fichier privé de l'application, jamais transmis).
 * v1 -> v2 : ajout budgets, épargne, factures (MIGRATION_1_2). Les données existantes sont conservées.
 * Pour une évolution future : incrémenter version, ajouter un Migration, garder les schémas exportés (app/schemas).
 */
@Database(
    entities = [
        Category::class, FinancialAccount::class, Income::class, Expense::class, ShoppingList::class, ShoppingItem::class,
        BudgetLimit::class, SavingsGoal::class, SavingsTransaction::class, Bill::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shoppingDao(): ShoppingDao
    abstract fun budgetDao(): BudgetDao
    abstract fun savingsDao(): SavingsDao
    abstract fun billDao(): BillDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `budget_limit` (`id` TEXT NOT NULL, `month` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `limitCents` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_budget_limit_month_categoryId` ON `budget_limit` (`month`, `categoryId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `savings_goal` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `targetCents` INTEGER NOT NULL, `targetEpochDay` INTEGER, `createdEpochDay` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `savings_transaction` (`id` TEXT NOT NULL, `goalId` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `type` TEXT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`goalId`) REFERENCES `savings_goal`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_transaction_goalId` ON `savings_transaction` (`goalId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `bill` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `dueEpochDay` INTEGER NOT NULL, `categoryId` TEXT NOT NULL, `paid` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        private val seedCategories = listOf(
            "cat_alimentation" to "Alimentation", "cat_fruits_legumes" to "Fruits et légumes",
            "cat_viande_poisson" to "Viande et poisson", "cat_menager" to "Produits ménagers",
            "cat_loyer" to "Loyer", "cat_energie" to "Électricité et gaz", "cat_eau" to "Eau",
            "cat_telecom" to "Internet et téléphone", "cat_transport" to "Transport et carburant",
            "cat_sante" to "Santé et pharmacie", "cat_ecole" to "École et enfants",
            "cat_vetements" to "Vêtements", "cat_loisirs" to "Loisirs et sorties",
            "cat_abonnements" to "Abonnements", "cat_autres" to "Autres dépenses"
        )

        private val seedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                seedCategories.forEachIndexed { index, (id, name) ->
                    db.execSQL("INSERT INTO category(id, name, sortOrder) VALUES ('$id', '$name', ${index + 1})")
                }
                db.execSQL("INSERT INTO financial_account(id, name, initialBalanceCents) VALUES ('main', 'Compte principal', 0)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mon_budget.db")
                .addCallback(seedCallback)
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
