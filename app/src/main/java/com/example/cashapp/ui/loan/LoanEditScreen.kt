package com.example.cashapp.ui.loan

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ColorPicker
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.IconPicker
import com.example.cashapp.domain.model.CategoryIcon
import androidx.compose.foundation.layout.Spacer
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.ui.components.currencyLabel
import com.example.cashapp.ui.theme.CashTheme

/**
 * @param onDone mentés / törlés / vissza után. Új kölcsön mentésekor megkapja az azonosítóját
 *   (hogy a részletek oldal nyílhasson), egyébként `null`.
 */
@Composable
fun LoanEditRoute(
    onDone: (newLoanId: Long?) -> Unit,
    viewModel: LoanEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.doneId) {
        val id = state.doneId ?: return@LaunchedEffect
        onDone(if (state.isNew && id > 0) id else null)
    }
    LoanEditScreen(
        state = state,
        onBack = { onDone(null) },
        onName = viewModel::onName,
        onDirection = viewModel::onDirection,
        onCurrency = viewModel::onCurrency,
        onOpening = viewModel::onOpening,
        onRepaid = viewModel::onRepaid,
        onBookAmount = viewModel::onBookAmount,
        onBookRepaid = viewModel::onBookRepaid,
        onAccount = viewModel::onAccount,
        onStartDate = viewModel::onStartDate,
        onNote = viewModel::onNote,
        onColor = viewModel::onColor,
        onIcon = viewModel::onIcon,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
    if (state.saveFailed) SaveErrorDialog(onDismiss = viewModel::dismissError)
}

/** „Számítson bele a bevétel/kiadásba” kapcsoló a nyitó mezők alatt. */
@Composable
private fun BookSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.loan_book_switch), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanEditScreen(
    state: LoanEditUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onDirection: (LoanDirection) -> Unit,
    onCurrency: (String) -> Unit,
    onOpening: (String) -> Unit,
    onRepaid: (String) -> Unit,
    onBookAmount: (Boolean) -> Unit,
    onBookRepaid: (Boolean) -> Unit,
    onAccount: (Long) -> Unit,
    onStartDate: (java.time.LocalDate) -> Unit,
    onNote: (String) -> Unit,
    onColor: (Long) -> Unit = {},
    onIcon: (CategoryIcon) -> Unit = {},
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(stringResource(if (state.isNew) R.string.loan_new_title else R.string.loan_edit_title), fontWeight = FontWeight.Bold)
                },
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
                    onClick = onSave,
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
        val currency = state.currencies.firstOrNull { it.code == state.currencyCode }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.personName,
                onValueChange = onName,
                label = { Text(stringResource(R.string.loan_person)) },
                placeholder = { Text(stringResource(R.string.loan_person_hint)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.cat_name_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.loan_direction))
            val directions = listOf(LoanDirection.LENT, LoanDirection.BORROWED)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                directions.forEachIndexed { i, d ->
                    SegmentedButton(
                        selected = state.direction == d,
                        onClick = { onDirection(d) },
                        enabled = !state.locked,
                        shape = SegmentedButtonDefaults.itemShape(i, directions.size),
                    ) { Text(stringResource(directionLabel(d))) }
                }
            }

            DropdownSelector(
                label = stringResource(R.string.loan_currency),
                value = currency?.let { currencyLabel(it) } ?: state.currencyCode,
                options = if (state.locked) emptyList() else state.currencies,
                optionLabel = { currencyLabel(it) },
                onSelect = { onCurrency(it.code) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.locked) {
                Text(stringResource(R.string.loan_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = state.openingText,
                onValueChange = onOpening,
                label = { Text(stringResource(R.string.loan_opening)) },
                placeholder = { Text("0") },
                suffix = { Text(currency?.symbol ?: "") },
                isError = state.openingError,
                supportingText = {
                    Text(stringResource(if (state.openingError) R.string.loan_opening_error else R.string.loan_opening_hint))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            BookSwitch(state.bookAmount, onBookAmount)

            OutlinedTextField(
                value = state.repaidText,
                onValueChange = onRepaid,
                label = { Text(stringResource(R.string.loan_opening_repaid)) },
                placeholder = { Text("0") },
                suffix = { Text(currency?.symbol ?: "") },
                isError = state.repaidError,
                supportingText = {
                    Text(
                        when {
                            state.repaidError -> stringResource(R.string.loan_opening_repaid_error)
                            else -> stringResource(R.string.loan_opening_repaid_hint)
                        }
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            BookSwitch(state.bookRepaid, onBookRepaid)

            if (state.booksAnything) {
                if (state.accounts.isEmpty()) {
                    Text(
                        stringResource(R.string.loan_mv_no_account, state.currencyCode),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    DropdownSelector(
                        label = stringResource(R.string.loan_book_account),
                        value = state.account?.let { "${it.name} (${it.currencyCode})" } ?: "",
                        options = state.accounts,
                        optionLabel = { "${it.name} (${it.currencyCode})" },
                        onSelect = { onAccount(it.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.accountError) {
                    Text(stringResource(R.string.loan_book_account_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    stringResource(R.string.loan_book_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            DateField(state.startDate, onStartDate, Modifier.fillMaxWidth(), label = stringResource(R.string.loan_start_date))

            OutlinedTextField(
                value = state.note,
                onValueChange = onNote,
                label = { Text(stringResource(R.string.form_note)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            // Szín és ikon / betű – ugyanaz, mint a kategóriáknál.
            SectionTitle(stringResource(R.string.cat_color))
            ColorPicker(state.color ?: 0L, onColor)
            SectionTitle(stringResource(R.string.cat_icon))
            val previewColor = state.color?.let { Color(it) } ?: directionColor(state.direction, settled = false)
            IconPicker(state.icon.orEmpty(), previewColor, state.personName, onIcon)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.loan_delete_title),
            text = stringResource(R.string.loan_delete_text, state.personName),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
