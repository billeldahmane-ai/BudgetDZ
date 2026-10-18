@file:OptIn(ExperimentalMaterial3Api::class)

package dz.monbudget.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Accueil", Icons.Default.Home),
    Tab("expenses", "Dépenses", Icons.AutoMirrored.Filled.List),
    Tab("shopping", "Courses", Icons.Default.ShoppingCart),
    Tab("budget", "Budget", Icons.Default.DateRange),
    Tab("profile", "Profil", Icons.Default.Person)
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val isTopLevel = tabs.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    val categories by vm.categories.collectAsState()
    var quickExpense by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val title = when {
        route == "home" -> "Mon Budget DZ"
        route == "expenses" -> "Dépenses"
        route == "shopping" -> "Listes de courses"
        route == "budget" -> "Budget mensuel"
        route == "profile" -> "Profil et réglages"
        route == "income" -> "Revenus"
        route == "savings" -> "Épargne"
        route == "bills" -> "Factures"
        route == "stats" -> "Statistiques"
        route?.startsWith("shop/") == true -> "Mode courses"
        else -> "Liste de courses"
    }
    val go: (String) -> Unit = { nav.navigate(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!isTopLevel) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                }
            )
        },
        bottomBar = {
            if (isTopLevel) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (isTopLevel) ExtendedFloatingActionButton(
                onClick = { quickExpense = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Dépense") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, go) }
            composable("expenses") { ExpenseScreen(vm) }
            composable("shopping") { ShoppingListsScreen(vm) { id -> nav.navigate("list/$id") } }
            composable("budget") { BudgetScreen(vm) }
            composable("profile") { ProfileScreen(vm, go) }
            composable("income") { IncomeScreen(vm) }
            composable("savings") { SavingsScreen(vm) }
            composable("bills") { BillsScreen(vm) }
            composable("stats") { StatsScreen(vm) }
            composable("list/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("id") ?: ""
                ShoppingDetailScreen(vm, id, onShopMode = { nav.navigate("shop/$id") }, onClose = { nav.popBackStack() })
            }
            composable("shop/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                ShopModeScreen(vm, entry.arguments?.getString("id") ?: "")
            }
        }
    }

    if (quickExpense) {
        ExpenseDialog(null, categories, onSave = { vm.saveExpense(it); quickExpense = false }, onDismiss = { quickExpense = false })
    }
}
