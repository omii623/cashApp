package com.example.cashapp.ui.transactions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.TransactionRow
import com.example.cashapp.ui.components.TransactionRowUi
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate

@Composable
fun TransactionsRoute(
    onEdit: (TransactionRowUi) -> Unit,
    viewModel: TransactionsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TransactionsScreen(
        state = state,
        actions = TransactionsActions(
            onPreviousMonth = viewModel::previousMonth,
            onNextMonth = viewModel::nextMonth,
            onType = viewModel::setType,
            onAccount = viewModel::setAccount,
            onCategory = viewModel::setCategory,
            onQuery = viewModel::setQuery,
            onClearFilters = viewModel::clearFilters,
            onEdit = onEdit,
        ),
    )
}

data class TransactionsActions(
    val onPreviousMonth: () -> Unit = {},
    val onNextMonth: () -> Unit = {},
    val onType: (TransactionType?) -> Unit = {},
    val onAccount: (Long?) -> Unit = {},
    val onCategory: (Long?) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onEdit: (TransactionRowUi) -> Unit = {},
)

@Composable
fun TransactionsScreen(state: TransactionsUiState, actions: TransactionsActions) {
    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Header(state, actions) }
            item { SummaryRow(state) }
            item { SearchField(state.filter.query, actions.onQuery) }
            item { FilterChips(state, actions) }

            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                state.days.isEmpty() -> item {
                    Text(
                        stringResource(if (state.filter.hasActiveFilters) R.string.txl_empty_filtered else R.string.txl_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
                else -> state.days.forEach { day ->
                    item(key = "h-${day.date}") { DayHeader(day) }
                    item(key = "d-${day.date}") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.animateItem(),
                        ) {
                            Column {
                                day.rows.forEachIndexed { i, row ->
                                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                    TransactionRow(row, onClick = { actions.onEdit(row) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(state: TransactionsUiState, actions: TransactionsActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.nav_transactions),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = actions.onPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.txl_prev_month))
        }
        Text(formatDate(state.filter.month, R.string.fmt_month_year), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = actions.onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.txl_next_month))
        }
    }
}

@Composable
private fun SummaryRow(state: TransactionsUiState) {
    val c = CashTheme.colors
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            SummaryCell(stringResource(R.string.home_month_income), state.incomeText, c.income, Modifier.weight(1f))
            SummaryCell(stringResource(R.string.home_month_expense), state.expenseText, c.expense, Modifier.weight(1f))
            SummaryCell(
                stringResource(R.string.txl_net),
                state.netText,
                if (state.netNegative) c.expense else MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(stringResource(R.string.txl_search)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.txl_clear_search)) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(50),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterChips(state: TransactionsUiState, actions: TransactionsActions) {
    val f = state.filter
    val typeOptions = listOf(
        null to stringResource(R.string.txl_all_types),
        TransactionType.INCOME to stringResource(R.string.action_income),
        TransactionType.EXPENSE to stringResource(R.string.action_expense),
        TransactionType.EXCHANGE to stringResource(R.string.action_exchange),
    )
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DropdownChip(
            label = typeOptions.first { it.first == f.type }.second,
            selected = f.type != null,
            options = typeOptions,
            onSelect = actions.onType,
        )
        DropdownChip(
            label = state.accounts.firstOrNull { it.id == f.accountId }?.label ?: stringResource(R.string.txl_all_accounts),
            selected = f.accountId != null,
            options = listOf<Pair<Long?, String>>(null to stringResource(R.string.txl_all_accounts)) +
                state.accounts.map { it.id to it.label },
            onSelect = actions.onAccount,
        )
        if (f.type != TransactionType.EXCHANGE) {
            DropdownChip(
                label = state.categories.firstOrNull { it.id == f.categoryId }?.label ?: stringResource(R.string.txl_all_categories),
                selected = f.categoryId != null,
                options = listOf<Pair<Long?, String>>(null to stringResource(R.string.txl_all_categories)) +
                    state.categories.map { it.id to it.label },
                onSelect = actions.onCategory,
            )
        }
        if (f.hasActiveFilters) {
            TextButton(onClick = actions.onClearFilters) { Text(stringResource(R.string.txl_clear_filters)) }
        }
    }
}

/** Szűrő chip lenyíló listával. A kiválasztott (nem „összes”) chip ki van emelve. */
@Composable
private fun <T> DropdownChip(label: String, selected: Boolean, options: List<Pair<T, String>>, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = selected, onClick = { expanded = true }, label = { Text("$label ▾") })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            dayLabel(day.date).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            day.netText,
            style = MaterialTheme.typography.labelMedium,
            color = if (day.netNegative) CashTheme.colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val formatted = formatDate(date, R.string.fmt_day_header)
    return when (date) {
        today -> "${stringResource(R.string.tx_today)} · $formatted"
        today.minusDays(1) -> "${stringResource(R.string.tx_yesterday)} · $formatted"
        else -> formatted
    }
}
