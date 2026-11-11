package com.example.cashapp.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.theme.CashTheme
import kotlin.math.roundToInt

@Composable
fun LoanCalculatorRoute(
    onBack: () -> Unit,
    onSaveAsLoan: (principalText: String, thmPercent: Double, months: Int) -> Unit = { _, _, _ -> },
    viewModel: LoanCalculatorViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LoanCalculatorScreen(
        state, onBack, viewModel::onAmount, viewModel::onThm, viewModel::onMonths,
        PrepayActions(viewModel::onPrepay, viewModel::onPrepayMonth, viewModel::onPenalty, viewModel::onThreshold),
        onInflation = viewModel::onInflation,
        onSaveAsLoan = { onSaveAsLoan(state.amountText.trim(), state.thmPercent, state.months) },
    )
}

/** Hitelkalkulátor: igényelt összeg mező, THM és hónap csúszka (mindkettő értékére koppintva kézzel is beírható), eredmény. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanCalculatorScreen(
    state: LoanCalculatorUiState,
    onBack: () -> Unit,
    onAmount: (String) -> Unit,
    onThm: (Double) -> Unit,
    onMonths: (Int) -> Unit,
    prepayActions: PrepayActions = PrepayActions(),
    onInflation: (Double) -> Unit = {},
    onSaveAsLoan: (() -> Unit)? = null,
) {
    var editInflation by remember { mutableStateOf(false) }
    var editThm by remember { mutableStateOf(false) }
    var editMonths by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.loan_hub_calculator), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ResultCard(state)
            // A kiszámolt hitel felvétele a banki kölcsönök közé (előre kitöltött űrlap).
            if (onSaveAsLoan != null) {
                OutlinedButton(
                    onClick = onSaveAsLoan,
                    enabled = state.monthlyPayment != null,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.calc_save_as_loan)) }
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = onAmount,
                label = { Text(stringResource(R.string.calc_amount)) },
                placeholder = { Text("0") },
                suffix = { Text(state.currency?.symbol ?: "") },
                isError = state.amountError,
                supportingText = if (state.amountError) ({ Text(stringResource(R.string.form_amount_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // THM: csúszka 0–50% + koppintható érték (kézzel bármekkora nem negatív szám; 50% fölött a csúszka a végén áll).
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.calc_thm), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = { editThm = true },
                        label = { Text(formatPercent(state.thmPercent), fontWeight = FontWeight.Bold) },
                        trailingIcon = { Icon(Icons.Filled.Edit, stringResource(R.string.calc_thm_edit)) },
                    )
                }
                Slider(
                    value = state.thmPercent.toFloat().coerceAtMost(LoanCalculatorViewModel.MAX_THM_SLIDER.toFloat()),
                    onValueChange = { onThm(Math.round(it * 10) / 10.0) },
                    valueRange = 0f..LoanCalculatorViewModel.MAX_THM_SLIDER.toFloat(),
                )
            }

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.calc_months), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    // Koppintva kézzel is beírható (a csúszka tartományán túl is).
                    AssistChip(
                        onClick = { editMonths = true },
                        label = { Text(monthsLabel(state.months), fontWeight = FontWeight.Bold) },
                        trailingIcon = { Icon(Icons.Filled.Edit, stringResource(R.string.calc_months_edit)) },
                    )
                }
                Slider(
                    value = state.months.coerceAtMost(LoanCalculatorViewModel.MAX_MONTHS_SLIDER).toFloat(),
                    onValueChange = { onMonths(it.roundToInt()) },
                    valueRange = LoanCalculatorViewModel.MIN_MONTHS.toFloat()..LoanCalculatorViewModel.MAX_MONTHS_SLIDER.toFloat(),
                )
            }

            // Infláció: csúszka 0–10% + koppintható érték (kézzel bármekkora nem negatív szám).
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.calc_inflation), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = { editInflation = true },
                        label = { Text(formatPercent(state.inflationPercent), fontWeight = FontWeight.Bold) },
                        trailingIcon = { Icon(Icons.Filled.Edit, stringResource(R.string.calc_inflation_edit)) },
                    )
                }
                Slider(
                    value = state.inflationPercent.toFloat().coerceAtMost(LoanCalculatorViewModel.MAX_INFLATION_SLIDER.toFloat()),
                    onValueChange = { onInflation(Math.round(it * 10) / 10.0) },
                    valueRange = 0f..LoanCalculatorViewModel.MAX_INFLATION_SLIDER.toFloat(),
                )
                Text(
                    stringResource(R.string.calc_inflation_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PrepaymentSection(state, prepayActions)

            Text(
                stringResource(R.string.calc_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (editMonths) {
        MonthsDialog(
            current = state.months,
            onConfirm = { onMonths(it); editMonths = false },
            onDismiss = { editMonths = false },
        )
    }
    if (editInflation) {
        ThmDialog(
            current = state.inflationPercent,
            title = stringResource(R.string.calc_inflation),
            onConfirm = { onInflation(it); editInflation = false },
            onDismiss = { editInflation = false },
        )
    }
    if (editThm) {
        ThmDialog(
            current = state.thmPercent,
            onConfirm = { onThm(it); editThm = false },
            onDismiss = { editThm = false },
        )
    }
}

@Composable
private fun ResultCard(state: LoanCalculatorUiState) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.calc_monthly), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            Text(
                state.monthlyPayment ?: "–",
                color = c.onHero,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.calc_total), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                    Text(state.totalRepayment ?: "–", color = c.onHero, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.calc_cost), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                    Text(state.totalCost ?: "–", color = c.onHero, fontWeight = FontWeight.Bold)
                }
            }
            // Vásárlóerő: csak ha van infláció és eredmény.
            if (state.realTotal != null) {
                Text(
                    stringResource(R.string.calc_real_title, formatPercent(state.inflationPercent)),
                    color = c.onHeroMuted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.calc_total), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                        Text(state.realTotal, color = c.onHero, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.calc_real_cost), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                        Text(state.realCost ?: "–", color = if (state.realCostNegative) c.income else c.onHero, fontWeight = FontWeight.Bold)
                    }
                }
                state.realLast?.let {
                    Text(stringResource(R.string.calc_real_last, it), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** THM kézi beírása (pl. „13,9”). */
