package com.example.service

import com.example.data.local.AppDatabase
import com.example.data.local.entities.ProcessedNotificationEntity
import com.example.data.model.TransactionType
import java.util.Locale
import kotlin.math.abs

object TransactionDeduplicator {

    /**
     * One payment normally produces several notifications within seconds: the bank, the payment app
     * and sometimes a caller-ID app. They word the merchant differently, so the amount, the
     * direction and how close the notifications are is what identifies them as the same payment.
     */
    private const val SAME_TRANSACTION_WINDOW_MS = 6 * 60 * 1000L

    /** A manual entry is timestamped by hand, so it is matched over a much wider window. */
    private const val MANUAL_ENTRY_WINDOW_MS = 12 * 60 * 60 * 1000L

    fun generateFingerprint(parsed: ParsedNotificationTransaction): String {
        val direction = parsed.type.name
        val amountStr = String.format(Locale.US, "%.2f", parsed.amount)
        if (!parsed.referenceId.isNullOrBlank()) {
            return "REF_${direction}_${parsed.referenceId.trim().uppercase()}"
        }
        val normalizedMerchant = normalize(parsed.merchantOrSender)
        val timeBucket = parsed.timestamp / SAME_TRANSACTION_WINDOW_MS
        return "NOREF_${direction}_${amountStr}_${normalizedMerchant}_${timeBucket}"
    }

    suspend fun isDuplicate(
        userId: Long,
        parsed: ParsedNotificationTransaction,
        database: AppDatabase
    ): Boolean {
        val refId = parsed.referenceId?.trim()
        val processedDao = database.processedNotificationDao()

        // 1. Reference ID is the strongest signal: the same ID in the same direction is the
        //    same payment.
        if (!refId.isNullOrBlank() &&
            processedDao.findByReferenceId(userId, refId, parsed.type.name) != null
        ) {
            return true
        }

        // 2. Exact fingerprint match.
        if (processedDao.findByFingerprint(userId, generateFingerprint(parsed)) != null) {
            return true
        }

        // 3. Same amount and direction within the window. Previously this also required the
        //    merchant names to match, which is exactly what fails across apps: the bank says
        //    "UPI/1234/CoffeeVPA", the payment app says "Coffee Shop" and a caller-ID app says
        //    nothing recognisable, so three notifications for one payment became three records.
        val candidates = processedDao.findMatchingNotifications(
            userId = userId,
            direction = parsed.type.name,
            amount = parsed.amount,
            minTimestamp = parsed.timestamp - SAME_TRANSACTION_WINDOW_MS,
            maxTimestamp = parsed.timestamp + SAME_TRANSACTION_WINDOW_MS
        )
        if (candidates.any { describesSamePayment(it, parsed, refId) }) {
            return true
        }

        // 4. The user may already have entered this transaction by hand.
        return matchesExistingLedgerEntry(userId, parsed, refId, database)
    }

    /**
     * Two notifications with the same amount and direction, arriving close together, are the same
     * payment - unless both carry a reference ID and those IDs differ, which is the one case where
     * two identical-looking payments are provably distinct.
     */
    private fun describesSamePayment(
        existing: ProcessedNotificationEntity,
        parsed: ParsedNotificationTransaction,
        refId: String?
    ): Boolean {
        val existingRef = existing.referenceId?.trim()
        if (!refId.isNullOrBlank() && !existingRef.isNullOrBlank()) {
            return refId.equals(existingRef, ignoreCase = true)
        }
        return true
    }

    private suspend fun matchesExistingLedgerEntry(
        userId: Long,
        parsed: ParsedNotificationTransaction,
        refId: String?,
        database: AppDatabase
    ): Boolean {
        val txDao = database.transactionDao()
        val minDate = parsed.timestamp - MANUAL_ENTRY_WINDOW_MS
        val maxDate = parsed.timestamp + MANUAL_ENTRY_WINDOW_MS

        // Entries created by this service are already covered by the fingerprint checks above, so
        // only manual entries need to be compared here.
        val entries: List<Triple<Double, Long, Pair<String, String>>> =
            if (parsed.type == TransactionType.EXPENSE) {
                txDao.getExpensesInRange(userId, minDate, maxDate)
                    .filter { it.paymentMethod != AUTO_SOURCE }
                    .map { Triple(it.amount, it.date, it.title to it.notes) }
            } else {
                txDao.getIncomeInRange(userId, minDate, maxDate)
                    .filter { it.paymentMethod != AUTO_SOURCE }
                    .map { Triple(it.amount, it.date, it.title to it.notes) }
            }

        return entries.any { (amount, date, titleAndNotes) ->
            if (abs(amount - parsed.amount) >= 0.01) return@any false

            val (title, notes) = titleAndNotes
            val timeDiff = abs(date - parsed.timestamp)

            val notesHaveRef = !refId.isNullOrBlank() && notes.contains(refId, ignoreCase = true)
            val justEntered = timeDiff <= SAME_TRANSACTION_WINDOW_MS
            val sameMerchantSameDay =
                timeDiff <= MANUAL_ENTRY_WINDOW_MS && isMerchantSimilar(title, parsed.merchantOrSender)

            notesHaveRef || justEntered || sameMerchantSameDay
        }
    }

    private fun isMerchantSimilar(m1: String, m2: String): Boolean {
        val norm1 = normalize(m1)
        val norm2 = normalize(m2)
        // An unknown merchant tells us nothing, so it must not count as a match on its own.
        if (norm1.isEmpty() || norm2.isEmpty()) return false
        if (norm1 == norm2) return true
        // Require some substance before treating one name as contained in the other, otherwise
        // short fragments match almost anything.
        val shorter = if (norm1.length <= norm2.length) norm1 else norm2
        if (shorter.length < 4) return false
        return norm1.contains(norm2) || norm2.contains(norm1)
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]"), "")

    private const val AUTO_SOURCE = "notification_auto"
}
