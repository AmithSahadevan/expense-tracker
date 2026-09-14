package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "Purchased",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** "Can afford" or "₹X more needed", always judged against Adult Money only. */
@Composable
fun AffordabilityChip(result: WishlistAffordability, currency: String, modifier: Modifier = Modifier) {
    val (label, background, showCheck) = when (result) {
        is WishlistAffordability.CanAfford -> Triple("Can afford", CanAffordGreen.copy(alpha = 0.16f), true)
        is WishlistAffordability.MoreNeeded ->
            Triple("${formatMoney(currency, result.amountNeeded)} more needed", MaterialTheme.colorScheme.surfaceVariant, false)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = background, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (showCheck) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
