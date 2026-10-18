package dz.monbudget.data

import org.json.JSONArray
import org.json.JSONObject

data class BackupSnapshot(
    val initialBalanceCents: Long,
    val incomes: List<Income>,
    val expenses: List<Expense>,
    val lists: List<ShoppingList>,
    val items: List<ShoppingItem>,
    val budgets: List<BudgetLimit>,
    val goals: List<SavingsGoal>,
    val savingsTx: List<SavingsTransaction>,
    val bills: List<Bill>
)

/** Sauvegarde = fichier JSON lisible, choisi par l'utilisateur. Aucune clé, aucun chiffrement (voir README). */
object BackupCodec {
    const val FORMAT = 1

    private fun JSONArray.objs(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
    private fun <T> arr(items: List<T>, f: (T) -> JSONObject) = JSONArray(items.map(f))

    fun encode(s: BackupSnapshot): String = JSONObject().apply {
        put("app", "MonBudgetDZ")
        put("format", FORMAT)
        put("initialBalanceCents", s.initialBalanceCents)
        put("incomes", arr(s.incomes) {
            JSONObject().put("id", it.id).put("amountCents", it.amountCents).put("dateEpochDay", it.dateEpochDay)
                .put("category", it.category).put("description", it.description).put("referenceMonth", it.referenceMonth).put("status", it.status)
        })
        put("expenses", arr(s.expenses) {
            JSONObject().put("id", it.id).put("amountCents", it.amountCents).put("dateEpochDay", it.dateEpochDay)
                .put("categoryId", it.categoryId).put("description", it.description).put("paymentMethod", it.paymentMethod)
                .put("note", it.note).put("status", it.status).put("shoppingListId", it.shoppingListId ?: JSONObject.NULL)
        })
        put("lists", arr(s.lists) {
            JSONObject().put("id", it.id).put("name", it.name).put("createdEpochDay", it.createdEpochDay)
                .put("plannedBudgetCents", it.plannedBudgetCents).put("status", it.status)
        })
        put("items", arr(s.items) {
            JSONObject().put("id", it.id).put("listId", it.listId).put("name", it.name).put("categoryId", it.categoryId)
                .put("quantityMilli", it.quantityMilli).put("unit", it.unit).put("estUnitCents", it.estUnitCents)
                .put("realUnitCents", it.realUnitCents ?: JSONObject.NULL).put("store", it.store).put("note", it.note).put("bought", it.bought)
        })
        put("budgets", arr(s.budgets) {
            JSONObject().put("id", it.id).put("month", it.month).put("categoryId", it.categoryId).put("limitCents", it.limitCents)
        })
        put("goals", arr(s.goals) {
            JSONObject().put("id", it.id).put("name", it.name).put("targetCents", it.targetCents)
                .put("targetEpochDay", it.targetEpochDay ?: JSONObject.NULL).put("createdEpochDay", it.createdEpochDay)
        })
        put("savingsTx", arr(s.savingsTx) {
            JSONObject().put("id", it.id).put("goalId", it.goalId).put("amountCents", it.amountCents).put("type", it.type)
                .put("dateEpochDay", it.dateEpochDay).put("note", it.note)
        })
        put("bills", arr(s.bills) {
            JSONObject().put("id", it.id).put("name", it.name).put("amountCents", it.amountCents).put("dueEpochDay", it.dueEpochDay)
                .put("categoryId", it.categoryId).put("paid", it.paid)
        })
    }.toString(2)

    /** Lève une exception si le fichier n'est pas une sauvegarde valide : AVANT toute modification des données. */
    fun decode(text: String): BackupSnapshot {
        val o = JSONObject(text)
        require(o.optString("app") == "MonBudgetDZ" && o.optInt("format") == FORMAT) { "Fichier de sauvegarde non reconnu" }
        return BackupSnapshot(
            initialBalanceCents = o.getLong("initialBalanceCents"),
            incomes = o.getJSONArray("incomes").objs().map {
                Income(it.getString("id"), it.getLong("amountCents"), it.getLong("dateEpochDay"), it.getString("category"),
                    it.getString("description"), it.getString("referenceMonth"), it.getString("status"))
            },
            expenses = o.getJSONArray("expenses").objs().map {
                Expense(it.getString("id"), it.getLong("amountCents"), it.getLong("dateEpochDay"), it.getString("categoryId"),
                    it.getString("description"), it.getString("paymentMethod"), it.getString("note"), it.getString("status"),
                    if (it.isNull("shoppingListId")) null else it.getString("shoppingListId"))
            },
            lists = o.getJSONArray("lists").objs().map {
                ShoppingList(it.getString("id"), it.getString("name"), it.getLong("createdEpochDay"), it.getLong("plannedBudgetCents"), it.getString("status"))
            },
            items = o.getJSONArray("items").objs().map {
                ShoppingItem(it.getString("id"), it.getString("listId"), it.getString("name"), it.getString("categoryId"),
                    it.getLong("quantityMilli"), it.getString("unit"), it.getLong("estUnitCents"), it.longOrNull("realUnitCents"),
                    it.getString("store"), it.getString("note"), it.getBoolean("bought"))
            },
            budgets = o.getJSONArray("budgets").objs().map {
                BudgetLimit(it.getString("id"), it.getString("month"), it.getString("categoryId"), it.getLong("limitCents"))
            },
            goals = o.getJSONArray("goals").objs().map {
                SavingsGoal(it.getString("id"), it.getString("name"), it.getLong("targetCents"), it.longOrNull("targetEpochDay"), it.getLong("createdEpochDay"))
            },
            savingsTx = o.getJSONArray("savingsTx").objs().map {
                SavingsTransaction(it.getString("id"), it.getString("goalId"), it.getLong("amountCents"), it.getString("type"),
                    it.getLong("dateEpochDay"), it.getString("note"))
            },
            bills = o.getJSONArray("bills").objs().map {
                Bill(it.getString("id"), it.getString("name"), it.getLong("amountCents"), it.getLong("dueEpochDay"), it.getString("categoryId"), it.getBoolean("paid"))
            }
        )
    }
}
