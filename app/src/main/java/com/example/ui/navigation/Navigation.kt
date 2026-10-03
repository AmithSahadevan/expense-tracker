package com.example.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.ui.components.PhosphorIcons

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    HOME("home", "Home", PhosphorIcons.Bold.House),
    TRANSACTIONS("transactions", "Transactions", PhosphorIcons.Bold.Receipt),
    MONEY_FLOW("money_flow", "Money Flow", PhosphorIcons.Bold.ArrowsLeftRight),
    WISHLIST("wishlist", "Wishlist", PhosphorIcons.Bold.Star),
    SAVINGS("savings", "Savings", PhosphorIcons.Bold.Target),
    BUDGETS("budgets", "Budgets", PhosphorIcons.Bold.ChartPie),
    NOTIFICATIONS("notifications", "Notifications", PhosphorIcons.Bold.Bell),
    PROFILE("profile", "Profile", PhosphorIcons.Bold.User),
    SETTINGS("settings", "Settings", PhosphorIcons.Bold.Gear),
    EXPORT("export", "Export as Excel", PhosphorIcons.Bold.DownloadSimple)
}
