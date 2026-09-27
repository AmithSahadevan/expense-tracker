package com.example.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.local.entities.CategoryEntity
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.ProcessedNotificationEntity
import com.example.data.local.preferences.TransactionDetectionPreferences
import com.example.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ExpenseNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // 1. Check feature toggle
        if (!TransactionDetectionPreferences.isAutoDetectionEnabled(this)) {
            return
        }

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        val packageName = sbn.packageName ?: ""

        // 2. Parse transaction notification
        val parsed = TransactionNotificationParser.parse(title, text, bigText, postTime) ?: return

        serviceScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)

                // 3. Resolve active user ID
                var userId = TransactionDetectionPreferences.getActiveUserId(applicationContext)
                if (userId <= 0) {
                    val users = db.userDao().getAllUsers().firstOrNull()
                    if (!users.isNullOrEmpty()) {
                        userId = users.first().id
                        TransactionDetectionPreferences.setActiveUserId(applicationContext, userId)
                    }
                }

                if (userId <= 0) {
                    Log.w(TAG, "No active user found to process notification")
                    return@launch
                }

                // 4. Duplicate check
                if (TransactionDeduplicator.isDuplicate(userId, parsed, db)) {
                    Log.i(TAG, "Duplicate transaction notification ignored: ${parsed.amount} at ${parsed.merchantOrSender}")
                    return@launch
                }

                // 5. Ensure "Auto" category exists
                val customCategories = db.categoryDao().getCustomCategoriesListForUser(userId)
                val hasAutoCategory = customCategories.any { it.name.equals("Auto", ignoreCase = true) }
                if (!hasAutoCategory) {
                    db.categoryDao().insertCategory(
                        CategoryEntity(
                            userId = userId,
                            name = "Auto",
                            emoji = "🤖",
                            type = "EXPENSE",
                            colorHex = "#6366F1"
                        )
                    )
                }

                val notesText = if (!parsed.referenceId.isNullOrBlank()) {
                    "Ref: ${parsed.referenceId}"
                } else {
                    "Auto-detected from notification"
                }

                // 6. Create Expense or Income transaction
                if (parsed.type == TransactionType.EXPENSE) {
                    db.transactionDao().insertExpense(
                        ExpenseEntity(
                            userId = userId,
                            title = parsed.merchantOrSender,
                            amount = parsed.amount,
                            category = "Auto",
                            date = parsed.timestamp,
                            paymentMethod = "notification_auto",
                            notes = notesText
                        )
                    )
                } else {
                    db.transactionDao().insertIncome(
                        IncomeEntity(
                            userId = userId,
                            title = parsed.merchantOrSender,
                            amount = parsed.amount,
                            category = "Auto",
                            date = parsed.timestamp,
                            paymentMethod = "notification_auto",
                            notes = notesText
                        )
                    )
                }

                // 7. Store processed notification fingerprint
                val fingerprint = TransactionDeduplicator.generateFingerprint(parsed)
                db.processedNotificationDao().insert(
                    ProcessedNotificationEntity(
                        userId = userId,
                        fingerprint = fingerprint,
                        referenceId = parsed.referenceId,
                        amount = parsed.amount,
                        direction = parsed.type.name,
                        merchant = parsed.merchantOrSender,
                        packageSource = packageName,
                        timestamp = parsed.timestamp
                    )
                )

                // 8. Periodically clean up old deduplication records (older than 30 days)
                val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                db.processedNotificationDao().deleteOlderThan(thirtyDaysAgo)

                Log.i(TAG, "Successfully auto-created ${parsed.type} transaction of ${parsed.amount} for user $userId")

            } catch (e: Exception) {
                Log.e(TAG, "Error processing transaction notification", e)
            }
        }
    }

    companion object {
        private const val TAG = "ExpenseNotifListener"
    }
}
