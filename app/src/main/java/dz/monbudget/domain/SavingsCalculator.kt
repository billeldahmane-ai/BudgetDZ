package dz.monbudget.domain

import dz.monbudget.data.SavingsTransaction
import dz.monbudget.data.TxType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Épargne = transferts internes. Solde d'un objectif = versements - retraits. */
object SavingsCalculator {

    private fun signed(tx: SavingsTransaction) = if (tx.type == TxType.DEPOSIT) tx.amountCents else -tx.amountCents

    fun balance(goalId: String, txs: List<SavingsTransaction>): Long = txs.filter { it.goalId == goalId }.sumOf { signed(it) }

    fun totalDeposits(txs: List<SavingsTransaction>): Long = txs.filter { it.type == TxType.DEPOSIT }.sumOf { it.amountCents }
    fun totalWithdrawals(txs: List<SavingsTransaction>): Long = txs.filter { it.type == TxType.WITHDRAW }.sumOf { it.amountCents }

    fun percent(savedCents: Long, targetCents: Long): Int =
        if (targetCents <= 0) 0 else (savedCents * 100 / targetCents).coerceIn(0, 100).toInt()

    /**
     * Montant à épargner chaque mois pour atteindre l'objectif à la date cible.
     * null : pas de date cible. 0 : objectif déjà atteint. Date passée ou ce mois-ci : tout le reste en 1 mois.
     */
    fun monthlyNeeded(targetCents: Long, savedCents: Long, today: LocalDate, targetEpochDay: Long?): Long? {
        val remaining = targetCents - savedCents
        if (remaining <= 0) return 0
        if (targetEpochDay == null) return null
        val target = LocalDate.ofEpochDay(targetEpochDay)
        val months = maxOf(1L, ChronoUnit.MONTHS.between(today.withDayOfMonth(1), target.withDayOfMonth(1)))
        return (remaining + months - 1) / months   // arrondi au centime supérieur
    }
}
