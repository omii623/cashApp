package com.example.cashapp.ui.account

import com.example.cashapp.ui.components.currencyLabel
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
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ColorPicker
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.IconPicker
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun AccountEditRoute(
    onDone: () -> Unit,
    viewModel: AccountEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    AccountEditScreen(
        state = state,
        onBack = onDone,
        onName = viewModel::onNameChange,
        onCurrency = viewModel::onCurrencySelect,
        onInitialBalance = viewModel::onInitialBalanceChange,
        onColor = viewModel::onColorSelect,
        onIcon = viewModel::onIconSelect,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditScreen(
    state: AccountEditUiState,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onCurrency: (String) -> Unit,
    onInitialBalance: (String) -> Unit,
    onColor: (Long) -> Unit,
    onIcon: (com.example.cashapp.domain.model.CategoryIcon) -> Unit,
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
                    Text(stringResource(if (state.isNew) R.string.acc_new_title else R.string.acc_edit_title), fontWeight = FontWeight.Bold)
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
                value = state.name,
                onValueChange = onName,
                label = { Text(stringResource(R.string.name)) },
                placeholder = { Text(stringResource(R.string.acc_name_hint)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.cat_name_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            DropdownSelector(
                label = stringResource(R.string.acc_currency),
                value = currency?.let { currencyLabel(it) } ?: state.currencyCode,
                options = if (state.currencyLocked) emptyList() else state.currencies,
                optionLabel = { currencyLabel(it) },
                onSelect = { onCurrency(it.code) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.currencyLocked) {
                Text(stringResource(R.string.acc_currency_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = state.initialBalanceText,
                onValueChange = onInitialBalance,
                label = { Text(stringResource(R.string.acc_initial_balance)) },
                placeholder = { Text("0") },
                suffix = { Text(currency?.symbol ?: "") },
                isError = state.initialBalanceError,
                supportingText = {
                    Text(stringResource(if (state.initialBalanceError) R.string.acc_initial_balance_error else R.string.acc_initial_balance_hint))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(stringResource(R.string.cat_color))
            ColorPicker(state.color, onColor)

            SectionTitle(stringResource(R.string.cat_icon))
            IconPicker(state.icon, Color(state.color), state.name, onIcon)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.acc_delete_title),
            text = stringResource(R.string.acc_delete_text, state.name),
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
