package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.UserEntity
import com.example.data.model.CategoryRegistry
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.TransactionRowItem
import com.example.ui.components.toColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TreeMap

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    currentUser: UserEntity?,
    transactions: List<TransactionItem>,
    onDeleteTransaction: (TransactionItem) -> Unit,
    onEditTransaction: (TransactionItem) -> Unit,
    onOpenAddTransaction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<TransactionType?>(null) }
    var showFilterMenu by remember { mutableStateOf(false) }

    // Apply Search and Type Filter
    val filteredList = transactions.filter { item ->
        val matchesType = filterType == null || item.type == filterType
        val matchesSearch = searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.category.contains(searchQuery, ignoreCase = true)
        matchesType && matchesSearch
    }

    // Group by date (ignoring time)
    val groupedTransactions = remember(filteredList) {
        val map = TreeMap<Long, MutableList<TransactionItem>>(reverseOrder())
        filteredList.forEach { item ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = item.date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = cal.timeInMillis
            map.getOrPut(dayStart) { mutableListOf() }.add(item)
        }
        map
    }

    val headerDateFormat = remember { SimpleDateFormat("d MMM EEEE", Locale.getDefault()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddTransaction,
                containerColor = Color(0xFFFF6B6B),
                contentColor = Color.White,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 90.dp, end = 4.dp)
                    .testTag("fab_add_transaction")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Transaction")
            }
        },
        modifier = modifier.testTag("transactions_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header
            Text(
                text = "Transactions",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Thin Search Bar with Filter
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Custom Thin Search Field
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .testTag("search_transactions_input"),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = searchQuery,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = true,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = remember { MutableInteractionSource() },
                            placeholder = {
                                Text(
                                    "search",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        )
                    }
                )

                Box {
                    IconButton(
                        onClick = { showFilterMenu = true },
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (filterType != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            modifier = Modifier.size(20.dp),
                            tint = if (filterType != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Transactions") },
                            onClick = {
                                filterType = null
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FilterList,
                                    contentDescription = null,
                                    tint = if (filterType == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Expenses Only") },
                            onClick = {
                                filterType = TransactionType.EXPENSE
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = if (filterType == TransactionType.EXPENSE) Color(0xFFFF6B6B) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Income Only") },
                            onClick = {
                                filterType = TransactionType.INCOME
                                showFilterMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = if (filterType == TransactionType.INCOME) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 20.dp, bottom = 140.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    FunkyEmptyState(
                        icon = Icons.Outlined.MonetizationOn,
                        headline = "No transactions found",
                        subtext = if (transactions.isEmpty())
                            "Your money ledger is empty. Tap '+ Add Transaction' to start recording your income and expenses!"
                        else
                            "No transactions match the selected search or filters.",
                        actionButtonText = "+ Add Transaction",
                        onActionClick = onOpenAddTransaction,
                        badgeText = "Ledger",
                        accentColor = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedTransactions.forEach { (dayStart, items) ->
                        // Date Header with Summary
                        stickyHeader {
                            DateHeader(
                                date = Date(dayStart),
                                dateFormat = headerDateFormat,
                                dailyIncome = items.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
                                dailyExpense = items.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                            )
                        }

                        items(items, key = { "${it.type.name}_${it.id}" }) { item ->
                            TransactionRowItem(
                                item = item,
                                onEdit = { onEditTransaction(item) }
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DateHeader(
    date: Date,
    dateFormat: SimpleDateFormat,
    dailyIncome: Double,
    dailyExpense: Double
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dateFormat.format(date),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (dailyExpense > 0) {
                    Text(
                        text = "Expenses: ${String.format(Locale.US, "%,.0f", dailyExpense)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                if (dailyIncome > 0) {
                    Text(
                        text = "Income: ${String.format(Locale.US, "%,.0f", dailyIncome)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}
