package com.example

import com.example.data.local.entities.GoalContributionEntity
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.model.GoalFunding
import com.example.data.model.SavingsGoalCalculator
import com.example.data.model.SavingsGoalInput
import com.example.data.model.SavingsGoalValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SavingsGoalCalculatorTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun goal(
        id: Long,
        title: String,
        target: Double,
        saved: Double = 0.0,
        targetDate: Long? = null
    ) = SavingsGoalEntity(
        id = id,
        userId = 1,
        title = title,
        goalAmount = target,
        savedAmount = saved,
        targetDate = targetDate
    )

    private fun contribution(
        id: Long,
        goalId: Long,
        amount: Double,
        date: Long,
        type: String = SavingsActionType.DEPOSIT
    ) = GoalContributionEntity(
        id = id,
        userId = 1,
        goalId = goalId,
        transactionType = type,
        amount = amount,
        date = date
    )

    private val phone = goal(1, "New phone", 45_000.0)
    private val gpu = goal(2, "GPU", 60_000.0)

    @Test
    fun aGoalHoldsItsSeedPlusTheNetOfItsLedger() {
        val contributions = listOf(
            contribution(1, phone.id, 5_000.0, now - 30 * day),
            contribution(2, phone.id, 3_000.0, now - 10 * day),
            contribution(3, phone.id, 1_000.0, now - 5 * day, SavingsActionType.WITHDRAWAL),
            contribution(4, gpu.id, 20_000.0, now - 10 * day) // another goal's money
        )

        assertEquals(7_000.0, SavingsGoalCalculator.currentAmount(phone, contributions), 0.001)
        assertEquals(20_000.0, SavingsGoalCalculator.currentAmount(gpu, contributions), 0.001)
    }

    @Test
    fun remainingAndPercentageComeFromTheTarget() {
        val contributions = listOf(contribution(1, phone.id, 9_000.0, now - 20 * day))

        val progress = SavingsGoalCalculator.progress(phone, contributions, now)

        assertEquals(9_000.0, progress.currentAmount, 0.001)
        assertEquals(36_000.0, progress.remaining, 0.001)
        assertEquals(20, progress.percentLabel)
        assertFalse(progress.isComplete)
    }

    @Test
    fun aFundedGoalStopsAtOneHundredPercentWithNothingRemaining() {
        val contributions = listOf(contribution(1, phone.id, 50_000.0, now - 20 * day))

        val progress = SavingsGoalCalculator.progress(phone, contributions, now)

        assertTrue(progress.isComplete)
        assertEquals(0.0, progress.remaining, 0.001)
        assertEquals(100, progress.percentLabel)
        assertNull("a funded goal has nothing left to estimate", progress.estimatedCompletion)
    }

    @Test
    fun theRateNeedsRealHistoryBeforeItIsReported() {
        val single = listOf(contribution(1, phone.id, 5_000.0, now - 60 * day))
        assertNull("one contribution is not a rate", SavingsGoalCalculator.monthlyRate(single, now))

        val tooRecent = listOf(
            contribution(1, phone.id, 5_000.0, now - 3 * day),
            contribution(2, phone.id, 5_000.0, now - 1 * day)
        )
        assertNull("three days of history is not a rate", SavingsGoalCalculator.monthlyRate(tooRecent, now))

        val enough = listOf(
            contribution(1, phone.id, 5_000.0, now - 60 * day),
            contribution(2, phone.id, 5_000.0, now - 30 * day)
        )
        assertNotNull(SavingsGoalCalculator.monthlyRate(enough, now))
    }

    @Test
    fun theRateIsNetContributionsOverTheObservedWindow() {
        // 10,000 deposited, 4,000 taken back, over 60.88 days = exactly two months.
        val contributions = listOf(
            contribution(1, phone.id, 10_000.0, now - 6088 * day / 100),
            contribution(2, phone.id, 4_000.0, now - 10 * day, SavingsActionType.WITHDRAWAL)
        )

        val rate = SavingsGoalCalculator.monthlyRate(contributions, now)!!

        assertEquals(3_000.0, rate, 1.0)
    }

    @Test
    fun aGoalGoingBackwardsGetsNoCompletionDate() {
        val contributions = listOf(
            contribution(1, phone.id, 5_000.0, now - 60 * day),
            contribution(2, phone.id, 6_000.0, now - 20 * day, SavingsActionType.WITHDRAWAL)
        )

        assertNull(SavingsGoalCalculator.monthlyRate(contributions, now))
        assertNull(SavingsGoalCalculator.progress(phone, contributions, now).estimatedCompletion)
    }

    @Test
    fun theEstimateLandsWhenTheRemainderIsCovered() {
        // 20,000 to go at 10,000 a month should land about two months out.
        val eta = SavingsGoalCalculator.estimatedCompletion(20_000.0, 10_000.0, now)!!

        val daysOut = (eta - now) / day
        assertTrue("expected about 61 days, got $daysOut", daysOut in 60..62)
    }

    @Test
    fun aGoalIsBehindWhenTheEstimateOvershootsItsTargetDate() {
        val dueSoon = goal(3, "Trip", 50_000.0, targetDate = now + 20 * day)
        val contributions = listOf(
            contribution(1, dueSoon.id, 2_000.0, now - 60 * day),
            contribution(2, dueSoon.id, 2_000.0, now - 30 * day)
        )

        val progress = SavingsGoalCalculator.progress(dueSoon, contributions, now)

        assertTrue("46,000 left at 2,000/mo cannot land in 20 days", progress.isBehindTargetDate)
    }

    @Test
    fun goalsAreFundedByAdultMoneyAndNeverByTheEmergencyFund() {
        val contributions = listOf(
            contribution(1, phone.id, 9_000.0, now - 20 * day),
            contribution(2, gpu.id, 6_000.0, now - 20 * day)
        )

        val summary = SavingsGoalCalculator.summarize(
            goals = listOf(phone, gpu),
            contributions = contributions,
            adultMoneyBalance = 25_000.0,
            now = now
        )

        // The pool is Adult Money alone: an Emergency Fund of any size is simply not in it.
        assertEquals(25_000.0, summary.funding.pool, 0.001)
        assertEquals(15_000.0, summary.funding.allocated, 0.001)
        assertEquals(10_000.0, summary.funding.unallocated, 0.001)
        assertFalse(summary.funding.isOverAllocated)
    }

    @Test
    fun aDepositCannotExceedUnallocatedAdultMoney() {
        val funding = GoalFunding(pool = 25_000.0, allocated = 20_000.0)

        assertNull(
            "5,000 fits in the 5,000 that is free",
            SavingsGoalCalculator.rejectionFor(5_000.0, funding, isWithdrawal = false, goalBalance = 0.0)
        )
        assertNotNull(
            "5,001 does not",
            SavingsGoalCalculator.rejectionFor(5_001.0, funding, isWithdrawal = false, goalBalance = 0.0)
        )
    }

    @Test
    fun aWithdrawalCannotExceedWhatTheGoalHolds() {
        val funding = GoalFunding(pool = 25_000.0, allocated = 9_000.0)

        assertNull(SavingsGoalCalculator.rejectionFor(9_000.0, funding, isWithdrawal = true, goalBalance = 9_000.0))
        assertNotNull(SavingsGoalCalculator.rejectionFor(9_001.0, funding, isWithdrawal = true, goalBalance = 9_000.0))
    }

    @Test
    fun zeroAndNegativeAmountsAreRefused() {
        val funding = GoalFunding(pool = 25_000.0, allocated = 0.0)

        assertNotNull(SavingsGoalCalculator.rejectionFor(0.0, funding, isWithdrawal = false, goalBalance = 0.0))
        assertNotNull(SavingsGoalCalculator.rejectionFor(-100.0, funding, isWithdrawal = false, goalBalance = 0.0))
    }

    @Test
    fun overAllocationIsReportedRatherThanHidden() {
        // Goals kept from a time when Adult Money was higher can outgrow the pool.
        val funding = GoalFunding(pool = 5_000.0, allocated = 12_000.0)

        assertTrue(funding.isOverAllocated)
        assertEquals(-7_000.0, funding.unallocated, 0.001)
        assertEquals(0.0, funding.roomFor(), 0.001)
    }

    @Test
    fun summaryTotalsAndOrderingPutTheSoonestDeadlineFirst() {
        val dated = goal(3, "Trip", 30_000.0, targetDate = now + 10 * day)
        val funded = goal(4, "Laptop", 10_000.0)
        val contributions = listOf(
            contribution(1, phone.id, 9_000.0, now - 20 * day),
            contribution(2, funded.id, 10_000.0, now - 20 * day)
        )

        val summary = SavingsGoalCalculator.summarize(
            goals = listOf(phone, dated, funded),
            contributions = contributions,
            adultMoneyBalance = 40_000.0,
            now = now
        )

        assertEquals("Trip", summary.goals.first().title)
        assertEquals("a funded goal sorts last", "Laptop", summary.goals.last().title)
        assertEquals(1, summary.completed.size)
        assertEquals(2, summary.active.size)
        assertEquals(85_000.0, summary.totalTarget, 0.001)
        assertEquals(19_000.0, summary.totalSaved, 0.001)
        assertEquals("New phone", summary.nextUp?.title)
    }

    @Test
    fun goalsNeedANameAndAPositiveTarget() {
        assertNull(SavingsGoalValidator.clean(SavingsGoalInput(title = "   ", targetAmount = 100.0)))
        assertNull(SavingsGoalValidator.clean(SavingsGoalInput(title = "GPU", targetAmount = 0.0)))

        val clean = SavingsGoalValidator.clean(
            SavingsGoalInput(title = "  GPU  ", targetAmount = 60_000.0, description = "  RTX  ")
        )
        assertEquals("GPU", clean?.title)
        assertEquals("RTX", clean?.description)
    }

    @Test
    fun aTargetDateIsOptional() {
        val undated = SavingsGoalCalculator.progress(phone, emptyList(), now)

        assertNull(undated.targetDate)
        assertFalse("no deadline means never behind one", undated.isBehindTargetDate)
    }

    @Test
    fun estimatesStayWithinAPlausibleHorizon() {
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        calendar.add(Calendar.YEAR, 100)

        assertNull(
            "a century of saving is not a useful date",
            SavingsGoalCalculator.estimatedCompletion(1_000_000.0, 10.0, now)
        )
    }
}
