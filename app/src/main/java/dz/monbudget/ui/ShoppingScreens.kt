@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dz.monbudget.data.*
import dz.monbudget.domain.ConvertMode
import dz.monbudget.domain.ShoppingCalculator
import dz.monbudget.util.Money
import java.util.UUID

private fun statusLabel(status: String) = when (status) {
    ListStatus.ACTIVE -> "En cours"
    ListStatus.ARCHIVED -> "Archivée"
    else -> "Enregistrée en dépenses"
}

// ---------------------------------------------------------------- Liste des listes
@Composable
fun ShoppingListsScreen(vm: MainViewModel, onOpen: (String) -> Unit) {
    val lists by vm.lists.collectAsState()
    val items by vm.items.collectAsState()
    var newListOpen by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ShoppingList?>(null) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Button(onClick = { newListOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Nouvelle liste de courses") } }
        if (lists.isEmpty()) item { Text("Aucune liste. Créez par exemple « Courses de la semaine ».") }
        items(lists, key = { it.id }) { list ->
            val summary = ShoppingCalculator.summarize(items.filter { it.listId == list.id }, list.plannedBudgetCents)
            Card(onClick = { onOpen(list.id) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(list.name, style = MaterialTheme.typography.titleSmall)
                        Text("${statusLabel(list.status)} · ${summary.doneCount}/${summary.totalCount} articles (${summary.percentDone} %)", style = MaterialTheme.typography.bodySmall)
                        Text("Estimé : ${Money.format(summary.estimatedTotal)}")
                    }
                    IconButton(onClick = { toDelete = list }) { Icon(Icons.Default.Delete, "Supprimer") }
                }
            }
        }
    }

    if (newListOpen) {
        var name by remember { mutableStateOf("") }
        var budget by remember { mutableStateOf("") }
        val budgetCents = if (budget.isBlank()) 0L else Money.parseAmount(budget, allowZero = true)
        AlertDialog(
            onDismissRequest = { newListOpen = false },
            title = { Text("Nouvelle liste") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField1("Nom de la liste", name) { name = it }
                    AmountField("Budget prévu en DZD (facultatif)", budget, { budget = it }, error = budgetCents == null)
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && budgetCents != null, onClick = {
                    vm.createList(name.trim(), budgetCents ?: 0L); newListOpen = false
                }) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { newListOpen = false }) { Text("Annuler") } }
        )
    }
    toDelete?.let { list ->
        ConfirmDialog("Supprimer la liste « ${list.name} » ?",
            "Ses articles seront supprimés. Les dépenses déjà enregistrées à partir de cette liste sont conservées.",
            onConfirm = { vm.deleteList(list.id) }, onDismiss = { toDelete = null })
    }
}

