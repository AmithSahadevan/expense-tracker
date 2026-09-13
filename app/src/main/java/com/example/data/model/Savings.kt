package com.example.data.model

import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToLong

/** A savings record as entered by the user (add or edit). */
data class SavingsTransactionInput(
    val savingsType: String,
    val transactionType: String,
    val amount: Double,
    val title: String,
    val notes: String,
    val date: Long,
    val affectsAvailableMoney: Boolean
)

data class MonthlySavingsContribution(
    val monthStart: Long,
    val label: String,
    val adultMoney: Double,
    val emergencyFund: Double
)

object SavingsCalculator {

    fun signedAmount(transaction: SavingsTransactionEntity): Double =
        if (transaction.transactionType == SavingsActionType.DEPOSIT) transaction.amount else -transaction.amount

    fun balance(transactions: List<SavingsTransactionEntity>, savingsType: String): Double =
        transactions.filter { it.savingsType == savingsType }.sumOf { signedAmount(it) }

    /**
     * Net amount the savings ledger has taken out of Available Money: linked deposits
     * minus linked withdrawals. Money sitting in either savings type is never spendable.
     */
    fun movedOutOfAvailableMoney(transactions: List<SavingsTransactionEntity>): Double =
        transactions.filter { it.affectsAvailableMoney }.sumOf { signedAmount(it) }

    /**
     * Applies a pending change (remove [removedId], then add [added]) and returns the
     * savings type whose balance would drop below zero, or null if the change is valid.
     * Only the types touched by the change are checked.
     */
    fun typeThatWouldGoNegative(
        transactions: List<SavingsTransactionEntity>,
        removedId: Long?,
        added: SavingsTransactionEntity?
    ): String? {
        val removed = transactions.find { it.id == removedId }
        val updated = transactions.filter { it.id != removedId } + listOfNotNull(added)
        val touchedTypes = listOfNotNull(removed?.savingsType, added?.savingsType).distinct()
        return touchedTypes.firstOrNull { toCents(balance(updated, it)) < 0 }
    }

    /** Net contributions per calendar month for the last [monthCount] months, oldest first. */
    fun monthlyContributions(
        transactions: List<SavingsTransactionEntity>,
        monthCount: Int = 6,
        now: Long = System.currentTimeMillis()
    ): List<MonthlySavingsContribution> {
        val labelFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -(monthCount - 1))
        }
        return (0 until monthCount).map {
            val start = cal.timeInMillis
            val label = labelFormat.format(cal.time)
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis
            val inMonth = transactions.filter { tx -> tx.date in start until end }
            MonthlySavingsContribution(
                monthStart = start,
                label = label,
                adultMoney = balance(inMonth, SavingsType.ADULT_MONEY),
                emergencyFund = balance(inMonth, SavingsType.EMERGENCY_FUND)
            )
        }
    }

    internal fun toCents(amount: Double): Long = (amount * 100).roundToLong()
}
