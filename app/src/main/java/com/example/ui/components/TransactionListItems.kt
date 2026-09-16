package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CategoryRegistry
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.util.Locale

@Composable
fun TransactionRowItem(
    item: TransactionItem,
    onEdit: () -> Unit
) {
    val catInfo = CategoryRegistry.getCategoryInfo(item.category, item.type)
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon Circle - Smaller (36dp)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(catInfo.colorHex.toColor()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = catInfo.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Title - Smaller font (15sp)
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Amount - Smaller font (16sp)
        Text(
            text = "${if (item.type == TransactionType.EXPENSE) "-" else ""}${String.format(Locale.US, "%,.0f", item.amount)}",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Helper to convert hex string to Color
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (_: Exception) {
        Color.Gray
    }
}
