package dz.monbudget.domain

import dz.monbudget.data.Expense
import dz.monbudget.data.ExpenseStatus
import dz.monbudget.data.Income
import dz.monbudget.data.IncomeStatus
import dz.monbudget.data.SavingsTransaction
import dz.monbudget.data.TxType
import java.time.LocalDate
import java.time.YearMonth

data class MonthReport(
    val month: YearMonth,
    val incomeReceived: Long,
    val expensesPaid: Long,
    val shoppingPaid: Long,
    val byCategory: List<Pair<String, Long>>,   // (categoryId, montant), décroissant
    val savingsNet: Long,                       // versements - retraits du mois
    val endBalance: Long,
    val prevIncomeReceived: Long,
    val prevExpensesPaid: Long
)

object StatsCalculator {
    private fun ym(day: Long) = YearMonth.from(LocalDate.ofEpochDay(day))

    /** Revenus REÇUS rattachés au mois de référence (même règle que le tableau de bord). */
    fun incomeReceived(month: YearMonth, incomes: List<Income>): Long =
        incomes.filter { it.status == IncomeStatus.RECEIVED && it.referenceMonth == month.toString() }.sumOf { it.amountCents }

    fun expensesPaid(month: YearMonth, expenses: List<Expense>): Long =
        expenses.filter { it.status == ExpenseStatus.PAID && ym(it.dateEpochDay) == month }.sumOf { it.amountCents }

    fun expensesByCategory(month: YearMonth, expenses: List<Expense>): Map<String, Long> =
        expenses.filter { it.status == ExpenseStatus.PAID && ym(it.dateEpochDay) == month }
            .groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amountCents } }

    fun savingsNet(month: YearMonth, txs: List<SavingsTransaction>): Long =
        txs.filter { ym(it.dateEpochDay) == month }.sumOf { if (it.type == TxType.DEPOSIT) it.amountCents else -it.amountCents }

    /** Solde courant à la fin d'un mois : même formule que le solde réel, limitée aux opérations datées jusqu'à cette fin de mois. */
    fun balanceAtEndOf(month: YearMonth, initialCents: Long, incomes: List<Income>, expenses: List<Expense>, txs: List<SavingsTransaction>): Long {
        val end = month.atEndOfMonth().toEpochDay()
        val received = incomes.filter { it.status == IncomeStatus.RECEIVED && it.dateEpochDay <= end }.sumOf { it.amountCents }
        val paid = expenses.filter { it.status == ExpenseStatus.PAID && it.dateEpochDay <= end }.sumOf { it.amountCents }
        val out = txs.filter { it.type == TxType.DEPOSIT && it.dateEpochDay <= end }.sumOf { it.amountCents }
        val back = txs.filter { it.type == TxType.WITHDRAW && it.dateEpochDay <= end }.sumOf { it.amountCents }
        return initialCents + received - paid + back - out
    }

    fun balanceSeries(last: YearMonth, count: Int, initialCents: Long, incomes: List<Income>, expenses: List<Expense>, txs: List<SavingsTransaction>): List<Pair<YearMonth, Long>> =
        (count - 1 downTo 0).map { last.minusMonths(it.toLong()) }.map { it to balanceAtEndOf(it, initialCents, incomes, expenses, txs) }

    /** Comparaison catégorie par catégorie entre deux mois : (categoryId, mois A, mois B). */
    fun compareByCategory(a: YearMonth, b: YearMonth, expenses: List<Expense>): List<Triple<String, Long, Long>> {
        val ma = expensesByCategory(a, expenses)
        val mb = expensesByCategory(b, expenses)
        return (ma.keys + mb.keys).map { Triple(it, ma[it] ?: 0L, mb[it] ?: 0L) }.sortedByDescending { maxOf(it.second, it.third) }
    }

    fun report(month: YearMonth, initialCents: Long, incomes: List<Income>, expenses: List<Expense>, txs: List<SavingsTransaction>): MonthReport {
        val prev = month.minusMonths(1)
        return MonthReport(
            month = month,
            incomeReceived = incomeReceived(month, incomes),
            expensesPaid = expensesPaid(month, expenses),
            shoppingPaid = expenses.filter { it.status == ExpenseStatus.PAID && it.shoppingListId != null && ym(it.dateEpochDay) == month }.sumOf { it.amountCents },
            byCategory = expensesByCategory(month, expenses).toList().sortedByDescending { it.second },
            savingsNet = savingsNet(month, txs),
            endBalance = balanceAtEndOf(month, initialCents, incomes, expenses, txs),
            prevIncomeReceived = incomeReceived(prev, incomes),
            prevExpensesPaid = expensesPaid(prev, expenses)
        )
    }
}
