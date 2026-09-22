package com.example.data.model

import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.BudgetScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** A budget envelope as entered by the user (add or edit). */
data class BudgetInput(
    val scope: String = BudgetScope.CATEGORY,
    val category: String = "",
    val allocatedAmount: Double = 0.0
) {
    val isOverall: Boolean get() = scope == BudgetScope.OVERALL
}

data class BudgetErrors(val category: String? = null, val amount: String? = null) {
    val isValid: Boolean get() = category == null && amount == null
}

object BudgetValidator {
    /**
     * [existing] is checked so a category never ends up with two envelopes, and so the
     * overall monthly budget stays single. [editingId] is the budget being edited, if any.
     */
    fun validate(
        input: BudgetInput,
        existing: List<BudgetEntity> = emptyList(),
        editingId: Long? = null
    ): BudgetErrors {
        val others = existing.filter { it.id != editingId }
        val category = when {
            input.isOverall ->
                if (others.any { it.scope == BudgetScope.OVERALL }) "An overall budget already exists" else null
            input.category.isBlank() -> "Pick a category"
            others.any { it.scope == BudgetScope.CATEGORY && it.category.equals(input.category.trim(), ignoreCase = true) } ->
                "${input.category.trim()} already has a budget"
            else -> null
        }
        return BudgetErrors(
            category = category,
            amount = if (input.allocatedAmount <= 0) "Enter an amount greater than 0" else null
        )
    }

    /** Trimmed input, or null when it isn't valid yet. */
    fun clean(
        input: BudgetInput,
        existing: List<BudgetEntity> = emptyList(),
        editingId: Long? = null
    ): BudgetInput? {
        if (!validate(input, existing, editingId).isValid) return null
        return input.copy(category = if (input.isOverall) "" else input.category.trim())
    }
}

enum class BudgetStatus {
    /** Comfortably inside the envelope. */
    ON_TRACK,

    /** Past [BudgetCalculator.APPROACHING_AT] of the envelope but not over it yet. */
    APPROACHING,

    /** Spending has passed the envelope. */
    OVER
}

/**
 * One envelope measured against the expenses actually recorded this month. Nothing here is
 * read from [BudgetEntity.spentAmount]; spend is always recomputed from the ledger.
 */
data class BudgetProgress(
    val budget: BudgetEntity,
    val spent: Double,
    val percentUsed: Float,
    val status: BudgetStatus
) {
    val id: Long get() = budget.id
    val allocated: Double get() = budget.allocatedAmount
    val isOverall: Boolean get() = budget.scope == BudgetScope.OVERALL
    val label: String get() = if (isOverall) "Overall budget" else budget.category

    /** Negative once the envelope is overspent. */
    val remaining: Double get() = allocated - spent
    val overspentBy: Double get() = (spent - allocated).coerceAtLeast(0.0)
    val isExhausted: Boolean get() = remaining <= 0.0

    /** Whole percent for display: 82 reads better than 0.8173. */
    val percentLabel: Int get() = Math.round(percentUsed * 100)
}

/** Every envelope for the current month, plus the month's totals. */
data class BudgetsSummary(
    val overall: BudgetProgress? = null,
    val categories: List<BudgetProgress> = emptyList(),
    val monthLabel: String = "",
    val monthStart: Long = 0L,
    val monthEnd: Long = 0L,
    val daysLeftInMonth: Int = 0,
    /** Every expense recorded this month, whether or not a budget covers it. */
    val monthSpent: Double = 0.0
) {
    val all: List<BudgetProgress> get() = listOfNotNull(overall) + categories
    val hasBudgets: Boolean get() = all.isNotEmpty()
    val categoryAllocated: Double get() = categories.sumOf { it.allocated }
    val categorySpent: Double get() = categories.sumOf { it.spent }

    val overspent: List<BudgetProgress> get() = all.filter { it.status == BudgetStatus.OVER }
    val approaching: List<BudgetProgress> get() = all.filter { it.status == BudgetStatus.APPROACHING }
    val alertCount: Int get() = overspent.size + approaching.size

    /**
     * The envelope the dashboard leads with: the overall budget when there is one, otherwise
     * the tightest category. Summing the two kinds would double-count the same expenses.
     */
    val headline: BudgetProgress?
        get() = overall ?: categories.maxByOrNull { it.percentUsed }
}

