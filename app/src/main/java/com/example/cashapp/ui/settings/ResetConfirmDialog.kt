package com.example.cashapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cashapp.R

/** Második megerősítés: be kell gépelni egy szót, hogy véletlenül ne lehessen mindent törölni. */
@Composable
internal fun TypeToConfirmDialog(word: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    // Kis/nagybetű és ékezet nem számít („torles” = „TÖRLÉS”), hogy könnyű legyen begépelni.
    val matches = text.trim().withoutAccents().equals(word.withoutAccents(), ignoreCase = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.set_reset_type_word, word))
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = matches) {
                Text(stringResource(R.string.set_reset_all), color = if (matches) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

internal fun String.withoutAccents(): String =
    java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
