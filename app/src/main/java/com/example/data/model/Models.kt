package com.example.data.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.PhosphorIcons

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
        CategoryInfo("Food", PhosphorIcons.Bold.ForkKnife, TransactionType.EXPENSE, "#FF6B6B"),
        CategoryInfo("Fuel", PhosphorIcons.Bold.GasPump, TransactionType.EXPENSE, "#F59E0B"),
        CategoryInfo("Transport", PhosphorIcons.Bold.Bus, TransactionType.EXPENSE, "#3B82F6"),
        CategoryInfo("Clothes", PhosphorIcons.Bold.TShirt, TransactionType.EXPENSE, "#EC4899"),
        CategoryInfo("Bills", PhosphorIcons.Bold.Lightning, TransactionType.EXPENSE, "#EF4444"),
        CategoryInfo("Mobile Recharge", PhosphorIcons.Bold.DeviceMobile, TransactionType.EXPENSE, "#10B981"),
        CategoryInfo("Internet", PhosphorIcons.Bold.Globe, TransactionType.EXPENSE, "#06B6D4"),
        CategoryInfo("Subscriptions", PhosphorIcons.Bold.Television, TransactionType.EXPENSE, "#8B5CF6"),
        CategoryInfo("Shopping", PhosphorIcons.Bold.ShoppingBag, TransactionType.EXPENSE, "#F97316"),
        CategoryInfo("Entertainment", PhosphorIcons.Bold.FilmStrip, TransactionType.EXPENSE, "#6366F1"),
        CategoryInfo("Health", PhosphorIcons.Bold.FirstAid, TransactionType.EXPENSE, "#14B8A6"),
        CategoryInfo("Travel", PhosphorIcons.Bold.Airplane, TransactionType.EXPENSE, "#0EA5E9"),
        CategoryInfo("Education", PhosphorIcons.Bold.GraduationCap, TransactionType.EXPENSE, "#A855F7"),
        CategoryInfo("Other", PhosphorIcons.Bold.Package, TransactionType.EXPENSE, "#64748B")
    )
    val expenseCategories = defaultExpenseCategories

    val defaultIncomeCategories = listOf(
        CategoryInfo("Salary", PhosphorIcons.Bold.Briefcase, TransactionType.INCOME, "#10B981"),
        CategoryInfo("Freelance", PhosphorIcons.Bold.Desktop, TransactionType.INCOME, "#6366F1"),
        CategoryInfo("Investments", PhosphorIcons.Bold.TrendUp, TransactionType.INCOME, "#8B5CF6"),
        CategoryInfo("Side Gig", PhosphorIcons.Bold.Rocket, TransactionType.INCOME, "#F59E0B"),
        CategoryInfo("Bonus", PhosphorIcons.Bold.Gift, TransactionType.INCOME, "#EC4899"),
        CategoryInfo("Rental", PhosphorIcons.Bold.House, TransactionType.INCOME, "#14B8A6"),
        CategoryInfo("Other", PhosphorIcons.Bold.Money, TransactionType.INCOME, "#06B6D4")
    )
    val incomeCategories = defaultIncomeCategories

    fun getCategoryInfo(name: String, type: TransactionType): CategoryInfo {
        if (name.equals("Auto", ignoreCase = true)) {
            return CategoryInfo("Auto", PhosphorIcons.Bold.Sparkle, type, "#6366F1")
        }
        val list = if (type == TransactionType.INCOME) defaultIncomeCategories else defaultExpenseCategories
        return list.find { it.name.equals(name, ignoreCase = true) }
            ?: (if (type == TransactionType.INCOME)
                CategoryInfo(name, PhosphorIcons.Bold.Money, TransactionType.INCOME, "#10B981")
            else
                CategoryInfo(name, PhosphorIcons.Bold.CreditCard, TransactionType.EXPENSE, "#FF6B6B"))
    }
}
