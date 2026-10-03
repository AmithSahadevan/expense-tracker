package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.model.AppNotification
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationCenterUiState
import com.example.data.model.NotificationEntry
import com.example.data.model.NotificationKind
import com.example.data.model.NotificationSeverity
import com.example.ui.screens.NotificationActions
import com.example.ui.screens.NotificationsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w420dp-h2400dp")
class NotificationsScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val now = System.currentTimeMillis()

    private val overBudget = AppNotification(
        id = "budget_over:1:0",
        kind = NotificationKind.BUDGET_EXCEEDED,
        category = NotificationCategory.BUDGETS,
        severity = NotificationSeverity.CRITICAL,
        title = "Food budget exceeded",
        message = "You've spent ₹1,200 of ₹1,000.",
        timestamp = now - 3_600_000L,
        route = "budgets"
    )
    private val income = AppNotification(
        id = "income:7",
        kind = NotificationKind.INCOME_RECEIVED,
        category = NotificationCategory.SPENDING,
        severity = NotificationSeverity.POSITIVE,
        title = "₹50,000 received",
        message = "Monthly Salary was added to your income.",
        timestamp = now - 60_000L,
        route = "transactions"
    )

    private val marked = mutableListOf<String>()
    private var markedAll = 0
    private val dismissed = mutableListOf<String>()
    private val restored = mutableListOf<String>()
    private var restoredAll = 0
    private val toggles = mutableListOf<Pair<NotificationCategory, Boolean>>()
    private val navigated = mutableListOf<String>()
    private var wentBack = 0

    private val actions = NotificationActions(
        onMarkRead = { marked += it },
        onMarkAllRead = { markedAll++ },
        onDismiss = { dismissed += it },
        onRestore = { restored += it },
        onRestoreAllDismissed = { restoredAll++ },
        onSetCategoryEnabled = { category, enabled -> toggles += category to enabled }
    )

    private fun center(
        vararg entries: NotificationEntry,
        dismissedCount: Int = 0,
        muted: Set<NotificationCategory> = emptySet()
    ) = NotificationCenterUiState(entries.toList(), dismissedCount, muted, now)

    private fun show(center: NotificationCenterUiState) {
        composeTestRule.setContent {
            MyApplicationTheme {
                NotificationsScreen(
                    center = center,
                    actions = actions,
                    onNavigateTo = { navigated += it },
                    onBack = { wentBack++ }
                )
            }
        }
    }

    @Test
    fun notificationsAreOneFlatListWithNoSectionHeadings() {
        show(center(NotificationEntry(overBudget, isRead = false), NotificationEntry(income, isRead = true)))

        composeTestRule.onNodeWithText("Food budget exceeded").assertIsDisplayed()
        composeTestRule.onNodeWithText("₹50,000 received").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("NEEDS ATTENTION").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("TODAY").assertCountEquals(0)
        // One unread item means one dot.
        composeTestRule.onAllNodesWithTag("notification_unread_dot", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aRowCarriesItsHeadingMessageAndTimeAndNothingElse() {
        show(center(NotificationEntry(overBudget, isRead = false)))

        composeTestRule.onNodeWithText("Food budget exceeded").assertIsDisplayed()
        composeTestRule.onNodeWithText("You've spent ₹1,200 of ₹1,000.").assertIsDisplayed()
        composeTestRule.onNodeWithText("1h ago").assertIsDisplayed()
        // The category caption and the "View ›" affordance are gone from the row.
        composeTestRule.onAllNodesWithText("View", substring = true).assertCountEquals(0)
    }

    @Test
    fun unreadFilterShowsHowManyAreUnread() {
        show(center(NotificationEntry(overBudget, isRead = false), NotificationEntry(income, isRead = false)))

        composeTestRule.onNodeWithText("Unread · 2").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("notifications_summary").assertCountEquals(0)
    }

    @Test
    fun tappingANotificationMarksItReadAndOpensItsPage() {
        show(center(NotificationEntry(overBudget, isRead = false)))

        composeTestRule.onNodeWithTag("notification_budget_over:1:0").performClick()

        assertEquals(listOf("budget_over:1:0"), marked)
        assertEquals(listOf("budgets"), navigated)
    }

    @Test
    fun dismissingOffersAnUndoThatBringsTheNotificationBack() {
        show(center(NotificationEntry(overBudget, isRead = false)))

        composeTestRule.onNodeWithTag("notification_dismiss").performClick()

        assertEquals(listOf("budget_over:1:0"), dismissed)
        composeTestRule.onNodeWithText("Notification cleared").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("budget_over:1:0"), restored)
    }

    @Test
    fun clearAllDismissesEveryVisibleNotification() {
        show(center(NotificationEntry(overBudget, isRead = false), NotificationEntry(income, isRead = true)))

        composeTestRule.onNodeWithTag("notifications_menu_button").performClick()
        composeTestRule.onNodeWithTag("notifications_clear_all").performClick()

        assertEquals(setOf("budget_over:1:0", "income:7"), dismissed.toSet())
        composeTestRule.onNodeWithText("Cleared 2 notifications").assertIsDisplayed()
    }

    @Test
    fun markAllReadIsOnlyAvailableWhileSomethingIsUnread() {
        show(center(NotificationEntry(overBudget, isRead = false)))
        composeTestRule.onNodeWithTag("notifications_mark_all_read").assertIsEnabled().performClick()
        assertEquals(1, markedAll)
    }

    @Test
    fun markAllReadIsDisabledWhenEverythingIsRead() {
        show(center(NotificationEntry(overBudget, isRead = true)))
        composeTestRule.onNodeWithTag("notifications_mark_all_read").assertIsNotEnabled()
    }

    @Test
    fun unreadFilterHidesReadNotifications() {
        show(center(NotificationEntry(overBudget, isRead = false), NotificationEntry(income, isRead = true)))

        composeTestRule.onNodeWithTag("notifications_filter_unread").performClick()

        composeTestRule.onNodeWithText("Food budget exceeded").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("notification_income:7").assertCountEquals(0)
    }

    @Test
    fun categoryFilterNarrowsTheList() {
        show(center(NotificationEntry(overBudget, isRead = false), NotificationEntry(income, isRead = false)))

        composeTestRule.onNodeWithTag("notifications_filter_spending").performClick()

        composeTestRule.onNodeWithText("₹50,000 received").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("notification_budget_over:1:0").assertCountEquals(0)
    }

    @Test
    fun emptyCentreSaysYoureAllCaughtUp() {
        show(center())

        composeTestRule.onNodeWithText("You're all caught up").assertIsDisplayed()
    }

    @Test
    fun emptyUnreadFilterOffersToShowEverything() {
        show(center(NotificationEntry(overBudget, isRead = true)))

        composeTestRule.onNodeWithTag("notifications_filter_unread").performClick()

        composeTestRule.onNodeWithText("No unread notifications").assertIsDisplayed()
        composeTestRule.onNodeWithText("Show all").performClick()
        composeTestRule.onNodeWithText("Food budget exceeded").assertIsDisplayed()
    }

    @Test
    fun settingsSheetTogglesCategoriesAndRestoresClearedNotifications() {
        show(center(NotificationEntry(overBudget, isRead = false), dismissedCount = 3, muted = setOf(NotificationCategory.AUTO_TRACKED)))

        composeTestRule.onNodeWithTag("notifications_menu_button").performClick()
        composeTestRule.onNodeWithTag("notifications_open_settings").performClick()

        composeTestRule.onNodeWithTag("notification_settings_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("notification_toggle_budgets").performClick()
        composeTestRule.onNodeWithTag("notification_toggle_auto_tracked").performClick()
        assertEquals(
            listOf(NotificationCategory.BUDGETS to false, NotificationCategory.AUTO_TRACKED to true),
            toggles
        )

        composeTestRule.onNodeWithText("Restore 3 cleared notifications").performClick()
        assertEquals(1, restoredAll)
    }

    @Test
    fun backArrowReturnsHome() {
        show(center())

        composeTestRule.onNodeWithTag("notifications_back_button").performClick()

        assertTrue(wentBack == 1)
    }
}
