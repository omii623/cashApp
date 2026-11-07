package com.example.cashapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.cashapp.ui.category.CategoryPalette

/** Színválasztó körök (kategóriákhoz és számlákhoz). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Long, onSelect: (Long) -> Unit, colors: List<Long> = CategoryPalette.colors) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            val isSelected = color == selected
            Box(
                Modifier
                    .size(36.dp)
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                    )
                    .padding(4.dp)
                    .background(Color(color), CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}
