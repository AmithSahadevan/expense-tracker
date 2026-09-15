package com.example.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [Index(value = ["username"], unique = true), Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val email: String,
    val displayName: String,
    val avatarEmoji: String = "⚡",
    val avatarColorHex: String = "#242426",
    val passwordHash: String = "",
    val currencySymbol: String = "$",
    val monthlySalary: Double = 0.0,
    val paydayDayOfMonth: Int = 1,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "income",
    indices = [Index(value = ["userId"])]
)
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val recurrence: String = "NONE", // NONE, WEEKLY, MONTHLY
    val paymentMethod: String = "BANK",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["userId"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val amount: Double,
    val category: String,
    val date: Long = System.currentTimeMillis(),
    val recurrence: String = "NONE",
    val paymentMethod: String = "CARD", // CARD, CASH, TRANSFER
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "savings",
    indices = [Index(value = ["userId"])]
)
data class SavingsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val category: String = "General",
    val targetDate: Long? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

object SavingsType {
    const val ADULT_MONEY = "ADULT_MONEY"
    const val EMERGENCY_FUND = "EMERGENCY_FUND"
}

object SavingsActionType {
    const val DEPOSIT = "DEPOSIT"
    const val WITHDRAWAL = "WITHDRAWAL"
}

@Entity(
    tableName = "savings_transactions",
    indices = [Index(value = ["userId"]), Index(value = ["savingsType"])]
)
data class SavingsTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val savingsType: String, // "ADULT_MONEY" or "EMERGENCY_FUND"
    val transactionType: String, // "DEPOSIT" or "WITHDRAWAL"
    val amount: Double,
    val title: String,
    val notes: String = "",
    val date: Long = System.currentTimeMillis(),
    // true: deposit is taken out of Available Money / withdrawal is returned to it.
    // false: money came from (or left to) outside the ledger, e.g. savings that existed before the app.
    @ColumnInfo(defaultValue = "1") val affectsAvailableMoney: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

object MoneyFlowDirection {
    const val OWED_TO_ME = "OWED_TO_ME"
    const val I_OWE = "I_OWE"
}

@Entity(
    tableName = "money_flow",
    indices = [Index(value = ["userId"])]
)
data class MoneyFlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val personName: String,
    val direction: String, // OWED_TO_ME, I_OWE
    val amount: Double,
    // When the money actually changed hands (editable), as opposed to when the record was created.
    @ColumnInfo(defaultValue = "0") val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val isSettled: Boolean = false,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "wishlist_items",
    indices = [Index(value = ["userId"])]
)
data class WishlistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String, // Product name
    val estimatedCost: Double, // Price
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH, MUST_HAVE
    val url: String = "", // Product page link
    val notes: String = "",
    val isPurchased: Boolean = false,
    @ColumnInfo(defaultValue = "") val imageUrl: String = "",
    @ColumnInfo(defaultValue = "") val store: String = "",
    @ColumnInfo(defaultValue = "") val description: String = "",
    @ColumnInfo(defaultValue = "0") val dateAdded: Long = System.currentTimeMillis(),
    val targetPurchaseDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["userId"])]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val category: String,
    val allocatedAmount: Double,
    val period: String = "MONTHLY", // MONTHLY, WEEKLY
    val spentAmount: Double = 0.0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "savings_goals",
    indices = [Index(value = ["userId"])]
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val title: String,
    val goalAmount: Double,
    val savedAmount: Double = 0.0,
    val targetDate: Long? = null,
    val emoji: String = "🎯",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "custom_categories",
    indices = [Index(value = ["userId"])]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val name: String,
    val emoji: String = "🏷️",
    val type: String = "EXPENSE", // EXPENSE, INCOME
    val colorHex: String = "#6366F1",
    val createdAt: Long = System.currentTimeMillis()
)
