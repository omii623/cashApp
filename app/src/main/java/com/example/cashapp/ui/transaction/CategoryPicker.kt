package com.example.cashapp.ui.transaction

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.cashapp.ui.components.CategoryBadge
import com.example.cashapp.ui.components.CategorySymbol

/** 3 oszlopos kategória-rács (a látványterv csempéi). */
@Composable
fun CategoryGrid(
    categories: List<CategoryOption>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cat ->
                    val selected = cat.id == selectedId
                    Surface(
                        onClick = { onSelect(cat.id) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) accent else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        border = if (selected) null else BorderStroke(1.dp, Color(cat.color).copy(alpha = 0.5f)),
                        modifier = Modifier.weight(1f).height(72.dp),
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        ) {
                            // Kijelölve fehér jel a színes csempén, különben a kategória színes köre.
                            if (selected) CategorySymbol(cat.icon, Color.White, cat.name, size = 28.dp)
                            else CategoryBadge(cat.icon, Color(cat.color), cat.name, size = 28.dp)
                            Text(
                                cat.name,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
                // Hiányos utolsó sor kitöltése, hogy a csempék egyforma szélesek maradjanak.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SubcategoryChips(
    items: List<SubcategoryOption>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { sub ->
            FilterChip(
                selected = sub.id == selectedId,
                onClick = { onSelect(sub.id) },
                label = { Text(sub.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.18f),
                    selectedLabelColor = accent,
                ),
            )
        }
    }
}
