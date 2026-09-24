package com.example.halauncher.util

import com.example.halauncher.config.HomeScreenConfig
import java.util.Currency
import java.util.Locale

/**
 * The currency symbol for [locale]'s country ("₹" in India, "$" in the US, "€" in Germany),
 * or [HomeScreenConfig.MONEY_SYMBOL_FALLBACK] when the locale has no country or its symbol is
 * a multi-letter code (e.g. "US$", "CHF") that wouldn't fit a small button.
 */
fun localCurrencySymbol(locale: Locale = Locale.getDefault()): String {
    val symbol = try {
        Currency.getInstance(locale).getSymbol(locale)
    } catch (e: IllegalArgumentException) {
        null
    }
    return symbol?.takeIf { it.length <= 2 } ?: HomeScreenConfig.MONEY_SYMBOL_FALLBACK
}
