package com.example.cashapp.ui.recurring

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.RecurrenceFrequency
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.domain.money.AmountParser
import com.example.cashapp.notifications.RecurringNotifications
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.DatePickerModal
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate

@Composable
fun RecurringRoute(
    onBack: () -> Unit,
    onEditRule: (Long) -> Unit,
    onNewRule: () -> Unit,
    onOpenBankLoan: (Long) -> Unit,
    viewModel: RecurringViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RequestNotificationPermissionOnce()
    RecurringScreen(
        state = state,
        onBack = onBack,
        // A banki hitel szabálya a hitel oldalán kezelhető (a hitel tartja karban).
        onEditRule = { id -> state.rules.firstOrNull { it.id == id }?.bankLoanId?.let(onOpenBankLoan) ?: onEditRule(id) },
        onNewRule = onNewRule,
        onApprove = viewModel::approve,
        onSkip = viewModel::skip,
        onPostpone = viewModel::postpone,
        onSetActive = viewModel::setActive,
    )
}

/** Android 13+: az értesítéshez engedély kell – az ismétlődő tételek első megnyitásakor kérjük. */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked && !RecurringNotifications.canNotify(context)) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private sealed interface RecDialog {
    data class Approve(val item: PendingUi) : RecDialog
    data class Skip(val item: PendingUi) : RecDialog
    data class Postpone(val item: PendingUi) : RecDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    state: RecurringUiState,
    onBack: () -> Unit,
    onEditRule: (Long) -> Unit,
    onNewRule: () -> Unit,
    onApprove: (Long, Long, LocalDate) -> Unit,
    onSkip: (Long) -> Unit,
    onPostpone: (Long, LocalDate) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var dialog by remember { mutableStateOf<RecDialog?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.rec_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = onNewRule,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.rec_new)) },
                    containerColor = CashTheme.colors.hero,
                    contentColor = Color.White,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.rec_tab_pending, state.pending.size)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.rec_tab_rules)) })
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (tab == 0) {
                    if (state.pending.isEmpty() && !state.isLoading) item { EmptyText(R.string.rec_pending_empty) }
                    items(state.pending, key = { it.id }) { item ->
                        PendingCard(
                            item = item,
                            onApprove = { dialog = RecDialog.Approve(item) },
                            onPostpone = { dialog = RecDialog.Postpone(item) },
                            onSkip = { dialog = RecDialog.Skip(item) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (state.pending.isNotEmpty()) item {
                        Text(
                            stringResource(R.string.rec_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                } else {
                    if (state.rules.isEmpty() && !state.isLoading) item { EmptyText(R.string.rec_rules_empty) }
                    items(state.rules, key = { it.id }) { rule ->
                        RuleCard(rule, onClick = { onEditRule(rule.id) }, onActive = { onSetActive(rule.id, it) }, Modifier.animateItem())
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        is RecDialog.Approve -> ApproveDialog(
            item = d.item,
            onConfirm = { amount, date -> onApprove(d.item.id, amount, date); dialog = null },
            onDismiss = { dialog = null },
        )
        is RecDialog.Skip -> ConfirmDialog(
            title = stringResource(R.string.rec_skip_title),
            text = stringResource(R.string.rec_skip_text),
            confirmLabel = stringResource(R.string.rec_skip),
            onConfirm = { onSkip(d.item.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is RecDialog.Postpone -> DatePickerModal(
            initial = maxOf(d.item.date, LocalDate.now()).plusDays(1),
            onConfirm = { onPostpone(d.item.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun EmptyText(res: Int) = Text(
    stringResource(res),
    textAlign = TextAlign.Center,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(32.dp),
)

@Composable
private fun PendingCard(
    item: PendingUi,
    onApprove: () -> Unit,
    onPostpone: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = CashTheme.colors
    val (label, labelColor) = when (item.dueState) {
        DueState.TODAY -> stringResource(R.string.rec_due_today) to c.warning
        DueState.OVERDUE -> stringResource(R.string.rec_overdue, formatDate(item.date, R.string.fmt_short_date).uppercase()) to c.expense
        DueState.POSTPONED -> stringResource(R.string.rec_postponed, formatDate(item.date, R.string.fmt_short_date).uppercase()) to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, color = labelColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(item.categoryIcon, item.categoryColor?.let { Color(it) } ?: c.expense, item.categoryName ?: item.ruleName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.ruleName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(item.categoryName, item.subcategoryName, item.accountName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    item.amountText,
                    fontWeight = FontWeight.Bold,
                    color = if (item.type == TransactionType.INCOME) c.income else c.expense,
                )
            }
            // Kis belső margó, hogy a hosszabb feliratok (pl. „Elhalaszt”, „Postpone”) is kiférjenek.
            val tight = PaddingValues(horizontal = 6.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = c.income, contentColor = Color.White),
                    contentPadding = tight,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.rec_approve), maxLines = 1) }
                OutlinedButton(onClick = onPostpone, contentPadding = tight, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rec_postpone), maxLines = 1)
                }
                OutlinedButton(onClick = onSkip, contentPadding = tight, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.rec_skip), color = c.expense, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: RuleUi, onClick: () -> Unit, onActive: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = CashTheme.colors
    val alpha = if (rule.isActive) 1f else 0.5f
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(
                rule.categoryIcon,
                (rule.categoryColor?.let { Color(it) } ?: c.expense).copy(alpha = alpha),
                rule.categoryName ?: rule.name,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                Text(
                    rule.amountText,
                    fontWeight = FontWeight.Bold,
                    color = (if (rule.type == TransactionType.INCOME) c.income else c.expense).copy(alpha = alpha),
                )
                val status = when {
                    rule.isEnded -> stringResource(R.string.rec_ended)
                    !rule.isActive -> stringResource(R.string.rec_inactive)
                    else -> stringResource(R.string.rec_next_due, formatDate(rule.nextDue, R.string.fmt_date))
                }
                Text(
                    "${frequencyText(rule.frequency, rule.interval)} · ${rule.accountName} · $status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.isActive, onCheckedChange = onActive, enabled = !rule.isEnded)
        }
    }
}

/** „Havonta”, „2 hetente” stb. */
@Composable
fun frequencyText(frequency: RecurrenceFrequency, interval: Int): String {
    val res = when (frequency) {
        RecurrenceFrequency.DAILY -> R.plurals.rec_every_daily
        RecurrenceFrequency.WEEKLY -> R.plurals.rec_every_weekly
        RecurrenceFrequency.MONTHLY -> R.plurals.rec_every_monthly
        RecurrenceFrequency.YEARLY -> R.plurals.rec_every_yearly
    }
    return pluralStringResource(res, interval, interval)
}

/** Jóváhagyás előtt az összeg és a dátum módosítható. */
@Composable
private fun ApproveDialog(item: PendingUi, onConfirm: (Long, LocalDate) -> Unit, onDismiss: () -> Unit) {
    val decimals = item.currency?.decimals ?: 0
    var amountText by rememberSaveable { mutableStateOf(AmountParser.toEditText(item.amountMinor, decimals)) }
    var date by remember { mutableStateOf(minOf(item.date, LocalDate.now())) }
    val amount = AmountParser.parseAmount(amountText, decimals)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringResource(R.string.rec_approve_title)}: ${item.ruleName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.rec_amount)) },
                    suffix = { Text(item.currency?.symbol ?: "") },
                    isError = amount == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                DateField(date, { date = it }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.rec_approve_text, item.accountName), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { amount?.let { onConfirm(it, date) } }, enabled = amount != null) {
                Text(stringResource(R.string.rec_approve))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
