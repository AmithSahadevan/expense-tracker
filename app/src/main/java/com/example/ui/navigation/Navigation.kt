package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Home", Icons.Outlined.Home),
    TRANSACTIONS("transactions", "Transactions", Icons.AutoMirrored.Outlined.ReceiptLong),
    MONEY_FLOW("money_flow", "Money Flow", Icons.AutoMirrored.Outlined.CompareArrows),
    WISHLIST("wishlist", "Wishlist", Icons.Outlined.StarOutline),
    SAVINGS("savings", "Savings", Icons.Outlined.Savings),
    BUDGETS("budgets", "Budgets", Icons.Outlined.PieChart),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings)
}
