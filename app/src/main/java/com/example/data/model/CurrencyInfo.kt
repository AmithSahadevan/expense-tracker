package com.example.data.model

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String,
    val flag: String // Emoji flag
)

object Currencies {
    val all = listOf(
        CurrencyInfo("INR", "₹", "India", "🇮🇳"),
        CurrencyInfo("USD", "$", "United States", "🇺🇸"),
        CurrencyInfo("EUR", "€", "European Union", "🇪🇺"),
        CurrencyInfo("GBP", "£", "United Kingdom", "🇬🇧"),
        CurrencyInfo("JPY", "¥", "Japan", "🇯🇵"),
        CurrencyInfo("CNY", "¥", "China", "🇨🇳"),
        CurrencyInfo("AUD", "A$", "Australia", "🇦🇺"),
        CurrencyInfo("CAD", "C$", "Canada", "🇨🇦"),
        CurrencyInfo("CHF", "Fr", "Switzerland", "🇨🇭"),
        CurrencyInfo("AED", "د.إ", "United Arab Emirates", "🇦🇪"),
        CurrencyInfo("SGD", "S$", "Singapore", "🇸🇬"),
        CurrencyInfo("NZD", "NZ$", "New Zealand", "🇳🇿"),
        CurrencyInfo("KRW", "₩", "South Korea", "🇰🇷"),
        CurrencyInfo("BRL", "R$", "Brazil", "🇧🇷"),
        CurrencyInfo("RUB", "₽", "Russia", "🇷🇺"),
        CurrencyInfo("ZAR", "R", "South Africa", "🇿🇦"),
        CurrencyInfo("MYR", "RM", "Malaysia", "🇲🇾"),
        CurrencyInfo("IDR", "Rp", "Indonesia", "🇮🇩"),
        CurrencyInfo("THB", "฿", "Thailand", "🇹🇭"),
        CurrencyInfo("PHP", "₱", "Philippines", "🇵🇭"),
        CurrencyInfo("VND", "₫", "Vietnam", "🇻🇳"),
        CurrencyInfo("SAR", "﷼", "Saudi Arabia", "🇸🇦"),
        CurrencyInfo("TRY", "₺", "Turkey", "🇹🇷"),
        CurrencyInfo("MXN", "$", "Mexico", "🇲🇽")
    ).sortedBy { it.name }
}
