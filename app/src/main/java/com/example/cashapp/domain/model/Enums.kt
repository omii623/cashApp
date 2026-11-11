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

/**
 * Magánkölcsön iránya. [BORROWED]: én tartozom valakinek; [LENT]: nekem tartoznak.
 * A Room név szerint tárolja – átnevezés migrációt igényel!
 */
enum class LoanDirection { BORROWED, LENT }

/**
 * Kölcsön nyitó adatából könyvelt tétel: a kölcsön összege ([AMOUNT]) vagy az abból
 * már visszafizetett rész ([REPAID]). A Room név szerint tárolja.
 */
enum class LoanOpeningPart { AMOUNT, REPAID }

/** Banki hitel fajtája (ikon és alapértelmezett név miatt). A Room név szerint tárolja. */
enum class BankLoanKind { MORTGAGE, PERSONAL, CAR, OTHER }

/** Banki hitel eseménye: előtörlesztés vagy kamatváltozás. A Room név szerint tárolja. */
enum class BankLoanEventType { PREPAYMENT, RATE_CHANGE }

/**
 * Előtörlesztés utáni folytatás: [LOWER_PAYMENT] azonos futamidő kisebb részlettel,
 * [SHORTER_TERM] azonos részlet rövidebb futamidővel. A Room név szerint tárolja.
 */
enum class PrepaymentMode { LOWER_PAYMENT, SHORTER_TERM }
