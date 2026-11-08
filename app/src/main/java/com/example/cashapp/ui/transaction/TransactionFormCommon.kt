package com.example.cashapp.ui.transaction

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun SectionLabel(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable
internal fun ErrorText(text: String) =
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
