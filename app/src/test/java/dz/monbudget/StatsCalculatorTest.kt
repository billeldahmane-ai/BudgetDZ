package dz.monbudget

import dz.monbudget.data.*
import dz.monbudget.domain.StatsCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsCalculatorTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day).toEpochDay()

    private val incomes = listOf(
        Income(amountCents = 8_000_000, dateEpochDay = d(2026, 9, 28), category = "SALARY", referenceMonth = "2026-09", status = IncomeStatus.RECEIVED),
        Income(amountCents = 8_000_000, dateEpochDay = d(2026, 10, 28), category = "SALARY", referenceMonth = "2026-10", status = IncomeStatus.RECEIVED),
        Income(amountCents = 9_000_000, dateEpochDay = d(2026, 11, 28), category = "SALARY", referenceMonth = "2026-11", status = IncomeStatus.PLANNED)
    )
    private val expenses = listOf(
        Expense(amountCents = 2_000_000, dateEpochDay = d(2026, 9, 5), categoryId = "cat_loyer"),
        Expense(amountCents = 1_000_000, dateEpochDay = d(2026, 10, 5), categoryId = "cat_loyer"),
        Expense(amountCents = 300_000, dateEpochDay = d(2026, 10, 6), categoryId = "cat_alimentation", shoppingListId = "L1"),
        Expense(amountCents = 999_999, dateEpochDay = d(2026, 10, 7), categoryId = "cat_loisirs", status = ExpenseStatus.PLANNED)
    )
    private val txs = listOf(SavingsTransaction(goalId = "G", amountCents = 500_000, type = TxType.DEPOSIT, dateEpochDay = d(2026, 10, 10)))

    @Test fun balance_at_end_of_month_ignores_later_and_planned_items() {
        assertEquals(500_000L + 8_000_000 - 2_000_000, StatsCalculator.balanceAtEndOf(sep, 500_000, incomes, expenses, txs))
        assertEquals(500_000L + 16_000_000 - 3_300_000 - 500_000, StatsCalculator.balanceAtEndOf(oct, 500_000, incomes, expenses, txs))
    }

    @Test fun series_has_requested_length_in_chronological_order() {
        val s = StatsCalculator.balanceSeries(oct, 3, 0, incomes, expenses, txs)
        assertEquals(listOf(YearMonth.of(2026, 8), sep, oct), s.map { it.first })
    }

    @Test fun compare_by_category_merges_both_months() {
        val c = StatsCalculator.compareByCategory(oct, sep, expenses)
        assertEquals(Triple("cat_loyer", 1_000_000L, 2_000_000L), c.first())
        assertEquals(Triple("cat_alimentation", 300_000L, 0L), c.first { it.first == "cat_alimentation" })
    }

    @Test fun report_summarizes_month_with_previous_comparison() {
        val r = StatsCalculator.report(oct, 0, incomes, expenses, txs)
        assertEquals(8_000_000L, r.incomeReceived)
        assertEquals(1_300_000L, r.expensesPaid)
        assertEquals(300_000L, r.shoppingPaid)
        assertEquals(500_000L, r.savingsNet)
        assertEquals(8_000_000L, r.prevIncomeReceived)
        assertEquals(2_000_000L, r.prevExpensesPaid)
        assertEquals("cat_loyer", r.byCategory.first().first)
    }

    @Test fun empty_data_gives_zeros() {
        val r = StatsCalculator.report(oct, 0, emptyList(), emptyList(), emptyList())
        assertEquals(0L, r.incomeReceived); assertEquals(0L, r.endBalance); assertEquals(0, r.byCategory.size)
    }
}
