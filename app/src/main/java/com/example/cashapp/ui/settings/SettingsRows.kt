package com.example.cashapp.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cashapp.R
import com.example.cashapp.data.settings.AppLanguage
import com.example.cashapp.domain.model.ThemeMode

@Composable
internal fun languageLabel(lang: AppLanguage): String = when (lang) {
    AppLanguage.SYSTEM -> stringResource(R.string.set_language_system)
    // A nyelvek a saját nevükön szerepelnek, hogy idegen nyelvű felületen is megtalálhatók legyenek.
    AppLanguage.HUNGARIAN -> "Magyar"
    AppLanguage.ENGLISH -> "English"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val options = listOf(
        ThemeMode.LIGHT to R.string.set_theme_light,
        ThemeMode.DARK to R.string.set_theme_dark,
        ThemeMode.SYSTEM to R.string.set_theme_system,
    )
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(stringResource(label), maxLines = 1) }
        }
    }
}

@Composable
internal fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
internal fun Divider() = HorizontalDivider(Modifier.padding(horizontal = 16.dp))

@Composable
internal fun <T> DropdownRow(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    hint: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text("$value ▾", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyLarge)
        }
        // A menü a jobb oldali érték alá nyíljon (ott van, amire a felhasználó koppint).
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp)) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { expanded = false; onSelect(option) })
                }
            }
        }
    }
}

@Composable
internal fun SwitchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** [onClick] = null → még nem elérhető funkció (halványan, „Hamarosan…” felirattal). */
@Composable
internal fun LinkRow(label: String, onClick: (() -> Unit)?) {
    val enabled = onClick != null
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick?.invoke() }.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (enabled) label else "$label – ${stringResource(R.string.coming_soon)}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.weight(1f),
        )
        if (enabled) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}
