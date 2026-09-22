package com.example.data.model

import com.example.data.local.entities.GoalContributionEntity
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsGoalEntity
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.roundToLong

/** A savings goal as entered by the user (add or edit). */
data class SavingsGoalInput(
    val title: String = "",
    val targetAmount: Double = 0.0,
    val targetDate: Long? = null,
    val description: String = "",
    val emoji: String = "🎯"
)

data class SavingsGoalErrors(
    val title: String? = null,
    val targetAmount: String? = null
) {
    val isValid: Boolean get() = title == null && targetAmount == null
}

object SavingsGoalValidator {
    fun validate(input: SavingsGoalInput) = SavingsGoalErrors(
        title = if (input.title.isBlank()) "Give the goal a name" else null,
        targetAmount = if (input.targetAmount <= 0) "Enter an amount greater than 0" else null
    )

    /** Trimmed input, or null when it isn't valid yet. */
    fun clean(input: SavingsGoalInput): SavingsGoalInput? {
        if (!validate(input).isValid) return null
        return input.copy(title = input.title.trim(), description = input.description.trim())
    }
}

/**
 * The money a goal is allowed to draw on.
 *
 * Goals are discretionary, so the pool is the Adult Money balance and nothing else. The
 * Emergency Fund is deliberately absent: protected savings must never be counted as progress
 * toward a phone or a trip, which is also why no goal balance is ever derived from it.
 */
data class GoalFunding(
    /** Adult Money balance. Emergency Fund is excluded by design. */
    val pool: Double = 0.0,
    /** Already earmarked across every goal. */
    val allocated: Double = 0.0
) {
    /** Adult Money not yet claimed by a goal. Negative if goals promise more than exists. */
    val unallocated: Double get() = pool - allocated
    val isOverAllocated: Boolean get() = unallocated < -0.005

    /** Largest contribution that stays within Adult Money. */
    fun roomFor(existingContribution: Double = 0.0): Double =
        (unallocated + existingContribution).coerceAtLeast(0.0)
}

/** One goal measured against its contribution ledger. */
data class GoalProgress(
    val goal: SavingsGoalEntity,
    val currentAmount: Double,
    val contributionCount: Int,
    /** Net contributions per month, or null until there is enough history to mean anything. */
    val monthlyRate: Double? = null,
    /** When the goal lands at [monthlyRate], or null when complete or the rate is unknown. */
    val estimatedCompletion: Long? = null
) {
    val id: Long get() = goal.id
    val title: String get() = goal.title
    val target: Double get() = goal.goalAmount
    val targetDate: Long? get() = goal.targetDate
    val description: String get() = goal.notes

    val remaining: Double get() = (target - currentAmount).coerceAtLeast(0.0)
    val isComplete: Boolean get() = target > 0 && currentAmount >= target
    val percentComplete: Float get() = if (target <= 0) 0f else (currentAmount / target).toFloat().coerceIn(0f, 1f)
    val percentLabel: Int get() = Math.round(percentComplete * 100)

    /** Months of saving left at the current rate, null when the rate is unknown. */
    val monthsRemaining: Double?
        get() {
            val rate = monthlyRate ?: return null
            if (isComplete) return 0.0
            if (rate <= 0.0) return null
            return remaining / rate
        }

    /** True when the estimate lands after the date the user is aiming for. */
    val isBehindTargetDate: Boolean
        get() {
            val due = targetDate ?: return false
            val eta = estimatedCompletion ?: return false
            return !isComplete && eta > due
        }
}

/** Every goal, plus what the discretionary pool can still cover. */
data class GoalsSummary(
    val goals: List<GoalProgress> = emptyList(),
    val funding: GoalFunding = GoalFunding()
) {
    val active: List<GoalProgress> get() = goals.filter { !it.isComplete }
    val completed: List<GoalProgress> get() = goals.filter { it.isComplete }
    val hasGoals: Boolean get() = goals.isNotEmpty()

    val totalTarget: Double get() = goals.sumOf { it.target }
    val totalSaved: Double get() = goals.sumOf { it.currentAmount }
    val totalRemaining: Double get() = goals.sumOf { it.remaining }
    val overallPercent: Float get() = if (totalTarget <= 0) 0f else (totalSaved / totalTarget).toFloat().coerceIn(0f, 1f)

    /** The active goal closest to the finish line: what the dashboard leads with. */
    val nextUp: GoalProgress? get() = active.maxByOrNull { it.percentComplete }
}

object SavingsGoalCalculator {

    /** Below this many contributions a "rate" would just be one deposit wearing a disguise. */
    const val MIN_CONTRIBUTIONS_FOR_RATE = 2

