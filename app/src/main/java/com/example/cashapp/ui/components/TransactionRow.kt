package com.example.cashapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.domain.model.TransactionType
import com.example.cashapp.ui.theme.CashTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Egy tranzakció sor megformázott adatai. */
data class TransactionRowUi(
    val id: Long,
    val type: TransactionType,
    val date: LocalDate,
    val categoryName: String?,
    val subcategoryName: String?,
    val categoryColor: Long?,
    val categoryIcon: String?,
    val accountName: String,
    val fromCurrency: String,
    val toCurrency: String?,
    /** Pl. „1 € = 399 Ft” – csak váltásnál. */
    val rateText: String?,
    val amountText: String,
    /** Magánkölcsön mozgásánál: a kölcsön azonosítója és a másik fél neve. */
    val loanId: Long? = null,
    val loanName: String? = null,
    /** Igaz: új kölcsön (nő a tartozás); hamis: törlesztés. */
    val loanIncrease: Boolean = false,
    /** Banki hitel tételénél a hitel neve (kategória helyett). */
    val bankLoanName: String? = null,
)

/** Egy tranzakció sor (főoldal, később a tranzakciólista is ezt használja). */
@Composable
fun TransactionRow(item: TransactionRowUi, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val colors = CashTheme.colors
    val accent = when (item.type) {
        TransactionType.INCOME -> colors.income
        TransactionType.EXPENSE -> colors.expense
        TransactionType.EXCHANGE -> colors.exchange
    }
    val title = when {
        item.loanName != null ->
            stringResource(if (item.loanIncrease) R.string.loan_row_increase else R.string.loan_row_decrease, item.loanName)
        // Banki hitel tétele mindig törlesztés (csökken a tartozás) – mint a magánkölcsönnél.
        item.bankLoanName != null -> stringResource(R.string.loan_row_decrease, item.bankLoanName)
        else -> when (item.type) {
        TransactionType.EXCHANGE -> stringResource(R.string.tx_exchange_title, item.fromCurrency, item.toCurrency ?: "?")
        else -> listOfNotNull(item.categoryName, item.subcategoryName).joinToString(" · ")
            .ifEmpty { stringResource(R.string.tx_uncategorized) }
        }
    }
    val subtitle = listOfNotNull(relativeDate(item.date), item.rateText ?: item.accountName).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.type == TransactionType.EXCHANGE) {
            ExchangeBadge(accent)
        } else {
            CategoryBadge(item.categoryIcon, item.categoryColor?.let { Color(it) } ?: accent, item.categoryName ?: item.loanName ?: item.bankLoanName)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(item.amountText, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun relativeDate(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.tx_today)
        today.minusDays(1) -> stringResource(R.string.tx_yesterday)
        else -> formatDate(date, R.string.fmt_short_date)
    }
}
