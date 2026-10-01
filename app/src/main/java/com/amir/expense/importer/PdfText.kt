package com.amir.expense.importer

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfText {
    class WrongPassword : Exception("Wrong password")

    /** Decrypts the statement with [password] and returns its text, rows in reading order. */
    fun extract(context: Context, uri: Uri, password: String): String {
        val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the file")
        val doc = input.use {
            try {
                PDDocument.load(it, password)
            } catch (_: InvalidPasswordException) {
                throw WrongPassword()
            }
        }
        return doc.use { PDFTextStripper().apply { sortByPosition = true }.getText(it) }
    }
}
