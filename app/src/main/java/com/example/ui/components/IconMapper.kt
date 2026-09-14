package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {
    fun mapEmojiToIcon(emoji: String): ImageVector {
        return when (emoji) {
            "🍔" -> Icons.Outlined.Restaurant
            "⛽" -> Icons.Outlined.LocalGasStation
            "🚌" -> Icons.Outlined.DirectionsBus
            "👕" -> Icons.Outlined.Checkroom
            "⚡" -> Icons.Outlined.Bolt
            "📱" -> Icons.Outlined.Smartphone
            "🌐" -> Icons.Outlined.Language
            "📺" -> Icons.Outlined.Subscriptions
            "🛍️" -> Icons.Outlined.ShoppingBag
            "🍿" -> Icons.Outlined.Movie
            "💊" -> Icons.Outlined.MedicalServices
            "✈️" -> Icons.Outlined.Flight
            "📚" -> Icons.Outlined.School
            "📦" -> Icons.Outlined.Category
            "💼" -> Icons.Outlined.Work
            "💻" -> Icons.Outlined.Computer
            "📈" -> Icons.AutoMirrored.Outlined.TrendingUp
            "🚀" -> Icons.Outlined.RocketLaunch
            "🎁" -> Icons.Outlined.Redeem
            "🏠" -> Icons.Outlined.HomeWork
            "💵" -> Icons.Outlined.Payments
            "💸" -> Icons.Outlined.AccountBalanceWallet
            "💳" -> Icons.Outlined.CreditCard
            "🏦" -> Icons.Outlined.AccountBalance
            "🤝" -> Icons.Outlined.SwapHoriz
            "🎯" -> Icons.Outlined.Savings
            "📊" -> Icons.Outlined.BarChart
            "⚙️" -> Icons.Outlined.Settings
            "✨" -> Icons.Outlined.StarOutline
            "🛡️" -> Icons.Outlined.Shield
            "🦊" -> Icons.Outlined.Person
            "🐱" -> Icons.Outlined.Person
            "🌈" -> Icons.Outlined.Person
            "👾" -> Icons.Outlined.Person
            "🛹" -> Icons.Outlined.Person
            "🍕" -> Icons.Outlined.Person
            "🥑" -> Icons.Outlined.Person
            "🎧" -> Icons.Outlined.Person
            "💎" -> Icons.Outlined.Person
            "🏷️" -> Icons.Outlined.LocalOffer
            else -> Icons.Outlined.Category
        }
    }
}
