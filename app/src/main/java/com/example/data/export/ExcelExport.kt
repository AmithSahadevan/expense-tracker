package com.example.data.export

import com.example.data.local.entities.BudgetScope
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsType
import com.example.data.model.BudgetCalculator
import com.example.data.model.BudgetStatus
import com.example.data.model.MoneyFlowCalculator
import com.example.data.model.SavingsCalculator
import com.example.data.model.SavingsGoalCalculator
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.data.model.UserDataBackup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One calendar month of the ledger, as the export's month picker offers it. */
data class ExportMonth(val year: Int, val month: Int) : Comparable<ExportMonth> {

    /** `[start, end)` of this month in the device's time zone. */
    val range: LongRange
        get() {
            val cal = calendar()
            val start = cal.timeInMillis
            cal.add(Calendar.MONTH, 1)
            return start until cal.timeInMillis
        }

    val label: String get() = format("MMMM yyyy")

    /** Just the month's name, for a list that already says which year it belongs to. */
    val monthLabel: String get() = format("MMMM")

    private fun format(pattern: String) =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(calendar().timeInMillis))

    /** Sortable and filename-safe: `2026-09`. */
    val key: String get() = "%04d-%02d".format(year, month + 1)

    operator fun contains(millis: Long): Boolean = millis in range

    private fun calendar(): Calendar = Calendar.getInstance().apply {
        clear()
        set(year, month, 1, 0, 0, 0)
    }

    override fun compareTo(other: ExportMonth): Int =
        compareValuesBy(this, other, { it.year }, { it.month })

    companion object {
        fun of(millis: Long): ExportMonth {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return ExportMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH))
        }
    }
}

/**
 * Turns a user's data into the sheets of a spreadsheet.
 *
 * An export always covers a chosen set of months rather than the whole history, so the file stays
 * a size a person can actually read however long the account has been running.
 *
 * Amounts are signed throughout: money arriving is positive and money leaving is negative, so any
 * column of amounts can simply be summed. The wishlist is deliberately reduced to its links -
 * prices and priorities belong in the app, not in a financial record.
 */
object ExcelExport {

    /** The months these timestamps fall in, newest first, with no repeats. */
    fun monthsFrom(dates: List<Long>): List<ExportMonth> =
        dates.map { ExportMonth.of(it) }.distinct().sortedDescending()

    /** Named after the months it holds, so saved files sort and read sensibly in a folder. */
    fun fileName(months: Set<ExportMonth>): String {
        val sorted = months.sorted()
        val suffix = when {
            sorted.isEmpty() -> "empty"
            sorted.size == 1 -> sorted.first().key
            else -> "${sorted.first().key}_to_${sorted.last().key}"
        }
        return "Kyash-export-$suffix.xlsx"
    }

    fun build(
        backup: UserDataBackup,
        months: Set<ExportMonth>,
        now: Long = System.currentTimeMillis()
    ): List<XlsxSheet> {
        val currency = backup.user?.currencySymbol ?: "₹"
        val amount = "Amount ($currency)"
        val ledger = (backup.expenses.map { it.toItem() } + backup.income.map { it.toItem() })
        val inScope = ledger.filter { months.covers(it.date) }.sortedByDescending { it.date }

        return listOf(
            summarySheet(backup, months, inScope, now, currency),
            transactionsSheet(inScope, amount),
            budgetsSheet(backup, ledger, months, currency),
            savingsSheet(backup, months, amount),
            goalsSheet(backup, currency),
            contributionsSheet(backup, months, amount),
            moneyFlowSheet(backup, months, amount, now),
            wishlistLinksSheet(backup)
        )
    }

    // ---------------------------------------------------------------- summary

