package com.example.cashapp.ui.bankloan

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.BankLoanEventType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.DateField
import com.example.cashapp.ui.components.SaveErrorDialog
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.loan.ThmDialog
import com.example.cashapp.ui.loan.formatPercent
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate

@Composable
fun BankLoanDetailRoute(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPrepay: () -> Unit,
    viewModel: BankLoanDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Ha a hitelt közben törölték (szerkesztőből), ez az oldal is bezárul.
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }
    BankLoanDetailScreen(state, viewModel, onBack, onEdit, onPrepay)
    if (state.actionFailed) SaveErrorDialog(onDismiss = viewModel::dismissError)
}

/** Banki hitel részletei: haladás, 2×2 adat, befizetés / előtörlesztés gombok, események, törlesztési terv. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankLoanDetailScreen(
    state: BankLoanDetailUiState,
    vm: BankLoanDetailViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPrepay: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var payDialog by remember { mutableStateOf(false) }
    var rateDialog by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var undoEvent by remember { mutableStateOf<EventUi?>(null) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val item = state.item

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(item?.name.orEmpty(), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = item != null) { Icon(Icons.Filled.Edit, stringResource(R.string.cat_edit)) }
                    Box {
                        IconButton(onClick = { menu = true }, enabled = item != null) { Icon(Icons.Filled.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (item != null && !item.isDone) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bank_change_rate)) },
                                    onClick = { menu = false; rateDialog = true },
                                )
                            }
                            if (item?.isClosed == true) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.loan_reopen)) }, onClick = { menu = false; vm.reopen() })
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.bank_close)) },
                                    onClick = { menu = false; confirmClose = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading || item == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        // Összecsukva: az utolsó 2 befizetett és a következő 6 részlet.
        val visibleRows = if (showAll) state.rows
            else state.rows.drop((state.paidCount - 2).coerceAtLeast(0)).take(8)
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state, item) }
            item { InfoGrid(state) }
            item { ActionButtons(state, item, onPay = { payDialog = true }, onPrepay = onPrepay) }
            if (state.events.isNotEmpty()) {
                item { Text(stringResource(R.string.bank_events), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                item { EventsCard(state.events, onUndo = { undoEvent = it }) }
            }
            item { Text(stringResource(R.string.bank_schedule), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column {
                        visibleRows.forEachIndexed { i, row ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            ScheduleRow(row, isNext = row.index == state.paidCount + 1 && !item.isDone)
                        }
                    }
                }
            }
            if (state.rows.size > visibleRows.size || showAll) {
                item {
                    TextButton(onClick = { showAll = !showAll }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showAll) stringResource(R.string.bank_schedule_less) else stringResource(R.string.bank_schedule_more, state.rows.size))
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.bank_rule_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    val next = state.next
    if (payDialog && next != null) {
        PayDialog(
            next = next,
            symbol = state.currency?.symbol.orEmpty(),
            onConfirm = { date, amount -> if (vm.payInstallment(date, amount)) payDialog = false },
            onDismiss = { payDialog = false },
        )
    }
    if (rateDialog) {
        ThmDialog(
            current = state.thmPercent,
            title = stringResource(R.string.bank_change_rate),
            onConfirm = { vm.changeRate(it); rateDialog = false },
            onDismiss = { rateDialog = false },
        )
    }
    if (confirmClose) {
        ConfirmDialog(
            title = stringResource(R.string.bank_close),
            text = stringResource(R.string.bank_close_text),
            confirmLabel = stringResource(R.string.bank_close),
            onConfirm = { confirmClose = false; vm.close() },
            onDismiss = { confirmClose = false },
        )
    }
    undoEvent?.let { event ->
        ConfirmDialog(
            title = stringResource(R.string.bank_event_delete_title),
            text = stringResource(R.string.bank_event_delete_text),
            confirmLabel = stringResource(R.string.bank_event_delete_title),
            onConfirm = { undoEvent = null; vm.deleteEvent(event.id) },
            onDismiss = { undoEvent = null },
        )
    }
}

@Composable
private fun Header(state: BankLoanDetailUiState, item: BankLoanItemUi) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankLoanBadge(item, size = 48.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        listOf(item.bankName, stringResource(item.kind.label())).filter { it.isNotBlank() }.joinToString(" · "),
                        color = c.onHeroMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        when {
                            item.isClosed -> stringResource(R.string.loan_closed)
                            item.isPaidOff -> stringResource(R.string.bank_paid_off)
                            else -> item.outstanding
                        },
                        color = if (item.isDone) c.onHero else c.expense,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(stringResource(R.string.bank_of_original, state.originalPrincipal), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { state.paidPercent / 100f },
                color = c.income,
                trackColor = c.onHeroMuted.copy(alpha = 0.3f),
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
            Row(Modifier.padding(top = 6.dp)) {
                Text(stringResource(R.string.bank_paid_percent, state.paidPercent), color = c.onHero, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.bank_months_progress, item.paidMonths, item.totalMonths), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            }
            state.closedDate?.let {
                Text(stringResource(R.string.loan_closed_info, formatDate(it, R.string.fmt_date)), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.note.isNotBlank()) {
                Text(state.note, color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun InfoGrid(state: BankLoanDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(stringResource(R.string.bank_monthly), state.monthly, Modifier.weight(1f))
            InfoTile(stringResource(R.string.bank_thm), formatPercent(state.thmPercent), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoTile(stringResource(R.string.bank_end), state.endDate?.let { formatDate(it, R.string.fmt_date) } ?: "–", Modifier.weight(1f))
            InfoTile(stringResource(R.string.bank_interest_paid), state.interestPaid, Modifier.weight(1f))
        }
    }
}

@Composable
private fun InfoTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionButtons(state: BankLoanDetailUiState, item: BankLoanItemUi, onPay: () -> Unit, onPrepay: () -> Unit) {
    if (item.isPaidOff && !item.isClosed) {
        Text(stringResource(R.string.bank_paid_off_info), fontWeight = FontWeight.Bold, color = CashTheme.colors.income)
        return
    }
    val enabled = !item.isDone && state.next != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onPay,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = CashTheme.colors.hero, contentColor = Color.White),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(52.dp),
        ) { Text(stringResource(R.string.bank_pay_installment), fontWeight = FontWeight.Bold) }
        OutlinedButton(
            onClick = onPrepay,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (enabled) CashTheme.colors.hero else MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).height(52.dp),
        ) { Text(stringResource(R.string.bank_prepay)) }
    }
}

@Composable
private fun EventsCard(events: List<EventUi>, onUndo: (EventUi) -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            events.forEachIndexed { i, e ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                Column(Modifier.fillMaxWidth().clickable { onUndo(e) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        when {
                            e.type == BankLoanEventType.RATE_CHANGE -> stringResource(R.string.bank_event_rate, e.value)
                            e.penalty != null -> stringResource(R.string.bank_event_prepay_penalty, e.value, e.penalty)
                            else -> stringResource(R.string.bank_event_prepay, e.value)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.bank_event_after, formatDate(e.date, R.string.fmt_date), e.afterInstallment),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(row: ScheduleRowUi, isNext: Boolean) {
    Row(
        Modifier.fillMaxWidth().alpha(if (row.paid) 0.55f else 1f).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (row.paid) Icon(Icons.Filled.CheckCircle, null, tint = CashTheme.colors.income, modifier = Modifier.size(20.dp))
            else Text("${row.index}.", style = MaterialTheme.typography.bodySmall, fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                formatDate(row.dueDate, R.string.fmt_date),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                stringResource(R.string.bank_row_split, row.principal, row.interest),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            row.prepaidBefore?.let {
                Text(stringResource(R.string.bank_row_prepaid, it), style = MaterialTheme.typography.bodySmall, color = CashTheme.colors.income)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(row.payment, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(row.balanceAfter, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** „Részlet befizetve”: összeg (a terv szerinti részlettel előtöltve) és dátum (ma). */
@Composable
private fun PayDialog(next: NextInstallmentUi, symbol: String, onConfirm: (LocalDate, String) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf(next.amountText) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bank_pay_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.bank_pay_text, next.index, formatDate(next.dueDate, R.string.fmt_date)),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text(stringResource(R.string.bank_amount)) },
                    suffix = { Text(symbol) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                DateField(date, { date = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.bank_pay_date))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(date, amount) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
