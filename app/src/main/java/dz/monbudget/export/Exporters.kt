package dz.monbudget.export

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import dz.monbudget.data.BackupSnapshot
import dz.monbudget.data.ExpenseStatus
import dz.monbudget.data.IncomeCategories
import dz.monbudget.data.IncomeStatus
import dz.monbudget.domain.MonthReport
import dz.monbudget.util.Money
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDate

object CsvExporter {
    private fun amount(cents: Long) = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')

    /** Texte entre guillemets ; une valeur commençant par = + - @ est neutralisée (injection de formule dans un tableur). */
    private fun text(s: String): String {
        val safe = if (s.isNotEmpty() && s[0] in "=+-@") "'$s" else s
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    /** Séparateur « ; » et BOM UTF-8 : s'ouvre correctement dans Excel en français. */
    fun build(s: BackupSnapshot, categoryNames: Map<String, String>): String {
        val rows = ArrayList<Pair<Long, String>>()
        s.incomes.forEach {
            val status = if (it.status == IncomeStatus.RECEIVED) "Reçu" else "Prévu"
            rows += it.dateEpochDay to listOf(
                LocalDate.ofEpochDay(it.dateEpochDay).toString(), "Revenu", text(IncomeCategories.label(it.category)),
                text(it.description), amount(it.amountCents), "", status
            ).joinToString(";")
        }
        s.expenses.forEach {
            val status = if (it.status == ExpenseStatus.PAID) "Payé" else "Prévu"
            rows += it.dateEpochDay to listOf(
                LocalDate.ofEpochDay(it.dateEpochDay).toString(), "Dépense", text(categoryNames[it.categoryId] ?: it.categoryId),
                text(it.description), amount(it.amountCents), text(it.paymentMethod), status
            ).joinToString(";")
        }
        val header = "Date;Type;Catégorie;Description;Montant (DZD);Moyen de paiement;Statut"
        return "\uFEFF" + header + "\n" + rows.sortedByDescending { it.first }.joinToString("\n") { it.second } + "\n"
    }
}

object PdfReport {
    private const val W = 595
    private const val H = 842

    fun build(r: MonthReport, categoryNames: Map<String, String>): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val c = page.canvas
        val title = Paint().apply { textSize = 20f; isFakeBoldText = true }
        val head = Paint().apply { textSize = 14f; isFakeBoldText = true }
        val body = Paint().apply { textSize = 11f }
        val bold = Paint(body).apply { isFakeBoldText = true }
        val green = Paint().apply { color = Color.rgb(46, 125, 50) }
        val red = Paint().apply { color = Color.rgb(198, 40, 40) }
        val blue = Paint().apply { color = Color.rgb(25, 118, 210) }
        var y = 50f

        c.drawText("Mon Budget DZ – Rapport de ${r.month}", 40f, y, title); y += 32
        fun row(label: String, value: String) { c.drawText(label, 40f, y, body); c.drawText(value, 340f, y, bold); y += 18 }
        fun delta(now: Long, before: Long) = (if (now - before >= 0) "+" else "") + Money.format(now - before) + " vs mois précédent"

        row("Revenus totaux (reçus)", Money.format(r.incomeReceived))
        row("Dépenses totales (payées)", Money.format(r.expensesPaid))
        row("dont courses", Money.format(r.shoppingPaid))
        row("Épargne nette du mois", Money.format(r.savingsNet))
        row("Solde de fin de mois", Money.format(r.endBalance))
        y += 8
        c.drawText("Comparaison avec le mois précédent", 40f, y, head); y += 20
        row("Revenus : ${Money.format(r.prevIncomeReceived)} avant", delta(r.incomeReceived, r.prevIncomeReceived))
        row("Dépenses : ${Money.format(r.prevExpensesPaid)} avant", delta(r.expensesPaid, r.prevExpensesPaid))
        y += 8

        // Graphique 1 : revenus / dépenses, mois précédent et mois courant
        c.drawText("Revenus et dépenses", 40f, y, head); y += 16
        val maxBar = maxOf(1L, r.incomeReceived, r.expensesPaid, r.prevIncomeReceived, r.prevExpensesPaid).toFloat()
        fun bar(label: String, value: Long, paint: Paint) {
            c.drawText(label, 40f, y + 10f, body)
            c.drawRect(150f, y, 150f + 300f * value / maxBar, y + 12f, paint)
            c.drawText(Money.format(value), 456f, y + 10f, body); y += 18
        }
        bar("Revenus (préc.)", r.prevIncomeReceived, green)
        bar("Revenus", r.incomeReceived, green)
        bar("Dépenses (préc.)", r.prevExpensesPaid, red)
        bar("Dépenses", r.expensesPaid, red)
        y += 10

        // Graphique 2 : dépenses par catégorie
        c.drawText("Dépenses par catégorie", 40f, y, head); y += 16
        val maxCat = maxOf(1L, r.byCategory.maxOfOrNull { it.second } ?: 1L).toFloat()
        if (r.byCategory.isEmpty()) { c.drawText("Aucune dépense ce mois-ci.", 40f, y + 10f, body); y += 18 }
        r.byCategory.take(15).forEach { (id, value) ->
            c.drawText((categoryNames[id] ?: id).take(26), 40f, y + 10f, body)
            c.drawRect(190f, y, 190f + 260f * value / maxCat, y + 12f, blue)
            c.drawText(Money.format(value), 456f, y + 10f, body); y += 18
        }
        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
