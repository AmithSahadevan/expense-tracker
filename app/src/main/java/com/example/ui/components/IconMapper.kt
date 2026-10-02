package com.example.ui.components

import androidx.compose.ui.graphics.vector.ImageVector

object IconMapper {
    fun mapEmojiToIcon(emoji: String): ImageVector {
        return when (emoji) {
            "🍔" -> PhosphorIcons.Bold.ForkKnife
            "⛽" -> PhosphorIcons.Bold.GasPump
            "🚌" -> PhosphorIcons.Bold.Bus
            "👕" -> PhosphorIcons.Bold.TShirt
            "⚡" -> PhosphorIcons.Bold.Lightning
            "📱" -> PhosphorIcons.Bold.DeviceMobile
            "🌐" -> PhosphorIcons.Bold.Globe
            "📺" -> PhosphorIcons.Bold.Television
            "🛍️" -> PhosphorIcons.Bold.ShoppingBag
            "🍿" -> PhosphorIcons.Bold.FilmStrip
            "💊" -> PhosphorIcons.Bold.FirstAid
            "✈️" -> PhosphorIcons.Bold.Airplane
            "📚" -> PhosphorIcons.Bold.GraduationCap
            "📦" -> PhosphorIcons.Bold.Package
            "💼" -> PhosphorIcons.Bold.Briefcase
            "💻" -> PhosphorIcons.Bold.Desktop
            "📈" -> PhosphorIcons.Bold.TrendUp
            "🚀" -> PhosphorIcons.Bold.Rocket
            "🎁" -> PhosphorIcons.Bold.Gift
            "🏠" -> PhosphorIcons.Bold.House
            "💵" -> PhosphorIcons.Bold.Money
            "💸" -> PhosphorIcons.Bold.Wallet
            "💳" -> PhosphorIcons.Bold.CreditCard
            "🏦" -> PhosphorIcons.Bold.Bank
            "🤝" -> PhosphorIcons.Bold.ArrowsLeftRight
            "🎯" -> PhosphorIcons.Bold.Target
            "📊" -> PhosphorIcons.Bold.ChartBar
            "⚙️" -> PhosphorIcons.Bold.Gear
            "✨" -> PhosphorIcons.Bold.Sparkle
            "🛡️" -> PhosphorIcons.Bold.Shield
            "🦊" -> PhosphorIcons.Bold.User
            "🐱" -> PhosphorIcons.Bold.User
            "🌈" -> PhosphorIcons.Bold.User
            "👾" -> PhosphorIcons.Bold.User
            "🛹" -> PhosphorIcons.Bold.User
            "🍕" -> PhosphorIcons.Bold.User
            "🥑" -> PhosphorIcons.Bold.User
            "🎧" -> PhosphorIcons.Bold.User
            "💎" -> PhosphorIcons.Bold.User
            "🏷️" -> PhosphorIcons.Bold.Tag
            else -> PhosphorIcons.Bold.Package
        }
    }
}
