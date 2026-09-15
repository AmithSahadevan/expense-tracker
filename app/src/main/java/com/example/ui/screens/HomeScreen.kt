package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.data.model.CategoryRegistry
import com.example.data.model.TransactionType
import com.example.ui.components.BalanceHeroCard
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.MoneyFlowDashboardCard
import com.example.ui.viewmodel.DashboardSummaryUiState
import java.util.Locale

@Composable
fun HomeScreen(
    currentUser: UserEntity?,
    summary: DashboardSummaryUiState,
    onNavigateTo: (String) -> Unit,
    onOpenAddTransaction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "$"
    val hasTransactions = summary.recentTransactions.isNotEmpty() || summary.totalIncome > 0 || summary.totalExpense > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("home_screen_content")
    ) {


        // Balance Hero Card (Available Money, Income, Expense, Current Month's Spending)
        BalanceHeroCard(
            balance = summary.remainingMoney,
            income = summary.totalIncome,
            expense = summary.totalExpense,
            currentMonthSpending = summary.currentMonthSpending,
            currentMonthIncome = summary.currentMonthIncome,
            movedToSavings = summary.movedToSavings,
            currency = currency
        )

        // Pending money with other people, kept separate from Available Money above.
        if (summary.moneyFlow.hasPending) {
            Spacer(modifier = Modifier.height(16.dp))
            MoneyFlowDashboardCard(
                summary = summary.moneyFlow,
                currency = currency,
                onClick = { onNavigateTo("money_flow") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Recent Activity Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT TRANSACTIONS",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (hasTransactions) {
                Text(
                    text = "See all →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigateTo("transactions") }
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (!hasTransactions) {
            FunkyEmptyState(
                icon = Icons.Outlined.Park,
                headline = "Nothing recorded yet",
                subtext = "Enjoy the clean slate. Track income, expenses, and build your savings cushion.",
                actionButtonText = "View All Transactions",
                onActionClick = { onNavigateTo("transactions") },
                badgeText = "Fresh Canvas",
                accentColor = Color(0xFF10B981)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.recentTransactions.forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateTo("transactions") },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (item.type == TransactionType.INCOME)
                                                Color(0xFF10B981).copy(alpha = 0.15f)
                                            else
                                                Color(0xFFFF6B6B).copy(alpha = 0.15f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.type == TransactionType.INCOME)
                                            Icons.Outlined.AddCard
                                        else
                                            Icons.Outlined.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = if (item.type == TransactionType.INCOME) Color(0xFF10B981) else Color(0xFFFF6B6B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val catInfo = CategoryRegistry.getCategoryInfo(item.category, item.type)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = catInfo.icon,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = item.category,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (item.recurrence != "NONE") {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(text = "•", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Icon(
                                                    imageVector = Icons.Outlined.Repeat,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = item.recurrence.lowercase().replaceFirstChar { it.uppercase() },
                                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "${if (item.type == TransactionType.INCOME) "+" else "-"}$currency${String.format(Locale.US, "%,.2f", item.amount)}",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                                color = if (item.type == TransactionType.INCOME) Color(0xFF10B981) else Color(0xFFFF6B6B)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(96.dp))
    }
}
