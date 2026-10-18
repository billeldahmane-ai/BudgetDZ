package dz.monbudget.domain

import dz.monbudget.data.Expense
import dz.monbudget.data.ExpenseStatus
import dz.monbudget.data.Income
import dz.monbudget.data.IncomeStatus
import java.time.LocalDate
import java.time.YearMonth

/** Résumé d'un mois. Tous les montants en centimes. */
data class MonthSummary(
    val salaryReceived: Long,
    val salaryPlanned: Long,     // salaire attendu mais NON confirmé : affiché à part, jamais dans le solde
    val otherReceived: Long,
    val expensesPaid: Long,
    val expensesPlanned: Long,   // dépenses prévues non payées : affichées à part
    val shoppingPaid: Long,      // part des dépenses issues de listes de courses
    val todayPaid: Long
) {
    val incomeReceived: Long get() = salaryReceived + otherReceived
}

/**
 * RÈGLES DE CALCUL FINANCIER (source unique de vérité)
 *
 * Solde courant = solde initial + revenus REÇUS - dépenses PAYÉES + transferts entrants - transferts sortants
 *
 *  - Un revenu PLANNED (ex. salaire pas encore versé) n'est jamais inclus dans le solde.
 *  - Une dépense PLANNED n'est jamais déduite du solde.
 *  - Un transfert interne (ex. vers l'épargne) n'est NI un revenu NI une dépense : il n'apparaît
 *    que dans transfersIn / transfersOut (étape 3), donc jamais compté deux fois.
 *  - Un article de liste de courses n'est pas une dépense. Seule la validation de la liste crée des dépenses.
 */
object BalanceCalculator {

    fun currentBalance(
        initialCents: Long,
        incomes: List<Income>,
        expenses: List<Expense>,
        transfersInCents: Long = 0,
        transfersOutCents: Long = 0
    ): Long {
        val received = incomes.filter { it.status == IncomeStatus.RECEIVED }.sumOf { it.amountCents }
        val paid = expenses.filter { it.status == ExpenseStatus.PAID }.sumOf { it.amountCents }
        return initialCents + received - paid + transfersInCents - transfersOutCents
    }

    /** Revenus : rattachés au mois via referenceMonth. Dépenses : rattachées via leur date. */
    fun summarize(month: YearMonth, today: LocalDate, incomes: List<Income>, expenses: List<Expense>): MonthSummary {
        val key = month.toString()
        val monthIncomes = incomes.filter { it.referenceMonth == key }
        val monthExpenses = expenses.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
        val paid = monthExpenses.filter { it.status == ExpenseStatus.PAID }
        return MonthSummary(
            salaryReceived = monthIncomes.filter { it.category == "SALARY" && it.status == IncomeStatus.RECEIVED }.sumOf { it.amountCents },
            salaryPlanned = monthIncomes.filter { it.category == "SALARY" && it.status == IncomeStatus.PLANNED }.sumOf { it.amountCents },
            otherReceived = monthIncomes.filter { it.category != "SALARY" && it.status == IncomeStatus.RECEIVED }.sumOf { it.amountCents },
            expensesPaid = paid.sumOf { it.amountCents },
            expensesPlanned = monthExpenses.filter { it.status == ExpenseStatus.PLANNED }.sumOf { it.amountCents },
            shoppingPaid = paid.filter { it.shoppingListId != null }.sumOf { it.amountCents },
            todayPaid = expenses.filter { it.status == ExpenseStatus.PAID && it.dateEpochDay == today.toEpochDay() }.sumOf { it.amountCents }
        )
    }

    /** Pourcentage des revenus reçus du mois déjà dépensé (0 si aucun revenu reçu). */
    fun percentSpent(spentCents: Long, receivedCents: Long): Int =
        if (receivedCents <= 0) 0 else (spentCents * 100 / receivedCents).coerceIn(0, 999).toInt()
}
