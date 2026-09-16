package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Park
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
import com.example.data.model.TransactionItem
import com.example.ui.components.BalanceHeroCard
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.PlayfulTopBar
import com.example.ui.components.TransactionRowItem
import com.example.ui.theme.MintGreen
import com.example.ui.viewmodel.DashboardSummaryUiState

@Composable
fun HomeScreen(
    currentUser: UserEntity?,
    summary: DashboardSummaryUiState,
    onNavigateTo: (String) -> Unit,
    onOpenAddTransaction: () -> Unit = {},
    onEditTransaction: (TransactionItem) -> Unit = {},
    onOpenAuthModal: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    val hasTransactions = summary.recentTransactions.isNotEmpty() || summary.totalIncome > 0 || summary.totalExpense > 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("home_screen_content")
    ) {
        PlayfulTopBar(
            currentUser = currentUser,
            onUserClick = onOpenAuthModal
        )

        Column(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp)
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
                            color = Color.White
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
                    accentColor = MintGreen
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    summary.recentTransactions.forEach { item ->
                        TransactionRowItem(
                            item = item,
                            onEdit = { onEditTransaction(item) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
