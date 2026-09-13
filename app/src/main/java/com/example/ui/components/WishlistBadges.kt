package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.WishlistAffordability
import com.example.data.model.WishlistPriority

private val CanAffordGreen = Color(0xFF10B981)

private fun priorityColor(priority: String): Color = when (priority) {
    WishlistPriority.MUST_HAVE -> Color(0xFFFF6B6B)
    WishlistPriority.HIGH -> Color(0xFFFD79A8)
    else -> Color(0xFF6C5CE7)
}

/** Priority tag. Uses a solid surface so it stays legible on top of product photos. */
@Composable
fun PriorityBadge(priority: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = modifier
    ) {
        Text(
            text = WishlistPriority.label(priority).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 10.sp),
            color = priorityColor(priority),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun PurchasedBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
    ) {
        Text(
            text = "✓ Purchased",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/** "✓ Can afford" or "₹X more needed", always judged against Adult Money only. */
@Composable
fun AffordabilityChip(result: WishlistAffordability, currency: String, modifier: Modifier = Modifier) {
    val (label, background) = when (result) {
        is WishlistAffordability.CanAfford -> "✓ Can afford" to CanAffordGreen.copy(alpha = 0.16f)
        is WishlistAffordability.MoreNeeded ->
            "${formatMoney(currency, result.amountNeeded)} more needed" to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(8.dp), color = background, modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
