package com.amir.expense.data

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

private val INDIA: Locale = Locale.Builder().setLanguage("en").setRegion("IN").build()

/** 25050 -> "₹250.50", 12500000 -> "₹1,25,000". Display only; math stays in paise. */
fun formatRupees(paise: Long): String {
    val digits = if (paise % 100 == 0L) 0 else 2
    return NumberFormat.getCurrencyInstance(INDIA)
        .apply { minimumFractionDigits = digits; maximumFractionDigits = digits }
        .format(BigDecimal.valueOf(paise, 2))
}

/** "1,250.50" -> 125050. Null for blank/garbage. */
fun parseRupees(text: String): Long? =
    text.replace(",", "").trim().toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, java.math.RoundingMode.HALF_UP)?.toLong()

/** Normalised merchant name used as the auto-categorize rule key. */
fun merchantKey(merchant: String): String =
    merchant.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        .ifEmpty { merchant.lowercase().trim() }

/** 2026-10 -> 202610, the month key stored in the budget tables. */
val YearMonth.key: Int get() = year * 100 + monthValue

/** [start, end) epoch millis of this month in the phone's time zone. */
fun YearMonth.range(zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
    atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() to
        plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

fun yearMonthOf(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
    YearMonth.from(Instant.ofEpochMilli(timestamp).atZone(zone))
