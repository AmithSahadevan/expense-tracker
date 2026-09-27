package com.example.service

import com.example.data.local.AppDatabase
import com.example.data.model.TransactionType
import java.util.Locale
import kotlin.math.abs

object TransactionDeduplicator {

    fun generateFingerprint(parsed: ParsedNotificationTransaction): String {
        val direction = parsed.type.name
        val amountStr = String.format(Locale.US, "%.2f", parsed.amount)
        if (!parsed.referenceId.isNullOrBlank()) {
            return "REF_${direction}_${parsed.referenceId.trim().uppercase()}"
        }
        val normalizedMerchant = parsed.merchantOrSender.lowercase().replace(Regex("[^a-z0-9]"), "")
        val timeBucket = parsed.timestamp / (5 * 60 * 1000L)
        return "NOREF_${direction}_${amountStr}_${normalizedMerchant}_${timeBucket}"
    }

    suspend fun isDuplicate(
        userId: Long,
        parsed: ParsedNotificationTransaction,
        database: AppDatabase
    ): Boolean {
        val refId = parsed.referenceId?.trim()
        val processedDao = database.processedNotificationDao()

        // 1. Check reference ID in processed notifications
        if (!refId.isNullOrBlank()) {
            if (processedDao.findByReferenceId(userId, refId) != null) {
                return true
            }
        }

        // 2. Check exact fingerprint in processed notifications
        val fingerprint = generateFingerprint(parsed)
        if (processedDao.findByFingerprint(userId, fingerprint) != null) {
            return true
        }

        // 3. Check matching processed notification in a 10-minute window
        val windowMs = 10 * 60 * 1000L
        val matchingProcessed = processedDao.findMatchingNotification(
            userId = userId,
            direction = parsed.type.name,
            amount = parsed.amount,
            minTimestamp = parsed.timestamp - windowMs,
            maxTimestamp = parsed.timestamp + windowMs
        )
        if (matchingProcessed != null) {
            if (!refId.isNullOrBlank() && matchingProcessed.referenceId.equals(refId, ignoreCase = true)) {
                return true
            }
            if (isMerchantSimilar(matchingProcessed.merchant, parsed.merchantOrSender)) {
                return true
            }
        }

        // 4. Check existing ledger entries (ExpenseEntity or IncomeEntity) for active user
        val txDao = database.transactionDao()
        if (parsed.type == TransactionType.EXPENSE) {
            val recentExpenses = txDao.getExpensesListForUser(userId)
            val isDuplicateLedger = recentExpenses.any { expense ->
                val timeDiff = abs(expense.date - parsed.timestamp)
                val amountMatches = abs(expense.amount - parsed.amount) < 0.01
                if (!amountMatches) return@any false

                val notesHasRef = !refId.isNullOrBlank() && expense.notes.contains(refId, ignoreCase = true)
                val merchantMatches = isMerchantSimilar(expense.title, parsed.merchantOrSender)
                val isNearby = timeDiff <= 12 * 60 * 60 * 1000L // within 12 hours

                notesHasRef || (isNearby && merchantMatches)
            }
            if (isDuplicateLedger) return true
        } else {
            val recentIncome = txDao.getIncomeListForUser(userId)
            val isDuplicateLedger = recentIncome.any { income ->
                val timeDiff = abs(income.date - parsed.timestamp)
                val amountMatches = abs(income.amount - parsed.amount) < 0.01
                if (!amountMatches) return@any false

                val notesHasRef = !refId.isNullOrBlank() && income.notes.contains(refId, ignoreCase = true)
                val merchantMatches = isMerchantSimilar(income.title, parsed.merchantOrSender)
                val isNearby = timeDiff <= 12 * 60 * 60 * 1000L

                notesHasRef || (isNearby && merchantMatches)
            }
            if (isDuplicateLedger) return true
        }

        return false
    }

    private fun isMerchantSimilar(m1: String, m2: String): Boolean {
        val norm1 = m1.lowercase().replace(Regex("[^a-z0-9]"), "")
        val norm2 = m2.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (norm1.isEmpty() || norm2.isEmpty()) return true
        return norm1 == norm2 || norm1.contains(norm2) || norm2.contains(norm1)
    }
}