    private fun summarySheet(
        backup: UserDataBackup,
        months: Set<ExportMonth>,
        inScope: List<TransactionItem>,
        now: Long,
        currency: String
    ): XlsxSheet {
        val income = inScope.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = inScope.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        val savings = backup.savingsTransactions
        val adultMoney = SavingsCalculator.balance(savings, SavingsType.ADULT_MONEY)
        val emergency = SavingsCalculator.balance(savings, SavingsType.EMERGENCY_FUND)
        val movedToSavings = SavingsCalculator.movedOutOfAvailableMoney(savings)
        val allIncome = backup.income.sumOf { it.amount }
        val allExpense = backup.expenses.sumOf { it.amount }
        val flow = MoneyFlowCalculator.summarize(backup.moneyFlows, now)

        val rows = mutableListOf<List<XlsxCell>>()
        fun line(label: String, cell: XlsxCell) = rows.add(listOf(XlsxCell.Text(label), cell))
        fun section(title: String) {
            rows.add(listOf(XlsxCell.Empty, XlsxCell.Empty))
            rows.add(listOf(XlsxCell.Label(title), XlsxCell.Empty))
        }

        val user = backup.user
        line("Account", XlsxCell.Text(user?.let { "${it.displayName} (@${it.username})" } ?: "Unknown"))
        line("Exported", XlsxCell.DayTime(now))
        line("Period", XlsxCell.Text(months.describe()))
        line("Currency", XlsxCell.Text(currency))

        section("INCOME & SPENDING (selected period)")
        line("Total income", XlsxCell.Money(income))
        line("Total expenses", XlsxCell.Money(-expense))
        line("Net", XlsxCell.Money(income - expense))
        line("Transactions", XlsxCell.Number(inScope.size.toDouble()))

        section("BALANCES (all time, as of export)")
        line("Available money", XlsxCell.Money(allIncome - allExpense - movedToSavings))
        line("Moved into savings", XlsxCell.Money(movedToSavings))
        line("Adult Money", XlsxCell.Money(adultMoney))
        line("Emergency Fund", XlsxCell.Money(emergency))
        line("Total savings", XlsxCell.Money(adultMoney + emergency))

        section("PENDING MONEY FLOW (all time)")
        line("Owed to you", XlsxCell.Money(flow.expectedIncoming))
        line("You owe", XlsxCell.Money(-flow.pendingObligations))
        line("Overdue records", XlsxCell.Number(flow.overdueCount.toDouble()))

        section("HOW TO READ THIS")
        line("Amounts", XlsxCell.Text("Money in is positive, money out is negative, so a column can be summed."))
        line("Balances", XlsxCell.Text("Always all-time: a balance is cumulative and cannot be cut to one month."))
        line("Goals", XlsxCell.Text("Listed with today's progress, whichever period is selected."))
        line("Wishlist", XlsxCell.Text("Only the saved links are exported."))

        return XlsxSheet(
            name = "Summary",
            columns = listOf(XlsxColumn("Item", 30), XlsxColumn("Value", 46)),
            rows = rows,
            autoFilter = false
        )
    }

    // ----------------------------------------------------------------- sheets

    private fun transactionsSheet(inScope: List<TransactionItem>, amount: String) = XlsxSheet(
        name = "Transactions",
        columns = listOf(
            XlsxColumn("Date", 14),
            XlsxColumn("Type", 10),
            XlsxColumn("Title", 28),
            XlsxColumn("Category", 18),
            XlsxColumn(amount, 14),
            XlsxColumn("Payment method", 18),
            XlsxColumn("Repeats", 12),
            XlsxColumn("Notes", 34)
        ),
        rows = inScope.map { tx ->
            listOf(
                XlsxCell.Day(tx.date),
                XlsxCell.Text(if (tx.type == TransactionType.INCOME) "Income" else "Expense"),
                XlsxCell.Text(tx.title),
                XlsxCell.Text(tx.category),
                XlsxCell.Money(tx.signedAmount),
                XlsxCell.Text(tx.paymentMethod.readable()),
                XlsxCell.Text(tx.recurrence.readable()),
                XlsxCell.Text(tx.notes)
            )
        }
    )

