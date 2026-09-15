package com.example.data.model

import java.util.Calendar
import kotlin.math.roundToLong

/** Plain-language due dates, shared by the wishlist and money flow screens. */
object RelativeDates {
    /** Calendar days from [now] until [target] in the device time zone; negative once the date has passed. */
    fun daysUntil(target: Long, now: Long = System.currentTimeMillis()): Int {
        fun startOfDay(millis: Long) = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // Rounding absorbs the 23/25-hour days around daylight-saving changes.
        return ((startOfDay(target) - startOfDay(now)) / 86_400_000.0).roundToLong().toInt()
    }

    fun describe(target: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysUntil(target, now)
        return when {
            days == 0 -> "today"
            days == 1 -> "tomorrow"
            days > 1 -> "in $days days"
            days == -1 -> "yesterday"
            else -> "${-days} days ago"
        }
    }

    /** "Due in 3 days" / "Overdue by 2 days", for a record that is still pending. */
    fun describeDue(target: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysUntil(target, now)
        return when {
            days == 0 -> "Due today"
            days == 1 -> "Due tomorrow"
            days > 1 -> "Due in $days days"
            days == -1 -> "Overdue by 1 day"
            else -> "Overdue by ${-days} days"
        }
    }
}
