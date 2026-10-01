package com.amir.expense

import com.amir.expense.importer.PhonePeParser
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs the parser on a real PhonePe statement, without a phone.
 * Put the PDF in samples/ and the password (your PhonePe mobile number) in samples/password.txt.
 * samples/ is gitignored. Skipped when no PDF is present.
 */
class SampleStatementTest {
    @Test fun parsesRealStatements() {
        val dir = listOf(File("samples"), File("../samples")).firstOrNull { it.isDirectory }
        val pdfs = dir?.listFiles { f -> f.extension.equals("pdf", ignoreCase = true) }.orEmpty()
        assumeTrue("no PDFs in samples/", pdfs.isNotEmpty())
        val password = File(dir, "password.txt").takeIf { it.exists() }?.readText()?.trim().orEmpty()

        for (pdf in pdfs) {
            val text = PDDocument.load(pdf, password).use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
            File(dir, pdf.nameWithoutExtension + ".txt").writeText(text) // inspect when the parser misses rows
            val txns = PhonePeParser.parse(text)
            println("${pdf.name}: ${txns.size} transactions, debits ₹${txns.filter { it.amountPaise > 0 }.sumOf { it.amountPaise } / 100}")
            txns.take(5).forEach { println("  $it") }
            assertTrue("${pdf.name}: parsed nothing; see ${pdf.nameWithoutExtension}.txt", txns.isNotEmpty())
        }
    }
}
