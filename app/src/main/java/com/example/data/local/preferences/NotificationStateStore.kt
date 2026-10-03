package com.example.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationUserState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Remembers what each user has done with their notifications: which they have read, which they
 * have cleared, and which categories they have switched off.
 *
 * The notifications themselves are derived from the user's data on every change (see
 * [com.example.data.model.NotificationEngine]), so this is the only thing that needs to outlive
 * the process. Without a [Context] (unit tests) the state simply lives in memory.
 */
class NotificationStateStore(context: Context? = null) {

    private val prefs: SharedPreferences? =
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val states = MutableStateFlow<Map<Long, NotificationUserState>>(emptyMap())

    fun observe(userId: Long): Flow<NotificationUserState> =
        states.map { it[userId] ?: load(userId) }.distinctUntilChanged()

    fun markRead(userId: Long, ids: Collection<String>) = mutate(userId) { state ->
        state.copy(read = (state.read + ids).capped())
    }

    /** Dismissing also reads: a cleared notification should never count toward the unread badge. */
    fun dismiss(userId: Long, ids: Collection<String>) = mutate(userId) { state ->
        state.copy(read = (state.read + ids).capped(), dismissed = (state.dismissed + ids).capped())
    }

    fun restore(userId: Long, ids: Collection<String>) = mutate(userId) { state ->
        state.copy(dismissed = state.dismissed - ids.toSet())
    }

    fun restoreAllDismissed(userId: Long) = mutate(userId) { state ->
        state.copy(dismissed = emptySet())
    }

    fun setCategoryEnabled(userId: Long, category: NotificationCategory, enabled: Boolean) = mutate(userId) { state ->
        state.copy(mutedCategories = if (enabled) state.mutedCategories - category else state.mutedCategories + category)
    }

    /** Drops everything remembered for a user whose account has been deleted. */
    fun forget(userId: Long) {
        states.update { it - userId }
        prefs?.edit()
            ?.remove(key(KEY_READ, userId))
            ?.remove(key(KEY_DISMISSED, userId))
            ?.remove(key(KEY_MUTED, userId))
            ?.apply()
    }

    private fun mutate(userId: Long, transform: (NotificationUserState) -> NotificationUserState) {
        var updated: NotificationUserState? = null
        states.update { map ->
            val next = transform(map[userId] ?: load(userId))
            updated = next
            map + (userId to next)
        }
        updated?.let { save(userId, it) }
    }

    private fun load(userId: Long): NotificationUserState {
        val p = prefs ?: return NotificationUserState()
        return NotificationUserState(
            read = decode(p.getString(key(KEY_READ, userId), null)),
            dismissed = decode(p.getString(key(KEY_DISMISSED, userId), null)),
            mutedCategories = decode(p.getString(key(KEY_MUTED, userId), null))
                .mapNotNull { name -> NotificationCategory.entries.firstOrNull { it.name == name } }
                .toSet()
        )
    }

    private fun save(userId: Long, state: NotificationUserState) {
        prefs?.edit()
            ?.putString(key(KEY_READ, userId), state.read.joinToString(SEPARATOR))
            ?.putString(key(KEY_DISMISSED, userId), state.dismissed.joinToString(SEPARATOR))
            ?.putString(key(KEY_MUTED, userId), state.mutedCategories.joinToString(SEPARATOR) { it.name })
            ?.apply()
    }

    private fun decode(raw: String?): Set<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotEmpty() }?.toCollection(LinkedHashSet()) ?: emptySet()

    /** Oldest entries fall off first, so a long-lived install cannot grow these without bound. */
    private fun Set<String>.capped(): Set<String> =
        if (size <= MAX_REMEMBERED) this else toList().takeLast(MAX_REMEMBERED).toCollection(LinkedHashSet())

    private fun key(prefix: String, userId: Long) = "${prefix}_$userId"

    private companion object {
        const val PREFS_NAME = "notification_center_prefs"
        const val KEY_READ = "read"
        const val KEY_DISMISSED = "dismissed"
        const val KEY_MUTED = "muted"
        const val SEPARATOR = "|"
        const val MAX_REMEMBERED = 500
    }
}
