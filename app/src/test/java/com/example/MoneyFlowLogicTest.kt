package com.example

import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.model.MoneyFlowCalculator
import com.example.data.model.MoneyFlowFilter
import com.example.data.model.MoneyFlowInput
import com.example.data.model.MoneyFlowValidator
import com.example.data.model.RelativeDates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFlowLogicTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun flow(
        id: Long,
        direction: String,
        amount: Double,
        settled: Boolean = false,
        dueDate: Long? = null,
        date: Long = now
    ) = MoneyFlowEntity(
        id = id,
        userId = 1,
        personName = "Person $id",
        direction = direction,
        amount = amount,
        date = date,
        dueDate = dueDate,
        isSettled = settled
    )

    private val sarah = flow(1, MoneyFlowDirection.OWED_TO_ME, 2_000.0, dueDate = now + 3 * day)
    private val nina = flow(2, MoneyFlowDirection.OWED_TO_ME, 3_000.0, dueDate = now - 2 * day)
    private val rahul = flow(3, MoneyFlowDirection.I_OWE, 1_500.0)
    private val settledLoan = flow(4, MoneyFlowDirection.OWED_TO_ME, 9_000.0, settled = true)
    private val settledDebt = flow(5, MoneyFlowDirection.I_OWE, 700.0, settled = true, dueDate = now - 30 * day)
    private val records = listOf(sarah, nina, rahul, settledLoan, settledDebt)

    @Test
    fun onlyPendingRecordsCountTowardsTheTotals() {
        val summary = MoneyFlowCalculator.summarize(records, now)

        assertEquals(5_000.0, summary.expectedIncoming, 0.001)
        assertEquals(1_500.0, summary.pendingObligations, 0.001)
        assertEquals(2, summary.expectedCount)
        assertEquals(1, summary.obligationCount)
        assertEquals(3_500.0, summary.net, 0.001)
        assertTrue(summary.hasPending)
    }

    @Test
    fun overdueIsCountedOnlyForPendingRecordsWithAPastDueDate() {
        val summary = MoneyFlowCalculator.summarize(records, now)

        assertEquals(1, summary.overdueExpectedCount)
        assertEquals(0, summary.overdueObligationCount)
        assertEquals(1, summary.overdueCount)
        assertTrue(MoneyFlowCalculator.isOverdue(nina, now))
        assertFalse("a due date in the future is not overdue", MoneyFlowCalculator.isOverdue(sarah, now))
        assertFalse("records without a due date are never overdue", MoneyFlowCalculator.isOverdue(rahul, now))
        assertFalse("settled records are never overdue", MoneyFlowCalculator.isOverdue(settledDebt, now))
    }

    @Test
    fun nothingPendingGivesAnEmptySummary() {
        val summary = MoneyFlowCalculator.summarize(listOf(settledLoan, settledDebt), now)

        assertEquals(0.0, summary.expectedIncoming, 0.001)
        assertEquals(0.0, summary.pendingObligations, 0.001)
        assertFalse(summary.hasPending)
    }

    @Test
    fun sectionsSplitBySideAndPutTheMostUrgentFirst() {
        val owedToMe = MoneyFlowCalculator.section(records, MoneyFlowDirection.OWED_TO_ME, MoneyFlowFilter.PENDING)
        // Overdue (Nina) before the one due later (Sarah); the settled loan is filtered out.
        assertEquals(listOf(2L, 1L), owedToMe.map { it.id })

        assertEquals(listOf(3L), MoneyFlowCalculator.section(records, MoneyFlowDirection.I_OWE, MoneyFlowFilter.PENDING).map { it.id })
        assertEquals(listOf(4L), MoneyFlowCalculator.section(records, MoneyFlowDirection.OWED_TO_ME, MoneyFlowFilter.SETTLED).map { it.id })
        // "All" keeps pending first, settled last.
        assertEquals(listOf(2L, 1L, 4L), MoneyFlowCalculator.section(records, MoneyFlowDirection.OWED_TO_ME, MoneyFlowFilter.ALL).map { it.id })
    }

    @Test
    fun recordsWithoutDueDatesFallBackToNewestFirst() {
        val older = flow(6, MoneyFlowDirection.I_OWE, 100.0, date = now - 10 * day)
        val newer = flow(7, MoneyFlowDirection.I_OWE, 200.0, date = now - day)

        val section = MoneyFlowCalculator.section(listOf(older, newer, rahul), MoneyFlowDirection.I_OWE, MoneyFlowFilter.PENDING)

        assertEquals(listOf(3L, 7L, 6L), section.map { it.id })
    }

    @Test
    fun pendingTotalsAreCalculatedPerSide() {
        assertEquals(5_000.0, MoneyFlowCalculator.pendingTotal(records, MoneyFlowDirection.OWED_TO_ME), 0.001)
        assertEquals(1_500.0, MoneyFlowCalculator.pendingTotal(records, MoneyFlowDirection.I_OWE), 0.001)
    }

    @Test
    fun aPersonAndAPositiveAmountAreRequired() {
        val blank = MoneyFlowValidator.validate(MoneyFlowInput(" ", MoneyFlowDirection.I_OWE, 0.0))
        assertFalse(blank.isValid)
        assertTrue(blank.personName != null && blank.amount != null)
        assertNull(MoneyFlowValidator.clean(MoneyFlowInput("Rahul", MoneyFlowDirection.I_OWE, -5.0)))

        val clean = MoneyFlowValidator.clean(
            MoneyFlowInput("  Rahul ", MoneyFlowDirection.I_OWE, 1_500.0, notes = " concert tickets ")
        )!!
        assertEquals("Rahul", clean.personName)
        assertEquals("concert tickets", clean.notes)
    }

    @Test
    fun dueDatesReadAsPlainLanguage() {
        assertEquals("Due today", RelativeDates.describeDue(now, now))
        assertEquals("Due tomorrow", RelativeDates.describeDue(now + day, now))
        assertEquals("Due in 3 days", RelativeDates.describeDue(now + 3 * day, now))
        assertEquals("Overdue by 1 day", RelativeDates.describeDue(now - day, now))
        assertEquals("Overdue by 2 days", RelativeDates.describeDue(now - 2 * day, now))
    }
}
