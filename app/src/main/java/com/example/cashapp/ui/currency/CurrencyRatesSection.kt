package com.example.cashapp.ui.currency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.ui.components.formatDate
import java.time.Instant
import java.time.ZoneId

// ---------------------------------------------------------------- Árfolyamok

@Composable
internal fun RatesSection(state: CurrenciesUiState, onFetch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.cur_rates))
                Text(
                    state.lastFetchedAt?.let {
                        stringResource(R.string.ex_rate_fetched_at, formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()), R.string.fmt_date_time))
                    } ?: stringResource(R.string.ex_rate_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isFetching) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onFetch) { Icon(Icons.Filled.Refresh, stringResource(R.string.ex_rate_fetch)) }
            }
        }
        if (state.fetchFailed) {
            Text(stringResource(R.string.cur_fetch_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        SectionCard {
            state.rates.forEachIndexed { i, line ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CodeAvatar(line.code)
                    Spacer(Modifier.width(12.dp))
                    Text("1 ${line.leftCode}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        line.text?.let { "$it ${line.rightCode}" } ?: stringResource(R.string.cur_no_rate),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (line.text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
