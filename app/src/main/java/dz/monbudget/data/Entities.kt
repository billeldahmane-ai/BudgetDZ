package dz.monbudget.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object IncomeStatus { const val PLANNED = "PLANNED"; const val RECEIVED = "RECEIVED" }
object ExpenseStatus { const val PLANNED = "PLANNED"; const val PAID = "PAID" }
object ListStatus { const val ACTIVE = "ACTIVE"; const val ARCHIVED = "ARCHIVED"; const val CONVERTED = "CONVERTED" }
object DefaultIds { const val ACCOUNT = "main"; const val FOOD = "cat_alimentation" }

object IncomeCategories {
    val all = listOf(
        "SALARY" to "Salaire", "BONUS" to "Prime", "ALLOWANCE" to "Indemnité",
        "SIDE" to "Activité complémentaire", "REFUND" to "Remboursement", "OTHER" to "Autres revenus"
    )
    fun label(key: String) = all.firstOrNull { it.first == key }?.second ?: key
}

object PaymentMethods { val all = listOf("Espèces", "Carte bancaire", "Virement", "Autre") }
object Units { val all = listOf("Pièce", "Kg", "g", "L", "mL", "Paquet", "Carton", "Bouteille", "Autre") }

@Entity(tableName = "category")
data class Category(@PrimaryKey val id: String, val name: String, val sortOrder: Int)

/** Compte financier. Étape 2 : un seul compte "main". Les comptes multiples/épargne viendront à l'étape 3. */
@Entity(tableName = "financial_account")
data class FinancialAccount(@PrimaryKey val id: String, val name: String, val initialBalanceCents: Long)

/**
 * Revenu. status = PLANNED (attendu) ou RECEIVED (versement confirmé).
 * Seuls les revenus RECEIVED entrent dans le solde réel.
 */
@Entity(tableName = "income")
data class Income(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val dateEpochDay: Long,
    val category: String,
    val description: String = "",
    val referenceMonth: String,          // "2026-10"
    val status: String
)

/** Dépense. Seules les dépenses PAID réduisent le solde. shoppingListId renseigné si issue d'une liste de courses. */
@Entity(
    tableName = "expense",
    foreignKeys = [
        ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ShoppingList::class, parentColumns = ["id"], childColumns = ["shoppingListId"], onDelete = ForeignKey.SET_NULL)
    ],
    // Garde-fou anti-doublon : une seule dépense par (liste, catégorie). Les NULL (dépenses manuelles) sont ignorés.
    indices = [Index("categoryId"), Index("shoppingListId"), Index(value = ["shoppingListId", "categoryId"], unique = true)]
)
data class Expense(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val amountCents: Long,
    val dateEpochDay: Long,
    val categoryId: String,
    val description: String = "",
    val paymentMethod: String = "Espèces",
    val note: String = "",
    val status: String = ExpenseStatus.PAID,
    val shoppingListId: String? = null
)

@Entity(tableName = "shopping_list")
data class ShoppingList(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdEpochDay: Long,
    val plannedBudgetCents: Long = 0,    // 0 = pas de budget fixé (on utilise le total estimé)
    val status: String = ListStatus.ACTIVE
)

@Entity(
    tableName = "shopping_item",
    foreignKeys = [ForeignKey(entity = ShoppingList::class, parentColumns = ["id"], childColumns = ["listId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("listId")]
)
data class ShoppingItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val name: String,
    val categoryId: String = DefaultIds.FOOD,
    val quantityMilli: Long = 1000,
    val unit: String = "Pièce",
    val estUnitCents: Long = 0,
    val realUnitCents: Long? = null,
    val store: String = "",
    val note: String = "",
    val bought: Boolean = false
)

// ======================= Étape 3 =======================
object BudgetKeys {
    const val GLOBAL = "ALL"   // limite globale du mois (toutes catégories confondues)
    fun id(month: String, categoryId: String) = "$month|$categoryId"
}
object TxType { const val DEPOSIT = "DEPOSIT"; const val WITHDRAW = "WITHDRAW" }

/** Budget d'un mois donné ("2026-10") pour une catégorie (ou GLOBAL). Un enregistrement par mois : l'historique est conservé. */
@Entity(tableName = "budget_limit", indices = [Index(value = ["month", "categoryId"], unique = true)])
data class BudgetLimit(@PrimaryKey val id: String, val month: String, val categoryId: String, val limitCents: Long)

@Entity(tableName = "savings_goal")
data class SavingsGoal(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val targetCents: Long,
    val targetEpochDay: Long? = null,
    val createdEpochDay: Long
)

/**
 * Mouvement d'épargne = TRANSFERT interne (ni revenu ni dépense).
 * DEPOSIT : compte courant -> épargne (solde courant diminue). WITHDRAW : épargne -> compte courant.
 */
@Entity(
    tableName = "savings_transaction",
    foreignKeys = [ForeignKey(entity = SavingsGoal::class, parentColumns = ["id"], childColumns = ["goalId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("goalId")]
)
data class SavingsTransaction(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val goalId: String,
    val amountCents: Long,
    val type: String,
    val dateEpochDay: Long,
    val note: String = ""
)

/** Facture à payer. Marquée payée => crée UNE dépense "bill-<id>" (transaction), jamais deux. */
@Entity(tableName = "bill")
data class Bill(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amountCents: Long,
    val dueEpochDay: Long,
    val categoryId: String,
    val paid: Boolean = false
)
