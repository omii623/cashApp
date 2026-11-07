package com.example.cashapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.domain.model.CategoryIcon

/**
 * Ikon vagy betű választó (kategóriákhoz és számlákhoz):
 * nagy előnézet + Ikon/Betű váltó + ikonrács vagy betűmező.
 *
 * @param icon tárolt formában (lásd [CategoryIcon])
 * @param name a név – betű módban ennek első betűje az alapértelmezés
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IconPicker(icon: String, color: Color, name: String, onSelect: (CategoryIcon) -> Unit) {
    val current = CategoryIcon.parse(icon)
    var letterMode by rememberSaveable { mutableStateOf(current is CategoryIcon.Letter) }
    var letterText by rememberSaveable { mutableStateOf((current as? CategoryIcon.Letter)?.text ?: "") }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        CategoryBadge(icon, color, name, size = 56.dp)
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            SegmentedButton(
                selected = !letterMode,
                onClick = { letterMode = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text(stringResource(R.string.cat_icon_mode_icon)) }
            SegmentedButton(
                selected = letterMode,
                onClick = {
                    letterMode = true
                    // Átváltáskor azonnal betű legyen: a beírt, vagy a név első betűje.
                    val text = letterText.ifBlank { CategoryIcon.normalizeLetters(name.take(1)) }
                    if (text.isNotBlank()) {
                        letterText = text
                        onSelect(CategoryIcon.Letter(text))
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text(stringResource(R.string.cat_icon_mode_letter)) }
        }
    }

    if (letterMode) {
        OutlinedTextField(
            value = letterText,
            onValueChange = { input ->
                letterText = CategoryIcon.normalizeLetters(input)
                if (letterText.isNotBlank()) onSelect(CategoryIcon.Letter(letterText))
            },
            label = { Text(stringResource(R.string.cat_icon_letter_label)) },
            supportingText = { Text(stringResource(R.string.cat_icon_letter_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryIcons.all.forEach { (key, vector) ->
                val selected = (current as? CategoryIcon.Vector)?.key == key
                Surface(
                    onClick = { onSelect(CategoryIcon.Vector(key)) },
                    shape = CircleShape,
                    color = if (selected) color else color.copy(alpha = 0.12f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(vector, contentDescription = key, tint = if (selected) Color.White else color, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
