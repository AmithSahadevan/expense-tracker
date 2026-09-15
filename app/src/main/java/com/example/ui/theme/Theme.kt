package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkGrey,
    onPrimary = OffWhiteBg,
    primaryContainer = OffWhiteBg,
    onPrimaryContainer = DarkGrey,
    secondary = PunchyCoral,
    onSecondary = Color.White,
    tertiary = MintGreen,
    onTertiary = Color.White,
    background = DarkGrey,
    onBackground = OffWhiteBg,
    surface = Color(0xFF2C2C2E),
    onSurface = OffWhiteBg,
    surfaceVariant = Color(0xFF3A3A3C),
    onSurfaceVariant = MediumGrey,
    outline = BorderGrey
)

private val LightColorScheme = lightColorScheme(
    primary = DarkGrey,
    onPrimary = OffWhiteBg,
    primaryContainer = OffWhiteBg,
    onPrimaryContainer = DarkGrey,
    secondary = PunchyCoral,
    onSecondary = Color.White,
    secondaryContainer = MediumGrey,
    onSecondaryContainer = DarkText,
    tertiary = MintGreen,
    onTertiary = Color.White,
    background = OffWhiteBg,
    onBackground = DarkText,
    surface = LightGrey,
    onSurface = DarkText,
    surfaceVariant = MediumGrey,
    onSurfaceVariant = MutedText,
    outline = BorderGrey
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
