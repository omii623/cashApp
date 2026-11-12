package com.example.cashapp.ui.transaction

import com.example.cashapp.ui.components.ConfirmDialog
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.cashapp.ui.components.CategoryBadge
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate

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

            val bankLoan = state.bankLoan
            if (bankLoan != null) {
                // Banki hitel tétele: kategória helyett a hitel (a hitel oldalán kezelhető).
                SectionLabel(stringResource(R.string.form_bank_loan))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryBadge(bankLoan.icon, bankLoan.color?.let { Color(it) } ?: accent, bankLoan.name, size = 40.dp)
                    Text(bankLoan.name, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                SectionLabel(stringResource(R.string.form_category))
                if (state.categoryError) ErrorText(stringResource(R.string.form_category_error))
                CategoryGrid(state.categories, state.selectedCategoryId, accent, onCategorySelect)
            }

            if (bankLoan == null && state.subcategories.isNotEmpty()) {
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
