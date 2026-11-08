package com.example.cashapp.ui.currency

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog

@Composable
fun CurrenciesRoute(
    onBack: () -> Unit,
    viewModel: CurrenciesViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CurrenciesScreen(
        state = state,
        actions = CurrenciesActions(
            onBack = onBack,
            onAmount = viewModel::onAmountChange,
            onFromCurrency = viewModel::onFromCurrency,
            onBase = viewModel::setBaseCurrency,
            onFetch = viewModel::fetchRates,
            onAdd = viewModel::addCurrency,
            onRemove = viewModel::removeCurrency,
            onMessageShown = viewModel::messageShown,
        ),
    )
}

data class CurrenciesActions(
    val onBack: () -> Unit = {},
    val onAmount: (String) -> Unit = {},
    val onFromCurrency: (String) -> Unit = {},
    val onBase: (String) -> Unit = {},
    val onFetch: () -> Unit = {},
    val onAdd: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrenciesScreen(state: CurrenciesUiState, actions: CurrenciesActions) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Currency?>(null) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.more_currencies), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConverterCard(state, actions)
            RatesSection(state, actions.onFetch)
            CurrencyListSection(state, onBase = actions.onBase, onRemove = { removing = it }, onAdd = { showAdd = true })
        }
    }

    if (showAdd) {
        AddCurrencyDialog(
            existing = state.currencies.map { it.code }.toSet(),
            onSelect = { showAdd = false; actions.onAdd(it) },
            onDismiss = { showAdd = false },
        )
    }
    removing?.let { c ->
        ConfirmDialog(
            title = stringResource(R.string.cur_remove_title, c.code),
            text = stringResource(R.string.cur_remove_text),
            onConfirm = { removing = null; actions.onRemove(c.code) },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun messageText(message: CurrencyMessage): String = stringResource(
    when (message) {
        CurrencyMessage.ADDED -> R.string.cur_msg_added
        CurrencyMessage.ADD_FAILED -> R.string.cur_msg_add_failed
        CurrencyMessage.REMOVED -> R.string.cur_msg_removed
        CurrencyMessage.IN_USE -> R.string.cur_msg_in_use
        CurrencyMessage.PROTECTED -> R.string.cur_msg_protected
    }
)
