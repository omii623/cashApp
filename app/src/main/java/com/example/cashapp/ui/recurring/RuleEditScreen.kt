package com.example.cashapp.ui.recurring

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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.theme.CashTheme
import com.example.cashapp.ui.transaction.CategoryGrid
import com.example.cashapp.ui.transaction.SubcategoryChips

@Composable
fun RuleEditRoute(
    onDone: () -> Unit,
    viewModel: RuleEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    if (state.saveFailed) SaveErrorDialog(viewModel::dismissSaveError)
    RuleEditScreen(state, viewModel, onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditScreen(state: RuleEditUiState, vm: RuleEditViewModel, onBack: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val accent = if (state.type == TransactionType.INCOME) CashTheme.colors.income else CashTheme.colors.expense

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(if (state.isNew) R.string.rec_new_title else R.string.rec_edit_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            Row(
                Modifier.windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)).padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = vm::save,
                    enabled = !state.isLoading && !state.isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = CashTheme.colors.hero, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
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
            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onName,
                label = { Text(stringResource(R.string.rec_name)) },
                placeholder = { Text(stringResource(R.string.rec_name_hint)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.rec_name_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            TypeSelector(state.type, vm::onType)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = state.amountText,
                    onValueChange = vm::onAmount,
                    label = { Text(stringResource(R.string.rec_amount)) },
                    suffix = { Text(state.currency?.symbol ?: "") },
                    isError = state.amountError,
                    supportingText = if (state.amountError) ({ Text(stringResource(R.string.form_amount_error)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                DropdownSelector(
                    label = stringResource(R.string.form_account),
                    value = state.account?.let { "${it.name} (${it.currencyCode})" } ?: "",
                    options = state.accounts,
                    optionLabel = { "${it.name} (${it.currencyCode})" },
                    onSelect = { vm.onAccount(it.id) },
                    modifier = Modifier.weight(1f).padding(top = 8.dp),
                )
            }

            SectionLabel(stringResource(R.string.form_category))
            if (state.categoryError) Text(stringResource(R.string.form_category_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            CategoryGrid(state.categories, state.selectedCategoryId, accent, vm::onCategory)
            if (state.subcategories.isNotEmpty()) {
                SectionLabel(stringResource(R.string.form_subcategory))
                SubcategoryChips(state.subcategories, state.selectedSubcategoryId, accent, vm::onSubcategory)
            }

            SectionLabel(stringResource(R.string.rec_frequency))
            FrequencySelector(state.frequency, vm::onFrequency)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.intervalText,
                    onValueChange = vm::onInterval,
                    label = { Text(stringResource(R.string.rec_interval)) },
                    isError = state.intervalError,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(120.dp),
                )
                Spacer(Modifier.width(16.dp))
                // Előnézet: „Havonta”, „2 havonta” …
                Text(frequencyText(state.frequency, state.interval), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            DateField(state.startDate, vm::onStart, Modifier.fillMaxWidth(), label = stringResource(R.string.rec_start))
            SwitchRow(stringResource(R.string.rec_end_enable), state.hasEnd, vm::onHasEnd)
            if (state.hasEnd) {
                DateField(state.endDate, vm::onEnd, Modifier.fillMaxWidth(), label = stringResource(R.string.rec_end))
                if (state.endError) {
                    Text(stringResource(R.string.rec_end_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNote,
                label = { Text(stringResource(R.string.form_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(stringResource(R.string.rec_active), state.isActive, vm::onActive)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.rec_delete_title),
            text = stringResource(R.string.rec_delete_text, state.name),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    val options = listOf(TransactionType.INCOME to R.string.action_income, TransactionType.EXPENSE to R.string.action_expense)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (type, label) ->
            val color = if (type == TransactionType.INCOME) CashTheme.colors.income else CashTheme.colors.expense
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = color, activeContentColor = Color.White),
            ) { Text(stringResource(label)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FrequencySelector(selected: RecurrenceFrequency, onSelect: (RecurrenceFrequency) -> Unit) {
    val options = listOf(
        RecurrenceFrequency.DAILY to R.string.rec_freq_daily,
        RecurrenceFrequency.WEEKLY to R.string.rec_freq_weekly,
        RecurrenceFrequency.MONTHLY to R.string.rec_freq_monthly,
        RecurrenceFrequency.YEARLY to R.string.rec_freq_yearly,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (f, label) ->
            SegmentedButton(
                selected = selected == f,
                onClick = { onSelect(f) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                icon = {},
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionLabel(text: String) = Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
