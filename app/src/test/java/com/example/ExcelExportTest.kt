package com.example

import com.example.data.export.ExcelExport
import com.example.data.export.ExportMonth
import com.example.data.export.XlsxCell
import com.example.data.export.XlsxSheet
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.BudgetScope
import com.example.data.local.entities.ExpenseEntity
import com.example.data.local.entities.GoalContributionEntity
import com.example.data.local.entities.IncomeEntity
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.UserDataBackup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ExcelExportTest {

    private val now = at(2026, Calendar.SEPTEMBER, 15)
    private val september = ExportMonth(2026, Calendar.SEPTEMBER)
    private val august = ExportMonth(2026, Calendar.AUGUST)
    private val july = ExportMonth(2026, Calendar.JULY)

    /** Every month either side of the test data, for the cases that are not about filtering. */
    private val everyMonth: Set<ExportMonth> =
        ((0..11).map { ExportMonth(2026, it) } + (0..11).map { ExportMonth(2025, it) }).toSet()

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12) = Calendar.getInstance().apply {
        clear()
        set(year, month, day, hour, 0, 0)
    }.timeInMillis

    private val user = UserEntity(
        id = 1, username = "amith", email = "a@t.com", displayName = "Amith", currencySymbol = "₹"
    )

    private fun expense(id: Long, title: String, amount: Double, date: Long, category: String = "Food") =
        ExpenseEntity(id = id, userId = 1, title = title, amount = amount, category = category, date = date)

    private fun income(id: Long, title: String, amount: Double, date: Long) =
        IncomeEntity(id = id, userId = 1, title = title, amount = amount, category = "Salary", date = date)

    private fun backup(
        income: List<IncomeEntity> = emptyList(),
        expenses: List<ExpenseEntity> = emptyList(),
        savingsTransactions: List<SavingsTransactionEntity> = emptyList(),
        savingsGoals: List<SavingsGoalEntity> = emptyList(),
        goalContributions: List<GoalContributionEntity> = emptyList(),
        moneyFlows: List<MoneyFlowEntity> = emptyList(),
        wishlistItems: List<WishlistItemEntity> = emptyList(),
        budgets: List<BudgetEntity> = emptyList()
    ) = UserDataBackup(
        user = user,
        income = income,
        expenses = expenses,
        savingsTransactions = savingsTransactions,
        savingsGoals = savingsGoals,
        goalContributions = goalContributions,
        moneyFlows = moneyFlows,
        wishlistItems = wishlistItems,
        budgets = budgets
    )

    // ------------------------------------------------------------------ helpers

    private fun List<XlsxSheet>.sheet(name: String) = single { it.name == name }
    private fun XlsxSheet.column(header: String) = columns.indexOfFirst { it.header.startsWith(header) }
        .also { assertTrue("no '$header' column on $name", it >= 0) }

    private fun XlsxSheet.texts(header: String): List<String> {
        val index = column(header)
        return rows.map { (it[index] as? XlsxCell.Text)?.value ?: "" }
    }

    private fun XlsxSheet.amounts(header: String): List<Double> {
        val index = column(header)
        return rows.map { (it[index] as? XlsxCell.Money)?.value ?: 0.0 }
    }

    /** The summary sheet is a label/value report, so figures are looked up by their label. */
    private fun XlsxSheet.figure(label: String): Double {
        val row = rows.single { (it.first() as? XlsxCell.Text)?.value == label }
        return (row[1] as XlsxCell.Money).value
    }

    private fun XlsxSheet.note(label: String): String {
        val row = rows.single { (it.first() as? XlsxCell.Text)?.value == label }
        return (row[1] as XlsxCell.Text).value
    }

    // ------------------------------------------------------------------- months

    @Test
    fun theMonthsOnOfferAreTheOnesHoldingRecordsNewestFirst() {
        val dates = listOf(
            at(2026, Calendar.JULY, 3),
            at(2026, Calendar.SEPTEMBER, 1),
            at(2026, Calendar.AUGUST, 10)
        )

        assertEquals(listOf(september, august, july), ExcelExport.monthsFrom(dates))
    }

    @Test
    fun aMonthIsListedOnceHoweverManyRecordsItHolds() {
        val dates = (1..5).map { at(2026, Calendar.SEPTEMBER, it) }

        assertEquals(listOf(september), ExcelExport.monthsFrom(dates))
    }

    @Test
    fun anAccountWithNothingInItOffersNoMonths() {
        assertEquals(emptyList<ExportMonth>(), ExcelExport.monthsFrom(emptyList()))
    }

    // -------------------------------------------------------------- transactions

    @Test
    fun everyTransactionInTheChosenMonthsIsListedNewestFirst() {
        val data = backup(
            income = listOf(income(1, "Salary", 50_000.0, at(2026, Calendar.SEPTEMBER, 1))),
            expenses = listOf(
                expense(1, "Rent", 12_000.0, at(2026, Calendar.AUGUST, 3)),
                expense(2, "Coffee", 180.0, at(2026, Calendar.SEPTEMBER, 12))
            )
        )

        val sheet = ExcelExport.build(data, everyMonth, now).sheet("Transactions")

        assertEquals(listOf("Coffee", "Salary", "Rent"), sheet.texts("Title"))
    }

    @Test
    fun moneyInIsPositiveAndMoneyOutIsNegativeSoAColumnCanBeSummed() {
        val data = backup(
            income = listOf(income(1, "Salary", 50_000.0, at(2026, Calendar.SEPTEMBER, 1))),
            expenses = listOf(expense(1, "Rent", 12_000.0, at(2026, Calendar.SEPTEMBER, 3)))
        )

        val sheet = ExcelExport.build(data, everyMonth, now).sheet("Transactions")

        assertEquals(listOf("Expense", "Income"), sheet.texts("Type"))
        assertEquals(listOf(-12_000.0, 50_000.0), sheet.amounts("Amount"))
        assertEquals(38_000.0, sheet.amounts("Amount").sum(), 0.001)
    }

    @Test
    fun pickingMonthsLeavesOutEverythingOutsideThem() {
        val data = backup(
            income = listOf(income(1, "Salary", 50_000.0, at(2026, Calendar.SEPTEMBER, 1))),
            expenses = listOf(
                expense(1, "August rent", 12_000.0, at(2026, Calendar.AUGUST, 3)),
                expense(2, "July rent", 11_000.0, at(2026, Calendar.JULY, 3))
            )
        )

        val sheet = ExcelExport.build(data, setOf(august), now).sheet("Transactions")

        assertEquals(listOf("August rent"), sheet.texts("Title"))
    }

    @Test
    fun severalMonthsCanBeExportedTogether() {
        val data = backup(
            expenses = listOf(
                expense(1, "September", 1.0, at(2026, Calendar.SEPTEMBER, 3)),
                expense(2, "August", 2.0, at(2026, Calendar.AUGUST, 3)),
                expense(3, "July", 3.0, at(2026, Calendar.JULY, 3))
            )
        )

        val sheet = ExcelExport.build(data, setOf(september, july), now).sheet("Transactions")

        assertEquals(listOf("September", "July"), sheet.texts("Title"))
    }

    @Test
    fun theCurrencySymbolIsNamedInTheAmountHeading() {
        val sheet = ExcelExport.build(backup(), everyMonth, now).sheet("Transactions")

        assertTrue(sheet.columns.toString(), sheet.columns.any { it.header == "Amount (₹)" })
    }

    @Test
    fun storedCodesAreTurnedIntoSomethingReadable() {
        val auto = ExpenseEntity(
            id = 1, userId = 1, title = "Swiggy", amount = 450.0, category = "Auto",
            date = at(2026, Calendar.SEPTEMBER, 2), paymentMethod = "notification_auto", recurrence = "MONTHLY"
        )

        val sheet = ExcelExport.build(backup(expenses = listOf(auto)), everyMonth, now).sheet("Transactions")

        assertEquals(listOf("Auto-detected"), sheet.texts("Payment method"))
        assertEquals(listOf("Monthly"), sheet.texts("Repeats"))
    }

    // ------------------------------------------------------------------ budgets

    @Test
    fun budgetsGetARowPerMonthWithThatMonthsSpending() {
        val data = backup(
            expenses = listOf(
                expense(1, "Groceries", 8_000.0, at(2026, Calendar.SEPTEMBER, 4)),
                expense(2, "Groceries", 3_000.0, at(2026, Calendar.AUGUST, 4))
            ),
            budgets = listOf(
                BudgetEntity(id = 1, userId = 1, category = "Food", allocatedAmount = 7_000.0, createdAt = 0L)
            )
        )

        val sheet = ExcelExport.build(data, setOf(september, august), now).sheet("Budgets")

        assertEquals(listOf("September 2026", "August 2026"), sheet.texts("Month"))
        assertEquals(listOf(8_000.0, 3_000.0), sheet.amounts("Spent"))
        assertEquals(listOf("Over", "On track"), sheet.texts("Status"))
    }

    @Test
    fun aMonthBeforeTheEnvelopeExistedIsNotReportedAsAMiss() {
        val data = backup(
            expenses = listOf(expense(1, "Groceries", 9_000.0, at(2026, Calendar.JULY, 4))),
            budgets = listOf(
                BudgetEntity(
                    id = 1, userId = 1, category = "Food", allocatedAmount = 7_000.0,
                    createdAt = at(2026, Calendar.SEPTEMBER, 1)
                )
            )
        )

        val sheet = ExcelExport.build(data, setOf(september, july), now).sheet("Budgets")

        assertEquals(listOf("September 2026"), sheet.texts("Month"))
    }

    @Test
    fun theOverallEnvelopeIsLabelledAsSuch() {
        val data = backup(
            expenses = listOf(expense(1, "Anything", 500.0, at(2026, Calendar.SEPTEMBER, 4), category = "Shopping")),
            budgets = listOf(
                BudgetEntity(
                    id = 1, userId = 1, category = "", allocatedAmount = 60_000.0,
                    scope = BudgetScope.OVERALL, createdAt = 0L
                )
            )
        )

        val sheet = ExcelExport.build(data, setOf(september), now).sheet("Budgets")

        assertEquals(listOf("Overall"), sheet.texts("Scope"))
        assertEquals(listOf("Overall budget"), sheet.texts("Envelope"))
    }

    // ------------------------------------------------------------------ savings

    @Test
    fun depositsAndWithdrawalsReadWithTheRightSign() {
        val data = backup(
            savingsTransactions = listOf(
                SavingsTransactionEntity(
                    id = 1, userId = 1, savingsType = SavingsType.ADULT_MONEY,
                    transactionType = SavingsActionType.DEPOSIT, amount = 5_000.0,
                    title = "Set aside", date = at(2026, Calendar.SEPTEMBER, 2)
                ),
                SavingsTransactionEntity(
                    id = 2, userId = 1, savingsType = SavingsType.EMERGENCY_FUND,
                    transactionType = SavingsActionType.WITHDRAWAL, amount = 1_000.0,
                    title = "Repair", date = at(2026, Calendar.SEPTEMBER, 5)
                )
            )
        )

        val sheet = ExcelExport.build(data, everyMonth, now).sheet("Savings")

        assertEquals(listOf("Emergency Fund", "Adult Money"), sheet.texts("Fund"))
        assertEquals(listOf("Withdrawal", "Deposit"), sheet.texts("Action"))
        assertEquals(listOf(-1_000.0, 5_000.0), sheet.amounts("Amount"))
    }

    @Test
    fun goalsCarryTodaysProgressWhicheverPeriodIsChosen() {
        val data = backup(
            savingsGoals = listOf(
                SavingsGoalEntity(
                    id = 1, userId = 1, title = "New phone", goalAmount = 80_000.0,
                    savedAmount = 60_000.0, createdAt = at(2026, Calendar.JUNE, 1)
                )
            ),
            savingsTransactions = listOf(
                SavingsTransactionEntity(
                    id = 1, userId = 1, savingsType = SavingsType.ADULT_MONEY,
                    transactionType = SavingsActionType.DEPOSIT, amount = 90_000.0,
                    title = "Set aside", date = at(2026, Calendar.JUNE, 1)
                )
            )
        )

        val sheet = ExcelExport.build(data, setOf(september), now).sheet("Savings Goals")

        assertEquals(listOf("New phone"), sheet.texts("Goal"))
        assertEquals(listOf(60_000.0), sheet.amounts("Saved"))
        assertEquals(listOf(20_000.0), sheet.amounts("Remaining"))
    }

    @Test
    fun goalContributionsNameTheirGoalAndFollowTheSelectedPeriod() {
        val data = backup(
            savingsGoals = listOf(
                SavingsGoalEntity(id = 7, userId = 1, title = "New phone", goalAmount = 80_000.0)
            ),
            goalContributions = listOf(
                GoalContributionEntity(
                    id = 1, userId = 1, goalId = 7, transactionType = SavingsActionType.DEPOSIT,
                    amount = 5_000.0, date = at(2026, Calendar.SEPTEMBER, 2)
                ),
                GoalContributionEntity(
                    id = 2, userId = 1, goalId = 7, transactionType = SavingsActionType.WITHDRAWAL,
                    amount = 1_000.0, date = at(2026, Calendar.JULY, 2)
                )
            )
        )

        val sheet = ExcelExport.build(data, setOf(september), now).sheet("Goal Contributions")

        assertEquals(listOf("New phone"), sheet.texts("Goal"))
        assertEquals(listOf(5_000.0), sheet.amounts("Amount"))
    }

    // --------------------------------------------------------------- money flow

    @Test
    fun moneyFlowDirectionDecidesTheSignAndTheStatusIsSpeltOut() {
        val data = backup(
            moneyFlows = listOf(
                MoneyFlowEntity(
                    id = 1, userId = 1, personName = "Sarah", direction = MoneyFlowDirection.OWED_TO_ME,
                    amount = 5_000.0, date = at(2026, Calendar.SEPTEMBER, 2), dueDate = at(2026, Calendar.SEPTEMBER, 20)
                ),
                MoneyFlowEntity(
                    id = 2, userId = 1, personName = "Rahul", direction = MoneyFlowDirection.I_OWE,
                    amount = 1_500.0, date = at(2026, Calendar.SEPTEMBER, 1), isSettled = true
                )
            )
        )

        val sheet = ExcelExport.build(data, everyMonth, now).sheet("Money Flow")

        assertEquals(listOf("Sarah", "Rahul"), sheet.texts("Person"))
        assertEquals(listOf("Owed to you", "You owe"), sheet.texts("Direction"))
        assertEquals(listOf(5_000.0, -1_500.0), sheet.amounts("Amount"))
        assertEquals(listOf("Pending", "Settled"), sheet.texts("Status"))
    }

    // ----------------------------------------------------------------- wishlist

    @Test
    fun theWishlistIsReducedToItemsThatHaveALink() {
        val data = backup(
            wishlistItems = listOf(
                WishlistItemEntity(id = 1, userId = 1, title = "Headphones", estimatedCost = 8_000.0, url = "https://shop/x"),
                WishlistItemEntity(id = 2, userId = 1, title = "Desk", estimatedCost = 15_000.0, url = "")
            )
        )

        val sheet = ExcelExport.build(data, everyMonth, now).sheet("Wishlist Links")

        assertEquals(listOf("Item", "Link"), sheet.columns.map { it.header })
        assertEquals(listOf("Headphones"), sheet.texts("Item"))
        assertEquals(listOf("https://shop/x"), sheet.texts("Link"))
    }

    @Test
    fun noPriceOrPriorityLeaksIntoTheSpreadsheet() {
        val data = backup(
            wishlistItems = listOf(
                WishlistItemEntity(
                    id = 1, userId = 1, title = "Headphones", estimatedCost = 8_000.0,
                    url = "https://shop/x", priority = "HIGH", store = "Croma", notes = "wait for sale"
                )
            )
        )

        val everything = ExcelExport.build(data, everyMonth, now)
            .flatMap { sheet -> sheet.rows.flatten() + sheet.columns.map { XlsxCell.Text(it.header) } }
            .joinToString(" ")

        assertFalse(everything, everything.contains("Croma"))
        assertFalse(everything, everything.contains("HIGH"))
        assertFalse(everything, everything.contains("wait for sale"))
    }

    // ------------------------------------------------------------------ summary

    @Test
    fun summaryTotalsFollowTheChosenPeriodWhileBalancesStayAllTime() {
        val data = backup(
            income = listOf(
                income(1, "Salary", 50_000.0, at(2026, Calendar.SEPTEMBER, 1)),
                income(2, "Salary", 40_000.0, at(2026, Calendar.AUGUST, 1))
            ),
            expenses = listOf(
                expense(1, "Rent", 12_000.0, at(2026, Calendar.SEPTEMBER, 3)),
                expense(2, "Rent", 11_000.0, at(2026, Calendar.AUGUST, 3))
            ),
            savingsTransactions = listOf(
                SavingsTransactionEntity(
                    id = 1, userId = 1, savingsType = SavingsType.ADULT_MONEY,
                    transactionType = SavingsActionType.DEPOSIT, amount = 20_000.0,
                    title = "Set aside", date = at(2026, Calendar.AUGUST, 20)
                )
            )
        )

        val sheet = ExcelExport.build(data, setOf(september), now).sheet("Summary")

        assertEquals("only September income", 50_000.0, sheet.figure("Total income"), 0.001)
        assertEquals("expenses read as money out", -12_000.0, sheet.figure("Total expenses"), 0.001)
        assertEquals(38_000.0, sheet.figure("Net"), 0.001)
        // Balances are cumulative: 90,000 earned, 23,000 spent, 20,000 moved into savings.
        assertEquals(47_000.0, sheet.figure("Available money"), 0.001)
        assertEquals(20_000.0, sheet.figure("Adult Money"), 0.001)
        assertEquals("September 2026", sheet.note("Period"))
    }

    @Test
    fun theSummarySaysWhoAndWhichMonthsAndIsNotAFilterableTable() {
        val sheet = ExcelExport.build(backup(), setOf(september, august), now).sheet("Summary")

        assertEquals("Amith (@amith)", sheet.note("Account"))
        assertEquals("September 2026, August 2026", sheet.note("Period"))
        assertEquals("₹", sheet.note("Currency"))
        assertFalse("a report sheet should not carry a filter", sheet.autoFilter)
    }

    // -------------------------------------------------------------- the workbook

    @Test
    fun everySheetIsPresentEvenForAnEmptyAccount() {
        val sheets = ExcelExport.build(backup(), everyMonth, now)

        assertEquals(
            listOf(
                "Summary", "Transactions", "Budgets", "Savings",
                "Savings Goals", "Goal Contributions", "Money Flow", "Wishlist Links"
            ),
            sheets.map { it.name }
        )
        sheets.forEach { sheet ->
            assertTrue("${sheet.name} has no columns", sheet.columns.isNotEmpty())
        }
    }

    @Test
    fun everyRowMatchesItsSheetsColumnCount() {
        val data = backup(
            income = listOf(income(1, "Salary", 50_000.0, at(2026, Calendar.SEPTEMBER, 1))),
            expenses = listOf(expense(1, "Rent", 12_000.0, at(2026, Calendar.SEPTEMBER, 3))),
            budgets = listOf(BudgetEntity(id = 1, userId = 1, category = "Food", allocatedAmount = 7_000.0, createdAt = 0L)),
            wishlistItems = listOf(WishlistItemEntity(id = 1, userId = 1, title = "X", estimatedCost = 1.0, url = "https://a"))
        )

        ExcelExport.build(data, everyMonth, now).forEach { sheet ->
            sheet.rows.forEachIndexed { index, row ->
                assertEquals("${sheet.name} row $index", sheet.columns.size, row.size)
            }
        }
    }

    @Test
    fun theFileNameSaysWhichMonthsWereExported() {
        assertEquals("Kyash-export-2026-09.xlsx", ExcelExport.fileName(setOf(september)))
        assertEquals("Kyash-export-2026-07_to_2026-09.xlsx", ExcelExport.fileName(setOf(september, july)))
        assertEquals("Kyash-export-empty.xlsx", ExcelExport.fileName(emptySet()))
    }

    @Test
    fun nothingIsExportedForAMonthThatWasNotChosen() {
        val data = backup(expenses = listOf(expense(1, "Rent", 12_000.0, at(2026, Calendar.AUGUST, 3))))

        val sheet = ExcelExport.build(data, setOf(september), now).sheet("Transactions")

        assertTrue(sheet.rows.toString(), sheet.rows.isEmpty())
    }

    @Test
    fun aMonthKnowsItsOwnBoundaries() {
        assertTrue(at(2026, Calendar.SEPTEMBER, 1, hour = 0) in september)
        assertTrue(at(2026, Calendar.SEPTEMBER, 30, hour = 23) in september)
        assertFalse(at(2026, Calendar.AUGUST, 31, hour = 23) in september)
        assertFalse(at(2026, Calendar.OCTOBER, 1, hour = 0) in september)
        assertEquals("September 2026", september.label)
        assertEquals("September", september.monthLabel)
        assertEquals("2026-09", september.key)
    }
}
