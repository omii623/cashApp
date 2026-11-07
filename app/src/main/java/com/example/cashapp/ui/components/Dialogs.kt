package com.example.cashapp.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.example.cashapp.R

/** Egy szövegmezős párbeszédablak (új név / átnevezés). */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String = "",
    confirmLabel: String = stringResource(R.string.save),
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Megerősítő párbeszédablak veszélyes műveletekhez (törlés). */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Sikertelen mentés: az űrlap nyitva marad, a beírt adatok megmaradnak. */
@Composable
fun SaveErrorDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.save_failed_title)) },
        text = { Text(stringResource(R.string.save_failed_text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}
