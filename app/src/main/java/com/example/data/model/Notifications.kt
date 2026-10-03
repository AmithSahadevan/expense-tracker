package com.example.data.model

import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.GoalContributionEntity
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import com.example.data.local.entities.WishlistItemEntity
import com.example.ui.components.formatMoney
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/** What a notification is about. Drives the filter chips and the per-category mute switches. */
enum class NotificationCategory(val label: String, val description: String) {
    BUDGETS("Budgets", "Envelopes that are close to, or past, their limit"),
    MONEY_FLOW("Money Flow", "Overdue and upcoming IOUs, loans and shared costs"),
    SAVINGS("Savings & Goals", "Goal milestones, deadlines and emergency fund cover"),
    SPENDING("Spending & Income", "Big purchases, spending spikes, low balance and money received"),
    AUTO_TRACKED("Auto-tracked", "Payments recorded automatically from bank notifications"),
    REMINDERS("Reminders", "Payday, recurring payments, wishlist dates and logging nudges")
}

/** How urgent an alert is. The page reads it as the row icon's colour; order never affects the list. */
enum class NotificationSeverity {
    CRITICAL,
    WARNING,
    POSITIVE,
    INFO
}

enum class NotificationKind {
    BUDGET_EXCEEDED,
    BUDGET_NEARING,
    FLOW_OVERDUE,
    FLOW_DUE_SOON,
    GOAL_REACHED,
    GOAL_ALMOST,
    GOAL_DEADLINE,
    GOAL_BEHIND,
    GOALS_OVERALLOCATED,
    EMERGENCY_FUND_LOW,
    BALANCE_NEGATIVE,
    BALANCE_LOW,
    SPENDING_SPIKE,
    LARGE_EXPENSE,
    INCOME_RECEIVED,
    AUTO_TRACKED,
    PAYDAY,
    SALARY_MISSING,
    RECURRING_DUE,
    WISHLIST_DATE,
    LOG_REMINDER
}

/**
 * One alert. [id] is stable for as long as the situation behind it is the same, which is what
 * lets "read" and "dismissed" survive between launches: a budget that is over for September has
 * the same id all month, and a new one the moment October starts.
 */
data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val category: NotificationCategory,
    val severity: NotificationSeverity,
    val title: String,
    val message: String,
    /** When the situation arose (a threshold was crossed, a date came into view), not when it was last evaluated. */
    val timestamp: Long,
    /** [com.example.ui.navigation.AppDestination.route] the notification opens. */
    val route: String
)

/** Everything the engine reads. Plain data, so the rules can be exercised without a database. */
data class NotificationInputs(
    val currency: String = "₹",
    val transactions: List<TransactionItem> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val moneyFlows: List<MoneyFlowEntity> = emptyList(),
    val goals: GoalsSummary = GoalsSummary(),
    val goalContributions: List<GoalContributionEntity> = emptyList(),
    val wishlist: List<WishlistItemEntity> = emptyList(),
    val savingsTransactions: List<SavingsTransactionEntity> = emptyList(),
    val availableMoney: Double = 0.0,
    val adultMoneyBalance: Double = 0.0,
    val emergencyFundBalance: Double = 0.0,
    val monthlySalary: Double = 0.0,
    val paydayDayOfMonth: Int = 1
)

/**
 * Works out what deserves the user's attention from the data already on the device.
 *
 * Notifications are derived, not stored. A budget alert disappears the moment spending is edited
 * back under the limit, and an overdue IOU stops nagging once it is settled, with nothing to keep
 * in sync. The only thing persisted is what the user has done with them (read, dismissed, muted).
 */
object NotificationEngine {

    /** A pending IOU surfaces this many days before it falls due. */
    const val DUE_SOON_DAYS = 3

    /** Payday, recurring payments and wishlist dates surface this far ahead. */
    const val REMINDER_LEAD_DAYS = 3

    const val GOAL_DEADLINE_DAYS = 14
    const val WISHLIST_LEAD_DAYS = 7
    const val INACTIVITY_DAYS = 3

    /** One-off events (money received, a big purchase, an auto-tracked payment) fade after this long. */
    const val RECENT_EVENT_DAYS = 7

    /** Milestones such as "goal reached" fade after this long. */
    const val RECENT_MILESTONE_DAYS = 30

    /** Below this many days of money at the recent pace, the balance is flagged as low. */
    const val LOW_RUNWAY_DAYS = 7

