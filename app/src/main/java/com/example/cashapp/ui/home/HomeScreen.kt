package com.example.cashapp.ui.home

import com.example.cashapp.ui.components.formatDate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ExchangeIcon
import com.example.cashapp.ui.components.TransactionRow
import com.example.cashapp.ui.components.TransactionRowUi
import com.example.cashapp.ui.theme.CashAppTheme
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Navigációs események – a képernyő nem ismeri a navigációt, csak jelez. */
data class HomeActions(
    val onIncome: () -> Unit = {},
    val onExpense: () -> Unit = {},
    val onExchange: () -> Unit = {},
    val onPending: () -> Unit = {},
    val onToggleHidden: () -> Unit = {},
    /** Tételre koppintás → szerkesztés. */
    val onTransaction: (TransactionRowUi) -> Unit = {},
)

@Composable
fun HomeRoute(
    actions: HomeActions,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(state, actions.copy(onToggleHidden = viewModel::toggleHideAmounts))
}

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state) }
        item { BalanceCard(state, actions.onToggleHidden) }
        item { MonthSummary(state) }
        item { MainActions(actions) }
        if (state.pendingCount > 0) item { PendingBanner(state.pendingCount, actions.onPending) }
        item {
            Text(
                stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item { RecentList(state.recent, actions.onTransaction) }
    }
}

@Composable
private fun Header(state: HomeUiState) {
    val monthLabel = formatDate(state.month, R.string.fmt_month_year)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(monthLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BalanceCard(state: HomeUiState, onToggleHidden: () -> Unit) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.home_total_balance, state.baseCurrencyCode),
                    color = c.onHeroMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleHidden, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (state.amountsHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (state.amountsHidden) R.string.home_show_amounts else R.string.home_hide_amounts,
                        ),
                        tint = c.onHeroMuted,
                    )
                }
            }
            Text(
                state.totalBalance,
                color = c.onHero,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state.balancesByCurrency.size > 1) {
                Text(state.balancesByCurrency.joinToString(" · "), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.missingRates.isNotEmpty()) {
                Text(
                    stringResource(R.string.home_missing_rates, state.missingRates.sorted().joinToString(", ")),
                    color = c.warning,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun MonthSummary(state: HomeUiState) {
    val c = CashTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SummaryTile(stringResource(R.string.home_month_income), state.monthIncome, c.income, Modifier.weight(1f))
        SummaryTile(stringResource(R.string.home_month_expense), state.monthExpense, c.expense, Modifier.weight(1f))
        SummaryTile(
            stringResource(R.string.home_month_net),
            state.monthNet,
            if (state.monthNetNegative) c.expense else MaterialTheme.colorScheme.onSurface,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryTile(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
    }
}

@Composable
private fun MainActions(actions: HomeActions) {
    val c = CashTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton(stringResource(R.string.action_income), Icons.Filled.Add, c.income, actions.onIncome, Modifier.weight(1f))
        ActionButton(stringResource(R.string.action_expense), Icons.Filled.Remove, c.expense, actions.onExpense, Modifier.weight(1f))
        ActionButton(
            stringResource(R.string.action_exchange), ExchangeIcon.vector, c.exchange,
            actions.onExchange, Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        color = color,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(80.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PendingBanner(count: Int, onClick: () -> Unit) {
    val c = CashTheme.colors
    Surface(
        onClick = onClick,
        color = c.warningContainer,
        contentColor = c.warning,
        border = BorderStroke(1.dp, c.warning),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.home_pending, count, count),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun RecentList(items: List<TransactionRowUi>, onClick: (TransactionRowUi) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.home_recent_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        } else {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                TransactionRow(item, onClick = { onClick(item) })
            }
        }
    }
}

// ---------------------------------------------------------------- Preview

private fun previewRow(
    id: Long,
    type: TransactionType,
    date: LocalDate,
    category: String? = null,
    subcategory: String? = null,
    color: Long? = null,
    icon: String? = null,
    amount: String,
) = TransactionRowUi(
    id = id, type = type, date = date,
    categoryName = category, subcategoryName = subcategory, categoryColor = color, categoryIcon = icon,
    accountName = "Bankszámla", fromCurrency = "HUF", toCurrency = null, rateText = null, amountText = amount,
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun HomePreview() {
    val today = LocalDate.now()
    CashAppTheme {
        Surface {
            HomeScreen(
                HomeUiState(
                    isLoading = false,
                    baseCurrencyCode = "HUF",
                    totalBalance = "1 245 300 Ft",
                    balancesByCurrency = listOf("820 000 Ft", "650,00 €", "45 000 din.", "300,00 $"),
                    monthIncome = "+620 000", monthExpense = "−410 500", monthNet = "+209 500",
                    pendingCount = 2,
                    recent = listOf(
                        previewRow(1, TransactionType.EXPENSE, today, "Élelmiszer", "Bevásárlás", 0xFFEF5350, "food", "−12 500 Ft"),
                        previewRow(2, TransactionType.EXCHANGE, today.minusDays(1), amount = "250,63 €")
                            .copy(toCurrency = "EUR", rateText = "1 € = 399 Ft"),
                        previewRow(3, TransactionType.INCOME, today.minusDays(5), "Fizetés", "Alapbér", 0xFF2E7D32, "work", "+620 000 Ft"),
                    ),
                ),
                HomeActions(),
            )
        }
    }
}
