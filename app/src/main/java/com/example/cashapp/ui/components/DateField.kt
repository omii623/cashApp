package com.example.cashapp.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.cashapp.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Dátum mező naptárral (a mai napnál „(ma)” jelzéssel). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.form_date),
) {
    var open by remember { mutableStateOf(false) }
    val text = formatDate(date, R.string.fmt_date)
    SelectorField(
        label = label,
        value = if (date == LocalDate.now()) stringResource(R.string.form_today_suffix, text) else text,
        onClick = { open = true },
        modifier = modifier,
    )
    if (open) {
        DatePickerModal(
            initial = date,
            onConfirm = { onSelect(it); open = false },
            onDismiss = { open = false },
        )
    }
}

/** Naptáras dátumválasztó párbeszédablak (önállóan is használható, pl. halasztáshoz). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate, onConfirm: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // A DatePicker UTC éjfélben számol.
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(pickerState)
    }
}