    /** A single expense this many times the typical one is worth a mention. */
    const val LARGE_EXPENSE_FACTOR = 3.0

    /** Spending this far above last month's pace is flagged. */
    const val SPIKE_FACTOR = 1.3

    /** Emergency cover below this many months of spending is flagged. */
    const val EMERGENCY_TARGET_MONTHS = 3.0

    private const val DAY = 86_400_000L
    private const val ALMOST_THERE = 0.9f
    private const val MISSED_GOAL_WINDOW_DAYS = 90
    private const val MIN_DAY_OF_MONTH_FOR_SPIKE = 7
    private const val MIN_SAMPLES_FOR_SPIKE = 3
    private const val MIN_SAMPLES_FOR_LARGE = 8
    private const val MAX_AUTO_TRACKED = 15
    private const val MAX_INCOME = 5
    private const val MAX_LARGE = 3
    private const val AUTO_PAYMENT_METHOD = "notification_auto"

    fun generate(inputs: NotificationInputs, now: Long = System.currentTimeMillis()): List<AppNotification> {
        val ctx = Ctx(inputs, now)
        return (
            ctx.budgetAlerts() +
                ctx.moneyFlowAlerts() +
                ctx.goalAlerts() +
                ctx.balanceAlerts() +
                ctx.spendingAlerts() +
                ctx.incomeAlerts() +
                ctx.autoTrackedAlerts() +
                ctx.reminderAlerts()
            )
            .distinctBy { it.id }
            .sortedWith(compareByDescending<AppNotification> { it.timestamp }.thenBy { it.id })
    }

    private class Ctx(val i: NotificationInputs, val now: Long) {
        fun money(amount: Double): String = formatMoney(i.currency, amount)

        /** A rule's timestamp can never be in the future, nor before the thing it is about existed. */
        fun raisedAt(at: Long, notBefore: Long = 0L): Long = at.coerceAtLeast(notBefore).coerceAtMost(now)

        val latestTransactionAt: Long
            get() = i.transactions.maxOfOrNull { it.date }?.coerceAtMost(now) ?: now
    }

    // ------------------------------------------------------------------ budgets

    private fun Ctx.budgetAlerts(): List<AppNotification> {
        val summary = BudgetCalculator.summarize(i.budgets, i.transactions, now)
        val month = summary.monthStart until summary.monthEnd
        return summary.all.mapNotNull { p ->
            when (p.status) {
                BudgetStatus.ON_TRACK -> null
                BudgetStatus.OVER -> AppNotification(
                    id = "budget_over:${p.id}:${summary.monthStart}",
                    kind = NotificationKind.BUDGET_EXCEEDED,
                    category = NotificationCategory.BUDGETS,
                    severity = NotificationSeverity.CRITICAL,
                    title = if (p.isOverall) "Monthly budget exceeded" else "${p.label} budget exceeded",
                    message = "You've spent ${money(p.spent)} of ${money(p.allocated)}, " +
                        "${money(p.overspentBy)} over with ${plural(summary.daysLeftInMonth, "day")} left this month.",
                    timestamp = budgetCrossedAt(p, month, over = true),
                    route = ROUTE_BUDGETS
                )
                BudgetStatus.APPROACHING -> AppNotification(
                    id = "budget_near:${p.id}:${summary.monthStart}",
                    kind = NotificationKind.BUDGET_NEARING,
                    category = NotificationCategory.BUDGETS,
                    severity = NotificationSeverity.WARNING,
                    title = (if (p.isOverall) "Monthly budget" else "${p.label} budget") + " at ${p.percentLabel}%",
                    message = if (p.isExhausted) {
                        "It's fully used. Anything more will put you over."
                    } else {
                        "Only ${money(p.remaining)} left, about ${money(p.remaining / summary.daysLeftInMonth.coerceAtLeast(1))} " +
                            "a day for the ${plural(summary.daysLeftInMonth, "day")} remaining."
                    },
                    timestamp = budgetCrossedAt(p, month, over = false),
                    route = ROUTE_BUDGETS
                )
            }
        }
    }

