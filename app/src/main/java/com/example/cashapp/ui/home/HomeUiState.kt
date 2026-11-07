package com.example.cashapp.ui.home

import com.example.cashapp.ui.components.TransactionRowUi
import java.time.YearMonth

/** A főoldal teljes, már megformázott állapota – a Composable csak megjeleníti. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val month: YearMonth = YearMonth.now(),
    val baseCurrencyCode: String = "",
    val totalBalance: String = "",
    /** Igaz, ha az összegek csillagozva vannak. */
    val amountsHidden: Boolean = false,
    /** Pl. ["820 000 Ft", "650 €"] – a hős kártya alsó sora. */
    val balancesByCurrency: List<String> = emptyList(),
    /** Pénznemek, amelyekhez nincs árfolyam, ezért kimaradtak az összesből. */
    val missingRates: Set<String> = emptySet(),
    val monthIncome: String = "",
    val monthExpense: String = "",
    val monthNet: String = "",
    val monthNetNegative: Boolean = false,
    val pendingCount: Int = 0,
    val recent: List<TransactionRowUi> = emptyList(),
)
