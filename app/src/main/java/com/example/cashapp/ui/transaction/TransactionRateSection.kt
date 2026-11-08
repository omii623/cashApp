package com.example.cashapp.ui.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.ui.theme.CashTheme

@Composable
internal fun RateSection(rate: RateField, onRateChange: (String) -> Unit, onRateReset: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, CashTheme.colors.exchange),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.form_rate),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!rate.isDefault && !rate.noStoredRate) {
                    Text(
                        stringResource(R.string.form_rate_manual),
                        color = CashTheme.colors.warning,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onRateReset) {
                        Icon(Icons.Filled.Restore, stringResource(R.string.form_rate_reset))
                    }
                }
            }
            OutlinedTextField(
                value = rate.text,
                onValueChange = onRateChange,
                prefix = { Text("1 ${rate.leftCode} = ") },
                suffix = { Text(rate.rightCode) },
                singleLine = true,
                isError = rate.error,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                rate.error -> ErrorText(stringResource(R.string.form_rate_error))
                rate.noStoredRate && rate.text.isEmpty() ->
                    Text(stringResource(R.string.form_rate_missing), color = CashTheme.colors.warning, style = MaterialTheme.typography.bodySmall)
            }
            rate.accountAmountPreview?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