object BudgetCalculator {

    /**
     * Share of an envelope at which the user gets a heads-up. Kept as a Double: widening a
     * Float here lands on 4800.00007 for an 80% check against 6,000 and the boundary is missed.
     */
    const val APPROACHING_AT = 0.8

    fun statusFor(spent: Double, allocated: Double): BudgetStatus = when {
        allocated <= 0.0 -> BudgetStatus.ON_TRACK
        spent > allocated -> BudgetStatus.OVER
        spent >= allocated * APPROACHING_AT -> BudgetStatus.APPROACHING
        else -> BudgetStatus.ON_TRACK
    }

    /** Share of the envelope used. Not clamped: 1.4f means 40% over. */
    fun percentUsed(spent: Double, allocated: Double): Float =
        if (allocated <= 0.0) 0f else (spent / allocated).toFloat()

    /** `[start, end)` of the calendar month containing [now], in the device time zone. */
    fun monthRange(now: Long = System.currentTimeMillis()): LongRange {
        val cal = startOfMonth(now)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start until cal.timeInMillis
    }

    fun monthLabel(now: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(startOfMonth(now).time)

    /** Days still to come in this month, today included. */
    fun daysLeftInMonth(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1
    }

    /** Expenses recorded in [range]; [category] null totals every category. */
    fun spent(
        transactions: List<TransactionItem>,
        range: LongRange,
        category: String? = null
    ): Double = transactions
        .filter { it.type == TransactionType.EXPENSE && it.date in range }
        .filter { category == null || it.category.equals(category, ignoreCase = true) }
        .sumOf { it.amount }

    fun progress(
        budget: BudgetEntity,
        transactions: List<TransactionItem>,
        now: Long = System.currentTimeMillis()
    ): BudgetProgress = progress(budget, transactions, monthRange(now))

    private fun progress(
        budget: BudgetEntity,
        transactions: List<TransactionItem>,
        range: LongRange
    ): BudgetProgress {
        val category = if (budget.scope == BudgetScope.OVERALL) null else budget.category
        val spent = spent(transactions, range, category)
        return BudgetProgress(
            budget = budget,
            spent = spent,
            percentUsed = percentUsed(spent, budget.allocatedAmount),
            status = statusFor(spent, budget.allocatedAmount)
        )
    }

    /** Envelopes ordered by urgency: the fullest first, so warnings surface without scrolling. */
    fun summarize(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionItem>,
        now: Long = System.currentTimeMillis()
    ): BudgetsSummary {
        val range = monthRange(now)
        val (overall, categories) = budgets.partition { it.scope == BudgetScope.OVERALL }
        return BudgetsSummary(
            overall = overall.minByOrNull { it.id }?.let { progress(it, transactions, range) },
            categories = categories
                .map { progress(it, transactions, range) }
                .sortedWith(compareByDescending<BudgetProgress> { it.percentUsed }.thenBy { it.label.lowercase() }),
            monthLabel = monthLabel(now),
            monthStart = range.first,
            monthEnd = range.last + 1,
            daysLeftInMonth = daysLeftInMonth(now),
            monthSpent = spent(transactions, range)
        )
    }

    /** Categories that do not have an envelope yet, most-spent first, for the picker. */
    fun categoriesWithoutBudget(
        budgets: List<BudgetEntity>,
        transactions: List<TransactionItem>,
        customCategories: List<String> = emptyList(),
        now: Long = System.currentTimeMillis()
    ): List<String> {
        val taken = budgets.filter { it.scope == BudgetScope.CATEGORY }.map { it.category.lowercase() }.toSet()
        val range = monthRange(now)
        val known = CategoryRegistry.defaultExpenseCategories.map { it.name } +
            customCategories +
            transactions.filter { it.type == TransactionType.EXPENSE }.map { it.category }
        return known
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() !in taken }
            .sortedByDescending { spent(transactions, range, it) }
    }

    private fun startOfMonth(now: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
