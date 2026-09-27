package com.example.service

import com.example.data.model.TransactionType
import java.util.regex.Pattern

data class ParsedNotificationTransaction(
    val type: TransactionType,
    val amount: Double,
    val merchantOrSender: String,
    val referenceId: String?,
    val rawText: String,
    val timestamp: Long
)

object TransactionNotificationParser {

    private val OTP_PATTERN = Pattern.compile(
        "\\b(otp|one time password|verification code|is your code|secret code|security code|use code|authentication code)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val PROMO_PATTERN = Pattern.compile(
        "\\b(offer|discount|get up to|pre-approved|apply coupon|win up to|cashback up to|cashback of up to|reward points|claim now|promo code|loan approved)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val REMINDER_PATTERN = Pattern.compile(
        "\\b(is due on|payment reminder|bill generated|will be auto-debited|upcoming payment|due date|payable by|reminder:)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val FAILED_PATTERN = Pattern.compile(
        "\\b(failed|declined|unsuccessful|could not be|pending|reversed|cancelled|timed out|payment failed|unable to process)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val BALANCE_ONLY_PATTERN = Pattern.compile(
        "\\b(avail(able)? bal(ance)?|a/c bal|bal is|balance is|account balance)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val DEBIT_KEYWORDS = Pattern.compile(
        "\\b(debited|spent|paid|payment to|sent to|transfer to|withdrawn|charged|purchase at|paid at)\\b",
        Pattern.CASE_INSENSITIVE
    )

