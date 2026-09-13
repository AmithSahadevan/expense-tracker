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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.data.model.TransactionType
import com.example.ui.components.AdultMoneyCard
import com.example.ui.components.BalanceHeroCard
import com.example.ui.components.EmergencyFundCard
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.formatMoney
import com.example.ui.viewmodel.DashboardSummaryUiState
import java.util.Locale

@Composable
fun HomeScreen(
    currentUser: UserEntity?,
    summary: DashboardSummaryUiState,
    onNavigateTo: (String) -> Unit,
    onOpenAddTransaction: () -> Unit = {},
    onUpdateSalaryAndPayday: (salary: Double, payday: Int, logThisMonth: Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "$"
    val hasTransactions = summary.recentTransactions.isNotEmpty() || summary.totalIncome > 0 || summary.totalExpense > 0
    var showSalaryDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("home_screen_content")
    ) {
        // Welcome Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Hey, ${currentUser?.displayName ?: "there"}! 👋",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (hasTransactions)
                        "Here's your live financial vibe check."
                    else
                        "Your money has entered the chat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = currentUser?.avatarEmoji ?: "⚡", fontSize = 22.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(16.dp))

        // Savings Breakdown: Adult Money vs Emergency Fund
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_savings_section"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SAVINGS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Total savings: ${formatMoney(currency, summary.totalSavings)} →",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onNavigateTo("savings") }
                        .padding(vertical = 4.dp, horizontal = 6.dp)
                )
            }

            AdultMoneyCard(
                balance = summary.adultMoneyBalance,
                currency = currency,
                onClick = { onNavigateTo("savings") }
            )

            // Emergency Fund: visually protected and clearly separate from spendable money
            EmergencyFundCard(
                balance = summary.emergencyFundBalance,
                currency = currency,
                onClick = { onNavigateTo("savings") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monthly Salary & Payday Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("salary_payday_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "MONTHLY SALARY & PAYDAY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showSalaryDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Salary",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val currentSalary = currentUser?.monthlySalary ?: 0.0
                val payday = currentUser?.paydayDayOfMonth ?: 1

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = if (currentSalary > 0)
                                "$currency${String.format(Locale.US, "%,.2f", currentSalary)}"
                            else
                                "Not set yet",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Black
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Payday: Day $payday of each month",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = { showSalaryDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("set_salary_button")
                    ) {
                        Text(
                            text = if (currentSalary > 0) "Edit Salary" else "Set Salary",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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
                emoji = "🌱",
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
                                    Text(
                                        text = if (item.type == TransactionType.INCOME) "💰" else "💸",
                                        fontSize = 16.sp
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (item.recurrence != "NONE") {
                                            Text(
                                                text = "• 🔁 ${item.recurrence.lowercase().replaceFirstChar { it.uppercase() }}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "${if (item.type == TransactionType.INCOME) "+" else "-"}$currency${String.format(Locale.US, "%.2f", item.amount)}",
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

    // Salary & Payday Edit Dialog
    if (showSalaryDialog) {
        var salaryInput by remember {
            mutableStateOf(
                if ((currentUser?.monthlySalary ?: 0.0) > 0.0)
                    String.format(Locale.US, "%.2f", currentUser!!.monthlySalary)
                else
                    ""
            )
        }
        var paydayInput by remember {
            mutableIntStateOf(currentUser?.paydayDayOfMonth ?: 1)
        }
        var logDepositThisMonth by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showSalaryDialog = false },
            title = {
                Text(
                    text = "Monthly Salary & Payday",
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Set your expected monthly salary and the day of the month you get paid.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = salaryInput,
                        onValueChange = {
                            if (it.count { c -> c == '.' } <= 1 && it.all { c -> c.isDigit() || c == '.' }) {
                                salaryInput = it
                                inputError = null
                            }
                        },
                        label = { Text("Monthly Salary Amount") },
                        prefix = { Text("$currency ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column {
                        Text(
                            text = "Payday (Day of Month: $paydayInput)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            listOf(1, 5, 15, 25, 28, 30).forEach { day ->
                                val isSelected = paydayInput == day
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { paydayInput = day }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$day",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { logDepositThisMonth = !logDepositThisMonth }
                    ) {
                        Checkbox(
                            checked = logDepositThisMonth,
                            onCheckedChange = { logDepositThisMonth = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Record salary deposit for this month in ledger now",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (inputError != null) {
                        Text(
                            text = inputError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sal = salaryInput.toDoubleOrNull() ?: 0.0
                        if (sal < 0.0) {
                            inputError = "Salary cannot be negative"
                            return@Button
                        }
                        onUpdateSalaryAndPayday(sal, paydayInput, logDepositThisMonth)
                        showSalaryDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSalaryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
