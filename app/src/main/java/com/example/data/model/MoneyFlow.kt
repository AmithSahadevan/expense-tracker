package com.example.data.model

import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity

/** A money flow record as entered by the user (add or edit). */
data class MoneyFlowInput(
    val personName: String,
    val direction: String,
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val notes: String = ""
)

data class MoneyFlowErrors(val personName: String? = null, val amount: String? = null) {
    val isValid: Boolean get() = personName == null && amount == null
}

object MoneyFlowValidator {
    fun validate(input: MoneyFlowInput) = MoneyFlowErrors(
        personName = if (input.personName.isBlank()) "Whose money is this?" else null,
        amount = if (input.amount <= 0) "Enter an amount greater than 0" else null
    )

    /** Trimmed input, or null when it isn't valid yet. */
    fun clean(input: MoneyFlowInput): MoneyFlowInput? {
        if (!validate(input).isValid) return null
        return input.copy(personName = input.personName.trim(), notes = input.notes.trim())
    }
}

/**
 * Pending money on both sides. These totals are deliberately kept out of Available Money:
 * money someone owes the user has not arrived, and money the user owes has not left yet.
 */
data class MoneyFlowSummary(
    val expectedIncoming: Double = 0.0,
    val pendingObligations: Double = 0.0,
    val expectedCount: Int = 0,
    val obligationCount: Int = 0,
    val overdueExpectedCount: Int = 0,
    val overdueObligationCount: Int = 0
) {
    val hasPending: Boolean get() = expectedCount > 0 || obligationCount > 0
    val overdueCount: Int get() = overdueExpectedCount + overdueObligationCount
    /** What the user would be left holding once every pending record is settled. */
    val net: Double get() = expectedIncoming - pendingObligations
}

enum class MoneyFlowFilter(val label: String) {
    PENDING("Pending"),
    SETTLED("Settled"),
    ALL("All")
}

object MoneyFlowCalculator {

    fun summarize(flows: List<MoneyFlowEntity>, now: Long = System.currentTimeMillis()): MoneyFlowSummary {
        val pending = flows.filter { !it.isSettled }
        val (owedToMe, iOwe) = pending.partition { it.direction == MoneyFlowDirection.OWED_TO_ME }
        return MoneyFlowSummary(
            expectedIncoming = owedToMe.sumOf { it.amount },
            pendingObligations = iOwe.sumOf { it.amount },
            expectedCount = owedToMe.size,
            obligationCount = iOwe.size,
            overdueExpectedCount = owedToMe.count { isOverdue(it, now) },
            overdueObligationCount = iOwe.count { isOverdue(it, now) }
        )
    }

    fun isOverdue(flow: MoneyFlowEntity, now: Long = System.currentTimeMillis()): Boolean {
        val due = flow.dueDate ?: return false
        return !flow.isSettled && RelativeDates.daysUntil(due, now) < 0
    }

    /** Total still pending for one side, used for the section headers. */
    fun pendingTotal(flows: List<MoneyFlowEntity>, direction: String): Double =
        flows.filter { !it.isSettled && it.direction == direction }.sumOf { it.amount }

    /**
     * Records for one side of the screen. Pending records come first, the most urgent due date
     * at the top, then undated ones by date, and finally settled records.
     */
    fun section(
        flows: List<MoneyFlowEntity>,
        direction: String,
        filter: MoneyFlowFilter = MoneyFlowFilter.PENDING
    ): List<MoneyFlowEntity> = flows
        .filter { it.direction == direction }
        .filter {
            when (filter) {
                MoneyFlowFilter.PENDING -> !it.isSettled
                MoneyFlowFilter.SETTLED -> it.isSettled
                MoneyFlowFilter.ALL -> true
            }
        }
        .sortedWith(
            compareBy<MoneyFlowEntity> { it.isSettled }
                .thenBy { it.dueDate ?: Long.MAX_VALUE }
                .thenByDescending { it.date }
                .thenByDescending { it.id }
        )
}
