package com.example.cashapp.ui.currency

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.data.repository.CurrencyRepository.Companion.MAX_CURRENCIES
import com.example.cashapp.data.repository.CurrencyRepository.Companion.RATE_BASE
import com.example.cashapp.domain.model.Currency
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.ConfirmDialog
import com.example.cashapp.ui.components.appLocale
import com.example.cashapp.ui.components.currencyDisplayName
import com.example.cashapp.ui.components.formatDate
import com.example.cashapp.ui.theme.CashTheme
import java.time.Instant
import java.time.ZoneId

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

// ---------------------------------------------------------------- Átváltó

@Composable
private fun ConverterCard(state: CurrenciesUiState, actions: CurrenciesActions) {
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
private fun CurrencyPicker(selected: String, currencies: List<Currency>, onSelect: (String) -> Unit) {
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

// ---------------------------------------------------------------- Árfolyamok

@Composable
private fun RatesSection(state: CurrenciesUiState, onFetch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle(stringResource(R.string.cur_rates))
                Text(
                    state.lastFetchedAt?.let {
                        stringResource(R.string.ex_rate_fetched_at, formatDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()), R.string.fmt_date_time))
                    } ?: stringResource(R.string.ex_rate_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isFetching) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onFetch) { Icon(Icons.Filled.Refresh, stringResource(R.string.ex_rate_fetch)) }
            }
        }
        if (state.fetchFailed) {
            Text(stringResource(R.string.cur_fetch_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        SectionCard {
            state.rates.forEachIndexed { i, line ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CodeAvatar(line.code)
                    Spacer(Modifier.width(12.dp))
                    Text("1 ${line.leftCode}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        line.text?.let { "$it ${line.rightCode}" } ?: stringResource(R.string.cur_no_rate),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (line.text != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Pénznemek

@Composable
private fun CurrencyListSection(
    state: CurrenciesUiState,
    onBase: (String) -> Unit,
    onRemove: (Currency) -> Unit,
    onAdd: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle(stringResource(R.string.cur_list, state.currencies.size, MAX_CURRENCIES))
        SectionCard {
            state.currencies.forEachIndexed { i, cur ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                val isBase = cur.code == state.baseCurrency
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = !isBase) { onBase(cur.code) }
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CodeAvatar(cur.code)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(currencyDisplayName(cur), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${cur.code} · ${cur.symbol} · " + stringResource(R.string.cur_decimals, cur.decimals),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isBase) {
                        Badge(stringResource(R.string.cur_base_badge))
                        Spacer(Modifier.width(12.dp))
                    } else if (cur.code != RATE_BASE) {
                        IconButton(onClick = { onRemove(cur) }) {
                            Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.cur_list_hint, MAX_CURRENCIES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAdd, enabled = state.canAdd, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cur_add))
        }
    }
}

/** Hozzáadható pénznemek a rendszer ISO listájából, kereséssel. */
@Composable
private fun AddCurrencyDialog(existing: Set<String>, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
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
private val REPLACED_CODES = setOf(
    "ADP", "ATS", "BEF", "CYP", "DEM", "EEK", "ESP", "FIM", "FRF", "GRD", "HRK", "IEP", "ITL", "LTL",
    "LUF", "LVL", "MTL", "NLG", "PTE", "SIT", "SKK", "VAL", "BYB", "CSD", "CUC", "SLL", "VEF", "ZWL",
)

private val HISTORIC_NAME = Regex("""\(\d{4}""")

/**
 * Csak élő, valódi pénznemek: a megszűntek angol neve évszámot tartalmaz (pl. „Afghan Afghani (1927–2002)”),
 * az X-kódok pedig nem országos pénznemek (arany, SDR, teszt kód stb.).
 */
internal fun isCurrentCurrency(c: java.util.Currency): Boolean =
    c.defaultFractionDigits >= 0 &&
        !c.currencyCode.startsWith("X") &&
        c.currencyCode !in REPLACED_CODES &&
        !HISTORIC_NAME.containsMatchIn(c.getDisplayName(java.util.Locale.ENGLISH))

// ---------------------------------------------------------------- közös elemek

@Composable
private fun SectionTitle(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.primary,
)

@Composable
private fun SectionCard(content: @Composable () -> Unit) = Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = RoundedCornerShape(12.dp),
) { content() }

@Composable
private fun CodeAvatar(code: String) = Box(
    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    contentAlignment = Alignment.Center,
) {
    Text(code, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
}

@Composable
private fun Badge(text: String) = Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
