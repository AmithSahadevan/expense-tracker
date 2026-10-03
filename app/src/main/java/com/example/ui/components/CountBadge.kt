package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
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
import com.example.ui.theme.PunchyCoral

/** A small pill with a count in it: unread notifications on the bell and in the drawer. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    color: Color = PunchyCoral
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
            color = Color.White
        )
    }
}
