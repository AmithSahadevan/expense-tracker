package com.example

import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import com.example.data.model.SavingsCalculator
import com.example.ui.components.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class SavingsCalculatorTest {

    private fun tx(
        id: Long,
        type: String,
        action: String,
        amount: Double,
        affectsAvailable: Boolean = true,
        date: Long = System.currentTimeMillis()
    ) = SavingsTransactionEntity(
        id = id,
        userId = 1,
        savingsType = type,
        transactionType = action,
        amount = amount,
        title = "t$id",
        date = date,
        affectsAvailableMoney = affectsAvailable
    )

    private val ledger = listOf(
        tx(1, SavingsType.ADULT_MONEY, SavingsActionType.DEPOSIT, 25_000.0),
        tx(2, SavingsType.ADULT_MONEY, SavingsActionType.WITHDRAWAL, 5_000.0),
        tx(3, SavingsType.EMERGENCY_FUND, SavingsActionType.DEPOSIT, 40_000.0, affectsAvailable = false)
    )

    @Test
    fun balancesAreTrackedSeparatelyPerSavingsType() {
        assertEquals(20_000.0, SavingsCalculator.balance(ledger, SavingsType.ADULT_MONEY), 0.001)
        assertEquals(40_000.0, SavingsCalculator.balance(ledger, SavingsType.EMERGENCY_FUND), 0.001)
    }

    @Test
    fun onlyLinkedRecordsReduceAvailableMoney() {
        // 25,000 in, 5,000 back out; the pre-existing 40,000 emergency fund never touched Available Money.
        assertEquals(20_000.0, SavingsCalculator.movedOutOfAvailableMoney(ledger), 0.001)
    }

    @Test
    fun withdrawalBeyondBalanceIsRejected() {
        val overdraw = tx(0, SavingsType.ADULT_MONEY, SavingsActionType.WITHDRAWAL, 20_000.01)
        assertEquals(SavingsType.ADULT_MONEY, SavingsCalculator.typeThatWouldGoNegative(ledger, null, overdraw))

        val exact = tx(0, SavingsType.ADULT_MONEY, SavingsActionType.WITHDRAWAL, 20_000.0)
        assertNull(SavingsCalculator.typeThatWouldGoNegative(ledger, null, exact))
    }

    @Test
    fun emergencyFundCannotBeUsedToCoverAdultMoneyWithdrawal() {
        // Total savings is 60,000 but Adult Money only has 20,000.
        val withdrawal = tx(0, SavingsType.ADULT_MONEY, SavingsActionType.WITHDRAWAL, 28_000.0)
        assertEquals(SavingsType.ADULT_MONEY, SavingsCalculator.typeThatWouldGoNegative(ledger, null, withdrawal))
    }

    @Test
    fun editsAndDeletesThatWouldOverdrawAreRejected() {
        // Deleting the 25,000 deposit leaves only the 5,000 withdrawal.
        assertEquals(SavingsType.ADULT_MONEY, SavingsCalculator.typeThatWouldGoNegative(ledger, 1, null))

        // Moving the deposit to the Emergency Fund overdraws Adult Money.
        val moved = ledger[0].copy(savingsType = SavingsType.EMERGENCY_FUND)
        assertEquals(SavingsType.ADULT_MONEY, SavingsCalculator.typeThatWouldGoNegative(ledger, 1, moved))

        // Lowering the deposit to 6,000 is still fine.
        assertNull(SavingsCalculator.typeThatWouldGoNegative(ledger, 1, ledger[0].copy(amount = 6_000.0)))
    }

    @Test
    fun monthlyContributionsBucketNetAmountsByMonth() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 13, 12, 0) }.timeInMillis
        val august = Calendar.getInstance().apply { set(2026, Calendar.AUGUST, 20, 12, 0) }.timeInMillis
        val march = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 1, 12, 0) }.timeInMillis
        val list = listOf(
            tx(1, SavingsType.ADULT_MONEY, SavingsActionType.DEPOSIT, 3_000.0, date = now),
            tx(2, SavingsType.ADULT_MONEY, SavingsActionType.WITHDRAWAL, 1_000.0, date = now),
            tx(3, SavingsType.EMERGENCY_FUND, SavingsActionType.DEPOSIT, 5_000.0, date = august),
            tx(4, SavingsType.EMERGENCY_FUND, SavingsActionType.DEPOSIT, 9_999.0, date = march) // outside window
        )

        val months = SavingsCalculator.monthlyContributions(list, monthCount = 6, now = now)

        assertEquals(6, months.size)
        assertEquals(2_000.0, months.last().adultMoney, 0.001)
        assertEquals(0.0, months.last().emergencyFund, 0.001)
        assertEquals(5_000.0, months[4].emergencyFund, 0.001)
        assertEquals(0.0, months.sumOf { it.emergencyFund } - 5_000.0, 0.001)
    }

    @Test
    fun moneyFormattingDropsZeroDecimals() {
        assertEquals("₹8,000", formatMoney("₹", 8_000.0))
        assertEquals("₹8,000.50", formatMoney("₹", 8_000.5))
    }
}