    /** The expense that pushed the envelope over (or to 80%), so the alert is dated by what caused it. */
    private fun Ctx.budgetCrossedAt(p: BudgetProgress, month: LongRange, over: Boolean): Long {
        val category = if (p.isOverall) null else p.budget.category
        val limit = if (over) p.allocated else p.allocated * BudgetCalculator.APPROACHING_AT
        var running = 0.0
        var crossedAt = now
        val counted = i.transactions
            .filter { it.type == TransactionType.EXPENSE && it.date in month }
            .filter { category == null || it.category.equals(category, ignoreCase = true) }
            .sortedBy { it.date }
        for (tx in counted) {
            running += tx.amount
            if (if (over) running > limit else running >= limit) {
                crossedAt = tx.date
                break
            }
        }
        // An envelope created after the spending cannot have warned before it existed.
        return raisedAt(crossedAt, notBefore = maxOf(month.first, p.budget.createdAt))
    }

    // --------------------------------------------------------------- money flow

    private fun Ctx.moneyFlowAlerts(): List<AppNotification> = i.moneyFlows.mapNotNull { flow ->
        val due = flow.dueDate ?: return@mapNotNull null
        if (flow.isSettled) return@mapNotNull null
        val days = RelativeDates.daysUntil(due, now)
        val iOwe = flow.direction == MoneyFlowDirection.I_OWE
        val person = flow.personName
        val amount = money(flow.amount)

        when {
            days < 0 -> AppNotification(
                id = "flow_overdue:${flow.id}:$due",
                kind = NotificationKind.FLOW_OVERDUE,
                category = NotificationCategory.MONEY_FLOW,
                severity = if (iOwe) NotificationSeverity.CRITICAL else NotificationSeverity.WARNING,
                title = if (iOwe) "Payment to $person is overdue" else "$person hasn't paid you back",
                message = if (iOwe) {
                    "You owe $amount, due ${RelativeDates.describe(due, now)}. Pay it and mark it settled."
                } else {
                    "$amount was due ${RelativeDates.describe(due, now)}. A quick reminder might help."
                },
                timestamp = raisedAt(addDays(startOfDay(due), 1), notBefore = flow.createdAt),
                route = ROUTE_MONEY_FLOW
            )
            days <= DUE_SOON_DAYS -> AppNotification(
                id = "flow_due:${flow.id}:$due",
                kind = NotificationKind.FLOW_DUE_SOON,
                category = NotificationCategory.MONEY_FLOW,
                severity = if (days <= 1) NotificationSeverity.WARNING else NotificationSeverity.INFO,
                title = if (iOwe) {
                    "Pay $person $amount ${RelativeDates.describe(due, now)}"
                } else {
                    "$person should pay you ${RelativeDates.describe(due, now)}"
                },
                message = if (iOwe) {
                    "Due ${RelativeDates.describe(due, now)}. Mark it settled once it's paid."
                } else {
                    "$amount is expected ${RelativeDates.describe(due, now)}. Mark it settled when it arrives."
                },
                timestamp = raisedAt(addDays(startOfDay(due), -DUE_SOON_DAYS), notBefore = flow.createdAt),
                route = ROUTE_MONEY_FLOW
            )
            else -> null
        }
    }

    // -------------------------------------------------------------------- goals

