package com.example.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ExpenseNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected")
        if (!TransactionDetectionPreferences.isAutoDetectionEnabled(this)) return

        // Notifications posted while the listener was disconnected (process killed, app updated,
        // low memory) never fire onNotificationPosted. Replay the ones still in the shade so those
        // transactions are not lost. The deduplicator drops anything already recorded.
        try {
            val active = activeNotifications ?: return
            val cutoff = System.currentTimeMillis() - BACKFILL_WINDOW_MS
            active.filter { it.postTime >= cutoff }
                .sortedBy { it.postTime }
                .forEach { handleNotification(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to replay active notifications", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "Notification listener disconnected, requesting rebind")
        // Without this the service stays dead until the user re-grants notification access.
        try {
            NotificationListenerService.requestRebind(
                ComponentName(this, ExpenseNotificationListenerService::class.java)
            )
        } catch (e: Exception) {
            Log.e(TAG, "requestRebind failed", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        handleNotification(sbn)
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        if (!TransactionDetectionPreferences.isAutoDetectionEnabled(this)) return

        val notification = sbn.notification ?: return

        // A group summary mirrors the content of a child notification, so processing both would
        // record the same transaction twice.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val postTime = if (sbn.postTime > 0) sbn.postTime else System.currentTimeMillis()
        val packageName = sbn.packageName ?: ""

        // Apps re-post the same notification whenever they update it, and the listener replays the
        // shade on every reconnect, so drop content we have already looked at recently.
        val contentKey = sbn.key + "|" + title + "|" + text + "|" + bigText
        val now = System.currentTimeMillis()
        synchronized(recentNotificationKeys) {
            val lastSeen = recentNotificationKeys[contentKey]
            if (lastSeen != null && now - lastSeen < REPOST_WINDOW_MS) return
            recentNotificationKeys[contentKey] = now
        }

        val parsed = TransactionNotificationParser.parse(title, text, bigText, postTime) ?: return

        serviceScope.launch {
            // The bank, the payment app and a caller-ID app all notify within milliseconds of each
            // other. Without this lock each coroutine runs its duplicate check before any of them
            // has written a fingerprint, so every notification creates its own transaction.
            processingLock.withLock {
                try {
                    recordTransaction(parsed, packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing transaction notification", e)
                }
            }
        }
    }

    private suspend fun recordTransaction(
        parsed: ParsedNotificationTransaction,
        packageName: String
    ) {
        val db = AppDatabase.getInstance(applicationContext)

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
            return
        }

        val record = ProcessedNotificationEntity(
            userId = userId,
            fingerprint = TransactionDeduplicator.generateFingerprint(parsed),
            referenceId = parsed.referenceId,
            amount = parsed.amount,
            direction = parsed.type.name,
            merchant = parsed.merchantOrSender,
            packageSource = packageName,
            timestamp = parsed.timestamp
        )

        if (TransactionDeduplicator.isDuplicate(userId, parsed, db)) {
            Log.i(TAG, "Duplicate transaction notification ignored: ${parsed.amount} from $packageName")
            // Remember it anyway, so a third notification for the same payment still matches once
            // the original record ages out of the dedup window.
            db.processedNotificationDao().insert(record)
            return
        }

        // Record the fingerprint before the ledger write: if the insert below throws, the next
        // notification for this payment must not be treated as a brand new transaction.
        db.processedNotificationDao().insert(record)

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

        Log.i(TAG, "Auto-created ${parsed.type} of ${parsed.amount} for user $userId from $packageName")

        val now = System.currentTimeMillis()
        if (now - lastCleanupAt > CLEANUP_INTERVAL_MS) {
            lastCleanupAt = now
            db.processedNotificationDao().deleteOlderThan(now - RETENTION_MS)
        }
    }

    companion object {
        private const val TAG = "ExpenseNotifListener"

        /** Serialises the duplicate check and the inserts that follow it. */
        private val processingLock = Mutex()

        @Volatile
        private var lastCleanupAt = 0L

        /** Notifications already seen are kept here so re-posts and shade replays are cheap to drop. */
        private val recentNotificationKeys =
            object : LinkedHashMap<String, Long>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 200
            }

        private const val REPOST_WINDOW_MS = 10 * 60 * 1000L
        private const val BACKFILL_WINDOW_MS = 24 * 60 * 60 * 1000L
        private const val CLEANUP_INTERVAL_MS = 6 * 60 * 60 * 1000L
        private const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000L

        fun isNotificationAccessGranted(context: Context): Boolean {
            return try {
                NotificationManagerCompat.getEnabledListenerPackages(context)
                    .contains(context.packageName)
            } catch (e: Exception) {
                false
            }
        }

        /**
         * Android drops the binding to a notification listener after a process death or an app
         * update and does not reconnect on its own, which is why detection silently stops working.
         * Asking for a rebind - falling back to cycling the component, so the system treats it as
         * newly installed - restores it without the user re-granting notification access.
         */
        fun ensureListenerConnected(context: Context) {
            val appContext = context.applicationContext
            if (!isNotificationAccessGranted(appContext)) return
            val component = ComponentName(appContext, ExpenseNotificationListenerService::class.java)

            try {
                NotificationListenerService.requestRebind(component)
                return
            } catch (e: Exception) {
                Log.e(TAG, "requestRebind failed, falling back to component toggle", e)
            }

            // Cycling the component makes the system treat the listener as newly installed, which
            // forces a fresh bind on OEM builds where requestRebind is a no-op.
            try {
                val pm = appContext.packageManager
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (e: Exception) {
                Log.e(TAG, "Component toggle failed", e)
            }
        }
    }
}
