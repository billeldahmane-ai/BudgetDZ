package dz.monbudget

import dz.monbudget.data.*
import dz.monbudget.domain.BudgetCalculator
import dz.monbudget.domain.BudgetLevel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetCalculatorTest {
    private val oct = YearMonth.of(2026, 10)
    private val day = LocalDate.of(2026, 10, 10).toEpochDay()

    private fun exp(cents: Long, cat: String = "cat_loisirs", status: String = ExpenseStatus.PAID, d: Long = day) =
        Expense(amountCents = cents, dateEpochDay = d, categoryId = cat, status = status)

    private fun limit(month: YearMonth, cat: String, cents: Long) = BudgetLimit(BudgetKeys.id(month.toString(), cat), month.toString(), cat, cents)

    @Test fun levels_follow_thresholds() {
        assertEquals(BudgetLevel.OK, BudgetCalculator.level(1000, 699, 70, 90))
        assertEquals(BudgetLevel.WARN, BudgetCalculator.level(1000, 700, 70, 90))
        assertEquals(BudgetLevel.CRITICAL, BudgetCalculator.level(1000, 900, 70, 90))
        assertEquals(BudgetLevel.CRITICAL, BudgetCalculator.level(1000, 1000, 70, 90))
        assertEquals(BudgetLevel.OVER, BudgetCalculator.level(1000, 1001, 70, 90))
        assertEquals(BudgetLevel.OK, BudgetCalculator.level(0, 500, 70, 90))   // pas de budget : pas d'alerte
    }

    @Test fun thresholds_are_customizable() {
        assertEquals(BudgetLevel.WARN, BudgetCalculator.level(1000, 500, 50, 80))
        assertEquals(BudgetLevel.CRITICAL, BudgetCalculator.level(1000, 800, 50, 80))
    }

    @Test fun lines_count_only_paid_expenses_of_the_month_and_category() {
        val budgets = listOf(limit(oct, "cat_loisirs", 400_000))
        val expenses = listOf(
            exp(200_000), exp(100_000, status = ExpenseStatus.PLANNED), exp(50_000, cat = "cat_transport"),
            exp(70_000, d = LocalDate.of(2026, 9, 30).toEpochDay())
        )
        val line = BudgetCalculator.categoryLines(oct, budgets, expenses, 70, 90).single()
        assertEquals(200_000L, line.spentCents)
        assertEquals(200_000L, line.remainingCents)
        assertEquals(50, line.percent)
        assertEquals(BudgetLevel.OK, line.level)
    }

    @Test fun global_line_sums_all_categories() {
        val budgets = listOf(limit(oct, BudgetKeys.GLOBAL, 1_000_000))
        val g = BudgetCalculator.globalLine(oct, budgets, listOf(exp(300_000), exp(500_000, "cat_transport")), 70, 90)!!
        assertEquals(800_000L, g.spentCents)
        assertEquals(BudgetLevel.WARN, g.level)
        assertNull(BudgetCalculator.globalLine(YearMonth.of(2026, 11), budgets, emptyList(), 70, 90))
    }

    @Test fun carry_over_keeps_history_and_does_not_overwrite_target() {
        val sep = YearMonth.of(2026, 9)
        val budgets = listOf(limit(sep, "cat_loisirs", 400_000), limit(sep, "cat_transport", 500_000), limit(oct, "cat_transport", 600_000))
        val added = BudgetCalculator.carryOver(budgets, sep, oct)
        assertEquals(1, added.size)
        assertEquals("cat_loisirs", added[0].categoryId)
        assertEquals("2026-10", added[0].month)
        assertEquals(3, budgets.size)   // l'historique de septembre est intact (la liste d'origine n'est pas modifiée)
    }
}
