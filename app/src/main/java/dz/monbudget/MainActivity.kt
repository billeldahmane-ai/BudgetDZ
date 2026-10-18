package dz.monbudget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dz.monbudget.ui.AppRoot
import dz.monbudget.ui.MainViewModel
import dz.monbudget.ui.theme.MonBudgetTheme
import dz.monbudget.worker.ReminderScheduler

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.schedule(applicationContext)   // rappels locaux : vérifiés une fois par jour, selon les réglages
        setContent { MonBudgetTheme { AppRoot(viewModel) } }
    }
}
