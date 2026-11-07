package com.example.cashapp.data.local.seed

import com.example.cashapp.data.local.entity.CurrencyEntity
import com.example.cashapp.domain.model.CategoryType

/** Első indításkor betöltött alapadatok (terv.md / promt.txt). Később minden módosítható. */
object DefaultData {

    val currencies = listOf(
        CurrencyEntity("HUF", "Magyar forint", "Ft", decimals = 0, sortOrder = 0),
        CurrencyEntity("RSD", "Szerb dinár", "din.", decimals = 0, sortOrder = 1),
        CurrencyEntity("EUR", "Euró", "€", decimals = 2, sortOrder = 2),
        CurrencyEntity("USD", "Amerikai dollár", "$", decimals = 2, sortOrder = 3),
    )

    const val BASE_CURRENCY = "HUF"

    data class SeedAccount(val name: String, val currencyCode: String, val icon: String, val color: Long)

    /** Pénznemenként egy készpénz-tárca, hogy a váltás azonnal használható legyen. */
    val accounts = listOf(
        SeedAccount("Készpénz", "HUF", "cash", 0xFF2E7D32),
        SeedAccount("Bankszámla", "HUF", "bank", 0xFF1565C0),
        SeedAccount("Készpénz EUR", "EUR", "cash", 0xFF29B6F6),
        SeedAccount("Készpénz RSD", "RSD", "cash", 0xFFAB47BC),
        SeedAccount("Készpénz USD", "USD", "cash", 0xFF66BB6A),
    )

    data class SeedCategory(
        val name: String,
        val type: CategoryType,
        val icon: String,
        val color: Long,
        val subcategories: List<String>,
    )

    val categories = listOf(
        // --- Bevétel ---
        SeedCategory("Fizetés", CategoryType.INCOME, "work", 0xFF2E7D32,
            listOf("Alapbér", "Bónusz", "13. havi fizetés", "Túlóra")),
        SeedCategory("Mellékjövedelem", CategoryType.INCOME, "side", 0xFF43A047,
            listOf("Freelance munka", "Bérbeadás", "Ösztöndíj")),
        SeedCategory("Egyéb bevétel", CategoryType.INCOME, "other", 0xFF66BB6A,
            listOf("Ajándék", "Visszatérítés", "Eladás", "Fuvar", "Egyéb")),

        // --- Kiadás ---
        SeedCategory("Lakhatás", CategoryType.EXPENSE, "home", 0xFF5C6BC0,
            listOf("Lakbér/albérlet", "Közös költség", "Felújítás", "Bútor")),
        SeedCategory("Számlák", CategoryType.EXPENSE, "bills", 0xFFFFA726,
            listOf("Áram", "Gáz", "Víz", "Internet", "Mobilszámla", "Előfizetések")),
        SeedCategory("Élelmiszer", CategoryType.EXPENSE, "food", 0xFFEF5350,
            listOf("Bevásárlás", "Étterem", "Rendelés", "Reggeli")),
        SeedCategory("Közlekedés", CategoryType.EXPENSE, "transport", 0xFF26A69A,
            listOf("Üzemanyag", "Tömegközlekedés", "Parkolás", "Taxi", "Autószerviz")),
        SeedCategory("Biztosítás", CategoryType.EXPENSE, "insurance", 0xFF8D6E63,
            listOf("Lakásbiztosítás", "Autóbiztosítás", "Életbiztosítás", "Utasbiztosítás")),
        SeedCategory("Egészségügy", CategoryType.EXPENSE, "health", 0xFFEC407A,
            listOf("Gyógyszer", "Orvosi költség", "Wellness/sport", "Szemüveg/fogászat")),
        SeedCategory("Szórakozás", CategoryType.EXPENSE, "fun", 0xFFAB47BC,
            listOf("Mozi/színház", "Kikapcsolódás", "Utazás/nyaralás", "Sörözés/étkezés")),
        SeedCategory("Ruházat", CategoryType.EXPENSE, "clothes", 0xFF7E57C2,
            listOf("Ruha", "Cipő", "Kiegészítő")),
        SeedCategory("Megtakarítás", CategoryType.EXPENSE, "savings", 0xFF29B6F6,
            listOf("Befektetés", "Vésztartalék", "Nyugdíjmegtakarítás")),
        SeedCategory("Hitel", CategoryType.EXPENSE, "loan", 0xFF78909C,
            listOf("Hiteltörlesztés", "Hitelkártya", "Diákhitel")),
        SeedCategory("Vásárlás", CategoryType.EXPENSE, "shopping", 0xFFFF7043,
            listOf("nasi+üdcsi", "online", "egyéb")),
        SeedCategory("Egyéb kiadás", CategoryType.EXPENSE, "other", 0xFF9E9E9E,
            listOf("Egyéb")),
        SeedCategory("Nyaralás", CategoryType.EXPENSE, "vacation", 0xFF00ACC1,
            emptyList()),
    )
}
