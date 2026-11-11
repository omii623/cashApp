package com.example.cashapp.ui.bankloan

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.loan.SectionTitle
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun BankLoansRoute(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    viewModel: BankLoansViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BankLoansScreen(state, onBack, onOpen, onNew)
}

/** Banki kölcsönök: összesítő (tartozás, havi teher), futó és kifizetett / lezárt hitelek kártyái. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankLoansScreen(
    state: BankLoansUiState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.bank_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.bank_new)) },
                containerColor = CashTheme.colors.hero,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.open.isEmpty() && state.done.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.bank_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
                return@LazyColumn
            }
            item { TotalsCard(state) }
            if (state.open.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.bank_section_open)) }
                items(state.open, key = { it.id }) { LoanCard(it, onOpen) }
            }
            if (state.done.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.bank_section_done)) }
                items(state.done, key = { it.id }) { LoanCard(it, onOpen) }
            }
        }
    }
}

@Composable
private fun TotalsCard(state: BankLoansUiState) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.bank_total_outstanding), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                state.totalOutstanding.ifEmpty { "–" },
                color = c.onHero,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.padding(top = 4.dp)) {
                Text(
                    "${stringResource(R.string.bank_monthly_burden)}: ${state.monthlyBurden.ifEmpty { "–" }}",
                    color = c.onHeroMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    pluralStringResource(R.plurals.bank_count, state.open.size, state.open.size),
                    color = c.onHeroMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LoanCard(item: BankLoanItemUi, onOpen: (Long) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        BankLoanRow(item, Modifier.clickable { onOpen(item.id) })
    }
}
