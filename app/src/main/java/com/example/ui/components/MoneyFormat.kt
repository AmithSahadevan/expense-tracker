package com.example.ui.components

import java.util.Locale
import kotlin.math.abs

/** "₹8,000" for whole amounts, "₹8,000.50" otherwise. Sign is not included. */
fun formatMoney(currency: String, amount: Double): String {
    val value = abs(amount)
    val pattern = if (value % 1.0 == 0.0) "%,.0f" else "%,.2f"
    return "$currency${String.format(Locale.US, pattern, value)}"
}