    /**
     * One row per envelope per month, so a multi-month export reads as budget history. A month
     * before the envelope existed is left out rather than shown as a miss it never had a chance at.
     */
    private fun budgetsSheet(
        backup: UserDataBackup,
        ledger: List<TransactionItem>,
        months: Set<ExportMonth>,
        currency: String
    ): XlsxSheet {
        val rows = months.sortedDescending().flatMap { month ->
            val anchor = month.range.first
            val summary = BudgetCalculator.summarize(backup.budgets, ledger, anchor)
            summary.all
                .filter { ExportMonth.of(it.budget.createdAt) <= month }
                .map { progress ->
                    listOf(
                        XlsxCell.Text(month.label),
                        XlsxCell.Text(if (progress.isOverall) "Overall" else "Category"),
                        XlsxCell.Text(progress.label),
                        XlsxCell.Money(progress.allocated),
                        XlsxCell.Money(progress.spent),
                        XlsxCell.Money(progress.remaining),
                        XlsxCell.Percent(progress.percentUsed.toDouble()),
                        XlsxCell.Text(progress.status.readable())
                    )
                }
        }
        return XlsxSheet(
            name = "Budgets",
            columns = listOf(
                XlsxColumn("Month", 16),
                XlsxColumn("Scope", 10),
                XlsxColumn("Envelope", 22),
                XlsxColumn("Allocated ($currency)", 16),
                XlsxColumn("Spent ($currency)", 14),
                XlsxColumn("Remaining ($currency)", 18),
                XlsxColumn("Used", 10),
                XlsxColumn("Status", 14)
            ),
            rows = rows
        )
    }

    private fun savingsSheet(backup: UserDataBackup, months: Set<ExportMonth>, amount: String) = XlsxSheet(
        name = "Savings",
        columns = listOf(
            XlsxColumn("Date", 14),
            XlsxColumn("Fund", 18),
            XlsxColumn("Action", 12),
            XlsxColumn(amount, 14),
            XlsxColumn("Title", 28),
            XlsxColumn("Left available money", 20),
            XlsxColumn("Notes", 34)
        ),
        rows = backup.savingsTransactions
            .filter { months.covers(it.date) }
            .sortedByDescending { it.date }
            .map { entry ->
                val deposit = entry.transactionType == SavingsActionType.DEPOSIT
                listOf(
                    XlsxCell.Day(entry.date),
                    XlsxCell.Text(if (entry.savingsType == SavingsType.EMERGENCY_FUND) "Emergency Fund" else "Adult Money"),
                    XlsxCell.Text(if (deposit) "Deposit" else "Withdrawal"),
                    XlsxCell.Money(if (deposit) entry.amount else -entry.amount),
                    XlsxCell.Text(entry.title),
                    XlsxCell.Text(if (entry.affectsAvailableMoney) "Yes" else "No"),
                    XlsxCell.Text(entry.notes)
                )
            }
    )

    private fun goalsSheet(backup: UserDataBackup, currency: String): XlsxSheet {
        val adultMoney = SavingsCalculator.balance(backup.savingsTransactions, SavingsType.ADULT_MONEY)
        val summary = SavingsGoalCalculator.summarize(backup.savingsGoals, backup.goalContributions, adultMoney)
        return XlsxSheet(
            name = "Savings Goals",
            columns = listOf(
                XlsxColumn("Goal", 26),
                XlsxColumn("Target ($currency)", 16),
                XlsxColumn("Saved ($currency)", 16),
                XlsxColumn("Remaining ($currency)", 18),
                XlsxColumn("Progress", 12),
                XlsxColumn("Target date", 14),
                XlsxColumn("Complete", 12),
                XlsxColumn("Notes", 30)
            ),
            rows = summary.goals.map { goal ->
                listOf(
                    XlsxCell.Text(goal.title),
                    XlsxCell.Money(goal.target),
                    XlsxCell.Money(goal.currentAmount),
                    XlsxCell.Money(goal.remaining),
                    XlsxCell.Percent(goal.percentComplete.toDouble()),
                    goal.targetDate?.let { XlsxCell.Day(it) } ?: XlsxCell.Empty,
                    XlsxCell.Text(if (goal.isComplete) "Yes" else "No"),
                    XlsxCell.Text(goal.description)
                )
            }
        )
    }

