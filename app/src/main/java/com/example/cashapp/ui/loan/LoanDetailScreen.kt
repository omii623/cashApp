package com.example.cashapp.ui.loan

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.loan.LoanMath
import com.example.cashapp.domain.model.LoanDirection
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.TransactionRow
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun LoanDetailRoute(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNewMovement: (increase: Boolean) -> Unit,
    onMovement: (transactionId: Long) -> Unit,
    viewModel: LoanDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Ha a kölcsönt közben törölték (szerkesztőből), ez az oldal is bezárul.
    LaunchedEffect(state.notFound) { if (state.notFound) onBack() }
    LoanDetailScreen(state, onBack, onEdit, onNewMovement, onMovement, onClose = viewModel::close, onReopen = viewModel::reopen)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanDetailScreen(
    state: LoanDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNew: (increase: Boolean) -> Unit,
    onMovement: (Long) -> Unit,
    onClose: () -> Unit = {},
    onReopen: () -> Unit = {},
) {
    var confirmClose by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(state.loan?.personName ?: "", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = onEdit, enabled = state.loan != null) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.cat_edit))
                    }
                },
            )
        },
    ) { padding ->
        val loan = state.loan
        if (state.isLoading || loan == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state, loan) }
            item { MovementButtons(loan, onNew) }
            item {
                Text(stringResource(R.string.loan_movements), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (state.movements.isEmpty() && state.openingRows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.loan_no_movements),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            } else {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column {
                            state.movements.forEachIndexed { i, row ->
                                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                // A sor címe itt a mozgás fajtája (a név a fejlécben van).
                                TransactionRow(row.copy(loanName = loan.personName), onClick = { onMovement(row.id) })
                            }
                            // A rögzítés előtti összegek dátum nélkül; a kölcsön szerkesztőjében módosíthatók.
                            state.openingRows.forEachIndexed { i, row ->
                                if (i > 0 || state.movements.isNotEmpty()) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                                OpeningRow(row, loan, onClick = onEdit)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.loan_mv_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                if (loan.isClosed) {
                    OutlinedButton(onClick = onReopen, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(stringResource(R.string.loan_reopen))
                    }
                } else {
                    OutlinedButton(
                        onClick = { confirmClose = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.loan_close), color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    val loan = state.loan
    if (confirmClose && loan != null) {
        ConfirmDialog(
            title = stringResource(R.string.loan_close_title),
            text = if (loan.canRepay) stringResource(R.string.loan_close_text_remaining, loan.outstanding)
                else stringResource(R.string.loan_close_text),
            confirmLabel = stringResource(R.string.loan_close),
            onConfirm = { confirmClose = false; onClose() },
            onDismiss = { confirmClose = false },
        )
    }
}

@Composable
private fun Header(state: LoanDetailUiState, loan: LoanItemUi) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(loan.icon, loan.color?.let { Color(it) } ?: directionColor(loan.direction, loan.isDone), loan.personName, size = 48.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${stringResource(directionLabel(loan.direction))} · ${stringResource(R.string.loan_outstanding)}",
                    color = c.onHeroMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when {
                        loan.isClosed -> stringResource(R.string.loan_closed)
                        loan.isSettled -> stringResource(R.string.loan_settled)
                        else -> loan.outstanding
                    },
                    color = if (loan.isDone) c.onHero else if (loan.direction == LoanDirection.BORROWED) c.expense else c.income,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                loan.closedDate?.let { date ->
                    val closedOn = formatDate(date, R.string.fmt_date)
                    // A lezáráskor le nem fizetett, leírt rész (ha volt).
                    val text = if (!loan.isSettled) stringResource(R.string.loan_closed_info_remaining, closedOn, loan.outstanding)
                        else stringResource(R.string.loan_closed_info, closedOn)
                    Text(text, color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                }
                state.opening?.let {
                    val text = state.openingRepaid?.let { repaid -> stringResource(R.string.loan_opening_info_repaid, it, repaid) }
                        ?: stringResource(R.string.loan_opening_info, it)
                    Text(text, color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (state.note.isNotBlank()) {
                    Text(state.note, color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Rögzítés előtti tétel (dátum nélkül) – a [TransactionRow] kinézetével. */
@Composable
private fun OpeningRow(row: OpeningRowUi, loan: LoanItemUi, onClick: () -> Unit) {
    val personName = loan.personName
    val direction = loan.direction
    // Ugyanaz a szín, mint egy valódi mozgásnál (kiadás piros, bevétel zöld).
    val accent = if (LoanMath.transactionType(direction, row.increase) == TransactionType.EXPENSE) CashTheme.colors.expense else CashTheme.colors.income
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(loan.icon, loan.color?.let { Color(it) } ?: accent, personName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (row.increase) R.string.loan_row_increase else R.string.loan_row_decrease, personName),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (row.bookedDate != null) "${formatDate(row.bookedDate, R.string.fmt_date)} · ${row.bookedAccount.orEmpty()}"
                else stringResource(R.string.loan_before_tracking),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(row.amountText, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Két nagy gomb, mint a főoldalon a Bevétel / Kiadás: zöld, ha pénz jön be a számlára
 * (pl. visszafizetést kapok), piros, ha kimegy (pl. kölcsönadok). Bal oldalt a zöld.
 * Törleszteni csak akkor lehet, ha van hátralék.
 */
@Composable
private fun MovementButtons(loan: LoanItemUi, onNew: (increase: Boolean) -> Unit) {
    val c = CashTheme.colors
    // Melyik fajta hoz pénzt a számlára: kölcsönadásnál a visszafizetés, kölcsönkérésnél a kölcsön.
    val incomeIsIncrease = LoanMath.transactionType(loan.direction, increase = true) == TransactionType.INCOME
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(incomeIsIncrease to c.income, !incomeIsIncrease to c.expense).forEach { (increase, color) ->
            val enabled = !loan.isClosed && (increase || loan.canRepay)
            Surface(
                onClick = { onNew(increase) },
                enabled = enabled,
                color = if (enabled) color else color.copy(alpha = 0.35f),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f).height(80.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                ) {
                    Icon(if (color == c.income) Icons.Filled.Add else Icons.Filled.Remove, contentDescription = null)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(LoanMovementViewModel.kindLabel(loan.direction, increase)),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
