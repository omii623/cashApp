package com.example.cashapp.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun ReportsRoute(viewModel: ReportsViewModel = viewModel(factory = AppViewModelProvider.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ReportsScreen(state, viewModel)
}

@Composable
private fun ReportsScreen(state: ReportsUiState, vm: ReportsViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.nav_reports), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item { PeriodSelector(state, vm) }
        if (state.isLoading) {
            item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else {
            item { BreakdownCard(state, vm) }
            item { BarCard(state, vm) }
        }
    }
}

// ------------------------------------------------------------------ időszak

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(state: ReportsUiState, vm: ReportsViewModel) {
    val modes = listOf(PeriodMode.WEEK to R.string.rep_week, PeriodMode.MONTH to R.string.rep_month, PeriodMode.YEAR to R.string.rep_year)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        modes.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = state.mode == mode,
                onClick = { vm.setMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
            ) { Text(stringResource(label)) }
        }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previous) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.txl_prev_month)) }
        val label = when (state.mode) {
            PeriodMode.WEEK -> "${formatDate(state.from, R.string.fmt_short_date)} – ${formatDate(state.to, R.string.fmt_short_date)}"
            PeriodMode.MONTH -> formatDate(state.from, R.string.fmt_month_year)
            PeriodMode.YEAR -> formatDate(state.from, R.string.fmt_year)
        }
        Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = vm::next) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.txl_next_month)) }
    }
}

// ------------------------------------------------------------------ kördiagram

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreakdownCard(state: ReportsUiState, vm: ReportsViewModel) {
    val c = CashTheme.colors
    val surface = MaterialTheme.colorScheme.surfaceContainer
    // Szeletszínek: kategória saját színe; lefúrva a kategória színének árnyalatai (sötéttől világosig).
    val colors = state.slices.mapIndexed { i, s ->
        when {
            s.isOther -> c.chartNeutral
            state.drillName != null -> lerp(Color(s.color), surface, (i.toFloat() / state.slices.size) * 0.65f)
            else -> Color(s.color)
        }
    }
    Surface(shape = RoundedCornerShape(14.dp), color = surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val types = listOf(TransactionType.EXPENSE to R.string.action_expense, TransactionType.INCOME to R.string.action_income)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = state.type == t,
                        onClick = { vm.setType(t) },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size),
                    ) { Text(stringResource(label)) }
                }
            }

            // Kategóriaválasztó: bármelyik kategória alkategória-bontása közvetlenül (az „Egyéb”-be kerülteké is).
            val allLabel = stringResource(R.string.txl_all_categories)
            DropdownSelector(
                label = stringResource(R.string.form_category),
                value = state.drillName ?: allLabel,
                options = listOf<CategoryChoice?>(null) + state.categoryOptions,
                optionLabel = { it?.name ?: allLabel },
                onSelect = { vm.selectCategory(it?.id) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.drillName == null) {
                Text(
                    stringResource(if (state.type == TransactionType.EXPENSE) R.string.rep_by_category_expense else R.string.rep_by_category_income),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::drillUp) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.rep_back_to_categories)) }
                    Text(
                        stringResource(R.string.rep_by_subcategory, state.drillName),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (state.slices.isEmpty()) {
                Text(
                    stringResource(R.string.rep_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
                return@Column
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DonutChart(
                    shares = state.slices.map { it.share },
                    colors = colors,
                    onSliceTap = { i -> state.slices[i].takeIf { it.canDrill }?.id?.let(vm::drillInto) },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.rep_total), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(state.totalText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            // Jelmagyarázat: név + összeg + %, így az azonosítás sosem csak színen múlik.
            state.slices.forEachIndexed { i, s ->
                LegendRow(
                    slice = s,
                    color = colors[i],
                    name = when {
                        s.isOther -> stringResource(R.string.rep_other)
                        s.isLoans -> stringResource(R.string.more_loans)
                        s.name.isNotBlank() -> s.name
                        state.drillName != null -> stringResource(R.string.rep_no_subcategory)
                        else -> stringResource(R.string.rep_uncategorized)
                    },
                    onClick = if (s.canDrill) ({ s.id?.let(vm::drillInto) }) else null,
                )
            }
            if (state.drillName == null && state.slices.any { it.canDrill }) {
                Text(stringResource(R.string.rep_tap_to_drill), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.missingRates.isNotEmpty()) {
                Text(stringResource(R.string.rep_missing_rates, state.missingRates.sorted().joinToString(", ")), style = MaterialTheme.typography.bodySmall, color = c.warning)
            }
        }
    }
}

@Composable
private fun LegendRow(slice: SliceUi, color: Color, name: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (slice.icon != null) CategoryBadge(slice.icon, color, name, size = 28.dp)
        else Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(10.dp))
        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(slice.percentText, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(12.dp))
        Text(slice.amountText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ oszlopdiagram

@Composable
private fun BarCard(state: ReportsUiState, vm: ReportsViewModel) {
    val c = CashTheme.colors
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.rep_income_vs_expense), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendSwatch(c.chartIncome, stringResource(R.string.action_income))
                LegendSwatch(c.chartExpense, stringResource(R.string.action_expense))
            }
            // Kijelölt hónap pontos értékei (a „hover” megfelelője érintőképernyőn).
            val sel = state.selectedBar?.let { state.bars.getOrNull(it) }
            if (sel != null) {
                Text(
                    "${formatDate(sel.month, R.string.fmt_month_year)}:  ${stringResource(R.string.action_income)} ${sel.incomeText} · " +
                        "${stringResource(R.string.action_expense)} ${sel.expenseText}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                )
            } else {
                Text(stringResource(R.string.rep_tap_bar), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GroupedBarChart(
                values = state.bars.map { it.incomeMinor to it.expenseMinor },
                max = state.barMax,
                firstColor = c.chartIncome,
                secondColor = c.chartExpense,
                gridColor = MaterialTheme.colorScheme.outlineVariant,
                selected = state.selectedBar,
                onTap = vm::selectBar,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                state.bars.forEachIndexed { i, b ->
                    Text(
                        formatDate(b.month, R.string.fmt_month_short),
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (state.selectedBar == i) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
