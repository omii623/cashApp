package com.example.cashapp.data.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Az app nyelve. [tag] = BCP-47 nyelvkód; üres = a telefon nyelvét követi. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    HUNGARIAN("hu"),
    ENGLISH("en"),
}

/**
 * Alkalmazásonkénti nyelv. A választást maga az Android tárolja (13+ rendszerbeállítás,
 * alatta az AppCompat szolgáltatása), ezért nem kell DataStore-ba menteni.
 * Váltáskor az Activity újraindul, és minden szöveg az új nyelven jelenik meg.
 */
object LanguageManager {

    fun current(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tag.isEmpty()) return AppLanguage.SYSTEM
        return AppLanguage.entries.firstOrNull { it.tag.isNotEmpty() && tag.startsWith(it.tag) } ?: AppLanguage.SYSTEM
    }

    fun set(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(language.tag)
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
