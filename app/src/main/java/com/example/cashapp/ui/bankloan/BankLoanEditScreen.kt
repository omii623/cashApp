package com.example.cashapp.ui.bankloan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.loan.SectionTitle
import com.example.cashapp.ui.theme.CashTheme
import com.example.cashapp.ui.transaction.CategoryGrid
import com.example.cashapp.ui.transaction.SubcategoryChips

/** @param onDone mentés után a hitel azonosítójával (új hitelnél a részletek oldal nyílik), vissza / törlés után `null`. */
@Composable
fun BankLoanEditRoute(
    onDone: (savedId: Long?) -> Unit,
    viewModel: BankLoanEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.doneId) {
        val id = state.doneId ?: return@LaunchedEffect
        onDone(id.takeIf { it > 0 })
    }
    BankLoanEditScreen(state, viewModel, onBack = { onDone(null) })
    if (state.saveFailed) SaveErrorDialog(onDismiss = viewModel::dismissError)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BankLoanEditScreen(state: BankLoanEditUiState, vm: BankLoanEditViewModel, onBack: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(if (state.isNew) R.string.bank_new_title else R.string.bank_edit_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
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
        val symbol = state.currency?.symbol.orEmpty()
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewCard(state.preview)

            OutlinedTextField(
                value = state.name,
                onValueChange = vm::onName,
                label = { Text(stringResource(R.string.bank_name)) },
                placeholder = { Text(stringResource(R.string.bank_name_hint)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.cat_name_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.bankName,
                onValueChange = vm::onBankName,
                label = { Text(stringResource(R.string.bank_bank)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.bank_kind))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BankLoanKind.entries.forEach { kind ->
                    FilterChip(
                        selected = state.kind == kind,
                        onClick = { vm.onKind(kind) },
                        label = { Text(stringResource(kind.label())) },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().toggleable(value = state.running, role = Role.Switch, onValueChange = vm::onRunning),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.bank_running), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.bank_running_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.running, onCheckedChange = null)
            }

            AmountField(
                value = state.principalText,
                onChange = vm::onPrincipal,
                label = stringResource(if (state.running) R.string.bank_principal_running else R.string.bank_principal),
                suffix = symbol,
                isError = state.principalError,
                errorText = stringResource(R.string.form_amount_error),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                OutlinedTextField(
                    value = state.thmText,
                    onValueChange = vm::onThm,
                    label = { Text(stringResource(R.string.bank_thm)) },
                    suffix = { Text("%") },
                    isError = state.thmError,
                    supportingText = if (state.thmError) ({ Text(stringResource(R.string.calc_thm_error)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.monthsText,
                    onValueChange = vm::onMonths,
                    label = { Text(stringResource(if (state.running) R.string.bank_months_running else R.string.bank_months)) },
                    suffix = { Text(stringResource(R.string.calc_months_unit)) },
                    isError = state.monthsError,
                    supportingText = if (state.monthsError) ({ Text(stringResource(R.string.calc_months_error)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            DateField(
                state.firstDueDate,
                vm::onFirstDue,
                Modifier.fillMaxWidth(),
                label = stringResource(if (state.running) R.string.bank_first_due_running else R.string.bank_first_due),
            )

            if (state.running) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = state.originalText,
                        onValueChange = vm::onOriginal,
                        label = { Text(stringResource(R.string.bank_original)) },
                        suffix = { Text(symbol) },
                        isError = state.originalError,
                        supportingText = if (state.originalError) ({ Text(stringResource(R.string.bank_original_error)) }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.paidBeforeText,
                        onValueChange = vm::onPaidBefore,
                        label = { Text(stringResource(R.string.bank_paid_before)) },
                        isError = state.paidBeforeError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.bank_optional_progress_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AmountField(
                value = state.overrideText,
                onChange = vm::onOverride,
                label = stringResource(R.string.bank_override),
                suffix = symbol,
                isError = state.overrideError,
                errorText = stringResource(R.string.form_amount_error),
                hint = stringResource(R.string.bank_override_hint, state.preview?.calculatedMonthly ?: "–"),
            )

            DropdownSelector(
                label = stringResource(R.string.bank_account),
                value = state.account?.let { "${it.name} (${it.currencyCode})" } ?: "",
                options = state.accounts,
                optionLabel = { "${it.name} (${it.currencyCode})" },
                onSelect = { vm.onAccount(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )

            val accent = CashTheme.colors.expense
            SectionTitle(stringResource(R.string.form_category))
            if (state.categoryError) {
                Text(stringResource(R.string.form_category_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            CategoryGrid(state.categories, state.categoryId, accent, vm::onCategory)
            if (state.subcategories.isNotEmpty()) {
                SectionTitle(stringResource(R.string.form_subcategory))
                SubcategoryChips(state.subcategories, state.subcategoryId, accent, vm::onSubcategory)
            }
            Text(
                stringResource(R.string.bank_book_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNote,
                label = { Text(stringResource(R.string.form_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.bank_delete_title),
            text = stringResource(R.string.bank_delete_text, state.name),
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    suffix: String,
    isError: Boolean,
    errorText: String,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { Text("0") },
        suffix = { Text(suffix) },
        isError = isError,
        supportingText = when {
            isError -> ({ Text(errorText) })
            hint != null -> ({ Text(hint) })
            else -> null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Élő előnézet: havi részlet, teljes visszafizetés, hitelköltség és az utolsó részlet dátuma. */
@Composable
private fun PreviewCard(preview: BankLoanPreview?) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.calc_monthly), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            Text(preview?.monthly ?: "–", color = c.onHero, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.calc_total), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                    Text(preview?.total ?: "–", color = c.onHero, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.calc_cost), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                    Text(preview?.cost ?: "–", color = c.onHero, fontWeight = FontWeight.Bold)
                }
            }
            preview?.lastDue?.let {
                Text(stringResource(R.string.bank_preview_end, formatDate(it, R.string.fmt_date)), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
