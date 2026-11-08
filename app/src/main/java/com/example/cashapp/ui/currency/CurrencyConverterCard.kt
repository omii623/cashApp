package com.example.cashapp.ui.currency

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.cashapp.ui.components.currencyDisplayName
import com.example.cashapp.ui.theme.CashTheme

// ---------------------------------------------------------------- Átváltó

@Composable
internal fun ConverterCard(state: CurrenciesUiState, actions: CurrenciesActions) {
    val c = CashTheme.colors
    Surface(color = c.hero, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cur_converter), color = c.onHeroMuted, style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = state.amountText,
                    onValueChange = actions.onAmount,
                    placeholder = { Text("0", color = c.onHeroMuted, style = MaterialTheme.typography.headlineMedium) },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = c.onHero, fontWeight = FontWeight.Bold),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = c.onHero,
                        unfocusedIndicatorColor = c.onHeroMuted,
                        cursorColor = c.onHero,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                CurrencyPicker(state.fromCode, state.currencies, actions.onFromCurrency)
            }
            if (state.converted.isEmpty()) {
                Text(stringResource(R.string.cur_converter_hint), color = c.onHeroMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                state.converted.forEach { line ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(line.currency.code, color = c.onHeroMuted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(48.dp))
                        Text(
                            line.text ?: stringResource(R.string.cur_no_rate),
                            color = if (line.text != null) c.onHero else c.onHeroMuted,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun CurrencyPicker(selected: String, currencies: List<Currency>, onSelect: (String) -> Unit) {
    val c = CashTheme.colors
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            color = c.onHero.copy(alpha = 0.15f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.clickable { open = true },
        ) {
            Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(selected, color = c.onHero, fontWeight = FontWeight.Bold)
                Icon(Icons.Filled.ArrowDropDown, null, tint = c.onHero)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            currencies.forEach { cur ->
                DropdownMenuItem(
                    text = { Text("${cur.code} – ${currencyDisplayName(cur)}") },
                    onClick = { open = false; onSelect(cur.code) },
                )
            }
        }
    }
}
