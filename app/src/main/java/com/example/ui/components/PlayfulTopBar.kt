package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.data.local.entities.UserEntity

@Composable
fun PlayfulTopBar(
    currentUser: UserEntity?,
    onUserClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("playful_top_bar"),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 0.dp),
            contentAlignment = Alignment.Center
        ) {
            // Left side: Profile Icon
            val avatarColor = remember(currentUser?.avatarColorHex) {
                try {
                    Color(android.graphics.Color.parseColor(currentUser?.avatarColorHex ?: "#0C0F14"))
                } catch (_: Exception) {
                    null
                }
            }

            val onSurface = MaterialTheme.colorScheme.onSurface
            val isDarkAvatar = currentUser?.avatarColorHex?.lowercase() == "#0c0f14" || 
                              currentUser?.avatarColorHex?.lowercase() == "#242426"
            val displayColor = if (isDarkAvatar || avatarColor == null) onSurface else avatarColor

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(displayColor.copy(alpha = 0.15f))
                    .clickable { onUserClick() }
                    .testTag("user_profile_icon"),
                contentAlignment = Alignment.Center
            ) {
                if (currentUser?.avatarImagePath != null) {
                    AsyncImage(
                        model = "file:///android_asset/${currentUser.avatarImagePath}",
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = displayColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Text(
                text = "Kyash",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
