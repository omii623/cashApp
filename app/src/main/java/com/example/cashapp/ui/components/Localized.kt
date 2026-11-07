package com.example.cashapp.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.cashapp.R
import com.example.cashapp.domain.model.Currency
import java.time.temporal.TemporalAccessor
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Nyelvfüggő megjelenítés. Mindig az app aktuális nyelvét használja (LocalConfiguration),
 * nem a Locale.getDefault()-ot, ami az alkalmazásonkénti nyelvváltás után is a telefon nyelve maradhat.
 */

/** Az app aktuális nyelve. */
@Composable
fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Dátum / idő formázása. A minta a strings.xml-ből jön ([R.string.fmt_month_year] stb.),
 * mert a sorrend nyelvenként más („2026. november” vs. „November 2026”).
 */
@Composable
fun formatDate(value: TemporalAccessor, @StringRes pattern: Int): String =
    DateTimeFormatter.ofPattern(stringResource(pattern), appLocale()).format(value)

/**
 * Pénznem neve az app nyelvén (pl. „magyar forint” → „Hungarian Forint”).
 * ISO kódnál a rendszer fordítását használja, különben a tárolt nevet.
 */
@Composable
fun currencyDisplayName(currency: Currency): String {
    val locale = appLocale()
    return runCatching { java.util.Currency.getInstance(currency.code).getDisplayName(locale) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != currency.code }
        ?.replaceFirstChar { it.titlecase(locale) }
        ?: currency.name
}

/** Lenyíló listákhoz: „HUF – Magyar forint”. */
@Composable
fun currencyLabel(currency: Currency): String = "${currency.code} – ${currencyDisplayName(currency)}"
