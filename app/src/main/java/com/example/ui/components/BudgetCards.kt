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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetProgress
import com.example.data.model.BudgetStatus
import com.example.data.model.BudgetsSummary
import com.example.ui.theme.MintGreen
import com.example.ui.theme.PunchyCoral
import com.example.ui.theme.SkyAzure
import com.example.ui.theme.SunnyYellow

/** One colour per state, so a glance at the bar says as much as reading the number. */
fun budgetStatusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.ON_TRACK -> MintGreen
    BudgetStatus.APPROACHING -> SunnyYellow
    BudgetStatus.OVER -> PunchyCoral
}

private fun budgetStatusIcon(status: BudgetStatus): ImageVector = when (status) {
    BudgetStatus.ON_TRACK -> Icons.Outlined.CheckCircle
    BudgetStatus.APPROACHING -> Icons.Outlined.WarningAmber
    BudgetStatus.OVER -> Icons.Outlined.ErrorOutline
}

/** Short warning text. Colour alone never carries the message. */
fun budgetStatusMessage(progress: BudgetProgress, currency: String): String = when (progress.status) {
    BudgetStatus.OVER -> "Over by ${formatMoney(currency, progress.overspentBy)}"
    BudgetStatus.APPROACHING ->
        if (progress.isExhausted) "Fully used" else "${formatMoney(currency, progress.remaining)} left"
    BudgetStatus.ON_TRACK -> "${formatMoney(currency, progress.remaining)} left"
}

/** Rounded track with a fill that stops at 100% and re-colours past it. */
@Composable
fun BudgetBar(
    percentUsed: Float,
    status: BudgetStatus,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 10.dp
) {
    val target = percentUsed.coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = target, label = "budget_bar")
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
                .background(budgetStatusColor(status))
        )
    }
}

/**
 * The month-wide envelope. Shown first because every category budget lives inside it.
 */
@Composable
fun OverallBudgetCard(
    progress: BudgetProgress,
    currency: String,
    monthLabel: String,
    daysLeft: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val accent = budgetStatusColor(progress.status)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 12.dp)
            .testTag("overall_budget_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "OVERALL BUDGET",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formatMoney(currency, progress.spent),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("overall_budget_spent")
                )
                Text(
                    text = "of ${formatMoney(currency, progress.allocated)} this month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatusPill(status = progress.status, text = "${progress.percentLabel}%")
        }

        Spacer(modifier = Modifier.height(16.dp))

        BudgetBar(percentUsed = progress.percentUsed, status = progress.status, height = 12.dp)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = budgetStatusMessage(progress, currency),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = accent,
                modifier = Modifier.testTag("overall_budget_remaining")
            )
            Text(
                text = "$monthLabel · $daysLeft ${if (daysLeft == 1) "day" else "days"} left",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One category envelope: allocation, spend, what is left and how much of it is gone. */
@Composable
fun CategoryBudgetCard(
    progress: BudgetProgress,
    currency: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val accent = budgetStatusColor(progress.status)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 10.dp)
            .testTag("budget_card_${progress.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.label,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatMoney(currency, progress.spent)} of ${formatMoney(currency, progress.allocated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            StatusPill(status = progress.status, text = "${progress.percentLabel}%")
        }

        Spacer(modifier = Modifier.height(12.dp))

        BudgetBar(percentUsed = progress.percentUsed, status = progress.status)

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = budgetStatusMessage(progress, currency),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = accent
        )
    }
}

/** Percentage badge that carries its own icon, so the state reads without relying on colour. */
@Composable
fun StatusPill(
    status: BudgetStatus,
    text: String,
    modifier: Modifier = Modifier
) {
    val accent = budgetStatusColor(status)
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = budgetStatusIcon(status),
            contentDescription = when (status) {
                BudgetStatus.ON_TRACK -> "On track"
                BudgetStatus.APPROACHING -> "Approaching the limit"
                BudgetStatus.OVER -> "Over budget"
            },
            tint = accent,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = accent
        )
    }
}

/** Sits above the list when something needs attention, naming the envelopes involved. */
@Composable
fun BudgetAlertBanner(
    summary: BudgetsSummary,
    currency: String,
    modifier: Modifier = Modifier
) {
    val over = summary.overspent
    val approaching = summary.approaching
    if (over.isEmpty() && approaching.isEmpty()) return

    val status = if (over.isNotEmpty()) BudgetStatus.OVER else BudgetStatus.APPROACHING
    val accent = budgetStatusColor(status)
    val flagged = over + approaching
    val headline = when {
        over.size == 1 -> "${over.first().label} is over its cap"
        over.size > 1 -> "${over.size} budgets are over their cap"
        approaching.size == 1 -> "${approaching.first().label} is close to its cap"
        else -> "${approaching.size} budgets are close to their cap"
    }
    // With a single envelope the headline already named it, so the detail drops the label.
    val detail = if (flagged.size == 1) {
        budgetStatusMessage(flagged.first(), currency)
    } else {
        flagged.joinToString(" · ") { "${it.label} ${budgetStatusMessage(it, currency).lowercase()}" }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("budget_alert_banner"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = budgetStatusIcon(status),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Compact dashboard tile: the leading envelope plus a count of anything needing attention. */
@Composable
fun BudgetSummaryCard(
    summary: BudgetsSummary,
    currency: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val headline = summary.headline
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("dashboard_budget_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BUDGETS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (headline == null) "Set one up →" else "See all →",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = SkyAzure
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (headline == null) {
                Text(
                    text = "No budget set for ${summary.monthLabel}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Cap a category and this card starts tracking it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headline.label,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatMoney(currency, headline.spent)} of ${formatMoney(currency, headline.allocated)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusPill(status = headline.status, text = "${headline.percentLabel}%")
                }

                Spacer(modifier = Modifier.height(10.dp))
                BudgetBar(percentUsed = headline.percentUsed, status = headline.status, height = 8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                val alerts = summary.alertCount
                Text(
                    text = when {
                        summary.overspent.isNotEmpty() ->
                            "$alerts ${if (alerts == 1) "budget needs" else "budgets need"} attention"
                        alerts > 0 -> "$alerts nearing the limit"
                        else -> budgetStatusMessage(headline, currency)
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = budgetStatusColor(headline.status)
                )
            }
        }
    }
}
