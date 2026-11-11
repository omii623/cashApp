package com.example.cashapp.ui.loan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.ui.more.MenuCard
import com.example.cashapp.ui.more.MoreEntry

/** Továbbiak → Kölcsönök: magánkölcsönök, hitelkalkulátor és (még nem elérhető, szürke) banki kölcsönök. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoanHubScreen(onBack: () -> Unit, onPrivateLoans: () -> Unit, onCalculator: () -> Unit) {
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(stringResource(R.string.more_loans_hub), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            MenuCard(
                listOf(
                    MoreEntry(R.string.more_loans, Icons.Filled.Handshake, onPrivateLoans),
                    // Banki kölcsönök: még nincs kész → szürke, nem nyitható.
                    MoreEntry(R.string.loan_hub_bank, Icons.Filled.AccountBalance, null),
                    MoreEntry(R.string.loan_hub_calculator, Icons.Filled.Calculate, onCalculator),
                )
            )
        }
    }
}
