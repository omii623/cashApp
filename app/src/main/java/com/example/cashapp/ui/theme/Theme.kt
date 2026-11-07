package com.example.cashapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.cashapp.domain.model.ThemeMode

/** Az app saját jelentéssel bíró színei (bevétel/kiadás/váltás/figyelmeztetés). */
@Immutable
data class CashColors(
    val income: Color,
    val expense: Color,
    val exchange: Color,
    val warning: Color,
    val warningContainer: Color,
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    /**
     * Diagramokhoz (bevétel vs. kiadás oszlopok). Színtévesztő-biztos pár – a dataviz validátorral
     * ellenőrizve (CVD ΔE ≥ 11, világos és sötét felületen is). A sima zöld/piros pár itt elbukik.
     */
    val chartIncome: Color,
    val chartExpense: Color,
    /** Az „Egyéb” szelet színe. */
    val chartNeutral: Color,
)

private val LightCashColors = CashColors(
    income = Color(0xFF2E7D32),
    expense = Color(0xFFC62828),
    exchange = Color(0xFF1565C0),
    warning = Color(0xFFE65100),
    warningContainer = Color(0xFFFFF3E0),
    hero = Color(0xFF1E3A5F),
    onHero = Color.White,
    onHeroMuted = Color(0xFFB8C7DD),
    chartIncome = Color(0xFF00897B),
    chartExpense = Color(0xFFD84315),
    chartNeutral = Color(0xFFB0B4BA),
)

private val DarkCashColors = CashColors(
    income = Color(0xFF66BB6A),
    expense = Color(0xFFEF5350),
    exchange = Color(0xFF42A5F5),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0xFF3E2A12),
    hero = Color(0xFF243B5A),
    onHero = Color.White,
    onHeroMuted = Color(0xFFB8C7DD),
    chartIncome = Color(0xFF26A69A),
    chartExpense = Color(0xFFF4511E),
    chartNeutral = Color(0xFF5F6368),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E3A5F),
    onPrimary = Color.White,
    secondary = Color(0xFF1565C0),
    background = Color(0xFFF5F6FA),
    surface = Color(0xFFF5F6FA),
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    onSurfaceVariant = Color(0xFF757575),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF90B4E0),
    onPrimary = Color(0xFF0D1B2E),
    secondary = Color(0xFF42A5F5),
    background = Color(0xFF121418),
    surface = Color(0xFF121418),
    surfaceContainer = Color(0xFF1E2128),
    surfaceContainerLow = Color(0xFF1E2128),
)

val LocalCashColors = staticCompositionLocalOf { LightCashColors }

/** Rövid elérés: `CashTheme.colors.income` */
object CashTheme {
    val colors: CashColors
        @Composable get() = LocalCashColors.current
}

@Composable
fun CashAppTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalCashColors provides if (dark) DarkCashColors else LightCashColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            content = content,
        )
    }
}
