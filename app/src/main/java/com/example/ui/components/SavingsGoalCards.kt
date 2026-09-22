package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GoalFunding
import com.example.data.model.GoalProgress
import com.example.data.model.GoalsSummary
import com.example.data.model.RelativeDates
import com.example.ui.theme.MintGreen
import com.example.ui.theme.PunchyCoral
import com.example.ui.theme.SunnyYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Sep 2026" — a month is as precise as a projection deserves to be. */
private fun monthYear(millis: Long): String =
    SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(millis))

/**
 * The one line that explains when a goal lands. Null when the ledger is too thin to say,
 * which is the honest answer rather than a made-up date.
 */
fun goalEtaText(progress: GoalProgress, currency: String): String? {
    if (progress.isComplete) return null
    val eta = progress.estimatedCompletion ?: return null
    val rate = progress.monthlyRate ?: return null
    return "${monthYear(eta)} at ${formatMoney(currency, rate)}/mo"
}

/** Horizontal progress track shared by every goal card. */
@Composable
fun GoalBar(
    percent: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 10.dp
) {
    val animated by animateFloatAsState(targetValue = percent.coerceIn(0f, 1f), label = "goal_bar")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(accent)
        )
    }
}

/**
 * Where goal money comes from, stated plainly. The Emergency Fund is named here precisely
 * so it is clear it is *not* part of the pool a goal can draw on.
 */
@Composable
fun GoalFundingCard(
    funding: GoalFunding,
    emergencyFundBalance: Double,
    currency: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("goal_funding_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "GOAL FUNDING",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                FundingFigure(
                    label = "Unallocated",
                    amount = funding.unallocated.coerceAtLeast(0.0),
                    currency = currency,
                    accent = MintGreen,
                    modifier = Modifier.weight(1f),
                    testTag = "goal_funding_unallocated"
                )
                FundingFigure(
                    label = "Earmarked",
                    amount = funding.allocated,
                    currency = currency,
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "goal_funding_allocated"
                )
                FundingFigure(
                    label = "Adult Money",
                    amount = funding.pool,
                    currency = currency,
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    testTag = "goal_funding_pool"
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Goals draw on Adult Money only. Your ${formatMoney(currency, emergencyFundBalance)} Emergency Fund is never counted toward them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("goal_funding_emergency_note")
                )
            }

            if (funding.isOverAllocated) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = PunchyCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Goals hold ${formatMoney(currency, -funding.unallocated)} more than Adult Money covers.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = PunchyCoral
                    )
                }
            }
        }
    }
}

@Composable
private fun FundingFigure(
    label: String,
    amount: Double,
    currency: String,
    accent: Color,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatMoney(currency, amount),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            color = accent,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}

/** One goal: what it holds, what is left, how far along, and when it lands. */
@Composable
fun SavingsGoalCard(
    progress: GoalProgress,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val accent = when {
        progress.isComplete -> MintGreen
        progress.isBehindTargetDate -> SunnyYellow
        else -> MaterialTheme.colorScheme.onSurface
    }
    val eta = remember(progress, currency) { goalEtaText(progress, currency) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .testTag("goal_card_${progress.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = progress.goal.emoji, fontSize = 20.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${formatMoney(currency, progress.currentAmount)} of ${formatMoney(currency, progress.target)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (progress.isComplete) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MintGreen.copy(alpha = 0.16f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MintGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "FUNDED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = MintGreen
                        )
                    }
                } else {
                    Text(
                        text = "${progress.percentLabel}%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("goal_percent_${progress.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            GoalBar(percent = progress.percentComplete, accent = MintGreen)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (progress.isComplete) "Fully funded" else "${formatMoney(currency, progress.remaining)} to go",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (progress.isComplete) MintGreen else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("goal_remaining_${progress.id}")
                )

                progress.targetDate?.let { due ->
                    Text(
                        text = if (progress.isComplete) monthYear(due) else RelativeDates.describeDue(due),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.isBehindTargetDate) SunnyYellow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (eta != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (progress.isBehindTargetDate) "$eta — later than planned" else eta,
                        style = MaterialTheme.typography.bodySmall,
                        color = accent,
                        modifier = Modifier.testTag("goal_eta_${progress.id}")
                    )
                }
            }

            if (progress.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = progress.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Compact dashboard tile: overall goal progress plus the goal closest to done. */
@Composable
fun GoalsSummaryCard(
    summary: GoalsSummary,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("dashboard_goals_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVINGS GOALS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (summary.hasGoals) "See all →" else "Start one →",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MintGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!summary.hasGoals) {
                Text(
                    text = "No goals yet",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Name what you're saving for and watch Adult Money fill it up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                return@Column
            }

            val next = summary.nextUp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatMoney(currency, summary.totalSaved),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "of ${formatMoney(currency, summary.totalTarget)} across ${summary.goals.size} ${if (summary.goals.size == 1) "goal" else "goals"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${Math.round(summary.overallPercent * 100)}%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = MintGreen
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            GoalBar(percent = summary.overallPercent, accent = MintGreen, height = 8.dp)

            if (next != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${next.goal.emoji} ${next.title} · ${formatMoney(currency, next.remaining)} to go",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (summary.completed.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Every goal is fully funded.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MintGreen
                )
            }
        }
    }
}
