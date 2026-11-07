package com.example.cashapp.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.data.backup.BackupFormatException
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.formatDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRoute(
    onBack: () -> Unit,
    viewModel: BackupViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportJson)
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsv)
    }
    // Néhány fájlkezelő a .json-t nem application/json-ként adja → minden fájl választható, a tartalmat ellenőrizzük.
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::pickRestoreFile)
    }

    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let {
        stringResource(
            when (it) {
                BackupMessage.JSON_EXPORTED -> R.string.bk_msg_json_done
                BackupMessage.CSV_EXPORTED -> R.string.bk_msg_csv_done
                BackupMessage.RESTORED -> R.string.bk_msg_restored
                BackupMessage.EXPORT_FAILED -> R.string.bk_msg_export_failed
            }
        )
    }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.messageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.bk_title), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionCard(
                icon = Icons.Filled.Save,
                title = stringResource(R.string.bk_json_title),
                text = stringResource(R.string.bk_json_text),
                button = stringResource(R.string.bk_json_button),
                enabled = !state.isWorking,
                onClick = { exportJson.launch("cashapp-mentes-$today.json") },
            )
            ActionCard(
                icon = Icons.Filled.Restore,
                title = stringResource(R.string.bk_restore_title),
                text = stringResource(R.string.bk_restore_text),
                button = stringResource(R.string.bk_restore_button),
                enabled = !state.isWorking,
                danger = true,
                onClick = { pickBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
            )
            ActionCard(
                icon = Icons.Filled.TableChart,
                title = stringResource(R.string.bk_csv_title),
                text = stringResource(R.string.bk_csv_text),
                button = stringResource(R.string.bk_csv_button),
                enabled = !state.isWorking,
                onClick = { exportCsv.launch("cashapp-tetelek-$today.csv") },
            )
            if (state.isWorking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bk_working), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    state.pendingRestore?.let { s ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.bk_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.bk_confirm_text,
                        formatDate(Instant.ofEpochMilli(s.exportedAt).atZone(ZoneId.systemDefault()), R.string.fmt_date_time),
                        s.accounts, s.transactions, s.categories, s.rules,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmRestore) {
                    Text(stringResource(R.string.bk_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.error?.let { e ->
        AlertDialog(
            onDismissRequest = viewModel::errorShown,
            title = { Text(stringResource(R.string.bk_error_title)) },
            text = { Text(stringResource(R.string.bk_error_text, errorReason(e))) },
            confirmButton = { TextButton(onClick = viewModel::errorShown) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun errorReason(e: BackupError): String = stringResource(
    when (e) {
        BackupError.Unknown -> R.string.bk_err_unknown
        is BackupError.Format -> when (e.reason) {
            BackupFormatException.Reason.NOT_JSON -> R.string.bk_err_not_json
            BackupFormatException.Reason.NOT_CASHAPP -> R.string.bk_err_not_cashapp
            BackupFormatException.Reason.UNKNOWN_FORMAT -> R.string.bk_err_unknown_format
            BackupFormatException.Reason.NEWER_VERSION -> R.string.bk_err_newer_version
            BackupFormatException.Reason.CORRUPT -> R.string.bk_err_corrupt
            BackupFormatException.Reason.UNREADABLE -> R.string.bk_err_unreadable
            BackupFormatException.Reason.EMPTY -> R.string.bk_err_empty
            BackupFormatException.Reason.BROKEN_REFERENCE -> R.string.bk_err_broken_reference
        }
    }
)

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    text: String,
    button: String,
    enabled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (danger) {
                OutlinedButton(
                    onClick = onClick,
                    enabled = enabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(button) }
            } else {
                Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(button) }
            }
        }
    }
}