    private fun Ctx.goalAlerts(): List<AppNotification> {
        val out = mutableListOf<AppNotification>()
        val lastActivityByGoal = i.goalContributions.groupBy { it.goalId }.mapValues { (_, list) -> list.maxOf { it.date } }

        for (g in i.goals.goals) {
            val touchedAt = raisedAt(lastActivityByGoal[g.id] ?: g.goal.createdAt, notBefore = g.goal.createdAt)
            val isRecent = now - touchedAt <= RECENT_MILESTONE_DAYS * DAY
            val target = g.targetDate
            val days = if (target != null) RelativeDates.daysUntil(target, now) else 0
            val name = "${g.goal.emoji} ${g.title}"

            when {
                g.isComplete -> if (isRecent) out += AppNotification(
                    id = "goal_done:${g.id}",
                    kind = NotificationKind.GOAL_REACHED,
                    category = NotificationCategory.SAVINGS,
                    severity = NotificationSeverity.POSITIVE,
                    title = "$name is fully funded",
                    message = "You've set aside ${money(g.currentAmount)} against a ${money(g.target)} target. " +
                        "Time to enjoy it, or pick the next goal.",
                    timestamp = touchedAt,
                    route = ROUTE_SAVINGS
                )

                target != null && days < 0 && days >= -MISSED_GOAL_WINDOW_DAYS -> out += AppNotification(
                    id = "goal_missed:${g.id}:$target",
                    kind = NotificationKind.GOAL_DEADLINE,
                    category = NotificationCategory.SAVINGS,
                    severity = NotificationSeverity.WARNING,
                    title = "$name missed its target date",
                    message = "The date passed ${RelativeDates.describe(target, now)} with ${money(g.remaining)} " +
                        "still to save. Move the date or top it up.",
                    timestamp = raisedAt(addDays(startOfDay(target), 1), notBefore = g.goal.createdAt),
                    route = ROUTE_SAVINGS
                )

                target != null && days in 0..GOAL_DEADLINE_DAYS -> out += AppNotification(
                    id = "goal_deadline:${g.id}:$target",
                    kind = NotificationKind.GOAL_DEADLINE,
                    category = NotificationCategory.SAVINGS,
                    severity = if (days <= DUE_SOON_DAYS) NotificationSeverity.WARNING else NotificationSeverity.INFO,
                    title = "$name target date is ${RelativeDates.describe(target, now)}",
                    message = "${money(g.remaining)} still to go. You're ${g.percentLabel}% of the way to ${money(g.target)}.",
                    timestamp = raisedAt(addDays(startOfDay(target), -GOAL_DEADLINE_DAYS), notBefore = g.goal.createdAt),
                    route = ROUTE_SAVINGS
                )

                target != null && days > GOAL_DEADLINE_DAYS && g.isBehindTargetDate -> out += AppNotification(
                    id = "goal_behind:${g.id}:$target",
                    kind = NotificationKind.GOAL_BEHIND,
                    category = NotificationCategory.SAVINGS,
                    severity = NotificationSeverity.INFO,
                    title = "$name is behind pace",
                    message = "At ${money(g.monthlyRate ?: 0.0)} a month you'd finish around " +
                        "${g.estimatedCompletion?.let { formatDate(it) } ?: "a later date"}, after your ${formatDate(target)} target.",
                    timestamp = touchedAt,
                    route = ROUTE_SAVINGS
                )

                g.percentComplete >= ALMOST_THERE && isRecent -> out += AppNotification(
                    id = "goal_almost:${g.id}",
                    kind = NotificationKind.GOAL_ALMOST,
                    category = NotificationCategory.SAVINGS,
                    severity = NotificationSeverity.POSITIVE,
                    title = "$name is almost there",
                    message = "${g.percentLabel}% saved, only ${money(g.remaining)} to go.",
                    timestamp = touchedAt,
                    route = ROUTE_SAVINGS
                )
            }
        }

        val funding = i.goals.funding
        if (funding.isOverAllocated) {
            val latestMovement = maxOf(
                i.goalContributions.maxOfOrNull { it.date } ?: 0L,
                i.savingsTransactions.filter { it.savingsType == SavingsType.ADULT_MONEY }.maxOfOrNull { it.date } ?: 0L
            )
            out += AppNotification(
                id = "goals_overallocated:${monthKey(now)}",
                kind = NotificationKind.GOALS_OVERALLOCATED,
                category = NotificationCategory.SAVINGS,
                severity = NotificationSeverity.WARNING,
                title = "Goals hold more than your Adult Money",
                message = "${money(funding.allocated)} is earmarked across goals but Adult Money is only " +
                    "${money(funding.pool)}. Withdraw from a goal or add to Adult Money.",
                timestamp = if (latestMovement > 0L) raisedAt(latestMovement) else now,
                route = ROUTE_SAVINGS
            )
        }

        emergencyFundAlert()?.let { out += it }
        return out
    }

    private fun Ctx.emergencyFundAlert(): AppNotification? {
        val fund = i.emergencyFundBalance
        if (fund <= 0.0) return null
        val average = averageMonthlySpend() ?: return null
        val months = fund / average
        if (months >= EMERGENCY_TARGET_MONTHS) return null

        val target = average * EMERGENCY_TARGET_MONTHS
        val latestDeposit = i.savingsTransactions
            .filter { it.savingsType == SavingsType.EMERGENCY_FUND }
            .maxOfOrNull { it.date }
        return AppNotification(
            id = "emergency_low:${monthKey(now)}",
            kind = NotificationKind.EMERGENCY_FUND_LOW,
            category = NotificationCategory.SAVINGS,
            severity = NotificationSeverity.INFO,
            title = "Emergency fund covers ${String.format(Locale.US, "%.1f", months)} months",
            message = "Three months of your spending is about ${money(target)}. " +
                "You're ${money(target - fund)} short.",
            timestamp = latestDeposit?.let { raisedAt(it) } ?: now,
            route = ROUTE_SAVINGS
        )
    }

