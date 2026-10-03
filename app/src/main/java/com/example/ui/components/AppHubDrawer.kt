package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.ui.navigation.AppDestination

internal fun AppDestination.drawerSubtitle(): String = when (this) {
    AppDestination.HOME -> "Balance, recent activity & insights"
    AppDestination.TRANSACTIONS -> "Your full income & expense ledger"
    AppDestination.WISHLIST -> "Things you're saving up to buy"
    AppDestination.SAVINGS -> "Adult Money & protected Emergency Fund"
    AppDestination.MONEY_FLOW -> "Track debts, IOUs & shared expenses"
    AppDestination.BUDGETS -> "Manage spending caps & allocations"
    AppDestination.NOTIFICATIONS -> "Budget alerts, dues & money activity"
    AppDestination.PROFILE -> "Your profile, account & spending stats"
    AppDestination.SETTINGS -> "App preferences, detection & backup"
    AppDestination.EXPORT -> "Save your money records as a spreadsheet"
}

/**
 * The app hub, presented as a side drawer. This replaces the old bottom-sheet "More" hub
 * so the dock can stay down to Home / Transactions / Wishlist / Add / Profile.
 */
@Composable
fun AppHubDrawerContent(
    currentUser: UserEntity?,
    currentDestination: AppDestination,
    accountCount: Int,
    onNavigate: (AppDestination) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
    unreadNotifications: Int = 0,
    onExportExcel: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .testTag("app_hub_drawer"),
    ) {
        // Profile header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSwitchProfile() }
                    .testTag("drawer_profile_header"),
            ) {
                ProfileAvatar(currentUser = currentUser, size = 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentUser?.displayName ?: "Your Wallet",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = currentUser?.username?.let { "@$it" } ?: "Tap to set up",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AppDestination.entries.filter { it != AppDestination.EXPORT }.forEach { destination ->
                DrawerItem(
                    icon = destination.icon,
                    label = destination.title,
                    subtitle = destination.drawerSubtitle(),
                    isSelected = destination == currentDestination,
                    onClick = { onNavigate(destination) },
                    badge = if (destination == AppDestination.NOTIFICATIONS) unreadNotifications else 0,
                    modifier = Modifier.testTag("drawer_item_${destination.route}"),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))

            DrawerItem(
                icon = PhosphorIcons.Bold.Users,
                label = "Switch Profile",
                subtitle = if (accountCount > 0) "$accountCount account${if (accountCount == 1) "" else "s"} on this device" else "Add an account",
                isSelected = false,
                onClick = onSwitchProfile,
                modifier = Modifier.testTag("drawer_item_switch_profile"),
            )

            DrawerItem(
                icon = AppDestination.EXPORT.icon,
                label = AppDestination.EXPORT.title,
                subtitle = AppDestination.EXPORT.drawerSubtitle(),
                isSelected = currentDestination == AppDestination.EXPORT,
                onClick = onExportExcel,
                modifier = Modifier.testTag("drawer_item_export_excel"),
            )
        }

        Spacer(modifier = Modifier.navigationBarsPadding())
    }
}

@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    val pill = if (isSelected) {
        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
    } else {
        Color.Transparent
    }
    val ink = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isSelected) 1f else 0.72f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    fontSize = 15.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (badge > 0) {
            CountBadge(count = badge, modifier = Modifier.testTag("drawer_notification_badge"))
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onBackground),
            )
        }
    }
}
