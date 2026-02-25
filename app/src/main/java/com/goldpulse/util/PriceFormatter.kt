package com.goldpulse.util

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun formatPrice(value: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    return runCatching {
        NumberFormat.getCurrencyInstance(locale).apply {
            currency = Currency.getInstance(currencyCode)
            maximumFractionDigits = 2
        }.format(value)
    }.getOrDefault("$value $currencyCode")
}

fun percentChange(old: Double, current: Double): Double {
    if (old == 0.0) return 0.0
    return ((current - old) / old) * 100.0
}
