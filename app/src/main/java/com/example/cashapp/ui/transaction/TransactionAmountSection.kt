package com.example.cashapp.ui.transaction

import com.example.cashapp.ui.components.currencyLabel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.cashapp.R

@Composable
internal fun AmountSection(
    state: TransactionFormUiState,
    onAmountChange: (String) -> Unit,
    onCurrencySelect: (String) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        TextField(
            value = state.amountText,
            onValueChange = onAmountChange,
            placeholder = {
                Text(
                    stringResource(R.string.form_amount_hint),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            textStyle = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            suffix = { Text(state.currency?.symbol ?: "", style = MaterialTheme.typography.headlineSmall) },
            singleLine = true,
            isError = state.amountError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        if (state.amountError) ErrorText(stringResource(R.string.form_amount_error))
        CurrencyChip(state, onCurrencySelect)
    }
}

@Composable
internal fun CurrencyChip(state: TransactionFormUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(top = 8.dp)) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Text("${state.currency?.code ?: ""} ▾", Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.currencies.forEach { c ->
                DropdownMenuItem(
                    text = { Text(currencyLabel(c)) },
                    onClick = { expanded = false; onSelect(c.code) },
                )
            }
        }
    }
}
