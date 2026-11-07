package com.example.cashapp.domain.model

/**
 * Pénznem. Az összegeket mindenhol a pénznem legkisebb egységében (`Long`) tároljuk,
 * a [decimals] adja meg, hány tizedesjegy van (pl. EUR: 2 → 250,63 € = 25063).
 */
data class Currency(
    val code: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
)
