package com.example.cashapp.ui.category

import com.example.cashapp.ui.components.currencyLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.CategoryIcon
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.ColorPicker
import com.example.cashapp.ui.components.IconPicker
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.components.TextInputDialog
import com.example.cashapp.ui.theme.CashTheme
import kotlin.math.roundToInt

@Composable
fun CategoryEditRoute(
    onDone: () -> Unit,
    viewModel: CategoryEditViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isDone) { if (state.isDone) onDone() }
    CategoryEditScreen(
        state = state,
        actions = CategoryEditActions(
            onBack = onDone,
            onNameChange = viewModel::onNameChange,
            onTypeSelect = viewModel::onTypeSelect,
            onColorSelect = viewModel::onColorSelect,
            onIconSelect = viewModel::onIconSelect,
            onAddSub = viewModel::addSubcategory,
            onRenameSub = viewModel::renameSubcategory,
            onRemoveSub = viewModel::removeSubcategory,
            onMoveSub = viewModel::moveSubcategory,
            onBudgetEnabledChange = viewModel::onBudgetEnabledChange,
            onBudgetAmountChange = viewModel::onBudgetAmountChange,
            onBudgetCurrencySelect = viewModel::onBudgetCurrencySelect,
            onBudgetThresholdChange = viewModel::onBudgetThresholdChange,
            onBudgetNotifyChange = viewModel::onBudgetNotifyChange,
            onSave = viewModel::save,
            onDelete = viewModel::delete,
        ),
    )
}

/** A képernyő összes eseménye egy helyen (kevesebb paraméter, könnyebb Preview). */
data class CategoryEditActions(
    val onBack: () -> Unit = {},
    val onNameChange: (String) -> Unit = {},
    val onTypeSelect: (CategoryType) -> Unit = {},
    val onColorSelect: (Long) -> Unit = {},
    val onIconSelect: (CategoryIcon) -> Unit = {},
    val onAddSub: (String) -> Unit = {},
    val onRenameSub: (Long, String) -> Unit = { _, _ -> },
    val onRemoveSub: (Long) -> Unit = {},
    val onMoveSub: (Long, Int) -> Unit = { _, _ -> },
    val onBudgetEnabledChange: (Boolean) -> Unit = {},
    val onBudgetAmountChange: (String) -> Unit = {},
    val onBudgetCurrencySelect: (String) -> Unit = {},
    val onBudgetThresholdChange: (Int) -> Unit = {},
    val onBudgetNotifyChange: (Boolean) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

private sealed interface EditDialog {
    data object AddSub : EditDialog
    data class RenameSub(val sub: EditableSubcategory) : EditDialog
    data object DeleteCategory : EditDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditScreen(state: CategoryEditUiState, actions: CategoryEditActions) {
    var dialog by remember { mutableStateOf<EditDialog?>(null) }
    val accent = if (state.type == CategoryType.INCOME) CashTheme.colors.income else CashTheme.colors.expense

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = {
                    Text(
                        stringResource(if (state.isNew) R.string.cat_new_title else R.string.cat_edit_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!state.isNew) {
                    OutlinedButton(
                        onClick = { dialog = EditDialog.DeleteCategory },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                }
                Button(
                    onClick = actions.onSave,
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
                onValueChange = actions.onNameChange,
                label = { Text(stringResource(R.string.name)) },
                isError = state.nameError,
                supportingText = if (state.nameError) ({ Text(stringResource(R.string.cat_name_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isNew) {
                SectionTitle(stringResource(R.string.cat_type))
                TypeSelector(state.type, actions.onTypeSelect)
            }

            SectionTitle(stringResource(R.string.cat_color))
            ColorPicker(state.color, actions.onColorSelect)

            SectionTitle(stringResource(R.string.cat_icon))
            IconPicker(state.icon, Color(state.color), state.name, actions.onIconSelect)

            if (state.supportsBudget) BudgetSection(state, actions)

            SectionTitle(stringResource(R.string.cat_subcategories_section))
            SubcategoryList(
                state = state,
                accent = accent,
                onMove = actions.onMoveSub,
                onRename = { dialog = EditDialog.RenameSub(it) },
                onRemove = actions.onRemoveSub,
                onAdd = { dialog = EditDialog.AddSub },
            )
            Spacer(Modifier.height(8.dp))
        }
    }

    when (val d = dialog) {
        EditDialog.AddSub -> TextInputDialog(
            title = stringResource(R.string.cat_new_subcategory),
            onConfirm = { actions.onAddSub(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is EditDialog.RenameSub -> TextInputDialog(
            title = stringResource(R.string.cat_rename_subcategory),
            initialValue = d.sub.name,
            onConfirm = { actions.onRenameSub(d.sub.key, it); dialog = null },
            onDismiss = { dialog = null },
        )
        EditDialog.DeleteCategory -> ConfirmDialog(
            title = stringResource(R.string.cat_delete_title),
            text = stringResource(R.string.cat_delete_text, state.name),
            onConfirm = { actions.onDelete(); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun BudgetSection(state: CategoryEditUiState, actions: CategoryEditActions) {
    SectionTitle(stringResource(R.string.cat_budget_section))
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (state.budgetEnabled) CashTheme.colors.income else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SwitchRow(stringResource(R.string.cat_budget_enable), state.budgetEnabled, actions.onBudgetEnabledChange)
            if (state.budgetEnabled) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    OutlinedTextField(
                        value = state.budgetAmountText,
                        onValueChange = actions.onBudgetAmountChange,
                        label = { Text(stringResource(R.string.cat_budget_amount)) },
                        isError = state.budgetAmountError,
                        supportingText = if (state.budgetAmountError) ({ Text(stringResource(R.string.cat_budget_amount_error)) }) else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(2f),
                    )
                    DropdownSelector(
                        label = stringResource(R.string.form_currency),
                        value = state.budgetCurrency,
                        options = state.currencies,
                        optionLabel = { currencyLabel(it) },
                        onSelect = { actions.onBudgetCurrencySelect(it.code) },
                        modifier = Modifier.weight(1f).padding(top = 8.dp),
                    )
                }
                Text(stringResource(R.string.cat_budget_threshold, state.budgetThreshold), style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.budgetThreshold.toFloat(),
                    onValueChange = { actions.onBudgetThresholdChange((it / 5).roundToInt() * 5) },
                    valueRange = 10f..100f,
                    steps = 17,
                )
                SwitchRow(stringResource(R.string.cat_budget_notify), state.budgetNotify, actions.onBudgetNotifyChange)
            }
        }
    }
}

@Composable
private fun SubcategoryList(
    state: CategoryEditUiState,
    accent: Color,
    onMove: (Long, Int) -> Unit,
    onRename: (EditableSubcategory) -> Unit,
    onRemove: (Long) -> Unit,
    onAdd: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (state.subcategories.isEmpty()) {
                Text(
                    stringResource(R.string.cat_subcategories_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            state.subcategories.forEachIndexed { index, sub ->
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onMove(sub.key, -1) }, enabled = index > 0, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.cat_move_up))
                    }
                    IconButton(onClick = { onMove(sub.key, 1) }, enabled = index < state.subcategories.lastIndex, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.cat_move_down))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(sub.name, Modifier.weight(1f))
                    IconButton(onClick = { onRename(sub) }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.cat_rename_subcategory), Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onRemove(sub.key) }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.delete), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
            OutlinedButton(onClick = onAdd, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cat_new_subcategory), color = accent)
            }
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
private fun SectionTitle(text: String) =
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
