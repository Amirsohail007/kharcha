package com.amir.expense

import com.amir.expense.importer.PhonePeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Synthetic statements in the two layouts seen in the wild. The real check is
 * [SampleStatementTest], which runs against your own PDF in samples/.
 */
class PhonePeParserTest {

    /** Older layout: one field per line, as copy-pasted from the PDF. */
    private val multiLine = """
        Transaction Statement for 9876543210
        Sep 01, 2026 - Sep 30, 2026
        Date Transaction Details Type Amount
        Sep 28, 2026
        10:42 pm
        Paid to - Swiggy Limited
        Transaction ID T2609282242123456789
        UTR No. 612345678901
        Paid by XXXXXX1234
        DEBIT ₹ 1,250.50
        Sep 27, 2026
        12:05 am
        Received from Rahul Sharma
        Transaction ID T2609270005987654321
        UTR No. 698765432109
        Credited to XXXXXX1234
        CREDIT
        500.00
        Page 1 of 2
    """.trimIndent()

    /** Table layout: date, details, type and amount on one row; ₹ extracted as junk. */
    private val tableRows = """
        Date Transaction Details Type Amount
        Sep 26, 2026 Paid to ZOMATO DEBIT ` 349
        09:15 AM Transaction ID : T2609260915000000001
        UTR No. : 600000000001
        Paid by XXXXXX1234
        Sep 25, 2026 Mobile recharged 9876543210 DEBIT ₹299
        07:30 pm Transaction ID : T2609251930000000002
        UTR No. : 600000000002
    """.trimIndent()

    @Test fun parsesMultiLineLayout() {
        val txns = PhonePeParser.parse(multiLine)
        assertEquals(2, txns.size)

        val swiggy = txns[0]
        assertEquals("T2609282242123456789", swiggy.transactionId)
        assertEquals("Swiggy Limited", swiggy.merchant)
        assertEquals(125050L, swiggy.amountPaise)
        assertEquals(LocalDateTime.of(2026, 9, 28, 22, 42), swiggy.dateTime)
        assertEquals("612345678901", swiggy.utr)

        val credit = txns[1]
        assertEquals("Rahul Sharma", credit.merchant)
        assertEquals(-50000L, credit.amountPaise)
        assertEquals(LocalDateTime.of(2026, 9, 27, 0, 5), credit.dateTime)
    }

    @Test fun parsesTableLayout() {
        val txns = PhonePeParser.parse(tableRows)
        assertEquals(2, txns.size)
        assertEquals("ZOMATO", txns[0].merchant)
        assertEquals(34900L, txns[0].amountPaise)
        assertEquals(LocalDateTime.of(2026, 9, 26, 9, 15), txns[0].dateTime)
        assertEquals("Mobile recharged 9876543210", txns[1].merchant)
        assertEquals(29900L, txns[1].amountPaise)
    }

    @Test fun ignoresTextWithoutTransactions() {
        assertEquals(emptyList<Any>(), PhonePeParser.parse("Sep 01, 2026 - Sep 30, 2026\nNo transactions"))
        assertNull(PhonePeParser.parse("").firstOrNull())
    }
}