    /** Mean spend over the last three full months that had any, or null before there is history to judge by. */
    private fun Ctx.averageMonthlySpend(): Double? {
        val thisMonthStart = BudgetCalculator.monthRange(now).first
        val totals = (1..3)
            .map { back ->
                val start = addMonths(thisMonthStart, -back)
                BudgetCalculator.spent(i.transactions, start until addMonths(thisMonthStart, -(back - 1)))
            }
            .filter { it > 0.0 }
        return if (totals.isEmpty()) null else totals.average()
    }

    // ------------------------------------------------------- balance & spending

    private fun Ctx.balanceAlerts(): List<AppNotification> {
        val available = i.availableMoney
        if (available < 0.0) {
            return listOf(
                AppNotification(
                    id = "balance_negative:${monthKey(now)}",
                    kind = NotificationKind.BALANCE_NEGATIVE,
                    category = NotificationCategory.SPENDING,
                    severity = NotificationSeverity.CRITICAL,
                    title = "Available money is below zero",
                    message = "Expenses and savings transfers add up to ${money(abs(available))} more than you've " +
                        "recorded earning. Check recent entries.",
                    timestamp = latestTransactionAt,
                    route = ROUTE_TRANSACTIONS
                )
            )
        }

        val spentRecently = i.transactions
            .filter { it.type == TransactionType.EXPENSE && it.date in (now - 30 * DAY)..now }
            .sumOf { it.amount }
        if (spentRecently <= 0.0) return emptyList()

        val perDay = spentRecently / 30.0
        val runwayDays = available / perDay
        if (runwayDays >= LOW_RUNWAY_DAYS) return emptyList()

        val lasts = if (runwayDays < 1.0) "won't cover another day" else "lasts about ${plural(floor(runwayDays).toInt(), "more day")}"
        return listOf(
            AppNotification(
                id = "balance_low:${monthKey(now)}",
                kind = NotificationKind.BALANCE_LOW,
                category = NotificationCategory.SPENDING,
                severity = NotificationSeverity.WARNING,
                title = "Available money is running low",
                message = "At your recent pace of ${money(perDay)} a day, ${money(available)} $lasts.",
                timestamp = latestTransactionAt,
                route = ROUTE_TRANSACTIONS
            )
        )
    }

    private fun Ctx.spendingAlerts(): List<AppNotification> = spendingSpike() + largeExpenses()

    private fun Ctx.spendingSpike(): List<AppNotification> {
        val thisMonth = BudgetCalculator.monthRange(now)
        val dayOfMonth = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_MONTH)
        if (dayOfMonth < MIN_DAY_OF_MONTH_FOR_SPIKE) return emptyList()

        // Same stretch of both months: day 1 to today, against day 1 to the same day last month.
        val lastStart = addMonths(thisMonth.first, -1)
        val lastEnd = minOf(addDays(lastStart, dayOfMonth), thisMonth.first)
        val lastSoFar = i.transactions.filter { it.type == TransactionType.EXPENSE && it.date >= lastStart && it.date < lastEnd }
        val lastTotal = lastSoFar.sumOf { it.amount }
        if (lastSoFar.size < MIN_SAMPLES_FOR_SPIKE || lastTotal <= 0.0) return emptyList()

        val thisSoFar = i.transactions.filter { it.type == TransactionType.EXPENSE && it.date in thisMonth && it.date <= now }
        val thisTotal = thisSoFar.sumOf { it.amount }
        if (thisTotal < lastTotal * SPIKE_FACTOR) return emptyList()

