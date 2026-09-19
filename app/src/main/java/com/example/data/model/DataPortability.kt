package com.example.data.model

import com.example.data.local.entities.*
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class UserDataBackup(
    val exportDate: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val user: UserEntity?,
    val income: List<IncomeEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val savingsTransactions: List<SavingsTransactionEntity> = emptyList(),
    val savingsGoals: List<SavingsGoalEntity> = emptyList(),
    val moneyFlows: List<MoneyFlowEntity> = emptyList(),
    val wishlistItems: List<WishlistItemEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val customCategories: List<CategoryEntity> = emptyList()
)
