package com.example.cashapp.ui.exchange

import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.example.cashapp.ui.components.ConfirmDialog
import androidx.compose.material.icons.filled.Delete
import com.example.cashapp.ui.components.formatDate
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.ExchangeIcon
import com.example.cashapp.ui.theme.CashTheme
import com.example.cashapp.ui.transaction.AccountOption
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ExchangeRoute(
    onDone: () -> Unit,
    viewModel: ExchangeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) { if (state.isSaved) onDone() }
    if (state.saveFailed) SaveErrorDialog(viewModel::dismissSaveError)
    ExchangeScreen(
        state,
        ExchangeActions(
            onBack = onDone,
            onFromAccount = viewModel::onFromAccountSelect,
            onToAccount = viewModel::onToAccountSelect,
            onSwap = viewModel::swapAccounts,
            onFromAmount = viewModel::onFromAmountChange,
            onToAmount = viewModel::onToAmountChange,
            onRate = viewModel::onRateChange,
            onRateReset = viewModel::onRateReset,
            onFetch = viewModel::fetchRates,
            onDate = viewModel::onDateSelect,
            onFee = viewModel::onFeeChange,
            onNote = viewModel::onNoteChange,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        ),
    )
}

data class ExchangeActions(
    val onBack: () -> Unit = {},
    val onFromAccount: (Long) -> Unit = {},
    val onToAccount: (Long) -> Unit = {},
    val onSwap: () -> Unit = {},
    val onFromAmount: (String) -> Unit = {},
    val onToAmount: (String) -> Unit = {},
    val onRate: (String) -> Unit = {},
    val onRateReset: () -> Unit = {},
    val onFetch: () -> Unit = {},
    val onDate: (java.time.LocalDate) -> Unit = {},
    val onFee: (String) -> Unit = {},
    val onNote: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExchangeScreen(state: ExchangeUiState, actions: ExchangeActions) {
    val accent = CashTheme.colors.exchange
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(ExchangeIcon.vector, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(if (state.isEditing) R.string.ex_title_edit else R.string.ex_title), fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
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
                onClick = actions.onSave,
                enabled = !state.isLoading && !state.isSaving && state.accounts.size > 1,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    .padding(16.dp)
                    .height(56.dp),
            ) { Text(stringResource(R.string.ex_save), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) }
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
            if (state.accounts.size < 2) {
                Text(stringResource(R.string.ex_need_account), color = MaterialTheme.colorScheme.error)
            }
            Box {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountCard(
                        label = stringResource(R.string.ex_from),
                        account = state.from,
                        accounts = state.accounts,
                        currency = state.fromCurrency,
                        amountText = state.fromAmountText,
                        amountColor = MaterialTheme.colorScheme.onSurface,
                        error = state.fromAmountError,
                        onAccount = actions.onFromAccount,
                        onAmount = actions.onFromAmount,
                    )
                    AmountCard(
                        label = stringResource(R.string.ex_to),
                        account = state.to,
                        accounts = state.accounts,
                        currency = state.toCurrency,
                        amountText = state.toAmountText,
                        amountColor = accent,
                        error = state.toAmountError,
                        onAccount = actions.onToAccount,
                        onAmount = actions.onToAmount,
                    )
                }
                // ⇅ a két kártya között
                Surface(
                    onClick = actions.onSwap,
                    shape = CircleShape,
                    color = accent,
                    contentColor = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.SwapVert, stringResource(R.string.ex_swap)) }
                }
            }
            if (state.sameAccountError) {
                Text(stringResource(R.string.ex_same_account), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            val rate = state.rate
            if (rate == null) {
                Text(stringResource(R.string.ex_same_currency), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            } else {
                RateCard(state, rate, actions)
            }

            DateField(state.date, actions.onDate, Modifier.fillMaxWidth())

            OutlinedTextField(
                value = state.feeText,
                onValueChange = actions.onFee,
                label = { Text(stringResource(R.string.ex_fee)) },
                suffix = { Text(state.fromCurrency?.symbol ?: "") },
                supportingText = {
                    Text(stringResource(if (state.feeError) R.string.ex_fee_error else R.string.ex_fee_hint))
                },
                isError = state.feeError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            state.totalDebitText?.let {
                Text(
                    stringResource(R.string.ex_total_debit, it),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            OutlinedTextField(
                value = state.note,
                onValueChange = actions.onNote,
                label = { Text(stringResource(R.string.form_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tx_delete_title),
            text = stringResource(R.string.tx_delete_text),
            onConfirm = { confirmDelete = false; actions.onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AmountCard(
    label: String,
    account: AccountOption?,
    accounts: List<AccountOption>,
    currency: Currency?,
    amountText: String,
    amountColor: Color,
    error: Boolean,
    onAccount: (Long) -> Unit,
    onAmount: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            DropdownSelector(
                label = label,
                value = account?.let { "${it.name} · ${it.currencyCode}" } ?: "",
                options = accounts,
                optionLabel = { "${it.name} · ${it.currencyCode}" },
                onSelect = { onAccount(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = amountText,
                onValueChange = onAmount,
                placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = amountColor),
                suffix = { Text(currency?.symbol ?: "", style = MaterialTheme.typography.titleLarge) },
                isError = error,
                supportingText = if (error) ({ Text(stringResource(R.string.ex_amount_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RateCard(state: ExchangeUiState, rate: ExchangeRateField, actions: ExchangeActions) {
    val accent = CashTheme.colors.exchange
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ex_rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (rate.isManual) {
                    Surface(shape = RoundedCornerShape(50), color = CashTheme.colors.warningContainer) {
                        Text(
                            stringResource(R.string.ex_rate_manual),
                            color = CashTheme.colors.warning,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    if (rate.storedText != null) {
                        IconButton(onClick = actions.onRateReset, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Restore, stringResource(R.string.ex_rate_reset))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = rate.text,
                onValueChange = actions.onRate,
                prefix = { Text("1 ${rate.leftCode} = ") },
                suffix = { Text(rate.rightCode) },
                isError = rate.error,
                supportingText = if (rate.error) ({ Text(stringResource(R.string.ex_rate_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            val info = buildList {
                add(rate.storedText?.let { stringResource(R.string.ex_rate_stored, it) } ?: stringResource(R.string.ex_rate_none))
                state.lastFetchedAt?.let { add(stringResource(R.string.ex_rate_fetched_at, formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()), R.string.fmt_date_time))) }
            }.joinToString(" · ")
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.fetchFailed) {
                Text(stringResource(R.string.ex_rate_fetch_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FilledTonalButton(
                onClick = actions.onFetch,
                enabled = !state.isFetching,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.12f), contentColor = accent),
            ) {
                if (state.isFetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ex_rate_fetch))
            }
        }
    }
}
