package com.amir.expense.importer

import java.time.LocalDateTime

/** One row of a PhonePe statement. amountPaise > 0 = DEBIT (money out), < 0 = CREDIT. */
data class ParsedTxn(
    val transactionId: String,
    val dateTime: LocalDateTime,
    val merchant: String,
    val amountPaise: Long,
    val utr: String?,
)

/**
 * Turns the extracted text of a PhonePe "Transaction Statement" PDF into transactions.
 *
 * Each record starts with a date like "Sep 28, 2026" and carries, in some order:
 * time ("10:42 am"), "Paid to X" / "Received from X", "Transaction ID T…", "UTR No. …",
 * "DEBIT"/"CREDIT" and an amount. Line breaks and field order differ between statement
 * versions and PDF extractors, so each field is searched for inside the record instead of
 * being read by position. Records without a Transaction ID (headers, the statement's date
 * range) are skipped.
 */
object PhonePeParser {
    private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val DATE = Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]* (\d{1,2}), (\d{4})\b""")
    private val TIME = Regex("""\b(\d{1,2}):(\d{2})\s*([AaPp])\.?[Mm]\.?""")
    private val TXN_ID = Regex("""Transaction ID\s*:?\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val UTR = Regex("""UTR No\.?\s*:?\s*([A-Za-z0-9]+)""", RegexOption.IGNORE_CASE)
    private val TYPE = Regex("""\b(DEBIT|CREDIT)\b""", RegexOption.IGNORE_CASE)
    private const val NUMBER = """(\d[\d,]*(?:\.\d{1,2})?)"""
    private val AMOUNT_WITH_CURRENCY = Regex("""(?:₹|Rs\.?|INR)\s*$NUMBER""")
    // The ₹ glyph sometimes extracts as junk; fall back to "DEBIT <junk?> 250.00"…
    private val AMOUNT_AFTER_TYPE = Regex("""\b(?:DEBIT|CREDIT)\b\s*[^\d\s]?\s*$NUMBER""", RegexOption.IGNORE_CASE)
    // …or to an amount alone on its line.
    private val AMOUNT_ALONE = Regex("""(?m)^\s*[^\d\s]?\s*(\d[\d,]*\.\d{2})\s*$""")
    private val PARTY = Regex(
        """\b(?:Paid to|Received from|Payment to|Transfer to|Transferred to|Refund from|Bill paid|Paid)\s*[-:]?\s*(.+)""",
    )
    private val NOT_MERCHANT = Regex("""^(Transaction ID|UTR|Paid by|Debited from|Credited to|Page \d)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<ParsedTxn> {
        val starts = DATE.findAll(text).toList()
        return starts.mapIndexedNotNull { i, date ->
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else text.length
            parseRecord(date, text.substring(date.range.last + 1, end))
        }
    }

    private fun parseRecord(date: MatchResult, body: String): ParsedTxn? {
        val id = TXN_ID.find(body)?.groupValues?.get(1) ?: return null
        val type = TYPE.find(body)?.value?.uppercase() ?: return null
        val amountText = (AMOUNT_WITH_CURRENCY.find(body) ?: AMOUNT_AFTER_TYPE.find(body) ?: AMOUNT_ALONE.find(body))
            ?.groupValues?.get(1) ?: return null
        val paise = amountText.replace(",", "").toBigDecimal().movePointRight(2).toLong()

        val (mon, day, year) = date.destructured
        var hour = 0
        var minute = 0
        TIME.find(body)?.destructured?.let { (h, m, ampm) ->
            hour = h.toInt() % 12 + if (ampm.equals("p", ignoreCase = true)) 12 else 0
            minute = m.toInt()
        }

        return ParsedTxn(
            transactionId = id,
            dateTime = LocalDateTime.of(year.toInt(), MONTHS.indexOf(mon) + 1, day.toInt(), hour, minute),
            merchant = merchantOf(body),
            amountPaise = if (type == "CREDIT") -paise else paise,
            utr = UTR.find(body)?.groupValues?.get(1),
        )
    }

    private fun merchantOf(body: String): String {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val raw = lines.firstNotNullOfOrNull { line ->
            if (NOT_MERCHANT.containsMatchIn(line) || line.startsWith("Paid by", ignoreCase = true)) null
            else PARTY.find(line)?.groupValues?.get(1)
        } ?: lines.firstOrNull { !NOT_MERCHANT.containsMatchIn(it) && !TIME.matches(it) }.orEmpty()
        return clean(raw).ifEmpty { "Unknown" }
    }

    /** Drops whatever trails the name on the same line: type, amount, time, IDs. */
    private fun clean(s: String): String {
        val cut = listOfNotNull(
            TYPE.find(s)?.range?.first,
            Regex("""₹|Rs\.|INR """).find(s)?.range?.first,
            TXN_ID.find(s)?.range?.first,
            TIME.find(s)?.range?.first,
        ).minOrNull() ?: s.length
        return s.substring(0, cut).trim().trim('-', ':').trim().replace(Regex("""\s+"""), " ")
    }
}
