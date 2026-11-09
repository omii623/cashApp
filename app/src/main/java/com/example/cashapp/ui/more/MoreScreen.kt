package com.example.cashapp.ui.more

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.example.cashapp.R

/** A „Továbbiak” menü egy eleme. [onClick] = null → még nincs kész (halványan látszik). */
data class MoreEntry(@StringRes val label: Int, val icon: ImageVector, val onClick: (() -> Unit)?)

@Composable
fun MoreScreen(
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onRecurring: () -> Unit,
    onLoans: () -> Unit,
    onCurrencies: () -> Unit,
    onSettings: () -> Unit,
) {
    val entries = listOf(
        MoreEntry(R.string.more_accounts, Icons.Filled.AccountBalanceWallet, onAccounts),
        MoreEntry(R.string.more_categories, Icons.Filled.Category, onCategories),
        MoreEntry(R.string.more_recurring, Icons.Filled.Repeat, onRecurring),
        MoreEntry(R.string.more_loans_hub, Icons.Filled.Payments, onLoans),
        MoreEntry(R.string.more_currencies, Icons.Filled.CurrencyExchange, onCurrencies),
        MoreEntry(R.string.more_settings, Icons.Filled.Settings, onSettings),
    )
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(R.string.nav_more),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        MenuCard(entries)
    }
}

/** Menüpontok kártyája (Továbbiak, Kölcsönök). Az [MoreEntry.onClick] = null elem szürke és nem nyitható. */
@Composable
fun MenuCard(entries: List<MoreEntry>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        entries.forEachIndexed { i, entry ->
            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            val enabled = entry.onClick != null
            val alpha = if (enabled) 1f else 0.4f
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(entry.label) + if (enabled) "" else " – " + stringResource(R.string.coming_soon),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    )
                },
                leadingContent = { Icon(entry.icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)) },
                trailingContent = if (enabled) ({ Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) else null,
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.fillMaxWidth().then(
                    if (enabled) Modifier.clickable { entry.onClick?.invoke() } else Modifier
                ),
            )
        }
    }
}
