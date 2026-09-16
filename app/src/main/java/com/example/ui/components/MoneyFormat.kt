package com.example.ui.components

import java.util.Locale
import kotlin.math.abs

/** "₹8,000" for whole amounts, "₹8,000.50" otherwise. Sign is not included. */
fun formatMoney(currency: String, amount: Double): String {
    val value = abs(amount)
    return "$currency${String.format(Locale.US, "%,.0f", value)}"
}
