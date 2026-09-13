package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val emoji: String
) {
    HOME("home", "Home", Icons.Default.Home, "🏠"),
    TRANSACTIONS("transactions", "Transactions", Icons.Default.ReceiptLong, "🧾"),
    MONEY_FLOW("money_flow", "Money Flow", Icons.Default.CompareArrows, "🤝"),
    WISHLIST("wishlist", "Wishlist", Icons.Default.Star, "✨"),
    SAVINGS("savings", "Savings", Icons.Default.Savings, "🎯"),
    BUDGETS("budgets", "Budgets", Icons.Default.PieChart, "📊"),
    SETTINGS("settings", "Settings", Icons.Default.Settings, "⚙️")
}
