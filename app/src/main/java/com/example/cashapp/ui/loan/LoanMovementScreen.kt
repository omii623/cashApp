package com.example.cashapp.ui.loan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.ui.theme.CashTheme
import com.example.cashapp.ui.transaction.CurrencyChip
import com.example.cashapp.ui.transaction.RateSection
import java.time.LocalDate

@Composable
fun LoanMovementRoute(
    onDone: () -> Unit,
    viewModel: LoanMovementViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    if (state.saveFailed) SaveErrorDialog(viewModel::dismissError)
    LoanMovementScreen(
        state = state,
        onBack = onDone,
        onAmount = viewModel::onAmount,
        onAccount = viewModel::onAccount,
        onCurrency = viewModel::onCurrency,
        onRate = viewModel::onRate,
        onRateReset = viewModel::onRateReset,
        onLoanRate = viewModel::onLoanRate,
        onLoanRateReset = viewModel::onLoanRateReset,
        onDate = viewModel::onDate,
        onNote = viewModel::onNote,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

/** Ugyanaz a felépítés, mint a bevétel/kiadás űrlapé: színes fejléc, nagy összeg, számla + dátum, megjegyzés. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanMovementScreen(
    state: LoanMovementUiState,
    onBack: () -> Unit,
    onAmount: (String) -> Unit,
    onAccount: (Long) -> Unit,
    onCurrency: (String) -> Unit,
    onRate: (String) -> Unit,
    onRateReset: () -> Unit,
    onLoanRate: (String) -> Unit,
    onLoanRateReset: () -> Unit,
    onDate: (LocalDate) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val accent = if (state.isIncome) CashTheme.colors.income else CashTheme.colors.expense
    val title = stringResource(LoanMovementViewModel.kindLabel(state.direction, state.increase)) +
        if (state.personName.isNotEmpty()) " · ${state.personName}" else ""
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = {
            Button(
                onClick = onSave,
                enabled = !state.isLoading && !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    .padding(16.dp)
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.form_save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AmountField(state, onAmount, onCurrency)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = stringResource(R.string.form_account),
                    value = state.account?.let { "${it.name} (${it.currencyCode})" } ?: "",
                    options = state.accounts,
                    optionLabel = { "${it.name} (${it.currencyCode})" },
                    onSelect = { onAccount(it.id) },
                    modifier = Modifier.weight(1f),
                )
                DateField(state.date, onDate, Modifier.weight(1f))
            }
            // Ugyanaz, mint a bevétel/kiadásnál: más pénznemnél árfolyam a számlára…
            state.rate?.let { RateSection(it, onRate, onRateReset) }
            // …és ha a kölcsön pénzneme is eltér, árfolyam a kölcsön pénznemére (a hátralékhoz).
            state.loanRate?.let {
                RateSection(it, onLoanRate, onLoanRateReset, title = stringResource(R.string.loan_rate_title, state.loanCurrencyCode))
            }
            state.loanAmountPreview?.let {
                Text(stringResource(R.string.loan_amount_in_loan, it), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = onNote,
                label = { Text(stringResource(R.string.form_note)) },
                placeholder = { Text(stringResource(R.string.form_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.loan_mv_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.loan_mv_delete_title),
            text = stringResource(R.string.loan_mv_delete_text),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Nagy, középre igazított összegmező pénznem-választóval – mint a bevétel/kiadás űrlapon. */
@Composable
private fun AmountField(state: LoanMovementUiState, onAmount: (String) -> Unit, onCurrency: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(
            value = state.amountText,
            onValueChange = onAmount,
            placeholder = {
                Text(
                    stringResource(R.string.form_amount_hint),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            suffix = { Text(state.currency?.symbol ?: "", style = MaterialTheme.typography.headlineSmall) },
            singleLine = true,
            isError = state.amountError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        val error = when {
            state.amountError -> stringResource(R.string.form_amount_error)
            else -> null
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        CurrencyChip(state.currency?.code, state.currencies, onCurrency)
    }
}
