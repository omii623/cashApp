package com.example.cashapp.domain.model

/** Tranzakció típusa. A Room név szerint tárolja – átnevezés migrációt igényel! */
enum class TransactionType { INCOME, EXPENSE, EXCHANGE }

/** Kategória típusa (váltásnak nincs kategóriája). */
enum class CategoryType { INCOME, EXPENSE }

/** Honnan származik egy árfolyam. */
enum class RateSource { API, MANUAL }

/** Ismétlődő szabály gyakorisága. */
enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/** Esedékes ismétlődő tétel állapota. */
enum class PendingStatus { PENDING, APPROVED, SKIPPED }

/** Megjelenési téma. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
