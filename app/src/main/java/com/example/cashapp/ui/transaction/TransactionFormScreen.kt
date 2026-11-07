package com.example.cashapp.ui.transaction

import com.example.cashapp.ui.components.ConfirmDialog
import androidx.compose.material.icons.filled.Delete
import com.example.cashapp.ui.components.currencyLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.CategorySymbol
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.SelectorField
import com.example.cashapp.ui.theme.CashTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun TransactionFormRoute(
    onDone: () -> Unit,
    viewModel: TransactionFormViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isSaved) { if (state.isSaved) onDone() }
    if (state.saveFailed) SaveErrorDialog(viewModel::dismissSaveError)
    TransactionFormScreen(
        state = state,
        onBack = onDone,
        onAmountChange = viewModel::onAmountChange,
        onCurrencySelect = viewModel::onCurrencySelect,
        onAccountSelect = viewModel::onAccountSelect,
        onDateSelect = viewModel::onDateSelect,
        onCategorySelect = viewModel::onCategorySelect,
        onSubcategorySelect = viewModel::onSubcategorySelect,
        onNoteChange = viewModel::onNoteChange,
        onRateChange = viewModel::onRateChange,
        onRateReset = viewModel::onRateReset,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormScreen(
    state: TransactionFormUiState,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onCurrencySelect: (String) -> Unit,
    onAccountSelect: (Long) -> Unit,
    onDateSelect: (LocalDate) -> Unit,
    onCategorySelect: (Long) -> Unit,
    onSubcategorySelect: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onRateChange: (String) -> Unit,
    onRateReset: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit = {},
) {
    val accent = if (state.type == TransactionType.INCOME) CashTheme.colors.income else CashTheme.colors.expense
    val title = stringResource(
        when {
            state.type == TransactionType.INCOME && state.isEditing -> R.string.form_title_income_edit
            state.type == TransactionType.INCOME -> R.string.form_title_income
            state.isEditing -> R.string.form_title_expense_edit
            else -> R.string.form_title_expense
        }
    )
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        // A külső Scaffold már kezeli a rendszersávokat.
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
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete))
                        }
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
                // A navigációs sáv helyét a külső Scaffold már kihagyta → csak a billentyűzet „többletét” kell.
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AmountSection(state, onAmountChange, onCurrencySelect)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownSelector(
                    label = stringResource(R.string.form_account),
                    value = state.account?.let { "${it.name} (${it.currencyCode})" } ?: "",
                    options = state.accounts,
                    optionLabel = { "${it.name} (${it.currencyCode})" },
                    onSelect = { onAccountSelect(it.id) },
                    modifier = Modifier.weight(1f),
                )
                DateField(state.date, onDateSelect, Modifier.weight(1f))
            }
            state.rate?.let { RateSection(it, onRateChange, onRateReset) }

            SectionLabel(stringResource(R.string.form_category))
            if (state.categoryError) ErrorText(stringResource(R.string.form_category_error))
            CategoryGrid(state.categories, state.selectedCategoryId, accent, onCategorySelect)

            if (state.subcategories.isNotEmpty()) {
                SectionLabel(stringResource(R.string.form_subcategory))
                SubcategoryChips(state.subcategories, state.selectedSubcategoryId, accent, onSubcategorySelect)
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.form_note)) },
                placeholder = { Text(stringResource(R.string.form_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.tx_delete_title),
            text = stringResource(R.string.tx_delete_text),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AmountSection(
    state: TransactionFormUiState,
    onAmountChange: (String) -> Unit,
    onCurrencySelect: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(
            value = state.amountText,
            onValueChange = onAmountChange,
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
        if (state.amountError) ErrorText(stringResource(R.string.form_amount_error))
        CurrencyChip(state, onCurrencySelect)
    }
}

@Composable
private fun CurrencyChip(state: TransactionFormUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text("${state.currency?.code ?: ""} ▾", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.currencies.forEach { c ->
                DropdownMenuItem(
                    text = { Text(currencyLabel(c)) },
                    onClick = { expanded = false; onSelect(c.code) },
                )
            }
        }
    }
}

@Composable
private fun RateSection(rate: RateField, onRateChange: (String) -> Unit, onRateReset: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, CashTheme.colors.exchange),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.form_rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!rate.isDefault && !rate.noStoredRate) {
                    Text(
                        stringResource(R.string.form_rate_manual),
                        color = CashTheme.colors.warning,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onRateReset) {
                        Icon(Icons.Filled.Restore, stringResource(R.string.form_rate_reset))
                    }
                }
            }
            OutlinedTextField(
                value = rate.text,
                onValueChange = onRateChange,
                prefix = { Text("1 ${rate.leftCode} = ") },
                suffix = { Text(rate.rightCode) },
                singleLine = true,
                isError = rate.error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                rate.error -> ErrorText(stringResource(R.string.form_rate_error))
                rate.noStoredRate && rate.text.isEmpty() ->
                    Text(stringResource(R.string.form_rate_missing), color = CashTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
            }
            rate.accountAmountPreview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 3 oszlopos kategória-rács (a látványterv csempéi). */
@Composable
fun CategoryGrid(
    categories: List<CategoryOption>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cat ->
                    val selected = cat.id == selectedId
                    Surface(
                        onClick = { onSelect(cat.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        border = if (selected) null else BorderStroke(1.dp, Color(cat.color).copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).height(72.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            // Kijelölve fehér jel a színes csempén, különben a kategória színes köre.
                            if (selected) CategorySymbol(cat.icon, Color.White, cat.name, size = 28.dp)
                            else CategoryBadge(cat.icon, Color(cat.color), cat.name, size = 28.dp)
                            Text(
                                cat.name,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                // Hiányos utolsó sor kitöltése, hogy a csempék egyforma szélesek maradjanak.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubcategoryChips(
    items: List<SubcategoryOption>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { sub ->
            FilterChip(
                selected = sub.id == selectedId,
                onClick = { onSelect(sub.id) },
                label = { Text(sub.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = accent,
                ),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
private fun ErrorText(text: String) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
