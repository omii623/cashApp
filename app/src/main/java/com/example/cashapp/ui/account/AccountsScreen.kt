package com.example.cashapp.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cashapp.R
import com.example.cashapp.ui.AppViewModelProvider
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.theme.CashTheme

@Composable
fun AccountsRoute(
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    viewModel: AccountsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsScreen(state, onBack, onOpen, onNew, viewModel::move, viewModel::restore)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    state: AccountsUiState,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
    onMove: (Long, Int) -> Unit,
    onRestore: (Long) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.acc_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringResource(R.string.acc_new)) },
                containerColor = CashTheme.colors.hero,
                contentColor = Color.White,
            )
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.active.isEmpty() && state.archived.isEmpty() -> Text(
                stringResource(R.string.acc_empty),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(padding).padding(32.dp),
            )
            else -> LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.active, key = { _, it -> it.id }) { index, item ->
                    AccountCard(
                        item = item,
                        onClick = { onOpen(item.id) },
                        modifier = Modifier.animateItem(),
                        leading = {
                            Column {
                                IconButton(onClick = { onMove(item.id, -1) }, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.cat_move_up))
                                }
                                IconButton(
                                    onClick = { onMove(item.id, 1) },
                                    enabled = index < state.active.lastIndex,
                                    modifier = Modifier.size(28.dp),
                                ) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.cat_move_down)) }
                            }
                        },
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                    )
                }
                if (state.archived.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.acc_archived_section),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(state.archived, key = { it.id }) { item ->
                        AccountCard(
                            item = item,
                            onClick = { onOpen(item.id) },
                            modifier = Modifier.animateItem().alpha(0.6f),
                            trailing = { TextButton(onClick = { onRestore(item.id) }) { Text(stringResource(R.string.acc_restore)) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccountCard(
    item: AccountItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Spacer(Modifier.width(4.dp))
            CategoryBadge(item.icon, Color(item.color), item.name, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(item.currencyCode, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                item.balance,
                fontWeight = FontWeight.Bold,
                color = if (item.isNegative) CashTheme.colors.expense else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(4.dp))
            trailing()
        }
    }
}
