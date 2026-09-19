package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.BubblegumPink
import com.example.ui.theme.DarkGrey
import com.example.ui.theme.MintGreen
import com.example.ui.theme.SkyAzure
import com.example.ui.theme.SunnyYellow
import kotlin.math.abs

private val placeholderAccents = listOf(DarkGrey, BubblegumPink, SkyAzure, MintGreen, SunnyYellow)

/**
 * Product photo loaded from a user-entered image link. Shows a tinted placeholder while there is
 * no link, while loading, or when the link can't be loaded.
 */
@Composable
fun ProductImage(
    imageUrl: String,
    productName: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholderFontSize: Int = 40
) {
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    val accent = placeholderAccents[abs(productName.hashCode()) % placeholderAccents.size]

    Box(
        modifier = modifier.background(
            if (loaded) Brush.linearGradient(listOf(Color.White, Color.White))
            else Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.08f)))
        ),
        contentAlignment = Alignment.Center
    ) {
        if (!loaded) {
            Text(text = "🛍️", fontSize = placeholderFontSize.sp)
        }
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
