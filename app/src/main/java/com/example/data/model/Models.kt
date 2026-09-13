package com.example.data.model

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
    val emoji: String,
    val type: TransactionType,
    val colorHex: String
)

object CategoryRegistry {
    val defaultExpenseCategories = listOf(
        CategoryInfo("Food", "🍔", TransactionType.EXPENSE, "#FF6B6B"),
        CategoryInfo("Fuel", "⛽", TransactionType.EXPENSE, "#F59E0B"),
        CategoryInfo("Transport", "🚌", TransactionType.EXPENSE, "#3B82F6"),
        CategoryInfo("Clothes", "👕", TransactionType.EXPENSE, "#EC4899"),
        CategoryInfo("Bills", "⚡", TransactionType.EXPENSE, "#EF4444"),
        CategoryInfo("Mobile Recharge", "📱", TransactionType.EXPENSE, "#10B981"),
        CategoryInfo("Internet", "🌐", TransactionType.EXPENSE, "#06B6D4"),
        CategoryInfo("Subscriptions", "📺", TransactionType.EXPENSE, "#8B5CF6"),
        CategoryInfo("Shopping", "🛍️", TransactionType.EXPENSE, "#F97316"),
        CategoryInfo("Entertainment", "🍿", TransactionType.EXPENSE, "#6366F1"),
        CategoryInfo("Health", "💊", TransactionType.EXPENSE, "#14B8A6"),
        CategoryInfo("Travel", "✈️", TransactionType.EXPENSE, "#0EA5E9"),
        CategoryInfo("Education", "📚", TransactionType.EXPENSE, "#A855F7"),
        CategoryInfo("Other", "📦", TransactionType.EXPENSE, "#64748B")
    )
    val expenseCategories = defaultExpenseCategories

    val defaultIncomeCategories = listOf(
        CategoryInfo("Salary", "💼", TransactionType.INCOME, "#10B981"),
        CategoryInfo("Freelance", "💻", TransactionType.INCOME, "#6366F1"),
        CategoryInfo("Investments", "📈", TransactionType.INCOME, "#8B5CF6"),
        CategoryInfo("Side Gig", "🚀", TransactionType.INCOME, "#F59E0B"),
        CategoryInfo("Bonus", "🎁", TransactionType.INCOME, "#EC4899"),
        CategoryInfo("Rental", "🏠", TransactionType.INCOME, "#14B8A6"),
        CategoryInfo("Other", "💵", TransactionType.INCOME, "#06B6D4")
    )
    val incomeCategories = defaultIncomeCategories

    fun getCategoryInfo(name: String, type: TransactionType): CategoryInfo {
        val list = if (type == TransactionType.INCOME) defaultIncomeCategories else defaultExpenseCategories
        return list.find { it.name.equals(name, ignoreCase = true) }
            ?: (if (type == TransactionType.INCOME)
                CategoryInfo(name, "💸", TransactionType.INCOME, "#10B981")
            else
                CategoryInfo(name, "💳", TransactionType.EXPENSE, "#FF6B6B"))
    }
}
