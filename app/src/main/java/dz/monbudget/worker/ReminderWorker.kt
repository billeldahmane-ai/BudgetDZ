package dz.monbudget.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dz.monbudget.MainActivity
import dz.monbudget.data.*
import dz.monbudget.domain.BudgetCalculator
import dz.monbudget.domain.BudgetLevel
import dz.monbudget.domain.SavingsCalculator
import dz.monbudget.util.Money
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.concurrent.TimeUnit

private const val CHANNEL = "rappels"

object ReminderScheduler {
    /** Une exécution par jour, vers 19 h (WorkManager peut décaler l'heure : économie de batterie). Aucun serveur. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(19, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminders", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try { check() } catch (e: Exception) { /* un rappel raté ne doit jamais faire planter l'application */ }
        return Result.success()
    }

    private suspend fun check() {
        val ctx = applicationContext
        val settingsRepo = SettingsRepository(ctx)
        val s = settingsRepo.settings.first()
        if (s.notifications.isEmpty()) return
        val db = AppDatabase.get(ctx)
        val today = LocalDate.now()
        val month = YearMonth.from(today)
        ensureChannel(ctx)

        if (NotifType.SALARY in s.notifications && today.dayOfMonth == minOf(s.salaryDay, today.lengthOfMonth())) {
            val received = db.incomeDao().getAll().any { it.category == "SALARY" && it.referenceMonth == month.toString() && it.status == IncomeStatus.RECEIVED }
            if (!received) post(ctx, 1, "Jour de salaire", "Confirmez la réception de votre salaire dans Revenus.")
        }
        if (NotifType.EXPENSE_ENTRY in s.notifications) {
            if (db.expenseDao().getAll().none { it.dateEpochDay == today.toEpochDay() }) post(ctx, 2, "Dépenses du jour", "Pensez à saisir vos dépenses d'aujourd'hui.")
        }
        if (NotifType.SHOPPING in s.notifications && today.dayOfWeek == DayOfWeek.THURSDAY) {
            if (db.shoppingDao().getAllLists().none { it.status == ListStatus.ACTIVE }) post(ctx, 3, "Courses", "Préparez votre liste de courses pour le week-end.")
        }
        if (NotifType.BUDGET in s.notifications) {
            val expenses = db.expenseDao().getAll()
            val budgets = db.budgetDao().getAll()
            val names = db.categoryDao().getAll().associate { it.id to it.name }
            val lines = BudgetCalculator.categoryLines(month, budgets, expenses, s.warnPct, s.critPct) +
                listOfNotNull(BudgetCalculator.globalLine(month, budgets, expenses, s.warnPct, s.critPct))
            val sent = settingsRepo.alertsSent().filter { it.startsWith(month.toString()) }.toMutableSet()
            lines.forEach { line ->
                if (line.level == BudgetLevel.OK) return@forEach
                val key = "$month:${line.categoryId}:${line.level}"
                if (sent.add(key)) {
                    val name = if (line.categoryId == BudgetKeys.GLOBAL) "Budget global" else names[line.categoryId] ?: line.categoryId
                    val text = if (line.level == BudgetLevel.OVER) "$name : budget dépassé (${line.percent} %)." else "$name : ${line.percent} % du budget consommé."
                    post(ctx, 100 + (key.hashCode() and 0xFFFF), "Alerte budget", text)
                }
            }
            settingsRepo.saveAlertsSent(sent)
        }
        if (NotifType.BILL in s.notifications) {
            val due = db.billDao().getAll().filter { !it.paid && it.dueEpochDay <= today.plusDays(3).toEpochDay() }
            if (due.isNotEmpty()) post(ctx, 5, "Factures à payer", "${due.size} facture(s) à régler, ${Money.format(due.sumOf { it.amountCents })} au total.")
        }
        if (NotifType.SAVINGS in s.notifications && today.dayOfMonth == 1) {
            val goals = db.savingsDao().getGoals()
            val txs = db.savingsDao().getTransactions()
            if (goals.any { SavingsCalculator.balance(it.id, txs) < it.targetCents }) post(ctx, 6, "Épargne", "Pensez à alimenter vos objectifs d'épargne ce mois-ci.")
        }
    }

    private fun ensureChannel(ctx: Context) {
        val manager = ctx.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Rappels", NotificationManager.IMPORTANCE_DEFAULT))
    }

    private fun post(ctx: Context, id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(ctx)
        if (!manager.areNotificationsEnabled()) return
        val intent = PendingIntent.getActivity(ctx, id, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text)
            .setContentIntent(intent).setAutoCancel(true).build()
        try { manager.notify(id, notification) } catch (e: SecurityException) { /* permission refusée */ }
    }
}
