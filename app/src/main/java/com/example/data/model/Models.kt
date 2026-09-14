package com.example.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class TransactionType {
    EXPENSE,
    INCOME
}

data class TransactionItem(
    val id: Long,
    val userId: Long,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val date: Long,
    val notes: String = "",
    val paymentMethod: String = "CARD",
    val recurrence: String = "NONE"
)

data class CategoryInfo(
    val name: String,
    val icon: ImageVector,
    val type: TransactionType,
    val colorHex: String
)

object CategoryRegistry {
    val defaultExpenseCategories = listOf(
        CategoryInfo("Food", Icons.Outlined.Restaurant, TransactionType.EXPENSE, "#FF6B6B"),
        CategoryInfo("Fuel", Icons.Outlined.LocalGasStation, TransactionType.EXPENSE, "#F59E0B"),
        CategoryInfo("Transport", Icons.Outlined.DirectionsBus, TransactionType.EXPENSE, "#3B82F6"),
        CategoryInfo("Clothes", Icons.Outlined.Checkroom, TransactionType.EXPENSE, "#EC4899"),
        CategoryInfo("Bills", Icons.Outlined.Bolt, TransactionType.EXPENSE, "#EF4444"),
        CategoryInfo("Mobile Recharge", Icons.Outlined.Smartphone, TransactionType.EXPENSE, "#10B981"),
        CategoryInfo("Internet", Icons.Outlined.Language, TransactionType.EXPENSE, "#06B6D4"),
        CategoryInfo("Subscriptions", Icons.Outlined.Subscriptions, TransactionType.EXPENSE, "#8B5CF6"),
        CategoryInfo("Shopping", Icons.Outlined.ShoppingBag, TransactionType.EXPENSE, "#F97316"),
        CategoryInfo("Entertainment", Icons.Outlined.Movie, TransactionType.EXPENSE, "#6366F1"),
        CategoryInfo("Health", Icons.Outlined.MedicalServices, TransactionType.EXPENSE, "#14B8A6"),
        CategoryInfo("Travel", Icons.Outlined.Flight, TransactionType.EXPENSE, "#0EA5E9"),
        CategoryInfo("Education", Icons.Outlined.School, TransactionType.EXPENSE, "#A855F7"),
        CategoryInfo("Other", Icons.Outlined.Category, TransactionType.EXPENSE, "#64748B")
    )
    val expenseCategories = defaultExpenseCategories

    val defaultIncomeCategories = listOf(
        CategoryInfo("Salary", Icons.Outlined.Work, TransactionType.INCOME, "#10B981"),
        CategoryInfo("Freelance", Icons.Outlined.Computer, TransactionType.INCOME, "#6366F1"),
        CategoryInfo("Investments", Icons.AutoMirrored.Outlined.TrendingUp, TransactionType.INCOME, "#8B5CF6"),
        CategoryInfo("Side Gig", Icons.Outlined.RocketLaunch, TransactionType.INCOME, "#F59E0B"),
        CategoryInfo("Bonus", Icons.Outlined.Redeem, TransactionType.INCOME, "#EC4899"),
        CategoryInfo("Rental", Icons.Outlined.HomeWork, TransactionType.INCOME, "#14B8A6"),
        CategoryInfo("Other", Icons.Outlined.Payments, TransactionType.INCOME, "#06B6D4")
    )
    val incomeCategories = defaultIncomeCategories

    fun getCategoryInfo(name: String, type: TransactionType): CategoryInfo {
        val list = if (type == TransactionType.INCOME) defaultIncomeCategories else defaultExpenseCategories
        return list.find { it.name.equals(name, ignoreCase = true) }
            ?: (if (type == TransactionType.INCOME)
                CategoryInfo(name, Icons.Outlined.Payments, TransactionType.INCOME, "#10B981")
            else
                CategoryInfo(name, Icons.Outlined.CreditCard, TransactionType.EXPENSE, "#FF6B6B"))
    }
}
