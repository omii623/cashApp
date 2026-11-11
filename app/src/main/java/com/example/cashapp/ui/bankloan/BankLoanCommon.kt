package com.example.cashapp.ui.bankloan

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.data.repository.BankLoanOverview
import com.example.cashapp.domain.model.BankLoanKind
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.home.HomeViewModel
import com.example.cashapp.ui.loan.format
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate

/** Egy banki hitel a listában / a részletek fejlécében, megformázva. */
data class BankLoanItemUi(
    val id: Long,
    val name: String,
    val bankName: String,
    val kind: BankLoanKind,
    /** Választott jelölés (null: a fajta ikonja / a kiadás színe). */
    val icon: String?,
    val color: Long?,
    val outstanding: String,
    val paidMonths: Int,
    val totalMonths: Int,
    val nextDate: LocalDate?,
    val nextAmount: String?,
    /** A következő részlet 3 napon belül esedékes (vagy már lejárt). */
    val dueSoon: Boolean,
    val isClosed: Boolean,
    val isPaidOff: Boolean,
) {
    val isDone: Boolean get() = isClosed || isPaidOff
    val progress: Float get() = if (totalMonths == 0) 0f else paidMonths.toFloat() / totalMonths
}

internal const val DUE_SOON_DAYS = 3L

internal fun BankLoanOverview.toItemUi(currencies: Map<String, Currency>, hidden: Boolean, today: LocalDate = LocalDate.now()): BankLoanItemUi {
    fun money(minor: Long) = if (hidden) HomeViewModel.MASK else format(minor, loan.currencyCode, currencies)
    val next = nextInstallment
    return BankLoanItemUi(
        id = loan.id,
        name = loan.name,
        bankName = loan.bankName,
        kind = loan.kind,
        icon = loan.icon,
        color = loan.color,
        outstanding = money(outstandingMinor),
        paidMonths = paidMonthsForProgress,
        totalMonths = totalMonthsForProgress,
        nextDate = next?.dueDate,
        nextAmount = next?.let { money(it.paymentMinor) },
        dueSoon = !isClosed && next != null && !next.dueDate.isAfter(today.plusDays(DUE_SOON_DAYS)),
        isClosed = isClosed,
        isPaidOff = isPaidOff,
    )
}

/** A fajta alapértelmezett ikonja (a kategória-ikonok közül). */
internal fun BankLoanKind.iconKey(): String = when (this) {
    BankLoanKind.MORTGAGE -> "home"
    BankLoanKind.PERSONAL -> "money"
    BankLoanKind.CAR -> "transport"
    BankLoanKind.OTHER -> "bank"
}

@StringRes
internal fun BankLoanKind.label(): Int = when (this) {
    BankLoanKind.MORTGAGE -> R.string.bank_kind_mortgage
    BankLoanKind.PERSONAL -> R.string.bank_kind_personal
    BankLoanKind.CAR -> R.string.bank_kind_car
    BankLoanKind.OTHER -> R.string.bank_kind_other
}

/** Futó hitel → kiadás színe (tartozás), kifizetett / lezárt → halvány. */
@Composable
internal fun bankAccent(item: BankLoanItemUi): Color =
    item.color?.takeIf { !item.isDone }?.let { Color(it) }
        ?: if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else CashTheme.colors.expense

@Composable
internal fun BankLoanBadge(item: BankLoanItemUi, size: androidx.compose.ui.unit.Dp = 40.dp) =
    CategoryBadge(item.icon ?: item.kind.iconKey(), bankAccent(item), item.name, size = size)

/** Lista sor: jelvény, név + bank, hátralék, haladás sáv és a következő részlet. */
@Composable
internal fun BankLoanRow(item: BankLoanItemUi, modifier: Modifier = Modifier) {
    val accent = bankAccent(item)
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankLoanBadge(item)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    item.bankName.ifBlank { stringResource(item.kind.label()) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    item.isClosed -> stringResource(R.string.loan_closed)
                    item.isPaidOff -> stringResource(R.string.bank_paid_off)
                    else -> stringResource(R.string.bank_remaining, item.outstanding)
                },
                color = accent,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LinearProgressIndicator(
                progress = { item.progress },
                color = if (item.isDone) MaterialTheme.colorScheme.onSurfaceVariant else CashTheme.colors.income,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
                gapSize = 0.dp,
                modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.bank_months_progress, item.paidMonths, item.totalMonths),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!item.isDone && item.nextDate != null && item.nextAmount != null) {
            Text(
                stringResource(R.string.bank_next, formatDate(item.nextDate, R.string.fmt_date), item.nextAmount),
                style = MaterialTheme.typography.bodySmall,
                color = if (item.dueSoon) CashTheme.colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (item.dueSoon) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