    private val CREDIT_KEYWORDS = Pattern.compile(
        "\\b(credited|received|added to|deposited|refunded|received from|salary credited)\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for amounts e.g., "Rs. 500.00", "Rs 1,200", "₹500", "INR 500", "500.00 Rs"
    private val AMOUNT_PATTERN_1 = Pattern.compile(
        "(?:Rs\\.?|INR|₹|\\$|USD)\\s*([\\d,]+(?:\\.\\d{1,2})?)",
        Pattern.CASE_INSENSITIVE
    )

    private val AMOUNT_PATTERN_2 = Pattern.compile(
        "([\\d,]+(?:\\.\\d{1,2})?)\\s*(?:Rs\\.?|INR|₹)",
        Pattern.CASE_INSENSITIVE
    )

    // Regex for reference ID / UTR
    private val REF_ID_PATTERN = Pattern.compile(
        "\\b(?:Ref(?:erence)?(?:\\s*No|\\s*ID)?|Txn(?:\\s*ID|\\s*No)?|UTR|UPI\\s*Ref|RRN)[^\\w]*([A-Za-z0-9]{6,22})\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Regex patterns for extracting merchant/sender
    private val MERCHANT_PATTERNS = listOf(
        Pattern.compile("\\b(?:paid to|sent to|transfer to|payment to|to)\\s+([A-Za-z0-9\\s&.'-]{2,30})\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:at|spent at|purchase at|paid at)\\s+([A-Za-z0-9\\s&.'-]{2,30})\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\b(?:from|received from|credited by|by|via)\\s+([A-Za-z0-9\\s&.'-]{2,30})\\b", Pattern.CASE_INSENSITIVE)
    )

    fun parse(
        title: String?,
        text: String?,
        bigText: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): ParsedNotificationTransaction? {
        val titleClean = title?.trim() ?: ""
        val textClean = text?.trim() ?: ""
        val bigTextClean = bigText?.trim() ?: ""

        val fullText = "$titleClean $textClean $bigTextClean".trim()
        if (fullText.isEmpty()) return null

        // 1. Exclude non-transaction messages
        if (OTP_PATTERN.matcher(fullText).find()) return null
        if (PROMO_PATTERN.matcher(fullText).find()) return null
        if (REMINDER_PATTERN.matcher(fullText).find()) return null
        if (FAILED_PATTERN.matcher(fullText).find()) return null

        // 2. Check balance-only messages
        val hasBalance = BALANCE_ONLY_PATTERN.matcher(fullText).find()
        val hasDebit = DEBIT_KEYWORDS.matcher(fullText).find()
        val hasCredit = CREDIT_KEYWORDS.matcher(fullText).find()

        if (hasBalance && !hasDebit && !hasCredit) {
            return null
        }

        // 3. Determine transaction type
        val type: TransactionType = when {
            hasDebit && !hasCredit -> TransactionType.EXPENSE
            hasCredit && !hasDebit -> TransactionType.INCOME
            hasDebit && hasCredit -> {
                // Disambiguate if both appear
                val debitIdx = indexOfPattern(DEBIT_KEYWORDS, fullText)
                val creditIdx = indexOfPattern(CREDIT_KEYWORDS, fullText)
                if (debitIdx != -1 && creditIdx != -1) {
                    if (debitIdx < creditIdx) TransactionType.EXPENSE else TransactionType.INCOME
                } else if (debitIdx != -1) {
                    TransactionType.EXPENSE
                } else {
                    TransactionType.INCOME
                }
            }
            else -> return null // Neither debit nor credit keyword found
        }

        // 4. Extract amount
        val amount = extractAmount(fullText) ?: return null
        if (amount <= 0.0) return null

        // 5. Extract Reference ID
        val referenceId = extractReferenceId(fullText)

        // 6. Extract Merchant / Sender name
        val merchantOrSender = extractMerchant(fullText, titleClean, type)

        return ParsedNotificationTransaction(
            type = type,
            amount = amount,
            merchantOrSender = merchantOrSender,
            referenceId = referenceId,
            rawText = fullText,
            timestamp = timestamp
        )
    }

    private fun indexOfPattern(pattern: Pattern, text: String): Int {
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.start() else -1
    }

    private fun extractAmount(text: String): Double? {
        val matcher1 = AMOUNT_PATTERN_1.matcher(text)
        if (matcher1.find()) {
            val raw = matcher1.group(1)?.replace(",", "") ?: ""
            return raw.toDoubleOrNull()
        }

        val matcher2 = AMOUNT_PATTERN_2.matcher(text)
        if (matcher2.find()) {
            val raw = matcher2.group(1)?.replace(",", "") ?: ""
            return raw.toDoubleOrNull()
        }

        return null
    }

    private fun extractReferenceId(text: String): String? {
        val matcher = REF_ID_PATTERN.matcher(text)
        if (matcher.find()) {
            val ref = matcher.group(1)?.trim()
            if (!ref.isNullOrEmpty() && ref.length >= 6) {
                return ref
            }
        }
        return null
    }

    private fun extractMerchant(text: String, title: String, type: TransactionType): String {
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val candidate = matcher.group(1)?.trim()
                if (!candidate.isNullOrEmpty()) {
                    val cleanCandidate = cleanMerchantString(candidate)
                    if (cleanCandidate.length in 2..35 && !isGenericStopWord(cleanCandidate)) {
                        return cleanCandidate
                    }
                }
            }
        }

        // Fallback: title if meaningful (e.g., "Starbucks", "Paytm", "HDFC Bank")
        if (title.isNotEmpty() && !isGenericBankTitle(title)) {
            return cleanMerchantString(title)
        }

        return if (type == TransactionType.EXPENSE) "Merchant" else "Bank Deposit"
    }

    private fun cleanMerchantString(raw: String): String {
        var clean = raw
            .replace(Regex("(?i)\\b(on|for|ref|using|via|vpa|upi|a/c|account|bank|card|netbanking|dated|time|bal|balance)\\b.*"), "")
            .trim()
            .trim('.', ',', ':', ';', '-', '_')

        // Capitalize words nicely
        clean = clean.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

        return clean.ifEmpty { "Merchant" }
    }

    private fun isGenericStopWord(str: String): Boolean {
        val lower = str.lowercase()
        val stopWords = setOf("your", "account", "bank", "card", "upi", "vpa", "a/c", "wallet", "self")
        return stopWords.contains(lower)
    }

    private fun isGenericBankTitle(title: String): Boolean {
        val lower = title.lowercase()
        return lower.contains("bank") || lower.contains("alerts") || lower.contains("notification")
    }
}
