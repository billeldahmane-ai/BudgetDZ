package dz.monbudget.domain

import dz.monbudget.data.BudgetKeys
import dz.monbudget.data.BudgetLimit
import dz.monbudget.data.Expense
import dz.monbudget.data.ExpenseStatus
import java.time.LocalDate
import java.time.YearMonth

enum class BudgetLevel { OK, WARN, CRITICAL, OVER }

data class BudgetLine(val categoryId: String, val limitCents: Long, val spentCents: Long, val level: BudgetLevel) {
    val remainingCents: Long get() = limitCents - spentCents
    val percent: Int get() = if (limitCents <= 0) 0 else (spentCents * 100 / limitCents).coerceIn(0, 999).toInt()
}

/**
 * RÈGLES DE BUDGET
 *  - Montant dépensé = dépenses PAYÉES du mois (par date) dans la catégorie. Les dépenses prévues ne comptent pas.
 *  - Niveaux : OVER si dépensé > budget ; CRITICAL si >= seuil critique (90 % par défaut) ;
 *    WARN si >= seuil d'alerte (70 % par défaut) ; sinon OK. Seuils personnalisables.
 *  - Un budget appartient à UN mois : modifier le mois courant ne touche jamais les mois passés.
 */
object BudgetCalculator {

    fun level(limitCents: Long, spentCents: Long, warnPct: Int, critPct: Int): BudgetLevel = when {
        limitCents <= 0 -> BudgetLevel.OK
        spentCents > limitCents -> BudgetLevel.OVER
        spentCents * 100 >= critPct.toLong() * limitCents -> BudgetLevel.CRITICAL
        spentCents * 100 >= warnPct.toLong() * limitCents -> BudgetLevel.WARN
        else -> BudgetLevel.OK
    }

    private fun paidInMonth(month: YearMonth, expenses: List<Expense>) =
        expenses.filter { it.status == ExpenseStatus.PAID && YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }

    fun categoryLines(month: YearMonth, budgets: List<BudgetLimit>, expenses: List<Expense>, warnPct: Int, critPct: Int): List<BudgetLine> {
        val paid = paidInMonth(month, expenses)
        return budgets.filter { it.month == month.toString() && it.categoryId != BudgetKeys.GLOBAL }.map { b ->
            val spent = paid.filter { it.categoryId == b.categoryId }.sumOf { it.amountCents }
            BudgetLine(b.categoryId, b.limitCents, spent, level(b.limitCents, spent, warnPct, critPct))
        }
    }

    fun globalLine(month: YearMonth, budgets: List<BudgetLimit>, expenses: List<Expense>, warnPct: Int, critPct: Int): BudgetLine? {
        val b = budgets.firstOrNull { it.month == month.toString() && it.categoryId == BudgetKeys.GLOBAL } ?: return null
        val spent = paidInMonth(month, expenses).sumOf { it.amountCents }
        return BudgetLine(BudgetKeys.GLOBAL, b.limitCents, spent, level(b.limitCents, spent, warnPct, critPct))
    }

    /** Copie les budgets de [from] vers [to] SANS écraser ceux déjà définis pour [to] ni toucher à [from]. */
    fun carryOver(budgets: List<BudgetLimit>, from: YearMonth, to: YearMonth): List<BudgetLimit> {
        val existing = budgets.filter { it.month == to.toString() }.map { it.categoryId }.toSet()
        return budgets.filter { it.month == from.toString() && it.categoryId !in existing }
            .map { BudgetLimit(BudgetKeys.id(to.toString(), it.categoryId), to.toString(), it.categoryId, it.limitCents) }
    }
}
