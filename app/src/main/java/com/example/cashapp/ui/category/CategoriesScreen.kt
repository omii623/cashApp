package com.example.cashapp.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.data.local.entity.SubcategoryEntity
import com.example.cashapp.domain.budget.BudgetCalculator
import com.example.cashapp.domain.model.CategoryType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.TextInputDialog
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun CategoriesRoute(
    onBack: () -> Unit,
    onEdit: (id: Long) -> Unit,
    onNew: (CategoryType) -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesScreen(
        state = state,
        onBack = onBack,
        onTypeSelect = viewModel::selectType,
        onEdit = onEdit,
        onNew = { onNew(state.type) },
        onMove = viewModel::move,
        onAddSubcategory = viewModel::addSubcategory,
        onRenameSubcategory = viewModel::renameSubcategory,
        onDeleteSubcategory = viewModel::deleteSubcategory,
        onMessageShown = viewModel::messageShown,
    )
}

/** Melyik párbeszédablak van nyitva a listán. */
private sealed interface ListDialog {
    data class AddSub(val categoryId: Long) : ListDialog
    data class RenameSub(val sub: SubcategoryEntity) : ListDialog
    data class DeleteSub(val sub: SubcategoryEntity) : ListDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onBack: () -> Unit,
    onTypeSelect: (CategoryType) -> Unit,
    onEdit: (Long) -> Unit,
    onNew: () -> Unit,
    onMove: (id: Long, delta: Int) -> Unit,
    onAddSubcategory: (categoryId: Long, name: String) -> Unit,
    onRenameSubcategory: (SubcategoryEntity, String) -> Unit,
    onDeleteSubcategory: (SubcategoryEntity) -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { stringResource(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }
    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var dialog by remember { mutableStateOf<ListDialog?>(null) }
    val accent = if (state.type == CategoryType.INCOME) CashTheme.colors.income else CashTheme.colors.expense

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.cat_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.cat_new)) },
                containerColor = accent,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TypeSelector(state.type, onTypeSelect, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.items.isEmpty() -> Text(
                    stringResource(R.string.cat_empty),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        CategoryCard(
                            item = item,
                            isExpanded = item.id in expanded,
                            canMoveUp = index > 0,
                            canMoveDown = index < state.items.lastIndex,
                            accent = accent,
                            onToggle = { expanded = if (item.id in expanded) expanded - item.id else expanded + item.id },
                            onEdit = { onEdit(item.id) },
                            onMove = { delta -> onMove(item.id, delta) },
                            onAddSub = { dialog = ListDialog.AddSub(item.id) },
                            onRenameSub = { dialog = ListDialog.RenameSub(it) },
                            onDeleteSub = { dialog = ListDialog.DeleteSub(it) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is ListDialog.AddSub -> TextInputDialog(
            title = stringResource(R.string.cat_new_subcategory),
            onConfirm = { onAddSubcategory(d.categoryId, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is ListDialog.RenameSub -> TextInputDialog(
            title = stringResource(R.string.cat_rename_subcategory),
            initialValue = d.sub.name,
            onConfirm = { onRenameSubcategory(d.sub, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is ListDialog.DeleteSub -> ConfirmDialog(
            title = stringResource(R.string.cat_delete_subcategory_title),
            text = stringResource(R.string.cat_delete_subcategory_text, d.sub.name),
            onConfirm = { onDeleteSubcategory(d.sub); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeSelector(selected: CategoryType, onSelect: (CategoryType) -> Unit, modifier: Modifier = Modifier) {
    val options = listOf(CategoryType.INCOME to R.string.cat_tab_income, CategoryType.EXPENSE to R.string.cat_tab_expense)
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (type, label) ->
            val color = if (type == CategoryType.INCOME) CashTheme.colors.income else CashTheme.colors.expense
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                colors = SegmentedButtonDefaults.colors(activeContainerColor = color, activeContentColor = Color.White),
            ) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun CategoryCard(
    item: CategoryItemUi,
    isExpanded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    accent: Color,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onAddSub: () -> Unit,
    onRenameSub: (SubcategoryEntity) -> Unit,
    onDeleteSub: (SubcategoryEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.cat_move_up))
                }
                IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.cat_move_down))
                }
            }
            Spacer(Modifier.width(4.dp))
            CategoryBadge(item.icon, Color(item.color), item.name, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                BudgetLine(item)
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.cat_edit)) }
            Icon(
                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = stringResource(R.string.cat_expand),
                modifier = Modifier.padding(end = 12.dp),
            )
        }
        if (isExpanded) {
            HorizontalDivider()
            item.subcategories.forEach { sub ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 76.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(sub.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { onRenameSub(sub) }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.cat_rename_subcategory), Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onDeleteSub(sub) }) {
                        Icon(
                            Icons.Filled.Delete, stringResource(R.string.delete), Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            OutlinedButton(onClick = onAddSub, modifier = Modifier.padding(start = 76.dp, bottom = 12.dp, top = 4.dp)) {
                Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.cat_new_subcategory), color = accent)
            }
        }
    }
}

@Composable
private fun BudgetLine(item: CategoryItemUi) {
    val budget = item.budget
    if (budget == null) {
        val count = pluralStringResource(R.plurals.cat_subcategory_count, item.subcategories.size, item.subcategories.size)
        Text(
            "$count · ${stringResource(R.string.cat_no_budget)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val color = when (budget.level) {
        BudgetCalculator.Level.OK -> CashTheme.colors.income
        BudgetCalculator.Level.WARNING -> CashTheme.colors.warning
        BudgetCalculator.Level.OVER -> CashTheme.colors.expense
    }
    Text(stringResource(R.string.cat_budget_prefix, budget.text), style = MaterialTheme.typography.bodySmall, color = color)
    Spacer(Modifier.height(4.dp))
    LinearProgressIndicator(
        progress = { budget.progress },
        color = color,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        drawStopIndicator = {},
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp).height(6.dp),
    )
}
