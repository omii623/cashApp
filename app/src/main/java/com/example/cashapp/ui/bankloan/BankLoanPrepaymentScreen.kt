package com.example.cashapp.ui.bankloan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.PrepaymentMode
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.ui.loan.InfoLine
import com.example.cashapp.ui.loan.PrepaymentResultCard
import com.example.cashapp.ui.loan.SectionTitle
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun BankLoanPrepaymentRoute(
    onDone: () -> Unit,
    viewModel: BankLoanPrepaymentViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val note = stringResource(R.string.bank_prepay_note, state.loanName)
    BankLoanPrepaymentScreen(state, viewModel, onBack = onDone, onSave = { viewModel.save(note) })
    if (state.saveFailed) SaveErrorDialog(onDismiss = viewModel::dismissError)
}

/** Előtörlesztés: összeg, büntető % és küszöb, dátum → a két változat, választás és rögzítés. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankLoanPrepaymentScreen(
    state: BankLoanPrepaymentUiState,
    vm: BankLoanPrepaymentViewModel,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.bank_prepay), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            Box(Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)).padding(16.dp)) {
                Button(
                    onClick = onSave,
                    enabled = state.result != null && !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = CashTheme.colors.hero, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(stringResource(R.string.bank_prepay_save), fontWeight = FontWeight.Bold) }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val symbol = state.currency?.symbol.orEmpty()
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(state.loanName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            // Eredmény nélkül itt látszik a hátralék; utána az eredménykártya első sora mutatja.
            if (state.result == null) InfoLine(stringResource(R.string.bank_prepay_balance), state.balance, bold = true)

            OutlinedTextField(
                value = state.amountText,
                onValueChange = vm::onAmount,
                label = { Text(stringResource(R.string.calc_prepay_amount)) },
                placeholder = { Text("0") },
                suffix = { Text(symbol) },
                isError = state.amountError,
                supportingText = if (state.amountError) ({ Text(stringResource(R.string.form_amount_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.penaltyText,
                    onValueChange = vm::onPenalty,
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
                    onValueChange = vm::onThreshold,
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
            DateField(state.date, vm::onDate, Modifier.fillMaxWidth())

            state.result?.let { result ->
                PrepaymentResultCard(result, balanceLabel = stringResource(R.string.bank_prepay_balance))
                if (!result.paidOff) {
                    SectionTitle(stringResource(R.string.bank_prepay_mode))
                    val modes = listOf(PrepaymentMode.SHORTER_TERM, PrepaymentMode.LOWER_PAYMENT)
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = state.mode == mode,
                                onClick = { vm.onMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            ) {
                                Text(
                                    stringResource(
                                        if (mode == PrepaymentMode.SHORTER_TERM) R.string.calc_option_shorter else R.string.calc_option_lower
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.bank_prepay_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
