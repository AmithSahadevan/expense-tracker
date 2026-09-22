package com.example.data.repository

import com.example.data.local.dao.UserDao
import com.example.data.local.entities.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(
    private val userDao: UserDao,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _currentUser = MutableStateFlow<UserEntity?>(null)
    val currentUser: StateFlow<UserEntity?> = _currentUser.asStateFlow()

    val allUsers: Flow<List<UserEntity>> = userDao.getAllUsers()

    init {
        scope.launch {
            initializeDefaultUserIfNeeded()
        }
    }

    suspend fun initializeDefaultUserIfNeeded() = withContext(Dispatchers.IO) {
        try {
            val count = userDao.getUserCount()
            if (count == 0) {
                val defaultUser = UserEntity(
                    username = "alex",
                    email = "alex@expensetracker.fun",
                    displayName = "Alex",
                    avatarEmoji = "⚡",
                    avatarColorHex = "#0C0F14",
                    avatarImagePath = "pfp/pfp_1.jpg",
                    currencySymbol = "₹"
                )
                val id = userDao.insertUser(defaultUser)
                val created = userDao.findUserById(id)
                _currentUser.value = created
            } else {
                val alex = userDao.getUserByUsername("alex")
                if (alex != null && (alex.avatarImagePath == null || alex.avatarImagePath == "pfp/download.jpg")) {
                    userDao.updateUser(alex.copy(avatarImagePath = "pfp/pfp_1.jpg"))
                }
                
                val users = userDao.getAllUsers().firstOrNull()
                if (_currentUser.value == null && !users.isNullOrEmpty()) {
                    _currentUser.value = users.first()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Failed to initialize default user", e)
        }
    }

    suspend fun registerUser(
        username: String,
        email: String,
        displayName: String,
        avatarEmoji: String,
        avatarColorHex: String,
        avatarImagePath: String? = null
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        val cleanUsername = username.trim().lowercase()
        val cleanEmail = email.trim().lowercase()

        if (cleanUsername.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Username cannot be empty"))
        }

        if (userDao.getUserByUsername(cleanUsername) != null) {
            return@withContext Result.failure(IllegalArgumentException("Username '@$cleanUsername' is already taken!"))
        }

        if (userDao.getUserByEmail(cleanEmail) != null) {
            return@withContext Result.failure(IllegalArgumentException("Email is already registered!"))
        }

        val newUser = UserEntity(
            username = cleanUsername,
            email = cleanEmail,
            displayName = displayName.ifBlank { cleanUsername },
            avatarEmoji = avatarEmoji,
            avatarColorHex = avatarColorHex,
            avatarImagePath = avatarImagePath
        )

        val id = userDao.insertUser(newUser)
        val created = userDao.findUserById(id) ?: newUser.copy(id = id)
        _currentUser.value = created
        Result.success(created)
    }

    fun switchUser(user: UserEntity) {
        _currentUser.value = user
    }

    fun logout() {
        _currentUser.value = null
    }

    suspend fun updateUser(user: UserEntity) = withContext(Dispatchers.IO) {
        userDao.updateUser(user)
        if (_currentUser.value?.id == user.id) {
            _currentUser.value = user
        }
    }

    suspend fun deleteUser(userId: Long) = withContext(Dispatchers.IO) {
        userDao.deleteUser(userId)
        if (_currentUser.value?.id == userId) {
            _currentUser.value = allUsers.firstOrNull()?.firstOrNull { it.id != userId }
        }
    }
}
