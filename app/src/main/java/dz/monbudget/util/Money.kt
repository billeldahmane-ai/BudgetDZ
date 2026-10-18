package dz.monbudget.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * RÈGLE MÉTIER : tous les montants sont stockés en CENTIMES de DZD (Long).
 * Aucun Double/Float n'est utilisé pour l'argent : pas d'erreur d'arrondi.
 * Les quantités sont stockées en millièmes d'unité (2,5 kg = 2500).
 */
object Money {
    const val MAX_AMOUNT_CENTS = 1_000_000_000L   // 10 000 000 DZD : plafond de saisie
    const val MAX_QTY_MILLI = 100_000_000L        // 100 000 unités : évite tout dépassement de Long

    private val numberPattern = Regex("^\\d{1,12}(\\.\\d{1,6})?$")

    /** Saisie utilisateur (virgule ou point) -> centimes. null si invalide (négatif, texte, vide, trop grand). */
    fun parseAmount(input: String, allowZero: Boolean = false): Long? {
        val cents = parseScaled(input, 2) ?: return null
        if (cents > MAX_AMOUNT_CENTS) return null
        if (cents == 0L && !allowZero) return null
        return cents
    }

    /** Saisie utilisateur -> millièmes d'unité. null si invalide ou <= 0. */
    fun parseQuantity(input: String): Long? {
        val milli = parseScaled(input, 3) ?: return null
        if (milli <= 0L || milli > MAX_QTY_MILLI) return null
        return milli
    }

    private fun parseScaled(input: String, scale: Int): Long? {
        val s = input.trim().replace(" ", "").replace(",", ".")
        if (!numberPattern.matches(s)) return null
        return BigDecimal(s).setScale(scale, RoundingMode.HALF_UP).movePointRight(scale).toLong()
    }

    /** 123456 -> "1 234,56 DZD" ; 120000 -> "1 200 DZD". */
    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = Math.abs(cents)
        val dinars = (abs / 100).toString().reversed().chunked(3).joinToString(" ").reversed()
        val rest = abs % 100
        val body = if (rest == 0L) dinars else "$dinars,${rest.toString().padStart(2, '0')}"
        return (if (negative) "-" else "") + body + " DZD"
    }

    /** Centimes -> texte éditable ("1400" ou "1400.5"). */
    fun toInput(cents: Long): String = BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

    /** 2500 -> "2,5" ; 2000 -> "2". */
    fun formatQuantity(milli: Long): String =
        BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString().replace('.', ',')

    fun quantityToInput(milli: Long): String = BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
}