        val up = ((thisTotal / lastTotal - 1.0) * 100).roundToInt()
        return listOf(
            AppNotification(
                id = "spend_spike:${monthKey(now)}",
                kind = NotificationKind.SPENDING_SPIKE,
                category = NotificationCategory.SPENDING,
                severity = NotificationSeverity.WARNING,
                title = "Spending is up $up% on last month",
                message = "${money(thisTotal)} spent so far this month, against ${money(lastTotal)} by this point in " +
                    "${monthName(lastStart)}.",
                timestamp = raisedAt(thisSoFar.maxOf { it.date }),
                route = ROUTE_TRANSACTIONS
            )
        )
    }

    private fun Ctx.largeExpenses(): List<AppNotification> {
        val since = now - RECENT_EVENT_DAYS * DAY
        val expenses = i.transactions.filter { it.type == TransactionType.EXPENSE }
        return expenses
            .filter { it.date in since..now && it.recurrence == "NONE" && it.paymentMethod != AUTO_PAYMENT_METHOD }
            .mapNotNull { tx ->
                val history = expenses
                    .filter { it.id != tx.id && it.date < tx.date && it.date >= tx.date - 90 * DAY }
                    .map { it.amount }
                    .sorted()
                if (history.size < MIN_SAMPLES_FOR_LARGE) return@mapNotNull null
                val typical = median(history)
                if (typical <= 0.0 || tx.amount < typical * LARGE_EXPENSE_FACTOR) return@mapNotNull null
                tx to typical
            }
            .sortedByDescending { (tx, _) -> tx.amount }
            .take(MAX_LARGE)
            .map { (tx, typical) ->
                val times = tx.amount / typical
                AppNotification(
                    id = "large:${tx.id}",
                    kind = NotificationKind.LARGE_EXPENSE,
                    category = NotificationCategory.SPENDING,
                    severity = NotificationSeverity.INFO,
                    title = "Large expense: ${tx.title}",
                    message = "${money(tx.amount)} is ${if (times >= 10) times.roundToInt().toString() else String.format(Locale.US, "%.1f", times)}× " +
                        "your typical expense of ${money(typical)}.",
                    timestamp = tx.date,
                    route = ROUTE_TRANSACTIONS
                )
            }
    }

    private fun Ctx.incomeAlerts(): List<AppNotification> {
        val since = now - RECENT_EVENT_DAYS * DAY
        return i.transactions
            .filter { it.type == TransactionType.INCOME && it.date in since..now && it.paymentMethod != AUTO_PAYMENT_METHOD }
            .sortedByDescending { it.date }
            .take(MAX_INCOME)
            .map { tx ->
                AppNotification(
                    id = "income:${tx.id}",
                    kind = NotificationKind.INCOME_RECEIVED,
                    category = NotificationCategory.SPENDING,
                    severity = NotificationSeverity.POSITIVE,
                    title = "${money(tx.amount)} received",
                    message = "${tx.title} was added to your income under ${tx.category}.",
                    timestamp = tx.date,
                    route = ROUTE_TRANSACTIONS
                )
            }
    }

    // ------------------------------------------------------------- auto-tracked

    private fun Ctx.autoTrackedAlerts(): List<AppNotification> {
        val since = now - RECENT_EVENT_DAYS * DAY
        return i.transactions
            .filter { it.paymentMethod == AUTO_PAYMENT_METHOD && it.date in since..now }
            .sortedByDescending { it.date }
            .take(MAX_AUTO_TRACKED)
            .map { tx ->
                val isExpense = tx.type == TransactionType.EXPENSE
                val who = tx.title.ifBlank { if (isExpense) "a payment" else "someone" }
                AppNotification(
                    id = "auto:${tx.type.name.lowercase()}:${tx.id}",
                    kind = NotificationKind.AUTO_TRACKED,
                    category = NotificationCategory.AUTO_TRACKED,
                    severity = NotificationSeverity.INFO,
                    title = if (isExpense) "Auto-tracked ${money(tx.amount)} at $who" else "Auto-tracked ${money(tx.amount)} from $who",
                    message = if (isExpense) {
                        "Recorded from a payment notification. Open it to review or pick a category."
                    } else {
                        "A credit was detected from a notification and added to your income."
                    },
                    timestamp = tx.date,
                    route = ROUTE_TRANSACTIONS
                )
            }
    }

    // ---------------------------------------------------------------- reminders

    private fun Ctx.reminderAlerts(): List<AppNotification> =
        paydayAlerts() + recurringAlerts() + wishlistAlerts() + inactivityAlert()

    private fun Ctx.paydayAlerts(): List<AppNotification> {
        val salary = i.monthlySalary
        if (salary <= 0.0) return emptyList()
        val out = mutableListOf<AppNotification>()

        fun salaryLoggedSince(since: Long): Boolean = i.transactions.any {
            it.type == TransactionType.INCOME && it.date in since..now && it.amount >= salary * 0.5
        }

        val thisMonthPayday = paydayIn(now)
        val nextPayday = if (RelativeDates.daysUntil(thisMonthPayday, now) >= 0) thisMonthPayday else paydayIn(addMonths(now, 1))
        val daysToPayday = RelativeDates.daysUntil(nextPayday, now)
        if (daysToPayday in 0..REMINDER_LEAD_DAYS && !salaryLoggedSince(addDays(nextPayday, -7))) {
            out += AppNotification(
                id = "payday:$nextPayday",
                kind = NotificationKind.PAYDAY,
                category = NotificationCategory.REMINDERS,
                severity = NotificationSeverity.INFO,
                title = "Payday ${RelativeDates.describe(nextPayday, now)}",
                message = "Your ${money(salary)} salary is due ${RelativeDates.describe(nextPayday, now)}. " +
                    "Log it when it lands so Available Money stays right.",
                timestamp = raisedAt(addDays(nextPayday, -REMINDER_LEAD_DAYS)),
                route = ROUTE_TRANSACTIONS
            )
        }

        val daysSincePayday = -RelativeDates.daysUntil(thisMonthPayday, now)
        if (daysSincePayday in 1..10 && !salaryLoggedSince(addDays(thisMonthPayday, -3))) {
            out += AppNotification(
                id = "salary_missing:$thisMonthPayday",
                kind = NotificationKind.SALARY_MISSING,
                category = NotificationCategory.REMINDERS,
                severity = NotificationSeverity.INFO,
                title = "This month's salary isn't logged",
                message = "Payday was ${RelativeDates.describe(thisMonthPayday, now)}, but no deposit of " +
                    "${money(salary * 0.5)} or more is recorded since.",
                timestamp = raisedAt(addDays(thisMonthPayday, 1)),
                route = ROUTE_SETTINGS
            )
        }
        return out
    }

    private fun Ctx.paydayIn(monthMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = monthMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.DAY_OF_MONTH, i.paydayDayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH)))
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Repeating entries are only metadata (nothing creates the next one), so remind the user to log it. */
    private fun Ctx.recurringAlerts(): List<AppNotification> {
        return i.transactions
            .filter { it.recurrence == "WEEKLY" || it.recurrence == "MONTHLY" }
            .groupBy { listOf(it.type, it.title.trim().lowercase(), it.category.lowercase(), it.recurrence) }
            .values
            .mapNotNull { series ->
                val latest = series.maxByOrNull { it.date } ?: return@mapNotNull null
                val next = Calendar.getInstance().apply {
                    timeInMillis = latest.date
                    if (latest.recurrence == "WEEKLY") add(Calendar.WEEK_OF_YEAR, 1) else add(Calendar.MONTH, 1)
                }.timeInMillis
                val days = RelativeDates.daysUntil(next, now)
                val cadence = if (latest.recurrence == "WEEKLY") "weekly" else "monthly"
                val isExpense = latest.type == TransactionType.EXPENSE
                val amount = money(latest.amount)
                val idSuffix = "${latest.type.name.lowercase()}:${latest.id}:${startOfDay(next)}"

                when {
                    days in 0..REMINDER_LEAD_DAYS -> AppNotification(
                        id = "recurring_due:$idSuffix",
                        kind = NotificationKind.RECURRING_DUE,
                        category = NotificationCategory.REMINDERS,
                        severity = NotificationSeverity.INFO,
                        title = if (isExpense) "${latest.title} repeats ${RelativeDates.describe(next, now)}"
                        else "${latest.title} expected ${RelativeDates.describe(next, now)}",
                        message = if (isExpense) {
                            "Your $cadence $amount payment is due ${RelativeDates.describe(next, now)}. Log it once it's paid."
                        } else {
                            "Your $cadence $amount income is expected ${RelativeDates.describe(next, now)}."
                        },
                        timestamp = raisedAt(addDays(startOfDay(next), -REMINDER_LEAD_DAYS)),
                        route = ROUTE_TRANSACTIONS
                    )
                    days in -RECENT_EVENT_DAYS..-1 -> AppNotification(
                        id = "recurring_late:$idSuffix",
                        kind = NotificationKind.RECURRING_DUE,
                        category = NotificationCategory.REMINDERS,
                        severity = NotificationSeverity.INFO,
                        title = "${latest.title} hasn't been logged",
                        message = "Your $cadence $amount ${if (isExpense) "payment" else "income"} was due " +
                            "${RelativeDates.describe(next, now)}. Add it so your balance stays accurate.",
                        timestamp = raisedAt(addDays(startOfDay(next), 1)),
                        route = ROUTE_TRANSACTIONS
                    )
                    else -> null
                }
            }
    }

    private fun Ctx.wishlistAlerts(): List<AppNotification> = i.wishlist.mapNotNull { item ->
        val target = item.targetPurchaseDate ?: return@mapNotNull null
        if (item.isPurchased) return@mapNotNull null
        val days = RelativeDates.daysUntil(target, now)
        if (days > WISHLIST_LEAD_DAYS || days < -RECENT_EVENT_DAYS * 2) return@mapNotNull null

        val pool = i.adultMoneyBalance
        AppNotification(
            id = "wishlist:${item.id}:$target",
            kind = NotificationKind.WISHLIST_DATE,
            category = NotificationCategory.REMINDERS,
            severity = NotificationSeverity.INFO,
            title = "${item.title}: target date ${RelativeDates.describe(target, now)}",
            message = if (item.estimatedCost <= pool) {
                "Your Adult Money (${money(pool)}) covers the ${money(item.estimatedCost)} price."
            } else {
                "You're ${money(item.estimatedCost - pool)} short in Adult Money for this ${money(item.estimatedCost)} item."
            },
            timestamp = raisedAt(addDays(startOfDay(target), -WISHLIST_LEAD_DAYS), notBefore = item.createdAt),
            route = ROUTE_WISHLIST
        )
    }

    private fun Ctx.inactivityAlert(): List<AppNotification> {
        if (i.transactions.isEmpty()) return emptyList()
        val last = latestTransactionAt
        val idleDays = -RelativeDates.daysUntil(last, now)
        if (idleDays !in INACTIVITY_DAYS..30) return emptyList()
        return listOf(
            AppNotification(
                id = "inactive:${startOfDay(last)}",
                kind = NotificationKind.LOG_REMINDER,
                category = NotificationCategory.REMINDERS,
                severity = NotificationSeverity.INFO,
                title = "Nothing logged for $idleDays days",
                message = "Add recent spending so your budgets and balance stay accurate.",
                timestamp = raisedAt(addDays(startOfDay(last), INACTIVITY_DAYS)),
                route = ROUTE_TRANSACTIONS
            )
        )
    }

    // ------------------------------------------------------------------ helpers

    private const val ROUTE_BUDGETS = "budgets"
    private const val ROUTE_MONEY_FLOW = "money_flow"
    private const val ROUTE_SAVINGS = "savings"
    private const val ROUTE_TRANSACTIONS = "transactions"
    private const val ROUTE_WISHLIST = "wishlist"
    private const val ROUTE_SETTINGS = "settings"

    private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

    private fun median(sorted: List<Double>): Double {
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private fun addMonths(millis: Long, months: Int): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        add(Calendar.MONTH, months)
    }.timeInMillis

    private fun monthKey(millis: Long): String = SimpleDateFormat("yyyyMM", Locale.US).format(Date(millis))

    private fun monthName(millis: Long): String = SimpleDateFormat("MMMM", Locale.getDefault()).format(Date(millis))

    private fun formatDate(millis: Long): String = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
}

