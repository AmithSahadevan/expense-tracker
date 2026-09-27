package com.example.data.local.preferences

import android.content.Context
import android.content.SharedPreferences

object TransactionDetectionPreferences {
    private const val PREFS_NAME = "expense_tracker_prefs"
    private const val KEY_AUTO_DETECTION_ENABLED = "auto_transaction_detection_enabled"
    private const val KEY_ACTIVE_USER_ID = "active_user_id"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoDetectionEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DETECTION_ENABLED, false)
    }

    fun setAutoDetectionEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DETECTION_ENABLED, enabled).apply()
    }

    fun getActiveUserId(context: Context): Long {
        return getPrefs(context).getLong(KEY_ACTIVE_USER_ID, -1L)
    }

    fun setActiveUserId(context: Context, userId: Long) {
        getPrefs(context).edit().putLong(KEY_ACTIVE_USER_ID, userId).apply()
    }
}
