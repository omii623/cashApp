package com.example.cashapp.ui.loan

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun LoansRoute(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    viewModel: LoansViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoansScreen(state, onBack, onOpen, onNew)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    state: LoansUiState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.loan_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.loan_new)) },
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
            if (state.open.isEmpty() && state.settled.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.loan_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
                return@LazyColumn
            }
            item { TotalsCard(state) }
            if (state.open.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.loan_section_open)) }
                item { LoanCard(state.open, onOpen) }
            }
            if (state.settled.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.loan_section_settled)) }
                item { LoanCard(state.settled, onOpen) }
            }
        }
    }
}

@Composable
private fun TotalsCard(state: LoansUiState) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.loan_total_borrowed), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                Text(state.totalBorrowed.ifEmpty { "–" }, color = c.onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.loan_total_lent), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                Text(state.totalLent.ifEmpty { "–" }, color = c.onHero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LoanCard(items: List<LoanItemUi>, onOpen: (Long) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            items.forEachIndexed { i, item ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                LoanRow(item, Modifier.clickable { onOpen(item.id) })
            }
        }
    }
}

@Composable
internal fun LoanRow(item: LoanItemUi, modifier: Modifier = Modifier) {
    val accent = directionColor(item.direction, item.isDone)
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryBadge(item.icon, item.color?.let { Color(it) } ?: accent, item.personName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.personName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(
                stringResource(directionLabel(item.direction)),
                item.lastMovementDate?.let { stringResource(R.string.loan_last_movement, formatDate(it, R.string.fmt_date)) },
            ).joinToString(" · ")
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            when {
                item.isClosed -> stringResource(R.string.loan_closed)
                item.isSettled -> stringResource(R.string.loan_settled)
                else -> item.outstanding
            },
            color = accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Tartozásom → kiadás színe, nekem tartoznak → bevétel színe, rendezett → halvány. */
@Composable
internal fun directionColor(direction: LoanDirection, settled: Boolean): Color = when {
    settled -> MaterialTheme.colorScheme.onSurfaceVariant
    direction == LoanDirection.BORROWED -> CashTheme.colors.expense
    else -> CashTheme.colors.income
}

internal fun directionLabel(direction: LoanDirection): Int = when (direction) {
    LoanDirection.BORROWED -> R.string.loan_dir_borrowed
    LoanDirection.LENT -> R.string.loan_dir_lent
}

@Composable
internal fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