// ----------------------------------------------------------------- user state

/** What the user has done with notifications: which they've seen, cleared, or silenced by category. */
data class NotificationUserState(
    val read: Set<String> = emptySet(),
    val dismissed: Set<String> = emptySet(),
    val mutedCategories: Set<NotificationCategory> = emptySet()
)

data class NotificationEntry(
    val notification: AppNotification,
    val isRead: Boolean
)

/** The notification centre as the UI needs it: only what is visible, already filtered by the user's choices. */
data class NotificationCenterUiState(
    val entries: List<NotificationEntry> = emptyList(),
    /** Still-active notifications the user has cleared, which the settings sheet can bring back. */
    val dismissedCount: Int = 0,
    val mutedCategories: Set<NotificationCategory> = emptySet(),
    val now: Long = 0L
) {
    val unreadCount: Int get() = entries.count { !it.isRead }
    fun countFor(category: NotificationCategory): Int = entries.count { it.notification.category == category }
}

object NotificationCenter {

    fun build(
        all: List<AppNotification>,
        state: NotificationUserState,
        now: Long = System.currentTimeMillis()
    ): NotificationCenterUiState {
        val enabled = all.filter { it.category !in state.mutedCategories }
        val visible = enabled.filter { it.id !in state.dismissed }
        return NotificationCenterUiState(
            entries = visible.map { NotificationEntry(it, isRead = it.id in state.read) },
            dismissedCount = enabled.size - visible.size,
            mutedCategories = state.mutedCategories,
            now = now
        )
    }
}
