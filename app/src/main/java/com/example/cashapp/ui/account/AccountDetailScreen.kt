package com.example.cashapp.ui.account

import com.example.cashapp.ui.components.TransactionRowUi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.TransactionRow
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun AccountDetailRoute(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTransaction: (TransactionRowUi) -> Unit,
    viewModel: AccountDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Ha a számlát közben törölték (szerkesztőből), ez az oldal is bezárul.
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }
    AccountDetailScreen(state, onBack, onEdit, onTransaction)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailScreen(
    state: AccountDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onTransaction: (TransactionRowUi) -> Unit = {},
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(state.account?.name ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = state.account != null) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.cat_edit))
                    }
                },
            )
        },
    ) { padding ->
        val account = state.account
        if (state.isLoading || account == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BalanceHeader(state, account) }
            item {
                Text(
                    stringResource(R.string.acc_transactions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (state.transactions.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.acc_no_transactions),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            } else {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column {
                            state.transactions.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                TransactionRow(row, onClick = { onTransaction(row) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceHeader(state: AccountDetailUiState, account: AccountItemUi) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(account.icon, Color(account.color), account.name, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${stringResource(R.string.acc_balance)} (${account.currencyCode})", color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                    if (state.isArchived) {
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.acc_archived_badge), color = c.warning, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    account.balance,
                    color = if (account.isNegative) c.expense else c.onHero,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.acc_initial_balance_info, state.initialBalance), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
