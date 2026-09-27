package com.example

import com.example.data.model.TransactionType
import com.example.service.TransactionNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TransactionNotificationParserTest {

    @Test
    fun testParseDebitTransaction_HDFC() {
        val title = "HDFC Bank"
        val text = "Rs. 500.00 debited from a/c XX1234 on 12-MAY-24 at Starbucks. Ref No: 12345678"

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(500.0, parsed.amount, 0.001)
        assertEquals("Starbucks", parsed.merchantOrSender)
        assertEquals("12345678", parsed.referenceId)
    }

    @Test
    fun testParseDebitTransaction_UPI() {
        val title = "Google Pay"
        val text = "You paid ₹250.50 to Chai Point using UPI. UPI Ref: 987654321012."

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNotNull(parsed)
        assertEquals(TransactionType.EXPENSE, parsed!!.type)
        assertEquals(250.50, parsed.amount, 0.001)
        assertEquals("Chai Point", parsed.merchantOrSender)
        assertEquals("987654321012", parsed.referenceId)
    }

    @Test
    fun testParseCreditTransaction_Salary() {
        val title = "SBI Bank"
        val text = "Your A/C XX5678 has been credited with Rs 45,000.00 on 01-JUN-24 by ACME Corp. Ref: 87654321"

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(45000.0, parsed.amount, 0.001)
        assertEquals("Acme Corp", parsed.merchantOrSender)
        assertEquals("87654321", parsed.referenceId)
    }

    @Test
    fun testParseCreditTransaction_ReceivedPhonePe() {
        val title = "PhonePe"
        val text = "Received ₹1,200 from John Doe via PhonePe. Txn ID: T240601123456"

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNotNull(parsed)
        assertEquals(TransactionType.INCOME, parsed!!.type)
        assertEquals(1200.0, parsed.amount, 0.001)
        assertEquals("John Doe", parsed.merchantOrSender)
        assertEquals("T240601123456", parsed.referenceId)
    }

    @Test
    fun testIgnoreOTP() {
        val title = "Bank Alert"
        val text = "OTP for your card transaction of Rs 1,500 at Amazon is 482910. Do not share code."

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNull(parsed)
    }

    @Test
    fun testIgnorePromotional() {
        val title = "Swiggy Offers"
        val text = "Get up to ₹100 discount on your next order! Apply code CRAVE100."

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNull(parsed)
    }

    @Test
    fun testIgnorePaymentReminder() {
        val title = "Credit Card Alert"
        val text = "Your bill of Rs. 4,500 is due on 15-JUN-24. Payment reminder."

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNull(parsed)
    }

    @Test
    fun testIgnoreFailedTransaction() {
        val title = "Payment Failed"
        val text = "Payment of ₹300 to Uber failed due to technical error. Please try again."

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNull(parsed)
    }

    @Test
    fun testIgnoreBalanceOnly() {
        val title = "Axis Bank"
        val text = "Your available balance in A/C XX9911 is Rs. 14,230.50 as of 10-MAY-24."

        val parsed = TransactionNotificationParser.parse(title, text)

        assertNull(parsed)
    }
}
