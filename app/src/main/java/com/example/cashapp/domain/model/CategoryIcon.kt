package com.example.cashapp.domain.model

/**
 * Kategória jelölése: egy beépített ikon vagy 1–2 betű/karakter (akár emoji).
 *
 * Tárolás a `categories.icon` oszlopban szövegként:
 * - ikon: a kulcsa, pl. `food` (a régi / seed értékek is így vannak)
 * - betű: `letter:` előtaggal, pl. `letter:É`
 */
sealed interface CategoryIcon {
    data class Vector(val key: String) : CategoryIcon
    data class Letter(val text: String) : CategoryIcon

    fun encode(): String = when (this) {
        is Vector -> key
        is Letter -> LETTER_PREFIX + text
    }

    companion object {
        private const val LETTER_PREFIX = "letter:"
        const val MAX_LETTERS = 2

        fun parse(raw: String?): CategoryIcon? = when {
            raw.isNullOrBlank() -> null
            raw.startsWith(LETTER_PREFIX) -> raw.removePrefix(LETTER_PREFIX).takeIf { it.isNotBlank() }?.let(::Letter)
            else -> Vector(raw)
        }

        /** Beírt szöveg → legfeljebb [MAX_LETTERS] karakter (az emojit nem vágja ketté). */
        fun normalizeLetters(input: String): String {
            val trimmed = input.trim()
            val sb = StringBuilder()
            var count = 0
            var i = 0
            while (i < trimmed.length && count < MAX_LETTERS) {
                val cp = trimmed.codePointAt(i)
                sb.appendCodePoint(cp)
                i += Character.charCount(cp)
                count++
            }
            return sb.toString().uppercase()
        }
    }
}