    private fun contributionsSheet(backup: UserDataBackup, months: Set<ExportMonth>, amount: String): XlsxSheet {
        val goalNames = backup.savingsGoals.associate { it.id to it.title }
        return XlsxSheet(
            name = "Goal Contributions",
            columns = listOf(
                XlsxColumn("Date", 14),
                XlsxColumn("Goal", 26),
                XlsxColumn("Action", 12),
                XlsxColumn(amount, 14),
                XlsxColumn("Note", 34)
            ),
            rows = backup.goalContributions
                .filter { months.covers(it.date) }
                .sortedByDescending { it.date }
                .map { contribution ->
                    val deposit = contribution.transactionType == SavingsActionType.DEPOSIT
                    listOf(
                        XlsxCell.Day(contribution.date),
                        XlsxCell.Text(goalNames[contribution.goalId] ?: "Deleted goal"),
                        XlsxCell.Text(if (deposit) "Added" else "Withdrawn"),
                        XlsxCell.Money(if (deposit) contribution.amount else -contribution.amount),
                        XlsxCell.Text(contribution.note)
                    )
                }
        )
    }

    private fun moneyFlowSheet(
        backup: UserDataBackup,
        months: Set<ExportMonth>,
        amount: String,
        now: Long
    ) = XlsxSheet(
        name = "Money Flow",
        columns = listOf(
            XlsxColumn("Date", 14),
            XlsxColumn("Person", 22),
            XlsxColumn("Direction", 16),
            XlsxColumn(amount, 14),
            XlsxColumn("Due date", 14),
            XlsxColumn("Status", 14),
            XlsxColumn("Notes", 34)
        ),
        rows = backup.moneyFlows
            .filter { months.covers(it.date) }
            .sortedByDescending { it.date }
            .map { record ->
                val owedToMe = record.direction == MoneyFlowDirection.OWED_TO_ME
                listOf(
                    XlsxCell.Day(record.date),
                    XlsxCell.Text(record.personName),
                    XlsxCell.Text(if (owedToMe) "Owed to you" else "You owe"),
                    XlsxCell.Money(if (owedToMe) record.amount else -record.amount),
                    record.dueDate?.let { XlsxCell.Day(it) } ?: XlsxCell.Empty,
                    XlsxCell.Text(
                        when {
                            record.isSettled -> "Settled"
                            MoneyFlowCalculator.isOverdue(record, now) -> "Overdue"
                            else -> "Pending"
                        }
                    ),
                    XlsxCell.Text(record.notes)
                )
            }
    )

    /** Only the links: the wishlist is a shopping list, not part of the financial record. */
    private fun wishlistLinksSheet(backup: UserDataBackup) = XlsxSheet(
        name = "Wishlist Links",
        columns = listOf(XlsxColumn("Item", 34), XlsxColumn("Link", 62)),
        rows = backup.wishlistItems
            .filter { it.url.isNotBlank() }
            .sortedBy { it.title.lowercase() }
            .map { listOf(XlsxCell.Text(it.title), XlsxCell.Text(it.url)) }
    )

    // ---------------------------------------------------------------- helpers

    private fun Set<ExportMonth>.covers(millis: Long): Boolean = any { millis in it }

    private fun Set<ExportMonth>.describe(): String =
        sortedDescending().joinToString(", ") { it.label }.ifBlank { "Nothing selected" }

    private val TransactionItem.signedAmount: Double
        get() = if (type == TransactionType.INCOME) amount else -amount

    private fun BudgetStatus.readable(): String = when (this) {
        BudgetStatus.ON_TRACK -> "On track"
        BudgetStatus.APPROACHING -> "Approaching"
        BudgetStatus.OVER -> "Over"
    }

    /** Turns stored codes such as `NONE` or `notification_auto` into something readable. */
    private fun String.readable(): String = when (uppercase()) {
        "NONE" -> "No"
        "NOTIFICATION_AUTO" -> "Auto-detected"
        else -> lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun ExpenseEntity.toItem() = TransactionItem(
        id = id,
        userId = userId,
        title = title,
        amount = amount,
        type = TransactionType.EXPENSE,
        category = category,
        date = date,
        notes = notes,
        paymentMethod = paymentMethod,
        recurrence = recurrence
    )

    private fun IncomeEntity.toItem() = TransactionItem(
        id = id,
        userId = userId,
        title = title,
        amount = amount,
        type = TransactionType.INCOME,
        category = category,
        date = date,
        notes = notes,
        paymentMethod = paymentMethod,
        recurrence = recurrence
    )
}
