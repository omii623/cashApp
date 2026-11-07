package com.example.cashapp.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.cashapp.R

/** Ideiglenes képernyő a még el nem készült oldalakhoz. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)? = null) {
    // A külső (CashApp) Scaffold már kezeli a rendszersávokat, itt nem kell újra.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0),
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
