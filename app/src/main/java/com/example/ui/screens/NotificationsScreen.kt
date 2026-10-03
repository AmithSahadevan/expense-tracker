package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationCenterUiState
import com.example.data.model.NotificationEntry
import com.example.data.model.NotificationKind
import com.example.data.model.NotificationSeverity
import com.example.data.model.RelativeDates
import com.example.ui.components.AppChrome
import com.example.ui.components.ChoicePill
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.GradientTopBar
import com.example.ui.components.PhosphorIcons
import com.example.ui.theme.MintGreen
import com.example.ui.theme.PunchyCoral
import com.example.ui.theme.SkyAzure
import com.example.ui.theme.SunnyYellow
import kotlinx.coroutines.launch

/** Everything the page can do to notifications, handed over as one object instead of a lambda apiece. */
class NotificationActions(
    val onMarkRead: (String) -> Unit = {},
    val onMarkAllRead: () -> Unit = {},
    val onDismiss: (Collection<String>) -> Unit = {},
    val onRestore: (Collection<String>) -> Unit = {},
    val onRestoreAllDismissed: () -> Unit = {},
    val onSetCategoryEnabled: (NotificationCategory, Boolean) -> Unit = { _, _ -> }
)

private sealed interface NotificationFilter {
    data object All : NotificationFilter
    data object Unread : NotificationFilter
    data class Category(val category: NotificationCategory) : NotificationFilter
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    center: NotificationCenterUiState,
    actions: NotificationActions,
    onNavigateTo: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf<NotificationFilter>(NotificationFilter.All) }
    var showSettings by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val now = if (center.now > 0L) center.now else System.currentTimeMillis()
    val allMuted = center.mutedCategories.size == NotificationCategory.entries.size

    val visible = remember(center, filter) {
        center.entries.filter { entry ->
            when (val f = filter) {
                NotificationFilter.All -> true
                NotificationFilter.Unread -> !entry.isRead
                is NotificationFilter.Category -> entry.notification.category == f.category
            }
        }
    }

    // Clearing is always undoable: the ids are kept for the snackbar's action.
    val dismiss: (List<String>, String) -> Unit = { ids, message ->
        actions.onDismiss(ids)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) actions.onRestore(ids)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("notifications_screen"),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = AppChrome.topContentPadding,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
            ),
        ) {
            item(key = "filters") {
                FilterRow(
                    center = center,
                    selected = filter,
                    onSelect = { filter = it },
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (visible.isEmpty()) {
                item(key = "empty") {
                    EmptyNotifications(
                        filter = filter,
                        hasAnyEntries = center.entries.isNotEmpty(),
                        allMuted = allMuted,
                        onShowAll = { filter = NotificationFilter.All },
                        onOpenSettings = { showSettings = true }
                    )
                }
            } else {
                itemsIndexed(visible, key = { _, entry -> entry.notification.id }) { index, entry ->
                    NotificationRow(
                        entry = entry,
                        now = now,
                        showDivider = index < visible.lastIndex,
                        onOpen = {
                            actions.onMarkRead(entry.notification.id)
                            onNavigateTo(entry.notification.route)
                        },
                        onDismiss = { dismiss(listOf(entry.notification.id), "Notification cleared") },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }

        GradientTopBar(
            title = "Notifications",
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .testTag("notifications_back_button")
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.ArrowLeft,
                        contentDescription = "Back to Home",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = actions.onMarkAllRead,
                    enabled = center.unreadCount > 0,
                    modifier = Modifier.testTag("notifications_mark_all_read")
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.CheckCircle,
                        contentDescription = "Mark all as read",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = if (center.unreadCount > 0) 1f else 0.35f)
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.testTag("notifications_menu_button")
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Bold.DotsThree,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear all") },
                            enabled = center.entries.isNotEmpty(),
                            onClick = {
                                showMenu = false
                                val ids = center.entries.map { it.notification.id }
                                dismiss(ids, "Cleared ${ids.size} notification${if (ids.size == 1) "" else "s"}")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = PhosphorIcons.Bold.Trash,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("notifications_clear_all")
                        )
                        DropdownMenuItem(
                            text = { Text("Notification settings") },
                            onClick = {
                                showMenu = false
                                showSettings = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = PhosphorIcons.Bold.Gear,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.testTag("notifications_open_settings")
                        )
                    }
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        )
    }

    if (showSettings) {
        NotificationSettingsSheet(
            center = center,
            onSetCategoryEnabled = actions.onSetCategoryEnabled,
            onRestoreDismissed = actions.onRestoreAllDismissed,
            onDismiss = { showSettings = false }
        )
    }
}

// ----------------------------------------------------------------------- filters

@Composable
private fun FilterRow(
    center: NotificationCenterUiState,
    selected: NotificationFilter,
    onSelect: (NotificationFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = NotificationCategory.entries.filter { category ->
        center.countFor(category) > 0 || (selected as? NotificationFilter.Category)?.category == category
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.testTag("notifications_filters")
    ) {
        item(key = "all") {
            ChoicePill(
                label = "All",
                selected = selected == NotificationFilter.All,
                accent = Color.White,
                onClick = { onSelect(NotificationFilter.All) },
                modifier = Modifier.testTag("notifications_filter_all")
            )
        }
        item(key = "unread") {
            ChoicePill(
                label = if (center.unreadCount > 0) "Unread · ${center.unreadCount}" else "Unread",
                selected = selected == NotificationFilter.Unread,
                accent = Color.White,
                onClick = { onSelect(NotificationFilter.Unread) },
                modifier = Modifier.testTag("notifications_filter_unread")
            )
        }
        items(categories, key = { it.name }) { category ->
            val count = center.countFor(category)
            ChoicePill(
                label = if (count > 0) "${category.label} · $count" else category.label,
                selected = (selected as? NotificationFilter.Category)?.category == category,
                accent = Color.White,
                onClick = { onSelect(NotificationFilter.Category(category)) },
                modifier = Modifier.testTag("notifications_filter_${category.name.lowercase()}")
            )
        }
    }
}

// ------------------------------------------------------------------------- list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationRow(
    entry: NotificationEntry,
    now: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier
) {
    // The state outlives recompositions and keeps the first lambda it was given.
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDismiss()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier,
        backgroundContent = {
            // Only drawn mid-swipe, so it never tints the row at rest.
            if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(PunchyCoral.copy(alpha = 0.22f))
                        .padding(end = 24.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = PhosphorIcons.Bold.Trash,
                        contentDescription = null,
                        tint = PunchyCoral,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    ) {
        // Opaque, so the red backdrop only shows in the space the row has slid away from.
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            NotificationContent(entry = entry, now = now, onOpen = onOpen, onDismiss = onDismiss)
            if (showDivider) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
private fun NotificationContent(
    entry: NotificationEntry,
    now: Long,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    val n = entry.notification
    val tint = n.severity.color()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 14.dp)
            .semantics { stateDescription = if (entry.isRead) "Read" else "Unread" }
            .testTag("notification_${n.id}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = n.kind.icon(),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
            )
            if (!entry.isRead) {
                // Carved out of the page behind it, so the dot stays legible over the glyph.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(tint)
                        .testTag("notification_unread_dot")
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = n.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (entry.isRead) FontWeight.SemiBold else FontWeight.Black,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Nothing else dates the list now that the section headers are gone.
                Text(
                    text = RelativeDates.ago(n.timestamp, now),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = n.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(32.dp)
                .testTag("notification_dismiss")
        ) {
            Icon(
                imageVector = PhosphorIcons.Bold.X,
                contentDescription = "Dismiss notification",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

// ------------------------------------------------------------------ empty state

@Composable
private fun EmptyNotifications(
    filter: NotificationFilter,
    hasAnyEntries: Boolean,
    allMuted: Boolean,
    onShowAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val headline: String
    val subtext: String
    var actionText: String? = null
    var onAction: (() -> Unit)? = null
    when {
        allMuted -> {
            headline = "Notifications are switched off"
            subtext = "Every category is muted, so nothing will appear here or on the Home badge."
            actionText = "Notification settings"
            onAction = onOpenSettings
        }
        hasAnyEntries && filter == NotificationFilter.Unread -> {
            headline = "No unread notifications"
            subtext = "You've seen everything. New alerts will show up here as they happen."
            actionText = "Show all"
            onAction = onShowAll
        }
        hasAnyEntries -> {
            val label = (filter as? NotificationFilter.Category)?.category?.label ?: "this filter"
            headline = "Nothing in $label"
            subtext = "No notifications match right now."
            actionText = "Show all"
            onAction = onShowAll
        }
        else -> {
            headline = "You're all caught up"
            subtext = "Budget alerts, due dates, goal milestones and money activity will show up here as they happen."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .testTag("notifications_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = PhosphorIcons.Bold.Bell,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        FunkyEmptyState(
            icon = PhosphorIcons.Bold.Bell,
            headline = headline,
            subtext = subtext,
            actionButtonText = actionText,
            onActionClick = onAction
        )
    }
}

// ---------------------------------------------------------------- settings sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationSettingsSheet(
    center: NotificationCenterUiState,
    onSetCategoryEnabled: (NotificationCategory, Boolean) -> Unit,
    onRestoreDismissed: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("notification_settings_sheet")
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Notification settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose what appears here and counts toward the badge on Home.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            NotificationCategory.entries.forEach { category ->
                val enabled = category !in center.mutedCategories
                val accent = category.accent()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = if (enabled) 0.16f else 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon(),
                            contentDescription = null,
                            tint = accent.copy(alpha = if (enabled) 1f else 0.4f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = category.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { onSetCategoryEnabled(category, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("notification_toggle_${category.name.lowercase()}")
                    )
                }
            }

            if (center.dismissedCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onRestoreDismissed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notification_restore_dismissed")
                ) {
                    Text(
                        text = "Restore ${center.dismissedCount} cleared notification${if (center.dismissedCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------- styling

private fun NotificationSeverity.color(): Color = when (this) {
    NotificationSeverity.CRITICAL -> PunchyCoral
    NotificationSeverity.WARNING -> SunnyYellow
    NotificationSeverity.POSITIVE -> MintGreen
    NotificationSeverity.INFO -> SkyAzure
}

private fun NotificationKind.icon(): ImageVector = when (this) {
    NotificationKind.BUDGET_EXCEEDED -> PhosphorIcons.Bold.WarningCircle
    NotificationKind.BUDGET_NEARING -> PhosphorIcons.Bold.Warning
    NotificationKind.FLOW_OVERDUE -> PhosphorIcons.Bold.WarningCircle
    NotificationKind.FLOW_DUE_SOON -> PhosphorIcons.Bold.Calendar
    NotificationKind.GOAL_REACHED -> PhosphorIcons.Bold.Star
    NotificationKind.GOAL_ALMOST -> PhosphorIcons.Bold.Target
    NotificationKind.GOAL_DEADLINE -> PhosphorIcons.Bold.Calendar
    NotificationKind.GOAL_BEHIND -> PhosphorIcons.Bold.Info
    NotificationKind.GOALS_OVERALLOCATED -> PhosphorIcons.Bold.Warning
    NotificationKind.EMERGENCY_FUND_LOW -> PhosphorIcons.Bold.Shield
    NotificationKind.BALANCE_NEGATIVE -> PhosphorIcons.Bold.WarningCircle
    NotificationKind.BALANCE_LOW -> PhosphorIcons.Bold.Wallet
    NotificationKind.SPENDING_SPIKE -> PhosphorIcons.Bold.TrendUp
    NotificationKind.LARGE_EXPENSE -> PhosphorIcons.Bold.CreditCard
    NotificationKind.INCOME_RECEIVED -> PhosphorIcons.Bold.Money
    NotificationKind.AUTO_TRACKED -> PhosphorIcons.Bold.Sparkle
    NotificationKind.PAYDAY -> PhosphorIcons.Bold.Briefcase
    NotificationKind.SALARY_MISSING -> PhosphorIcons.Bold.Briefcase
    NotificationKind.RECURRING_DUE -> PhosphorIcons.Bold.ArrowsClockwise
    NotificationKind.WISHLIST_DATE -> PhosphorIcons.Bold.Star
    NotificationKind.LOG_REMINDER -> PhosphorIcons.Bold.PencilSimple
}

private fun NotificationCategory.icon(): ImageVector = when (this) {
    NotificationCategory.BUDGETS -> PhosphorIcons.Bold.ChartPie
    NotificationCategory.MONEY_FLOW -> PhosphorIcons.Bold.ArrowsLeftRight
    NotificationCategory.SAVINGS -> PhosphorIcons.Bold.Target
    NotificationCategory.SPENDING -> PhosphorIcons.Bold.Receipt
    NotificationCategory.AUTO_TRACKED -> PhosphorIcons.Bold.Sparkle
    NotificationCategory.REMINDERS -> PhosphorIcons.Bold.Calendar
}

private fun NotificationCategory.accent(): Color = when (this) {
    NotificationCategory.BUDGETS -> SkyAzure
    NotificationCategory.MONEY_FLOW -> Color(0xFF10B981)
    NotificationCategory.SAVINGS -> MintGreen
    NotificationCategory.SPENDING -> PunchyCoral
    NotificationCategory.AUTO_TRACKED -> Color(0xFF8B8DF5)
    NotificationCategory.REMINDERS -> SunnyYellow
}
