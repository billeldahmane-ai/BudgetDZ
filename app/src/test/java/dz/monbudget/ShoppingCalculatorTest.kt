package dz.monbudget

import dz.monbudget.data.ExpenseStatus
import dz.monbudget.data.ShoppingItem
import dz.monbudget.data.ShoppingList
import dz.monbudget.domain.ConvertMode
import dz.monbudget.domain.ShoppingCalculator
import org.junit.Assert.*
import org.junit.Test

class ShoppingCalculatorTest {
    private val list = ShoppingList(id = "L1", name = "Semaine", createdEpochDay = 0)

    private fun item(name: String, qtyMilli: Long, estCents: Long, real: Long? = null, bought: Boolean = false, cat: String = "cat_alimentation") =
        ShoppingItem(listId = "L1", name = name, categoryId = cat, quantityMilli = qtyMilli, estUnitCents = estCents, realUnitCents = real, bought = bought)

    @Test fun line_cost_scales_with_quantity_and_rounds_half_up() {
        assertEquals(280_000L, ShoppingCalculator.lineCost(140_000, 2000))   // 2 bouteilles à 1 400 DZD
        assertEquals(125L, ShoppingCalculator.lineCost(100, 1250))           // 1,25 x 1 DZD
        assertEquals(1L, ShoppingCalculator.lineCost(1, 500))                // 0,5 centime -> arrondi à 1
    }

    @Test fun summary_totals_and_percentages() {
        val items = listOf(
            item("Huile", 2000, 140_000, bought = true, real = 150_000),
            item("Sucre", 2000, 12_000),
            item("Riz", 2000, 25_000, bought = true)
        )
        val s = ShoppingCalculator.summarize(items, plannedBudgetCents = 0)
        assertEquals(280_000L + 24_000L + 50_000L, s.estimatedTotal)
        assertEquals(300_000L + 50_000L, s.realBought)
        assertEquals(24_000L, s.remainingToBuy)
        assertEquals(20_000L, s.variance)             // 300 000 réel - 280 000 estimé sur l'huile
        assertEquals(66, s.percentDone)
        assertEquals(354_000L - 350_000L, s.budgetRemaining)
    }

    @Test fun planned_budget_overrides_estimated_total() {
        val s = ShoppingCalculator.summarize(listOf(item("Lait", 10_000, 12_500, bought = true)), plannedBudgetCents = 200_000)
        assertEquals(200_000L - 125_000L, s.budgetRemaining)
    }

    @Test fun empty_list_does_not_crash() {
        val s = ShoppingCalculator.summarize(emptyList(), 0)
        assertEquals(0, s.percentDone)
        assertEquals(0L, s.estimatedTotal)
    }

    @Test fun global_conversion_builds_one_expense_with_real_prices_only_for_bought_items() {
        val bought = listOf(item("Huile", 1000, 100_000, real = 110_000, bought = true), item("Riz", 1000, 25_000, bought = true))
        val result = ShoppingCalculator.buildExpenses(list, bought, ConvertMode.GLOBAL, 100)
        assertEquals(1, result.size)
        assertEquals(135_000L, result[0].amountCents)
        assertEquals(ExpenseStatus.PAID, result[0].status)
        assertEquals("L1", result[0].shoppingListId)
    }

    @Test fun by_category_conversion_groups_amounts() {
        val bought = listOf(
            item("Huile", 1000, 100_000, bought = true),
            item("Riz", 1000, 25_000, bought = true),
            item("Tomates", 1000, 8_000, bought = true, cat = "cat_fruits_legumes")
        )
        val result = ShoppingCalculator.buildExpenses(list, bought, ConvertMode.BY_CATEGORY, 100)
        assertEquals(2, result.size)
        assertEquals(125_000L, result.first { it.categoryId == "cat_alimentation" }.amountCents)
        assertEquals(8_000L, result.first { it.categoryId == "cat_fruits_legumes" }.amountCents)
    }

    @Test fun conversion_ids_are_deterministic_so_a_replay_collides_instead_of_duplicating() {
        val bought = listOf(item("Riz", 1000, 25_000, bought = true))
        val first = ShoppingCalculator.buildExpenses(list, bought, ConvertMode.GLOBAL, 100)
        val second = ShoppingCalculator.buildExpenses(list, bought, ConvertMode.GLOBAL, 200)
        assertEquals(first[0].id, second[0].id)
    }

    @Test fun nothing_bought_creates_no_expense() {
        assertTrue(ShoppingCalculator.buildExpenses(list, emptyList(), ConvertMode.GLOBAL, 100).isEmpty())
    }
}
