package com.example

import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.BudgetScope
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.local.preferences.NotificationStateStore
import com.example.data.model.AppNotification
import com.example.data.model.NotificationCategory
import com.example.data.model.NotificationCenter
import com.example.data.model.NotificationEngine
import com.example.data.model.NotificationInputs
import com.example.data.model.NotificationKind
import com.example.data.model.NotificationSeverity
import com.example.data.model.NotificationUserState
import com.example.data.model.RelativeDates
import com.example.data.model.SavingsGoalCalculator
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NotificationEngineTest {

    /** 15 September 2026, noon: mid-month, so last month and next month are both easy to land on. */
    private val now = at(day = 15, hour = 12)

    private fun at(day: Int, hour: Int = 10, month: Int = Calendar.SEPTEMBER): Long =
        Calendar.getInstance().apply {
            set(2026, month, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun expense(
        id: Long,
        amount: Double,
        date: Long,
        category: String = "Food",
        title: String = "e$id",
        recurrence: String = "NONE",
        method: String = "CARD"
    ) = TransactionItem(id, 1, title, amount, TransactionType.EXPENSE, category, date, paymentMethod = method, recurrence = recurrence)

    private fun income(
        id: Long,
        amount: Double,
        date: Long,
        title: String = "i$id",
        category: String = "Salary",
        method: String = "BANK"
    ) = TransactionItem(id, 1, title, amount, TransactionType.INCOME, category, date, paymentMethod = method)

    private fun budget(id: Long, category: String, allocated: Double, scope: String = BudgetScope.CATEGORY) =
        BudgetEntity(id = id, userId = 1, category = category, allocatedAmount = allocated, scope = scope, createdAt = 0L)

    private fun flow(
        id: Long,
        person: String,
        direction: String,
        amount: Double,
        dueDate: Long?,
        settled: Boolean = false
    ) = MoneyFlowEntity(
        id = id, userId = 1, personName = person, direction = direction, amount = amount,
        dueDate = dueDate, isSettled = settled, createdAt = 0L
    )

    private fun goal(id: Long, title: String, target: Double, saved: Double, targetDate: Long? = null, createdAt: Long = at(1)) =
        SavingsGoalEntity(
            id = id, userId = 1, title = title, goalAmount = target, savedAmount = saved,
            targetDate = targetDate, createdAt = createdAt
        )

    private fun generate(inputs: NotificationInputs, at: Long = now) = NotificationEngine.generate(inputs, at)

    private fun List<AppNotification>.ofKind(kind: NotificationKind) = filter { it.kind == kind }

    // ------------------------------------------------------------------ budgets

    @Test
    fun budgetPastItsLimitIsCriticalAndDatedByTheExpenseThatBrokeIt() {
        val inputs = NotificationInputs(
            budgets = listOf(budget(1, "Food", 1_000.0)),
            transactions = listOf(
                expense(1, 500.0, at(2)),
                expense(2, 400.0, at(5)),
                expense(3, 300.0, at(10))
            )
        )

        val alert = generate(inputs).ofKind(NotificationKind.BUDGET_EXCEEDED).single()

        assertEquals(NotificationSeverity.CRITICAL, alert.severity)
        assertEquals(NotificationCategory.BUDGETS, alert.category)
        assertEquals("Food budget exceeded", alert.title)
        assertTrue(alert.message, alert.message.contains("₹1,200 of ₹1,000"))
        assertTrue(alert.message, alert.message.contains("₹200 over"))
        // 500 + 400 = 900 is still inside the envelope; the 10th is the expense that tipped it over.
        assertEquals(at(10), alert.timestamp)
        assertEquals("budgets", alert.route)
    }

    @Test
    fun budgetNearingItsLimitWarnsAtEightyPercentAndIsDatedByTheCrossing() {
        val inputs = NotificationInputs(
            budgets = listOf(budget(1, "Food", 1_000.0)),
            transactions = listOf(expense(1, 500.0, at(2)), expense(2, 350.0, at(5)))
        )

        val alerts = generate(inputs)

        assertTrue(alerts.ofKind(NotificationKind.BUDGET_EXCEEDED).isEmpty())
        val alert = alerts.ofKind(NotificationKind.BUDGET_NEARING).single()
        assertEquals(NotificationSeverity.WARNING, alert.severity)
        assertEquals("Food budget at 85%", alert.title)
        assertEquals(at(5), alert.timestamp)
    }

    @Test
    fun budgetComfortablyInsideItsLimitSaysNothing() {
        val inputs = NotificationInputs(
            budgets = listOf(budget(1, "Food", 1_000.0)),
            transactions = listOf(expense(1, 300.0, at(2)))
        )

        assertTrue(generate(inputs).none { it.category == NotificationCategory.BUDGETS })
    }

    @Test
    fun overallBudgetIsDescribedAsTheMonthlyBudget() {
        val inputs = NotificationInputs(
            budgets = listOf(budget(1, "", 1_000.0, BudgetScope.OVERALL)),
            transactions = listOf(expense(1, 1_100.0, at(3), category = "Shopping"))
        )

        val alert = generate(inputs).ofKind(NotificationKind.BUDGET_EXCEEDED).single()

        assertEquals("Monthly budget exceeded", alert.title)
    }

    @Test
    fun budgetAlertKeepsItsIdAllMonthAndGetsANewOneNextMonth() {
        val inputs = NotificationInputs(
            budgets = listOf(budget(1, "Food", 1_000.0)),
            transactions = listOf(expense(1, 1_500.0, at(3)))
        )

        val midMonth = generate(inputs, at(15)).ofKind(NotificationKind.BUDGET_EXCEEDED).single().id
        val lateMonth = generate(inputs, at(28)).ofKind(NotificationKind.BUDGET_EXCEEDED).single().id
        assertEquals("a dismissal must stick for the rest of the month", midMonth, lateMonth)

        // In October that September spending no longer counts, so there is nothing to warn about.
        assertTrue(generate(inputs, at(5, month = Calendar.OCTOBER)).ofKind(NotificationKind.BUDGET_EXCEEDED).isEmpty())

        val octoberInputs = inputs.copy(transactions = listOf(expense(2, 1_500.0, at(3, month = Calendar.OCTOBER))))
        val october = generate(octoberInputs, at(15, month = Calendar.OCTOBER)).ofKind(NotificationKind.BUDGET_EXCEEDED).single().id
        assertNotEquals("a new month is a new alert", midMonth, october)
    }

    // --------------------------------------------------------------- money flow

    @Test
    fun overdueDebtIsCriticalAndOverdueLoanIsAWarning() {
        val inputs = NotificationInputs(
            moneyFlows = listOf(
                flow(1, "Rahul", MoneyFlowDirection.I_OWE, 1_500.0, dueDate = at(10)),
                flow(2, "Sarah", MoneyFlowDirection.OWED_TO_ME, 2_000.0, dueDate = at(12))
            )
        )

        val alerts = generate(inputs).ofKind(NotificationKind.FLOW_OVERDUE)

        val debt = alerts.single { it.title.contains("Rahul") }
        assertEquals(NotificationSeverity.CRITICAL, debt.severity)
        assertEquals("Payment to Rahul is overdue", debt.title)
        assertTrue(debt.message, debt.message.contains("₹1,500"))
        // It turned overdue the day after it fell due.
        assertEquals(at(11, hour = 0), debt.timestamp)

        val loan = alerts.single { it.title.contains("Sarah") }
        assertEquals(NotificationSeverity.WARNING, loan.severity)
        assertEquals("Sarah hasn't paid you back", loan.title)
    }

    @Test
    fun upcomingDueDatesSurfaceThreeDaysAheadAndEscalateTheDayBefore() {
        val inputs = NotificationInputs(
            moneyFlows = listOf(
                flow(1, "Rahul", MoneyFlowDirection.I_OWE, 1_500.0, dueDate = at(16)),
                flow(2, "Sarah", MoneyFlowDirection.OWED_TO_ME, 2_000.0, dueDate = at(18)),
                flow(3, "Nina", MoneyFlowDirection.OWED_TO_ME, 900.0, dueDate = at(25))
            )
        )

        val alerts = generate(inputs).ofKind(NotificationKind.FLOW_DUE_SOON)

        assertEquals(2, alerts.size)
        assertEquals(NotificationSeverity.WARNING, alerts.single { it.title.contains("Rahul") }.severity)
        assertEquals("Pay Rahul ₹1,500 tomorrow", alerts.single { it.title.contains("Rahul") }.title)
        assertEquals(NotificationSeverity.INFO, alerts.single { it.title.contains("Sarah") }.severity)
    }

    @Test
    fun settledAndUndatedFlowsNeverNotify() {
        val inputs = NotificationInputs(
            moneyFlows = listOf(
                flow(1, "Rahul", MoneyFlowDirection.I_OWE, 1_500.0, dueDate = at(10), settled = true),
                flow(2, "Sarah", MoneyFlowDirection.OWED_TO_ME, 2_000.0, dueDate = null)
            )
        )

        assertTrue(generate(inputs).none { it.category == NotificationCategory.MONEY_FLOW })
    }

    // -------------------------------------------------------------------- goals

    @Test
    fun recentlyCompletedGoalIsCelebratedAndAnOldOneIsNot() {
        val done = goal(1, "Phone", target = 10_000.0, saved = 10_000.0, createdAt = at(10))
        val old = goal(2, "Bike", target = 5_000.0, saved = 5_000.0, createdAt = at(1, month = Calendar.JULY))
        val inputs = NotificationInputs(
            goals = SavingsGoalCalculator.summarize(listOf(done, old), emptyList(), adultMoneyBalance = 20_000.0, now = now)
        )

        val alert = generate(inputs).ofKind(NotificationKind.GOAL_REACHED).single()

        assertEquals(NotificationSeverity.POSITIVE, alert.severity)
        assertTrue(alert.title, alert.title.contains("Phone"))
    }

    @Test
    fun goalApproachingItsDateWarnsAndOneThatMissedItIsFlagged() {
        val soon = goal(1, "Trip", target = 10_000.0, saved = 2_500.0, targetDate = at(17))
        val later = goal(2, "Laptop", target = 10_000.0, saved = 2_500.0, targetDate = at(25))
        val missed = goal(3, "Course", target = 10_000.0, saved = 2_500.0, targetDate = at(10))
        val inputs = NotificationInputs(
            goals = SavingsGoalCalculator.summarize(listOf(soon, later, missed), emptyList(), adultMoneyBalance = 20_000.0, now = now)
        )

        val alerts = generate(inputs).ofKind(NotificationKind.GOAL_DEADLINE)

        assertEquals(NotificationSeverity.WARNING, alerts.single { it.title.contains("Trip") }.severity)
        assertEquals(NotificationSeverity.INFO, alerts.single { it.title.contains("Laptop") }.severity)
        val late = alerts.single { it.title.contains("Course") }
        assertEquals(NotificationSeverity.WARNING, late.severity)
        assertTrue(late.title, late.title.contains("missed"))
    }

    @Test
    fun goalsEarmarkingMoreThanAdultMoneyHoldsAreCalledOut() {
        val inputs = NotificationInputs(
            goals = SavingsGoalCalculator.summarize(
                listOf(goal(1, "Phone", target = 10_000.0, saved = 5_000.0)),
                emptyList(),
                adultMoneyBalance = 1_000.0,
                now = now
            )
        )

        val alert = generate(inputs).ofKind(NotificationKind.GOALS_OVERALLOCATED).single()

        assertEquals(NotificationSeverity.WARNING, alert.severity)
        assertTrue(alert.message, alert.message.contains("₹5,000") && alert.message.contains("₹1,000"))
    }

    // ------------------------------------------------------------------ balance

    @Test
    fun negativeAvailableMoneyIsCritical() {
        val inputs = NotificationInputs(availableMoney = -450.0, transactions = listOf(expense(1, 450.0, at(14))))

        val alert = generate(inputs).ofKind(NotificationKind.BALANCE_NEGATIVE).single()

        assertEquals(NotificationSeverity.CRITICAL, alert.severity)
        assertTrue(alert.message, alert.message.contains("₹450"))
        assertEquals(at(14), alert.timestamp)
    }

    @Test
    fun lowRunwayWarnsOnlyWhenTheMoneyWontLastAWeekAtTheRecentPace() {
        val spending = listOf(expense(1, 1_000.0, at(2)), expense(2, 1_000.0, at(6)), expense(3, 1_000.0, at(12)))

        // 3,000 over 30 days is 100 a day: 300 left is 3 days of money, 5,000 is 50 days.
        val low = generate(NotificationInputs(availableMoney = 300.0, transactions = spending))
            .ofKind(NotificationKind.BALANCE_LOW).single()
        assertEquals(NotificationSeverity.WARNING, low.severity)
        assertTrue(low.message, low.message.contains("about 3 more days"))

        assertTrue(
            generate(NotificationInputs(availableMoney = 5_000.0, transactions = spending))
                .ofKind(NotificationKind.BALANCE_LOW).isEmpty()
        )
    }

    // ----------------------------------------------------------------- spending

    @Test
    fun spendingWellAboveLastMonthsPaceIsFlaggedUsingTheSameDaysOfBothMonths() {
        val lastMonth = (1..4).map { expense(it.toLong(), 250.0, at(it * 3, month = Calendar.AUGUST)) } +
            // After the 15th, so it must not count against this month's first fifteen days.
            expense(50, 5_000.0, at(22, month = Calendar.AUGUST))
        val thisMonth = (1..5).map { expense(100L + it, 400.0, at(it * 2)) }
        val inputs = NotificationInputs(transactions = lastMonth + thisMonth)

        val alert = generate(inputs).ofKind(NotificationKind.SPENDING_SPIKE).single()

        assertEquals(NotificationSeverity.WARNING, alert.severity)
        assertEquals("Spending is up 100% on last month", alert.title)
    }

    @Test
    fun spendingSpikeNeedsEnoughHistoryAndEnoughOfTheMonthToJudge() {
        val lastMonth = (1..4).map { expense(it.toLong(), 250.0, at(it * 3, month = Calendar.AUGUST)) }
        val thisMonth = (1..5).map { expense(100L + it, 400.0, at(it)) }
        val inputs = NotificationInputs(transactions = lastMonth + thisMonth)

        // Day 5 of the month is too early to call a trend.
        assertTrue(generate(inputs, at(5)).ofKind(NotificationKind.SPENDING_SPIKE).isEmpty())
        // And with almost no history last month there is nothing to compare against.
        val thin = NotificationInputs(transactions = lastMonth.take(2) + thisMonth)
        assertTrue(generate(thin).ofKind(NotificationKind.SPENDING_SPIKE).isEmpty())
    }

    @Test
    fun anExpenseFarAboveTheTypicalOneIsMentioned() {
        val history = (1..10).map { expense(it.toLong(), 100.0, at(it, month = Calendar.AUGUST)) }
        val big = expense(99, 1_000.0, at(13), title = "Headphones", category = "Shopping")
        val inputs = NotificationInputs(transactions = history + big)

        val alert = generate(inputs).ofKind(NotificationKind.LARGE_EXPENSE).single()

        assertEquals("Large expense: Headphones", alert.title)
        assertTrue(alert.message, alert.message.contains("₹1,000 is 10×"))
        assertEquals(NotificationSeverity.INFO, alert.severity)
    }

    @Test
    fun largeExpenseIgnoresRecurringBillsAndNeedsABaseline() {
        val history = (1..10).map { expense(it.toLong(), 100.0, at(it, month = Calendar.AUGUST)) }
        val rent = expense(98, 9_000.0, at(13), title = "Rent", recurrence = "MONTHLY")
        assertTrue(generate(NotificationInputs(transactions = history + rent)).ofKind(NotificationKind.LARGE_EXPENSE).isEmpty())

        val big = expense(99, 1_000.0, at(13))
        assertTrue(
            "three past expenses is not enough to say what is typical",
            generate(NotificationInputs(transactions = history.take(3) + big)).ofKind(NotificationKind.LARGE_EXPENSE).isEmpty()
        )
    }

    @Test
    fun recentIncomeIsAnnouncedAndOldIncomeIsNot() {
        val inputs = NotificationInputs(
            transactions = listOf(
                income(1, 50_000.0, at(14), title = "Monthly Salary"),
                income(2, 9_000.0, at(1))
            )
        )

        val alert = generate(inputs).ofKind(NotificationKind.INCOME_RECEIVED).single()

        assertEquals(NotificationSeverity.POSITIVE, alert.severity)
        assertEquals("₹50,000 received", alert.title)
        assertTrue(alert.message, alert.message.contains("Monthly Salary"))
    }

    // ------------------------------------------------------------- auto-tracked

    @Test
    fun autoTrackedPaymentsAreListedOnceAndNotAlsoAsIncomeOrLargeExpenses() {
        val history = (1..10).map { expense(it.toLong(), 100.0, at(it, month = Calendar.AUGUST)) }
        val inputs = NotificationInputs(
            transactions = history + listOf(
                expense(90, 450.0, at(14), category = "Auto", title = "Swiggy", method = "notification_auto"),
                expense(91, 9_000.0, at(14), category = "Auto", title = "Jewellers", method = "notification_auto"),
                income(92, 2_000.0, at(14), title = "Rahul", category = "Auto", method = "notification_auto")
            )
        )

        val alerts = generate(inputs)

        val auto = alerts.ofKind(NotificationKind.AUTO_TRACKED)
        assertEquals(3, auto.size)
        assertTrue(auto.all { it.category == NotificationCategory.AUTO_TRACKED })
        assertTrue(auto.any { it.title == "Auto-tracked ₹450 at Swiggy" })
        assertTrue(auto.any { it.title == "Auto-tracked ₹2,000 from Rahul" })
        assertTrue(alerts.ofKind(NotificationKind.INCOME_RECEIVED).isEmpty())
        assertTrue(alerts.ofKind(NotificationKind.LARGE_EXPENSE).isEmpty())
    }

    // ---------------------------------------------------------------- reminders

    @Test
    fun paydayIsAnnouncedThreeDaysAheadUnlessTheSalaryIsAlreadyLogged() {
        val base = NotificationInputs(monthlySalary = 50_000.0, paydayDayOfMonth = 18)

        val alert = generate(base).ofKind(NotificationKind.PAYDAY).single()
        assertEquals("Payday in 3 days", alert.title)
        assertEquals(NotificationCategory.REMINDERS, alert.category)

        // An early deposit counts: no point reminding someone who has already been paid.
        val paidEarly = base.copy(transactions = listOf(income(1, 50_000.0, at(13))))
        assertTrue(generate(paidEarly).ofKind(NotificationKind.PAYDAY).isEmpty())

        // And a payday weeks away is not worth mentioning yet.
        assertTrue(generate(base.copy(paydayDayOfMonth = 28)).ofKind(NotificationKind.PAYDAY).isEmpty())
    }

    @Test
    fun missingSalaryIsFlaggedAfterPaydayUntilADepositAppears() {
        val base = NotificationInputs(monthlySalary = 50_000.0, paydayDayOfMonth = 10)

        val alert = generate(base).ofKind(NotificationKind.SALARY_MISSING).single()
        assertEquals("settings", alert.route)

        val logged = base.copy(transactions = listOf(income(1, 50_000.0, at(10))))
        assertTrue(generate(logged).ofKind(NotificationKind.SALARY_MISSING).isEmpty())

        assertTrue(
            "nothing to remind about when no salary is set up",
            generate(NotificationInputs(monthlySalary = 0.0, paydayDayOfMonth = 10)).ofKind(NotificationKind.SALARY_MISSING).isEmpty()
        )
    }

    @Test
    fun recurringPaymentIsRemindedAheadAndWhenItHasNotBeenLogged() {
        val dueSoon = expense(1, 649.0, at(17, month = Calendar.AUGUST), category = "Subscriptions", title = "Netflix", recurrence = "MONTHLY")
        val late = expense(2, 1_200.0, at(12, month = Calendar.AUGUST), category = "Bills", title = "Broadband", recurrence = "MONTHLY")

        val alerts = generate(NotificationInputs(transactions = listOf(dueSoon, late))).ofKind(NotificationKind.RECURRING_DUE)

        val netflix = alerts.single { it.title.startsWith("Netflix") }
        assertEquals("Netflix repeats in 2 days", netflix.title)
        assertTrue(netflix.id, netflix.id.startsWith("recurring_due:"))
        val broadband = alerts.single { it.title.startsWith("Broadband") }
        assertEquals("Broadband hasn't been logged", broadband.title)
        assertTrue(broadband.id, broadband.id.startsWith("recurring_late:"))
    }

    @Test
    fun recurringPaymentThatWasLoggedThisCycleIsNotRepeated() {
        val august = expense(1, 649.0, at(17, month = Calendar.AUGUST), title = "Netflix", recurrence = "MONTHLY")
        val september = expense(2, 649.0, at(14), title = "Netflix", recurrence = "MONTHLY")

        val alerts = generate(NotificationInputs(transactions = listOf(august, september))).ofKind(NotificationKind.RECURRING_DUE)

        assertTrue("next one is a month away", alerts.isEmpty())
    }

    @Test
    fun wishlistTargetDateSaysWhetherAdultMoneyCoversIt() {
        val item = WishlistItemEntity(
            id = 1, userId = 1, title = "Headphones", estimatedCost = 8_000.0,
            targetPurchaseDate = at(18), createdAt = 0L
        )

        val covered = generate(NotificationInputs(wishlist = listOf(item), adultMoneyBalance = 10_000.0))
            .ofKind(NotificationKind.WISHLIST_DATE).single()
        assertTrue(covered.message, covered.message.contains("covers"))

        val short = generate(NotificationInputs(wishlist = listOf(item), adultMoneyBalance = 3_000.0))
            .ofKind(NotificationKind.WISHLIST_DATE).single()
        assertTrue(short.message, short.message.contains("₹5,000 short"))

        val bought = item.copy(isPurchased = true)
        assertTrue(generate(NotificationInputs(wishlist = listOf(bought))).ofKind(NotificationKind.WISHLIST_DATE).isEmpty())
    }

    @Test
    fun aQuietStretchPromptsALogReminder() {
        val idle = NotificationInputs(transactions = listOf(expense(1, 100.0, at(10))))
        val alert = generate(idle).ofKind(NotificationKind.LOG_REMINDER).single()
        assertEquals("Nothing logged for 5 days", alert.title)

        val active = NotificationInputs(transactions = listOf(expense(1, 100.0, at(14))))
        assertTrue(generate(active).ofKind(NotificationKind.LOG_REMINDER).isEmpty())
        assertTrue("a brand new account has nothing to nag about", generate(NotificationInputs()).isEmpty())
    }

    @Test
    fun emergencyFundBelowThreeMonthsOfSpendingIsFlagged() {
        // 2,000 a month for each of the last three months, so three months of cover is 6,000.
        val history = listOf(
            expense(1, 2_000.0, at(10, month = Calendar.AUGUST)),
            expense(2, 2_000.0, at(10, month = Calendar.JULY)),
            expense(3, 2_000.0, at(10, month = Calendar.JUNE))
        )

        val low = generate(NotificationInputs(transactions = history, emergencyFundBalance = 3_000.0))
            .ofKind(NotificationKind.EMERGENCY_FUND_LOW).single()
        assertEquals("Emergency fund covers 1.5 months", low.title)
        assertTrue(low.message, low.message.contains("₹3,000 short"))

        assertTrue(
            generate(NotificationInputs(transactions = history, emergencyFundBalance = 7_000.0))
                .ofKind(NotificationKind.EMERGENCY_FUND_LOW).isEmpty()
        )
    }

    // ----------------------------------------------------------------- ordering

    @Test
    fun resultsAreNewestFirstWithUniqueIds() {
        val inputs = NotificationInputs(
            budgets = listOf(budget(1, "Food", 1_000.0)),
            transactions = listOf(expense(1, 1_200.0, at(3)), income(2, 5_000.0, at(14))),
            moneyFlows = listOf(flow(1, "Rahul", MoneyFlowDirection.I_OWE, 500.0, dueDate = at(10)))
        )

        val alerts = generate(inputs)

        assertEquals(alerts.map { it.timestamp }.sortedDescending(), alerts.map { it.timestamp })
        assertEquals(alerts.size, alerts.map { it.id }.toSet().size)
        assertTrue("no alert can be dated in the future", alerts.all { it.timestamp <= now })
    }

    // ------------------------------------------------------------ centre & state

    private fun note(
        id: String,
        category: NotificationCategory = NotificationCategory.BUDGETS,
        severity: NotificationSeverity = NotificationSeverity.INFO,
        timestamp: Long = now
    ) = AppNotification(
        id = id, kind = NotificationKind.BUDGET_NEARING, category = category, severity = severity,
        title = id, message = "", timestamp = timestamp, route = "budgets"
    )

    @Test
    fun centreHidesDismissedAndMutedAndCountsUnread() {
        val all = listOf(
            note("a"),
            note("b"),
            note("c"),
            note("d", category = NotificationCategory.REMINDERS)
        )
        val state = NotificationUserState(
            read = setOf("a"),
            dismissed = setOf("b"),
            mutedCategories = setOf(NotificationCategory.REMINDERS)
        )

        val centre = NotificationCenter.build(all, state, now)

        assertEquals(listOf("a", "c"), centre.entries.map { it.notification.id })
        assertEquals(1, centre.unreadCount)
        assertEquals(1, centre.dismissedCount)
        assertTrue(centre.entries.single { it.notification.id == "a" }.isRead)
        assertFalse(centre.entries.single { it.notification.id == "c" }.isRead)
    }

    @Test
    fun dismissedCountIgnoresNotificationsThatNoLongerExist() {
        val state = NotificationUserState(dismissed = setOf("gone", "a"))

        val centre = NotificationCenter.build(listOf(note("a")), state, now)

        assertEquals("only still-active notifications can be restored", 1, centre.dismissedCount)
    }

    @Test
    fun stateStoreRemembersReadDismissedAndMutedPerUser() = runBlocking {
        val store = NotificationStateStore()

        store.markRead(1, listOf("a", "b"))
        store.dismiss(1, listOf("c"))
        store.setCategoryEnabled(1, NotificationCategory.AUTO_TRACKED, enabled = false)

        val mine = store.observe(1).first()
        assertEquals(setOf("a", "b", "c"), mine.read)
        assertEquals(setOf("c"), mine.dismissed)
        assertEquals(setOf(NotificationCategory.AUTO_TRACKED), mine.mutedCategories)
        assertEquals("another account starts clean", NotificationUserState(), store.observe(2).first())

        store.restore(1, listOf("c"))
        store.setCategoryEnabled(1, NotificationCategory.AUTO_TRACKED, enabled = true)
        val restored = store.observe(1).first()
        assertTrue(restored.dismissed.isEmpty())
        assertTrue(restored.mutedCategories.isEmpty())

        store.dismiss(1, listOf("x", "y"))
        store.restoreAllDismissed(1)
        assertTrue(store.observe(1).first().dismissed.isEmpty())

        store.forget(1)
        assertEquals(NotificationUserState(), store.observe(1).first())
    }

    // ------------------------------------------------------------------ ago()

    @Test
    fun agoReadsLikeAPersonWouldSayIt() {
        assertEquals("Just now", RelativeDates.ago(now - 20_000L, now))
        assertEquals("5m ago", RelativeDates.ago(now - 5 * 60_000L, now))
        assertEquals("3h ago", RelativeDates.ago(at(15, hour = 9), now))
        assertEquals("Yesterday", RelativeDates.ago(at(14, hour = 20), now))
        assertEquals("3d ago", RelativeDates.ago(at(12), now))
        assertFalse(RelativeDates.ago(at(1), now).endsWith("ago"))
    }
}
