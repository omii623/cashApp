package com.example.cashapp.ui.settings

import com.example.cashapp.ui.components.currencyLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.data.settings.AppLanguage
import com.example.cashapp.domain.model.ThemeMode
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onCurrencies: () -> Unit,
    onBackup: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onAccounts = onAccounts,
            onCategories = onCategories,
            onCurrencies = onCurrencies,
            onBackup = onBackup,
            onLanguage = viewModel::setLanguage,
            onTheme = viewModel::setThemeMode,
            onBaseCurrency = viewModel::setBaseCurrency,
            onHideAmounts = viewModel::setHideAmounts,
            onRecurringNotifications = viewModel::setRecurringNotifications,
            onResetAll = viewModel::resetAllData,
            onResetMessageShown = viewModel::resetMessageShown,
        ),
    )
}

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onAccounts: () -> Unit = {},
    val onCategories: () -> Unit = {},
    val onCurrencies: () -> Unit = {},
    val onBackup: () -> Unit = {},
    val onLanguage: (AppLanguage) -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onBaseCurrency: (String) -> Unit = {},
    val onHideAmounts: (Boolean) -> Unit = {},
    val onRecurringNotifications: (Boolean) -> Unit = {},
    val onResetAll: () -> Unit = {},
    val onResetMessageShown: () -> Unit = {},
)

/** A törlés két lépésben kér megerősítést. */
private enum class ResetStep { NONE, CONFIRM, TYPE_WORD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    val snackbar = remember { SnackbarHostState() }
    val resetDoneText = stringResource(R.string.set_reset_done)
    LaunchedEffect(state.resetDone) {
        if (state.resetDone) {
            snackbar.showSnackbar(resetDoneText)
            actions.onResetMessageShown()
        }
    }
    var resetStep by remember { mutableStateOf(ResetStep.NONE) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.set_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section(R.string.set_section_appearance) {
                DropdownRow(
                    label = stringResource(R.string.set_language),
                    value = languageLabel(state.language),
                    options = AppLanguage.entries,
                    optionLabel = { languageLabel(it) },
                    onSelect = actions.onLanguage,
                )
                Divider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.bodyLarge)
                    ThemeSelector(state.themeMode, actions.onTheme)
                }
                Divider()
                SwitchRow(stringResource(R.string.set_hide_amounts), stringResource(R.string.set_hide_amounts_hint), state.hideAmounts, actions.onHideAmounts)
            }

            Section(R.string.set_section_currency) {
                DropdownRow(
                    label = stringResource(R.string.set_base_currency),
                    value = state.baseCurrency,
                    options = state.currencies,
                    optionLabel = { currencyLabel(it) },
                    onSelect = { actions.onBaseCurrency(it.code) },
                    hint = stringResource(R.string.set_base_currency_hint),
                )
                Divider()
                LinkRow(stringResource(R.string.more_currencies), actions.onCurrencies)
            }

            Section(R.string.set_section_notifications) {
                SwitchRow(
                    stringResource(R.string.set_notify_recurring),
                    stringResource(R.string.set_notify_recurring_hint),
                    state.recurringNotifications,
                    actions.onRecurringNotifications,
                )
            }

            Section(R.string.set_section_features) {
                LinkRow(stringResource(R.string.more_accounts), actions.onAccounts)
                Divider()
                LinkRow(stringResource(R.string.more_categories), actions.onCategories)
            }

            Section(R.string.set_section_data) {
                LinkRow(stringResource(R.string.set_export_import), actions.onBackup)
                Divider()
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !state.isResetting) { resetStep = ResetStep.CONFIRM }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.set_reset_all),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.isResetting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                stringResource(R.string.set_version, appVersion()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    when (resetStep) {
        ResetStep.CONFIRM -> ConfirmDialog(
            title = stringResource(R.string.set_reset_title),
            text = stringResource(R.string.set_reset_text),
            confirmLabel = stringResource(R.string.set_reset_next),
            onConfirm = { resetStep = ResetStep.TYPE_WORD },
            onDismiss = { resetStep = ResetStep.NONE },
        )
        ResetStep.TYPE_WORD -> TypeToConfirmDialog(
            word = stringResource(R.string.set_reset_word),
            onConfirm = { resetStep = ResetStep.NONE; actions.onResetAll() },
            onDismiss = { resetStep = ResetStep.NONE },
        )
        ResetStep.NONE -> Unit
    }
}

@Composable
private fun appVersion(): String {
    val context = LocalContext.current
    return remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
}
