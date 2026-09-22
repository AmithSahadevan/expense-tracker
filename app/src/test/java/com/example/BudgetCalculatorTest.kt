package com.example

import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.BudgetScope
import com.example.data.model.BudgetCalculator
import com.example.data.model.BudgetInput
import com.example.data.model.BudgetStatus
import com.example.data.model.BudgetValidator
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BudgetCalculatorTest {

    /** Mid-month so "last month" and "next month" are both easy to land on. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val day = 86_400_000L

    private fun budget(
        id: Long,
        category: String,
        allocated: Double,
        scope: String = BudgetScope.CATEGORY
    ) = BudgetEntity(
        id = id,
        userId = 1,
        category = category,
        allocatedAmount = allocated,
        scope = scope
    )

    private fun expense(id: Long, category: String, amount: Double, date: Long = now) = TransactionItem(
        id = id,
        userId = 1,
        title = "e$id",
        amount = amount,
        type = TransactionType.EXPENSE,
        category = category,
        date = date
    )

    private fun income(id: Long, amount: Double, date: Long = now) = TransactionItem(
        id = id,
        userId = 1,
        title = "i$id",
        amount = amount,
        type = TransactionType.INCOME,
        category = "Salary",
        date = date
    )

    private val food = budget(1, "Food", 6_000.0)
    private val fuel = budget(2, "Fuel", 4_000.0)
    private val subscriptions = budget(3, "Subscriptions", 1_000.0)
    private val overall = budget(4, BudgetScope.OVERALL, 20_000.0, BudgetScope.OVERALL)

    @Test
    fun spendComesFromRecordedExpensesNotTheStoredColumn() {
        // The stale column says 99,999; the ledger says 2,500. The ledger wins.
        val stale = food.copy(spentAmount = 99_999.0)
        val progress = BudgetCalculator.progress(
            stale,
            listOf(expense(1, "Food", 1_500.0), expense(2, "Food", 1_000.0)),
            now
        )

        assertEquals(2_500.0, progress.spent, 0.001)
        assertEquals(3_500.0, progress.remaining, 0.001)
    }

    @Test
    fun onlyThisMonthsExpensesCount() {
        val transactions = listOf(
            expense(1, "Food", 1_000.0),
            expense(2, "Food", 5_000.0, date = now - 30 * day), // August
            expense(3, "Food", 900.0, date = now + 30 * day)    // October
        )

        assertEquals(1_000.0, BudgetCalculator.progress(food, transactions, now).spent, 0.001)
    }

    @Test
    fun incomeNeverCountsAgainstABudget() {
        val transactions = listOf(income(1, 50_000.0), expense(2, "Food", 600.0))

        assertEquals(600.0, BudgetCalculator.progress(food, transactions, now).spent, 0.001)
    }

    @Test
    fun categoryMatchingIgnoresCase() {
        val transactions = listOf(expense(1, "food", 700.0), expense(2, "FOOD", 300.0))

        assertEquals(1_000.0, BudgetCalculator.progress(food, transactions, now).spent, 0.001)
    }

    @Test
    fun overallBudgetCountsEveryCategory() {
        val transactions = listOf(
            expense(1, "Food", 2_000.0),
            expense(2, "Fuel", 1_500.0),
            expense(3, "Anything", 500.0)
        )

        val progress = BudgetCalculator.progress(overall, transactions, now)
        assertEquals(4_000.0, progress.spent, 0.001)
        assertTrue(progress.isOverall)
    }

    @Test
    fun statusCrossesToApproachingAtEightyPercent() {
        assertEquals(BudgetStatus.ON_TRACK, BudgetCalculator.statusFor(4_799.0, 6_000.0))
        assertEquals(BudgetStatus.APPROACHING, BudgetCalculator.statusFor(4_800.0, 6_000.0))
        assertEquals(BudgetStatus.APPROACHING, BudgetCalculator.statusFor(6_000.0, 6_000.0))
        assertEquals(BudgetStatus.OVER, BudgetCalculator.statusFor(6_000.01, 6_000.0))
    }

    @Test
    fun overspendingReportsTheOverageNotANegativePercentage() {
        val progress = BudgetCalculator.progress(fuel, listOf(expense(1, "Fuel", 5_000.0)), now)

        assertEquals(BudgetStatus.OVER, progress.status)
        assertEquals(-1_000.0, progress.remaining, 0.001)
        assertEquals(1_000.0, progress.overspentBy, 0.001)
        assertEquals(125, progress.percentLabel)
        assertTrue(progress.isExhausted)
    }

    @Test
    fun anEnvelopeWithNoSpendIsOnTrackAtZeroPercent() {
        val progress = BudgetCalculator.progress(subscriptions, emptyList(), now)

        assertEquals(0.0, progress.spent, 0.001)
        assertEquals(1_000.0, progress.remaining, 0.001)
        assertEquals(0, progress.percentLabel)
        assertEquals(BudgetStatus.ON_TRACK, progress.status)
    }

    @Test
    fun summaryKeepsTheOverallEnvelopeOutOfTheCategoryTotals() {
        val transactions = listOf(
            expense(1, "Food", 5_000.0),
            expense(2, "Fuel", 1_000.0),
            expense(3, "Rent", 9_000.0)
        )

        val summary = BudgetCalculator.summarize(listOf(overall, food, fuel), transactions, now)

        assertNotNull(summary.overall)
        assertEquals(15_000.0, summary.overall!!.spent, 0.001)
        assertEquals(2, summary.categories.size)
        // Rent has no envelope, so it is in the month total but not in the category total.
        assertEquals(6_000.0, summary.categorySpent, 0.001)
        assertEquals(10_000.0, summary.categoryAllocated, 0.001)
        assertEquals(15_000.0, summary.monthSpent, 0.001)
    }

    @Test
    fun theFullestEnvelopeSortsFirstSoWarningsAreVisible() {
        val transactions = listOf(expense(1, "Food", 1_000.0), expense(2, "Fuel", 3_900.0))

        val summary = BudgetCalculator.summarize(listOf(food, fuel), transactions, now)

        assertEquals("Fuel", summary.categories.first().label)
        assertEquals(1, summary.approaching.size)
        assertEquals(1, summary.alertCount)
    }

    @Test
    fun theDashboardLeadsWithTheOverallEnvelopeWhenThereIsOne() {
        val transactions = listOf(expense(1, "Food", 5_900.0))

        val withOverall = BudgetCalculator.summarize(listOf(food, overall), transactions, now)
        assertTrue(withOverall.headline!!.isOverall)

        // Without one it leads with the tightest category instead of double-counting.
        val withoutOverall = BudgetCalculator.summarize(listOf(food, fuel), transactions, now)
        assertEquals("Food", withoutOverall.headline!!.label)
    }

    @Test
    fun emptySummaryHasNothingToShow() {
        val summary = BudgetCalculator.summarize(emptyList(), listOf(expense(1, "Food", 10.0)), now)

        assertFalse(summary.hasBudgets)
        assertNull(summary.headline)
        assertEquals(0, summary.alertCount)
    }

    @Test
    fun aCategoryCannotGetTwoEnvelopes() {
        val existing = listOf(food)

        val duplicate = BudgetValidator.validate(BudgetInput(category = "food", allocatedAmount = 500.0), existing)
        assertNotNull(duplicate.category)
        assertFalse(duplicate.isValid)

        val fresh = BudgetValidator.validate(BudgetInput(category = "Travel", allocatedAmount = 500.0), existing)
        assertTrue(fresh.isValid)

        // Editing the same envelope is not a duplicate of itself.
        val editingItself = BudgetValidator.validate(
            BudgetInput(category = "Food", allocatedAmount = 7_000.0),
            existing,
            editingId = food.id
        )
        assertTrue(editingItself.isValid)
    }

    @Test
    fun onlyOneOverallEnvelopeIsAllowed() {
        val input = BudgetInput(scope = BudgetScope.OVERALL, allocatedAmount = 25_000.0)

        assertTrue(BudgetValidator.validate(input, listOf(food)).isValid)
        assertFalse(BudgetValidator.validate(input, listOf(overall)).isValid)
        assertTrue(BudgetValidator.validate(input, listOf(overall), editingId = overall.id).isValid)
    }

    @Test
    fun budgetsNeedANameAndAPositiveAmount() {
        assertNull(BudgetValidator.clean(BudgetInput(category = "  ", allocatedAmount = 100.0)))
        assertNull(BudgetValidator.clean(BudgetInput(category = "Food", allocatedAmount = 0.0)))
        assertEquals("Food", BudgetValidator.clean(BudgetInput(category = "  Food  ", allocatedAmount = 100.0))?.category)
    }

    @Test
    fun suggestionsSkipBudgetedCategoriesAndLeadWithTheBiggestSpend() {
        val transactions = listOf(
            expense(1, "Food", 5_000.0),
            expense(2, "Travel", 3_000.0),
            expense(3, "Health", 100.0)
        )

        val suggestions = BudgetCalculator.categoriesWithoutBudget(listOf(food), transactions)

        assertFalse(suggestions.any { it.equals("Food", ignoreCase = true) })
        assertEquals("Travel", suggestions.first())
    }

    @Test
    fun daysLeftCountsTodayAndNeverGoesNegative() {
        val lastDay = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 30, 23, 0, 0)
        }.timeInMillis

        assertEquals(16, BudgetCalculator.daysLeftInMonth(now)) // 15th of a 30-day month
        assertEquals(1, BudgetCalculator.daysLeftInMonth(lastDay))
    }
}
