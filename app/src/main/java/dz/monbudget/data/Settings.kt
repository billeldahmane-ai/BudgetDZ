package dz.monbudget.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class NotifType(val label: String, val key: Preferences.Key<Boolean>) {
    SALARY("Jour de réception du salaire", booleanPreferencesKey("n_salary")),
    EXPENSE_ENTRY("Saisie des dépenses (chaque soir, 19 h)", booleanPreferencesKey("n_expense")),
    SHOPPING("Préparation des courses (le jeudi)", booleanPreferencesKey("n_shopping")),
    BUDGET("Alertes de budget (seuils et dépassement)", booleanPreferencesKey("n_budget")),
    BILL("Facture à payer (3 jours avant l'échéance)", booleanPreferencesKey("n_bill")),
    SAVINGS("Objectif d'épargne (le 1er du mois)", booleanPreferencesKey("n_savings"))
}

data class AppSettings(
    val warnPct: Int = 70,
    val critPct: Int = 90,
    val salaryDay: Int = 1,
    val monthlySavingsCents: Long = 0L,
    val hideAmounts: Boolean = false,
    val notifications: Set<NotifType> = emptySet()
)

class SettingsRepository(private val context: Context) {
    private object K {
        val warn = intPreferencesKey("warn_pct")
        val crit = intPreferencesKey("crit_pct")
        val salaryDay = intPreferencesKey("salary_day")
        val monthlySavings = longPreferencesKey("monthly_savings")
        val hide = booleanPreferencesKey("hide_amounts")
        val alertsSent = stringSetPreferencesKey("alerts_sent")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            warnPct = p[K.warn] ?: 70,
            critPct = p[K.crit] ?: 90,
            salaryDay = p[K.salaryDay] ?: 1,
            monthlySavingsCents = p[K.monthlySavings] ?: 0L,
            hideAmounts = p[K.hide] ?: false,
            notifications = NotifType.values().filter { p[it.key] == true }.toSet()
        )
    }

    suspend fun setThresholds(warn: Int, crit: Int) { context.dataStore.edit { it[K.warn] = warn; it[K.crit] = crit } }
    suspend fun setSalaryDay(day: Int) { context.dataStore.edit { it[K.salaryDay] = day } }
    suspend fun setMonthlySavings(cents: Long) { context.dataStore.edit { it[K.monthlySavings] = cents } }
    suspend fun setHideAmounts(hide: Boolean) { context.dataStore.edit { it[K.hide] = hide } }
    suspend fun setNotification(type: NotifType, enabled: Boolean) { context.dataStore.edit { it[type.key] = enabled } }

    /** Alertes de budget déjà notifiées ("2026-10:cat_loyer:WARN") : évite de répéter la même alerte chaque jour. */
    suspend fun alertsSent(): Set<String> = context.dataStore.data.first()[K.alertsSent] ?: emptySet()
    suspend fun saveAlertsSent(keys: Set<String>) { context.dataStore.edit { it[K.alertsSent] = keys } }
}