    /** And below this many days of history it would swing wildly week to week. */
    const val MIN_DAYS_FOR_RATE = 7

    private const val DAYS_PER_MONTH = 30.44
    private const val DAY_MILLIS = 86_400_000L

    fun signedAmount(contribution: GoalContributionEntity): Double =
        if (contribution.transactionType == SavingsActionType.DEPOSIT) contribution.amount
        else -contribution.amount

    fun contributionsFor(goalId: Long, contributions: List<GoalContributionEntity>): List<GoalContributionEntity> =
        contributions.filter { it.goalId == goalId }

    /**
     * What a goal holds now: the amount it started with plus everything set aside since.
     * The ledger is the source of truth, exactly as it is for Adult Money.
     */
    fun currentAmount(goal: SavingsGoalEntity, contributions: List<GoalContributionEntity>): Double =
        (goal.savedAmount + contributionsFor(goal.id, contributions).sumOf { signedAmount(it) })
            .coerceAtLeast(0.0)

    /**
     * Net money set aside per month, measured from the first contribution to [now]. Returns
     * null when the history is too thin to extrapolate from, so the UI can stay quiet rather
     * than promise a date it cannot support.
     */
    fun monthlyRate(
        contributions: List<GoalContributionEntity>,
        now: Long = System.currentTimeMillis()
    ): Double? {
        if (contributions.size < MIN_CONTRIBUTIONS_FOR_RATE) return null
        val first = contributions.minOf { it.date }
        val days = (now - first).toDouble() / DAY_MILLIS
        if (days < MIN_DAYS_FOR_RATE) return null
        val net = contributions.sumOf { signedAmount(it) }
        if (net <= 0.0) return null
        return net / days * DAYS_PER_MONTH
    }

    /** Midnight on the day the goal is reached at [monthlyRate]. */
    fun estimatedCompletion(
        remaining: Double,
        monthlyRate: Double?,
        now: Long = System.currentTimeMillis()
    ): Long? {
        if (remaining <= 0.0) return null
        val rate = monthlyRate ?: return null
        if (rate <= 0.0) return null
        val days = ceil(remaining / rate * DAYS_PER_MONTH).roundToLong()
        if (days > 365L * 50) return null // Beyond a lifetime of saving the date stops meaning anything.
        return Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, days.toInt())
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun progress(
        goal: SavingsGoalEntity,
        contributions: List<GoalContributionEntity>,
        now: Long = System.currentTimeMillis()
    ): GoalProgress {
        val own = contributionsFor(goal.id, contributions)
        val current = currentAmount(goal, contributions)
        val rate = monthlyRate(own, now)
        return GoalProgress(
            goal = goal,
            currentAmount = current,
            contributionCount = own.size,
            monthlyRate = rate,
            estimatedCompletion = estimatedCompletion((goal.goalAmount - current).coerceAtLeast(0.0), rate, now)
        )
    }

    /**
     * [adultMoneyBalance] is the whole funding pool on purpose. Callers must not add the
     * Emergency Fund balance to it: goals may only ever draw on discretionary savings.
     */
    fun summarize(
        goals: List<SavingsGoalEntity>,
        contributions: List<GoalContributionEntity>,
        adultMoneyBalance: Double,
        now: Long = System.currentTimeMillis()
    ): GoalsSummary {
        val progress = goals.map { progress(it, contributions, now) }
        return GoalsSummary(
            goals = progress.sortedWith(
                compareBy<GoalProgress> { it.isComplete }
                    .thenBy { it.targetDate ?: Long.MAX_VALUE }
                    .thenByDescending { it.percentComplete }
            ),
            funding = GoalFunding(
                pool = adultMoneyBalance,
                allocated = progress.sumOf { it.currentAmount }
            )
        )
    }

    /**
     * Why a contribution cannot go through, or null when it can. Keeping this in one place
     * is what stops a goal from quietly being funded by money that isn't there.
     */
    fun rejectionFor(
        amount: Double,
        funding: GoalFunding,
        isWithdrawal: Boolean,
        goalBalance: Double,
        currency: String = "₹"
    ): String? = when {
        amount <= 0.0 -> "Enter an amount greater than 0"
        isWithdrawal && amount > goalBalance + 0.005 ->
            "This goal only holds ${money(currency, goalBalance)}"
        !isWithdrawal && amount > funding.unallocated + 0.005 ->
            "Only ${money(currency, funding.unallocated.coerceAtLeast(0.0))} of Adult Money is unallocated"
        else -> null
    }

    private fun money(currency: String, amount: Double): String =
        currency + "%,.0f".format(amount)
}