@Composable
internal fun ThmDialog(current: Double, onConfirm: (Double) -> Unit, onDismiss: () -> Unit, title: String = stringResource(R.string.calc_thm)) {
    var text by remember { mutableStateOf(formatNumber(current)) }
    val value = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                suffix = { Text("%") },
                isError = value == null,
                supportingText = if (value == null) ({ Text(stringResource(R.string.calc_thm_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Az előtörlesztés mezőinek eseményei. */
data class PrepayActions(
    val onAmount: (String) -> Unit = {},
    val onMonth: (String) -> Unit = {},
    val onPenalty: (String) -> Unit = {},
    val onThreshold: (String) -> Unit = {},
)

/** Előtörlesztés: összeg, hányadik hónap után, büntető %, büntető küszöb → két változat egymás mellett. */
@Composable
private fun PrepaymentSection(state: LoanCalculatorUiState, actions: PrepayActions) {
    val symbol = state.currency?.symbol ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.calc_prepay), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.prepayText,
                onValueChange = actions.onAmount,
                label = { Text(stringResource(R.string.calc_prepay_amount)) },
                placeholder = { Text("0") },
                suffix = { Text(symbol) },
                isError = state.prepayError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.prepayMonthText,
                onValueChange = actions.onMonth,
                label = { Text(stringResource(R.string.calc_prepay_month)) },
                suffix = { Text(stringResource(R.string.calc_prepay_month_suffix)) },
                isError = state.prepayMonthError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
        if (state.prepayMonthError) {
            Text(
                stringResource(R.string.calc_prepay_month_error, state.months - 1),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.penaltyText,
                onValueChange = actions.onPenalty,
                label = { Text(stringResource(R.string.calc_penalty)) },
                placeholder = { Text("0") },
                suffix = { Text("%") },
                isError = state.penaltyError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.thresholdText,
                onValueChange = actions.onThreshold,
                label = { Text(stringResource(R.string.calc_threshold)) },
                placeholder = { Text("0") },
                suffix = { Text(symbol) },
                isError = state.thresholdError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            stringResource(R.string.calc_threshold_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.prepay?.let { PrepaymentResultCard(it) }
    }
}

@Composable
internal fun PrepaymentResultCard(p: PrepaymentUi, balanceLabel: String? = null) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            InfoLine(balanceLabel ?: stringResource(R.string.calc_balance_before, p.month), p.balanceBefore)
            InfoLine(stringResource(R.string.calc_prepaid), p.prepaid)
            InfoLine(stringResource(R.string.calc_penalty_amount), p.penalty)
            if (p.paidOff) {
                Text(stringResource(R.string.calc_paid_off), fontWeight = FontWeight.Bold)
                InfoLine(stringResource(R.string.calc_saving), p.lowerSaving, bold = true)
                p.lowerRealSaving?.let { InfoLine(stringResource(R.string.calc_real_saving), it, bold = true) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    OptionBox(
                        title = stringResource(R.string.calc_option_lower),
                        main = p.lowerMonthly,
                        mainLabel = stringResource(R.string.calc_new_monthly),
                        extra = null,
                        saving = p.lowerSaving,
                        realSaving = p.lowerRealSaving,
                        modifier = Modifier.weight(1f),
                    )
                    OptionBox(
                        title = stringResource(R.string.calc_option_shorter),
                        main = pluralStringResource(R.plurals.calc_months_value, p.shorterMonths, p.shorterMonths),
                        mainLabel = stringResource(R.string.calc_remaining),
                        extra = stringResource(R.string.calc_last_payment, p.shorterLast),
                        saving = p.shorterSaving,
                        realSaving = p.shorterRealSaving,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun OptionBox(title: String, main: String, mainLabel: String, extra: String?, saving: String, realSaving: String?, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(mainLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(main, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(stringResource(R.string.calc_saving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(saving, fontWeight = FontWeight.Bold, color = CashTheme.colors.income)
            // Vásárlóerő: ha van infláció, a megtakarítás mai pénzben is.
            realSaving?.let {
                Text(stringResource(R.string.calc_real_saving), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, fontWeight = FontWeight.Bold, color = CashTheme.colors.income)
            }
        }
    }
}

@Composable
internal fun InfoLine(label: String, value: String, bold: Boolean = false) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)
    }
}

/** Futamidő kézi beírása: pozitív egész hónap, felső határ nélkül. */
@Composable
internal fun MonthsDialog(current: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit, title: String = stringResource(R.string.calc_months)) {
    var text by remember { mutableStateOf(current.toString()) }
    val value = text.trim().toIntOrNull()?.takeIf { it >= 1 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                suffix = { Text(stringResource(R.string.calc_months_unit)) },
                isError = value == null,
                supportingText = if (value == null) ({ Text(stringResource(R.string.calc_months_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** „60 hónap (5 év)”, „18 hónap (1 év 6 hó)”, „7 hónap”. */
@Composable
internal fun monthsLabel(months: Int): String {
    val years = months / 12
    val rest = months % 12
    val base = pluralStringResource(R.plurals.calc_months_value, months, months)
    return when {
        years == 0 -> base
        rest == 0 -> "$base (${pluralStringResource(R.plurals.calc_years, years, years)})"
        else -> "$base (${stringResource(R.string.calc_years_months, years, rest)})"
    }
}

/** 15.0 → „15”, 13.9 → „13,9”, 13.95 → „13,95”. */
internal fun formatNumber(value: Double): String =
    java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

internal fun formatPercent(value: Double): String = formatNumber(value) + " %"
