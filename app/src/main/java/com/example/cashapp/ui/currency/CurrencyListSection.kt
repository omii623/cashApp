package com.example.cashapp.ui.currency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.data.repository.CurrencyRepository.Companion.MAX_CURRENCIES
import com.example.cashapp.data.repository.CurrencyRepository.Companion.RATE_BASE
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.components.currencyDisplayName

// ---------------------------------------------------------------- Pénznemek

@Composable
internal fun CurrencyListSection(
    state: CurrenciesUiState,
    onBase: (String) -> Unit,
    onRemove: (Currency) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.cur_list, state.currencies.size, MAX_CURRENCIES))
        SectionCard {
            state.currencies.forEachIndexed { i, cur ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val isBase = cur.code == state.baseCurrency
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !isBase) { onBase(cur.code) }
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeAvatar(cur.code)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currencyDisplayName(cur), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${cur.code} · ${cur.symbol} · " + stringResource(R.string.cur_decimals, cur.decimals),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isBase) {
                        Badge(stringResource(R.string.cur_base_badge))
                        Spacer(Modifier.width(12.dp))
                    } else if (cur.code != RATE_BASE) {
                        IconButton(onClick = { onRemove(cur) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.cur_list_hint, MAX_CURRENCIES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAdd, enabled = state.canAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cur_add))
        }
    }
}
