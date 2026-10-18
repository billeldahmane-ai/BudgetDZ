package dz.monbudget

import dz.monbudget.data.*
import dz.monbudget.domain.BalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BalanceCalculatorTest {
    private val today = LocalDate.of(2026, 10, 17)
    private val day = today.toEpochDay()

    private fun income(cents: Long, status: String, category: String = "SALARY", month: String = "2026-10") =
        Income(amountCents = cents, dateEpochDay = day, category = category, referenceMonth = month, status = status)

    private fun expense(cents: Long, status: String = ExpenseStatus.PAID, listId: String? = null) =
        Expense(amountCents = cents, dateEpochDay = day, categoryId = "cat_alimentation", status = status, shoppingListId = listId)

    @Test fun empty_database_gives_initial_balance() {
        assertEquals(0L, BalanceCalculator.currentBalance(0, emptyList(), emptyList()))
        assertEquals(500000L, BalanceCalculator.currentBalance(500000, emptyList(), emptyList()))
    }

    @Test fun planned_salary_is_not_in_balance_until_received() {
        val planned = income(8_000_000, IncomeStatus.PLANNED)
        assertEquals(0L, BalanceCalculator.currentBalance(0, listOf(planned), emptyList()))
        val received = planned.copy(status = IncomeStatus.RECEIVED)
        assertEquals(8_000_000L, BalanceCalculator.currentBalance(0, listOf(received), emptyList()))
    }

    @Test fun only_paid_expenses_reduce_balance() {
        val balance = BalanceCalculator.currentBalance(
            1_000_000, listOf(income(500_000, IncomeStatus.RECEIVED)),
            listOf(expense(200_000), expense(900_000, ExpenseStatus.PLANNED))
        )
        assertEquals(1_300_000L, balance)
    }

    @Test fun transfers_are_neither_income_nor_expense() {
        // 10 000 DZD transférés vers l'épargne : le solde courant baisse une seule fois.
        val balance = BalanceCalculator.currentBalance(2_000_000, emptyList(), emptyList(), transfersOutCents = 1_000_000)
        assertEquals(1_000_000L, balance)
        val summary = BalanceCalculator.summarize(YearMonth.of(2026, 10), today, emptyList(), emptyList())
        assertEquals(0L, summary.expensesPaid)
        assertEquals(0L, summary.incomeReceived)
    }

    @Test fun month_summary_separates_planned_and_real() {
        val incomes = listOf(
            income(8_000_000, IncomeStatus.RECEIVED),
            income(500_000, IncomeStatus.RECEIVED, category = "BONUS"),
            income(1_000_000, IncomeStatus.PLANNED, category = "SALARY", month = "2026-11")
        )
        val expenses = listOf(expense(300_000), expense(100_000, listId = "L1"), expense(50_000, ExpenseStatus.PLANNED))
        val s = BalanceCalculator.summarize(YearMonth.of(2026, 10), today, incomes, expenses)
        assertEquals(8_000_000L, s.salaryReceived)
        assertEquals(0L, s.salaryPlanned)             // le salaire prévu de novembre n'est pas dans octobre
        assertEquals(500_000L, s.otherReceived)
        assertEquals(400_000L, s.expensesPaid)
        assertEquals(50_000L, s.expensesPlanned)
        assertEquals(100_000L, s.shoppingPaid)
        assertEquals(400_000L, s.todayPaid)
    }

    @Test fun percent_spent_handles_zero_income() {
        assertEquals(0, BalanceCalculator.percentSpent(1000, 0))
        assertEquals(40, BalanceCalculator.percentSpent(400, 1000))
    }
}
