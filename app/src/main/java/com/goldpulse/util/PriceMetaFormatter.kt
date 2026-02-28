package com.goldpulse.util

import com.goldpulse.data.model.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatPriceType(value: String?): String = when (value?.lowercase()) {
    "midpoint" -> "midpoint"
    else -> "last"
}

fun formatPriceTimestamp(epochSeconds: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(epochSeconds * 1000))
}

fun PricePoint.metaLine(): String {
    return "${sourceLabel ?: "unknown"} • ${formatPriceType(priceType)} • ${formatPriceTimestamp(timestamp)}"
}
