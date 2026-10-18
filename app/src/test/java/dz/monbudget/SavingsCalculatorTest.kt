package dz.monbudget

import dz.monbudget.data.*
import dz.monbudget.domain.BalanceCalculator
import dz.monbudget.domain.SavingsCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SavingsCalculatorTest {
    private val today = LocalDate.of(2026, 10, 17)

    private fun tx(goal: String, type: String, cents: Long) =
        SavingsTransaction(goalId = goal, amountCents = cents, type = type, dateEpochDay = today.toEpochDay())

    @Test fun balance_is_deposits_minus_withdrawals_per_goal() {
        val txs = listOf(tx("A", TxType.DEPOSIT, 100_000), tx("A", TxType.WITHDRAW, 30_000), tx("B", TxType.DEPOSIT, 500_000))
        assertEquals(70_000L, SavingsCalculator.balance("A", txs))
        assertEquals(500_000L, SavingsCalculator.balance("B", txs))
        assertEquals(0L, SavingsCalculator.balance("C", txs))
    }

    @Test fun percent_is_capped_and_safe() {
        assertEquals(50, SavingsCalculator.percent(500, 1000))
        assertEquals(100, SavingsCalculator.percent(2000, 1000))
        assertEquals(0, SavingsCalculator.percent(500, 0))
    }

    @Test fun monthly_needed_rounds_up() {
        val target = LocalDate.of(2027, 4, 15).toEpochDay()   // 6 mois après octobre 2026
        assertEquals(100_000L, SavingsCalculator.monthlyNeeded(1_000_000, 400_000, today, target))
        assertEquals(166_667L, SavingsCalculator.monthlyNeeded(1_000_001, 0, today, target))   // arrondi supérieur
    }

    @Test fun monthly_needed_edge_cases() {
        assertNull(SavingsCalculator.monthlyNeeded(1_000_000, 0, today, null))
        assertEquals(0L, SavingsCalculator.monthlyNeeded(1_000_000, 1_000_000, today, null))
        val past = LocalDate.of(2026, 1, 1).toEpochDay()
        assertEquals(600_000L, SavingsCalculator.monthlyNeeded(1_000_000, 400_000, today, past))   // date passée : tout le reste
    }

    @Test fun deposit_lowers_balance_once_and_is_not_an_expense() {
        val txs = listOf(tx("A", TxType.DEPOSIT, 1_000_000))
        val balance = BalanceCalculator.currentBalance(3_000_000, emptyList(), emptyList(),
            transfersInCents = SavingsCalculator.totalWithdrawals(txs), transfersOutCents = SavingsCalculator.totalDeposits(txs))
        assertEquals(2_000_000L, balance)
        val withdraw = txs + tx("A", TxType.WITHDRAW, 400_000)
        val back = BalanceCalculator.currentBalance(3_000_000, emptyList(), emptyList(),
            transfersInCents = SavingsCalculator.totalWithdrawals(withdraw), transfersOutCents = SavingsCalculator.totalDeposits(withdraw))
        assertEquals(2_400_000L, back)
    }
}
