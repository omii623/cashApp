package com.example.cashapp.ui.currency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.components.appLocale

/** Hozzáadható pénznemek a rendszer ISO listájából, kereséssel. */
@Composable
internal fun AddCurrencyDialog(existing: Set<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = appLocale()
    val all = remember(locale, existing) {
        java.util.Currency.getAvailableCurrencies()
            .filter { it.currencyCode !in existing && isCurrentCurrency(it) }
            .map { it.currencyCode to it.getDisplayName(locale).replaceFirstChar { ch -> ch.titlecase(locale) } }
            .sortedBy { it.first }
    }
    var query by remember { mutableStateOf("") }
    val filtered = all.filter { (code, name) -> query.isBlank() || code.contains(query.trim(), true) || name.contains(query.trim(), true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cur_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.cur_add_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(filtered, key = { it.first }) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(code) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(code, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
                            Text(name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Megszűnt pénznemek, amelyek nevében nincs évszám (főleg az euró előtti nemzeti pénzek). */
internal val REPLACED_CODES = setOf(
    "ADP", "ATS", "BEF", "CYP", "DEM", "EEK", "ESP", "FIM", "FRF", "GRD", "HRK", "IEP", "ITL", "LTL",
    "LUF", "LVL", "MTL", "NLG", "PTE", "SIT", "SKK", "VAL", "BYB", "CSD", "CUC", "SLL", "VEF", "ZWL",
)

internal val HISTORIC_NAME = Regex("""\(\d{4}""")

/**
 * Csak élő, valódi pénznemek: a megszűntek angol neve évszámot tartalmaz (pl. „Afghan Afghani (1927–2002)”),
 * az X-kódok pedig nem országos pénznemek (arany, SDR, teszt kód stb.).
 */
internal fun isCurrentCurrency(c: java.util.Currency): Boolean =
    c.defaultFractionDigits >= 0 &&
        !c.currencyCode.startsWith("X") &&
        c.currencyCode !in REPLACED_CODES &&
        !HISTORIC_NAME.containsMatchIn(c.getDisplayName(java.util.Locale.ENGLISH))
