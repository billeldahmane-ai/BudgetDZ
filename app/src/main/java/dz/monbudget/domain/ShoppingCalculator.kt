package dz.monbudget.domain

import dz.monbudget.data.DefaultIds
import dz.monbudget.data.Expense
import dz.monbudget.data.ExpenseStatus
import dz.monbudget.data.ShoppingItem
import dz.monbudget.data.ShoppingList

enum class ConvertMode { GLOBAL, BY_CATEGORY }

data class ListSummary(
    val estimatedTotal: Long,     // coût estimé de TOUS les articles
    val realBought: Long,         // coût réel des articles cochés (prix réel, sinon estimé)
    val remainingToBuy: Long,     // coût estimé des articles non cochés
    val variance: Long,           // réel - estimé, sur les articles cochés (positif = dépassement)
    val budgetRemaining: Long,    // budget fixé (ou total estimé) - réel déjà dépensé
    val doneCount: Int,
    val totalCount: Int
) {
    val percentDone: Int get() = if (totalCount == 0) 0 else doneCount * 100 / totalCount
}

/**
 * RÈGLES DES LISTES DE COURSES
 *  - coût d'une ligne = prix unitaire x quantité (arrondi au centime, demi supérieur) ; recalculé à chaque modification.
 *  - un article de liste n'est JAMAIS une dépense tant que la liste n'est pas validée en "dépenses réelles".
 *  - conversion : seuls les articles cochés comptent, au prix réel (sinon estimé).
 */
object ShoppingCalculator {

    fun lineCost(unitCents: Long, qtyMilli: Long): Long = (unitCents * qtyMilli + 500L) / 1000L

    fun estimatedCost(item: ShoppingItem): Long = lineCost(item.estUnitCents, item.quantityMilli)

    fun realCost(item: ShoppingItem): Long = lineCost(item.realUnitCents ?: item.estUnitCents, item.quantityMilli)

    fun summarize(items: List<ShoppingItem>, plannedBudgetCents: Long): ListSummary {
        val bought = items.filter { it.bought }
        val notBought = items.filter { !it.bought }
        val estimatedTotal = items.sumOf { estimatedCost(it) }
        val realBought = bought.sumOf { realCost(it) }
        val estimatedBought = bought.sumOf { estimatedCost(it) }
        val reference = if (plannedBudgetCents > 0) plannedBudgetCents else estimatedTotal
        return ListSummary(
            estimatedTotal = estimatedTotal,
            realBought = realBought,
            remainingToBuy = notBought.sumOf { estimatedCost(it) },
            variance = realBought - estimatedBought,
            budgetRemaining = reference - realBought,
            doneCount = bought.size,
            totalCount = items.size
        )
    }

    /**
     * Construit les dépenses à enregistrer. Les identifiants sont DÉTERMINISTES ("shop-<liste>-<catégorie>") :
     * rejouer l'opération produit les mêmes clés, donc la base refuse le doublon (en plus du statut de la liste).
     */
    fun buildExpenses(list: ShoppingList, boughtItems: List<ShoppingItem>, mode: ConvertMode, dateEpochDay: Long): List<Expense> {
        val description = "Courses : ${list.name}"
        val result = when (mode) {
            ConvertMode.GLOBAL -> listOf(
                Expense(
                    id = "shop-${list.id}-ALL", amountCents = boughtItems.sumOf { realCost(it) },
                    dateEpochDay = dateEpochDay, categoryId = DefaultIds.FOOD, description = description,
                    status = ExpenseStatus.PAID, shoppingListId = list.id
                )
            )
            ConvertMode.BY_CATEGORY -> boughtItems.groupBy { it.categoryId }.map { (categoryId, group) ->
                Expense(
                    id = "shop-${list.id}-$categoryId", amountCents = group.sumOf { realCost(it) },
                    dateEpochDay = dateEpochDay, categoryId = categoryId, description = description,
                    status = ExpenseStatus.PAID, shoppingListId = list.id
                )
            }
        }
        return result.filter { it.amountCents > 0 }
    }
}
