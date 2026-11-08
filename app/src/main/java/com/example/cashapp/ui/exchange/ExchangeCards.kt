package com.example.cashapp.ui.exchange

import com.example.cashapp.ui.components.formatDate
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.components.DropdownSelector
import com.example.cashapp.ui.theme.CashTheme
import com.example.cashapp.ui.transaction.AccountOption
import java.time.Instant
import java.time.ZoneId

@Composable
internal fun AmountCard(
    label: String,
    account: AccountOption?,
    accounts: List<AccountOption>,
    currency: Currency?,
    amountText: String,
    amountColor: Color,
    error: Boolean,
    onAccount: (Long) -> Unit,
    onAmount: (String) -> Unit,
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            DropdownSelector(
                label = label,
                value = account?.let { "${it.name} · ${it.currencyCode}" } ?: "",
                options = accounts,
                optionLabel = { "${it.name} · ${it.currencyCode}" },
                onSelect = { onAccount(it.id) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = amountText,
                onValueChange = onAmount,
                placeholder = { Text("0", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.End, color = amountColor),
                suffix = { Text(currency?.symbol ?: "", style = MaterialTheme.typography.titleLarge) },
                isError = error,
                supportingText = if (error) ({ Text(stringResource(R.string.ex_amount_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun RateCard(state: ExchangeUiState, rate: ExchangeRateField, actions: ExchangeActions) {
    val accent = CashTheme.colors.exchange
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.ex_rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (rate.isManual) {
                    Surface(shape = RoundedCornerShape(50), color = CashTheme.colors.warningContainer) {
                        Text(
                            stringResource(R.string.ex_rate_manual),
                            color = CashTheme.colors.warning,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                        )
                    }
                    if (rate.storedText != null) {
                        IconButton(onClick = actions.onRateReset, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Restore, stringResource(R.string.ex_rate_reset))
                        }
                    }
                }
            }
            OutlinedTextField(
                value = rate.text,
                onValueChange = actions.onRate,
                prefix = { Text("1 ${rate.leftCode} = ") },
                suffix = { Text(rate.rightCode) },
                isError = rate.error,
                supportingText = if (rate.error) ({ Text(stringResource(R.string.ex_rate_error)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            val info = buildList {
                add(rate.storedText?.let { stringResource(R.string.ex_rate_stored, it) } ?: stringResource(R.string.ex_rate_none))
                state.lastFetchedAt?.let { add(stringResource(R.string.ex_rate_fetched_at, formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()), R.string.fmt_date_time))) }
            }.joinToString(" · ")
            Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.fetchFailed) {
                Text(stringResource(R.string.ex_rate_fetch_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FilledTonalButton(
                onClick = actions.onFetch,
                enabled = !state.isFetching,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.12f), contentColor = accent),
            ) {
                if (state.isFetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = accent)
                else Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ex_rate_fetch))
            }
        }
    }
}
