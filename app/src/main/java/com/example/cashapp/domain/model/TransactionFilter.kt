package com.example.cashapp.domain.model

import java.time.YearMonth

/** A Tranzakciók lap szűrői. A `null` azt jelenti: „összes”. */
data class TransactionFilter(
    val month: YearMonth = YearMonth.now(),
    val type: TransactionType? = null,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val query: String = "",
) {
    /** Van-e a hónapon kívül bármilyen szűrő beállítva (a „Szűrők törlése” gombhoz). */
    val hasActiveFilters: Boolean
        get() = type != null || accountId != null || categoryId != null || query.isNotBlank()
}
