package com.example

import com.example.data.model.TransactionType
import com.example.service.ParsedNotificationTransaction
import com.example.service.TransactionDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionDeduplicatorTest {

    @Test
    fun testGenerateFingerprintWithRefId() {
        val parsed = ParsedNotificationTransaction(
            type = TransactionType.EXPENSE,
            amount = 500.0,
            merchantOrSender = "Starbucks",
            referenceId = "REF123456",
            rawText = "Rs 500 debited...",
            timestamp = 1700000000000L
        )

        val fp = TransactionDeduplicator.generateFingerprint(parsed)

        assertEquals("REF_EXPENSE_REF123456", fp)
    }

    @Test
    fun testGenerateFingerprintWithoutRefId() {
        val parsed = ParsedNotificationTransaction(
            type = TransactionType.EXPENSE,
            amount = 250.0,
            merchantOrSender = "Chai Point",
            referenceId = null,
            rawText = "Paid Rs 250...",
            timestamp = 1700000000000L
        )

        val fp = TransactionDeduplicator.generateFingerprint(parsed)

        val timeBucket = 1700000000000L / (5 * 60 * 1000L)
        assertEquals("NOREF_EXPENSE_250.00_chaipoint_$timeBucket", fp)
    }
}
