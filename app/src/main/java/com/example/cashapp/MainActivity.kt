package com.example.cashapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cashapp.domain.model.ThemeMode
import com.example.cashapp.notifications.RecurringNotifications
import com.example.cashapp.ui.navigation.CashApp
import com.example.cashapp.ui.theme.CashAppTheme

/**
 * AppCompatActivity, mert az alkalmazásonkénti nyelvváltás (AppCompatDelegate.setApplicationLocales)
 * Android 13 alatt csak így működik. A UI teljesen Compose.
 */
class MainActivity : AppCompatActivity() {

    /** Minden értesítés-koppintásnál nő → a navigáció ilyenkor a jóváhagyás oldalra ugrik. */
    private val openPendingRequests = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val settings = (application as CashApplication).container.settingsRepository
        setContent {
            val themeMode by settings.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            CashAppTheme(themeMode) {
                CashApp(openPendingRequest = openPendingRequests.intValue)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(RecurringNotifications.EXTRA_OPEN_PENDING, false) == true) {
            openPendingRequests.intValue++
        }
    }
}
