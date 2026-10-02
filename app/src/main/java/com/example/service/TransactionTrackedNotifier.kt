package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.data.model.TransactionType
import java.util.Locale

/**
 * Posts the short confirmation shown after a notification-detected transaction is recorded.
 */
object TransactionTrackedNotifier {

    private const val TAG = "TransactionNotifier"
    private const val CHANNEL_ID = "auto_transaction_tracked"

    @Volatile
    private var channelCreated = false

    fun notifyTracked(
        context: Context,
        type: TransactionType,
        amount: Double,
        merchantOrSender: String,
        currencySymbol: String,
        rowId: Long
    ) {
        val manager = NotificationManagerCompat.from(context)

        // On Android 13+ this is false until POST_NOTIFICATIONS is granted, and on any version it
        // is false if the user turned the app's notifications off. Posting would be dropped.
        if (!manager.areNotificationsEnabled()) return

        ensureChannel(context)

        val formattedAmount = currencySymbol + String.format(Locale.US, "%.2f", amount)
        val label = if (type == TransactionType.EXPENSE) "Expense" else "Income"
        val body = if (merchantOrSender.isBlank()) {
            "$label of $formattedAmount recorded"
        } else {
            "$label of $formattedAmount · $merchantOrSender"
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_transaction)
            .setContentTitle("Transaction tracked!")
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        try {
            // One notification per recorded row, so several quick transactions each get their own.
            manager.notify((type.name + rowId).hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to post the tracked-transaction notification", e)
        }
    }

    private fun ensureChannel(context: Context) {
        if (channelCreated || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Transaction tracked",
                // Low: it appears in the shade without a sound or a heads-up pop, because one of
                // these fires for every transaction. Importance is fixed when the channel is first
                // created, so raising it later needs a new CHANNEL_ID to take effect.
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Confirms a transaction detected from your notifications was recorded"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
            channelCreated = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channel", e)
        }
    }
}
