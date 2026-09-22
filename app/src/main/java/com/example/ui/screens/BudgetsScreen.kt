package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.UserEntity
import com.example.data.model.BudgetInput
import com.example.data.model.BudgetsSummary
import com.example.ui.components.BudgetAlertBanner
import com.example.ui.components.BudgetFormSheet
import com.example.ui.components.CategoryBudgetCard
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.OverallBudgetCard
import com.example.ui.components.formatMoney

private data class BudgetFormRequest(val existing: BudgetEntity?)

@Composable
fun BudgetsScreen(
    currentUser: UserEntity?,
    budgets: List<BudgetEntity>,
    summary: BudgetsSummary,
    suggestedCategories: List<String>,
    onAddBudget: (BudgetInput) -> Unit,
    onUpdateBudget: (Long, BudgetInput) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    modifier: Modifier = Modifier,
    // Set by the dock's add button; the screen opens its own form and reports back.
    addRequested: Boolean = false,
    onAddRequestHandled: () -> Unit = {}
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    var formRequest by remember { mutableStateOf<BudgetFormRequest?>(null) }
    var pendingDelete by remember { mutableStateOf<BudgetEntity?>(null) }

    LaunchedEffect(addRequested) {
        if (addRequested) {
            formRequest = BudgetFormRequest(existing = null)
            onAddRequestHandled()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.testTag("budgets_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp)) {
                Text(
                    text = "Budgets",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${summary.monthLabel} · ${formatMoney(currency, summary.monthSpent)} spent so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (budgets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp)
                        .padding(top = 20.dp, bottom = 140.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    FunkyEmptyState(
                        icon = Icons.Outlined.BarChart,
                        headline = "Budgets are untouched",
                        subtext = "Cap a category — Food, Fuel, Subscriptions — or the whole month, and every expense you record counts against it automatically.",
                        actionButtonText = "+ Set a Budget",
                        onActionClick = { formRequest = BudgetFormRequest(existing = null) },
                        badgeText = "Untracked",
                        accentColor = Color(0xFF0984E3)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (summary.alertCount > 0) {
                        item(key = "alerts") {
                            BudgetAlertBanner(summary = summary, currency = currency)
                        }
                    }

                    summary.overall?.let { overall ->
                        item(key = "overall_${overall.id}") {
                            OverallBudgetCard(
                                progress = overall,
                                currency = currency,
                                monthLabel = summary.monthLabel,
                                daysLeft = summary.daysLeftInMonth,
                                onClick = { formRequest = BudgetFormRequest(existing = overall.budget) }
                            )
                        }
                    }

                    if (summary.categories.isNotEmpty()) {
                        item(key = "categories_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CATEGORY BUDGETS",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.2.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatMoney(currency, summary.categorySpent)} of ${formatMoney(currency, summary.categoryAllocated)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    items(summary.categories, key = { it.id }) { progress ->
                        CategoryBudgetCard(
                            progress = progress,
                            currency = currency,
                            onClick = { formRequest = BudgetFormRequest(existing = progress.budget) }
                        )
                    }

                    item(key = "add_budget") {
                        Button(
                            onClick = { formRequest = BudgetFormRequest(existing = null) },
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("budget_add_button")
                        ) {
                            Text("+ Add Budget", fontWeight = FontWeight.Bold)
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.navigationBarsPadding().height(96.dp))
                    }
                }
            }
        }
    }

    formRequest?.let { request ->
        BudgetFormSheet(
            initialBudget = request.existing,
            existingBudgets = budgets,
            suggestedCategories = suggestedCategories,
            currency = currency,
            onDismiss = { formRequest = null },
            onSave = { input ->
                val existing = request.existing
                if (existing != null) onUpdateBudget(existing.id, input) else onAddBudget(input)
                formRequest = null
            },
            onDelete = request.existing?.let { budget ->
                {
                    formRequest = null
                    pendingDelete = budget
                }
            }
        )
    }

    pendingDelete?.let { budget ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this budget?", fontWeight = FontWeight.Bold) },
            text = {
                Text("The envelope is removed. Your recorded expenses are not touched.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBudget(budget.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}