// ---------------------------------------------------------------- Détail d'une liste
@Composable
fun ShoppingDetailScreen(vm: MainViewModel, listId: String, onShopMode: () -> Unit, onClose: () -> Unit) {
    val lists by vm.lists.collectAsState()
    val allItems by vm.items.collectAsState()
    val categories by vm.categories.collectAsState()
    val list = lists.firstOrNull { it.id == listId }
    if (list == null) {
        Text("Liste introuvable.", Modifier.padding(16.dp))
        return
    }
    val items = allItems.filter { it.listId == listId }.sortedWith(compareBy({ it.bought }, { it.name.lowercase() }))
    val summary = ShoppingCalculator.summarize(items, list.plannedBudgetCents)
    val editable = list.status == ListStatus.ACTIVE
    var itemDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var toDelete by remember { mutableStateOf<ShoppingItem?>(null) }
    var validateOpen by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${list.name} · ${statusLabel(list.status)}", style = MaterialTheme.typography.titleMedium)
                    Text("Total estimé : ${Money.format(summary.estimatedTotal)}")
                    Text("Coût réel des articles achetés : ${Money.format(summary.realBought)}")
                    Text("Reste à acheter (estimé) : ${Money.format(summary.remainingToBuy)}")
                    Text("Écart réel / estimé : ${Money.format(summary.variance)}")
                    Text("Budget restant : ${Money.format(summary.budgetRemaining)}")
                    Text("Réalisation : ${summary.percentDone} %")
                    LinearProgressIndicator(progress = { summary.percentDone / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (editable) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { editing = null; itemDialog = true }, modifier = Modifier.weight(1f)) { Text("Ajouter") }
                    Button(onClick = onShopMode, enabled = items.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Mode courses") }
                }
            }
            item {
                OutlinedButton(onClick = { validateOpen = true }, enabled = items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    Text("Terminer et valider la liste")
                }
            }
        } else {
            item { Text("Cette liste est clôturée : elle n'est plus modifiable.") }
        }
        if (items.isEmpty()) item { Text("Aucun article.") }
        items(items, key = { it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = item.bought, enabled = editable, onCheckedChange = { vm.toggleBought(item) })
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall)
                        Text("${Money.formatQuantity(item.quantityMilli)} ${item.unit} · ${Money.format(ShoppingCalculator.estimatedCost(item))}", style = MaterialTheme.typography.bodySmall)
                        if (item.realUnitCents != null) Text("Réel : ${Money.format(ShoppingCalculator.realCost(item))}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (editable) {
                        IconButton(onClick = { editing = item; itemDialog = true }) { Icon(Icons.Default.Edit, "Modifier") }
                        IconButton(onClick = { toDelete = item }) { Icon(Icons.Default.Delete, "Supprimer") }
                    }
                }
            }
        }
    }

    if (itemDialog) {
        ItemDialog(listId, editing, categories, onSave = { vm.saveItem(it); itemDialog = false }, onDismiss = { itemDialog = false })
    }
    toDelete?.let { item ->
        ConfirmDialog("Supprimer « ${item.name} » ?", "Cet article sera retiré de la liste.", onConfirm = { vm.deleteItem(item) }, onDismiss = { toDelete = null })
    }
    if (validateOpen) {
        var record by remember { mutableStateOf(false) }
        var mode by remember { mutableStateOf(ConvertMode.GLOBAL) }
        AlertDialog(
            onDismissRequest = { validateOpen = false },
            title = { Text("Valider la liste") },
            text = {
                Column {
                    ChoiceRow("Archiver sans créer de dépenses", !record) { record = false }
                    ChoiceRow("Enregistrer les achats comme dépenses réelles", record) { record = true }
                    if (record) {
                        Spacer(Modifier.height(8.dp))
                        Text("Seuls les articles cochés comptent, au prix réel (sinon estimé).", style = MaterialTheme.typography.bodySmall)
                        ChoiceRow("Une seule dépense globale", mode == ConvertMode.GLOBAL) { mode = ConvertMode.GLOBAL }
                        ChoiceRow("Une dépense par catégorie", mode == ConvertMode.BY_CATEGORY) { mode = ConvertMode.BY_CATEGORY }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (record) vm.convertList(listId, mode) else vm.archiveList(listId)
                    validateOpen = false
                    onClose()
                }) { Text("Valider") }
            },
            dismissButton = { TextButton(onClick = { validateOpen = false }) { Text("Annuler") } }
        )
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
fun ItemDialog(listId: String, initial: ShoppingItem?, categories: List<Category>, onSave: (ShoppingItem) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: DefaultIds.FOOD) }
    var quantity by remember { mutableStateOf(initial?.let { Money.quantityToInput(it.quantityMilli) } ?: "1") }
    var unit by remember { mutableStateOf(initial?.unit ?: Units.all.first()) }
    var estimated by remember { mutableStateOf(initial?.let { Money.toInput(it.estUnitCents) } ?: "") }
    var real by remember { mutableStateOf(initial?.realUnitCents?.let { Money.toInput(it) } ?: "") }
    var store by remember { mutableStateOf(initial?.store ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val qty = Money.parseQuantity(quantity)
    val est = if (estimated.isBlank()) 0L else Money.parseAmount(estimated, allowZero = true)
    val realCents = if (real.isBlank()) null else Money.parseAmount(real, allowZero = true)
    val realInvalid = real.isNotBlank() && realCents == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nouvel article" else "Modifier l'article") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField1("Nom du produit", name) { name = it }
                DropdownField("Catégorie", categories.map { it.id }.ifEmpty { listOf(DefaultIds.FOOD) }, categoryId,
                    { id -> categories.firstOrNull { it.id == id }?.name ?: "Alimentation" }) { categoryId = it }
                AmountField("Quantité", quantity, { quantity = it }, error = qty == null)
                DropdownField("Unité", Units.all, unit, { it }) { unit = it }
                AmountField("Prix unitaire estimé (DZD)", estimated, { estimated = it }, error = est == null)
                AmountField("Prix unitaire réel (DZD, facultatif)", real, { real = it }, error = realInvalid)
                TextField1("Magasin (facultatif)", store) { store = it }
                TextField1("Note (facultatif)", note) { note = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && qty != null && est != null && !realInvalid, onClick = {
                if (qty != null && est != null) {
                    onSave(ShoppingItem(
                        id = initial?.id ?: UUID.randomUUID().toString(), listId = listId, name = name.trim(),
                        categoryId = categoryId, quantityMilli = qty, unit = unit, estUnitCents = est,
                        realUnitCents = realCents, store = store.trim(), note = note.trim(), bought = initial?.bought ?: false
                    ))
                }
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

// ---------------------------------------------------------------- Mode courses (hors ligne, gros éléments)
@Composable
fun ShopModeScreen(vm: MainViewModel, listId: String) {
    val lists by vm.lists.collectAsState()
    val allItems by vm.items.collectAsState()
    val list = lists.firstOrNull { it.id == listId }
    if (list == null) {
        Text("Liste introuvable.", Modifier.padding(16.dp))
        return
    }
    val items = allItems.filter { it.listId == listId }.sortedWith(compareBy({ it.bought }, { it.name.lowercase() }))
    val summary = ShoppingCalculator.summarize(items, list.plannedBudgetCents)
    var priceFor by remember { mutableStateOf<ShoppingItem?>(null) }
    val readOnly = list.status != ListStatus.ACTIVE

    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(12.dp)) {
                Text("Total estimé : ${Money.format(summary.estimatedTotal)}")
                Text("Total réel : ${Money.format(summary.realBought)}", style = MaterialTheme.typography.titleMedium)
                Text("Budget restant : ${Money.format(summary.budgetRemaining)}")
                Text("${summary.doneCount}/${summary.totalCount} articles · ${summary.percentDone} %")
            }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { item ->
                // Produits NON achetés mis en évidence (couleur + gras).
                val container = if (item.bought) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = item.bought, enabled = !readOnly, onCheckedChange = { vm.toggleBought(item) })
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (item.bought) FontWeight.Normal else FontWeight.Bold)
                            Text("${Money.formatQuantity(item.quantityMilli)} ${item.unit}", style = MaterialTheme.typography.bodySmall)
                            Text(if (item.realUnitCents != null) "Réel : ${Money.format(ShoppingCalculator.realCost(item))}"
                                 else "Estimé : ${Money.format(ShoppingCalculator.estimatedCost(item))}")
                        }
                        if (!readOnly) TextButton(onClick = { priceFor = item }) { Text("Prix réel") }
                    }
                }
            }
        }
    }

    priceFor?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.realUnitCents?.let { Money.toInput(it) } ?: "") }
        val cents = if (text.isBlank()) null else Money.parseAmount(text, allowZero = true)
        val invalid = text.isNotBlank() && cents == null
        AlertDialog(
            onDismissRequest = { priceFor = null },
            title = { Text(item.name) },
            text = {
                Column {
                    AmountField("Prix unitaire réel (DZD)", text, { text = it }, error = invalid)
                    Text("Laisser vide pour utiliser le prix estimé.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = !invalid, onClick = { vm.setRealPrice(item, cents); priceFor = null }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { priceFor = null }) { Text("Annuler") } }
        )
    }
}
