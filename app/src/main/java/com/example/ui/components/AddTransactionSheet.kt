package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.CategoryEntity
import com.example.data.model.CategoryInfo
import com.example.data.model.CategoryRegistry
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    currency: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    initialItem: TransactionItem? = null,
    customCategories: List<CategoryEntity> = emptyList(),
    onCreateCustomCategory: ((name: String, emoji: String, type: String, colorHex: String) -> Unit)? = null,
    onSaveTransaction: (
        type: TransactionType,
        title: String,
        amount: Double,
        category: String,
        notes: String,
        paymentMethod: String,
        date: Long,
        recurrence: String
    ) -> Unit
) {
    val context = LocalContext.current
    val isEditMode = initialItem != null

    var selectedType by remember(initialItem) {
        mutableStateOf(initialItem?.type ?: TransactionType.EXPENSE)
    }
    var amountText by remember(initialItem) {
        mutableStateOf(if (initialItem != null) String.format(Locale.US, "%.2f", initialItem.amount) else "")
    }
    var titleText by remember(initialItem) {
        mutableStateOf(initialItem?.title ?: "")
    }
    var selectedCategory by remember(initialItem, selectedType) {
        mutableStateOf(
            initialItem?.category
                ?: if (selectedType == TransactionType.EXPENSE)
                    CategoryRegistry.defaultExpenseCategories.first().name
                else
                    CategoryRegistry.defaultIncomeCategories.first().name
        )
    }
    var notesText by remember(initialItem) {
        mutableStateOf(initialItem?.notes ?: "")
    }
    var paymentMethod by remember(initialItem) {
        mutableStateOf(initialItem?.paymentMethod ?: "CARD")
    }
    var selectedDate by remember(initialItem) {
        mutableLongStateOf(initialItem?.date ?: System.currentTimeMillis())
    }
    var recurrence by remember(initialItem) {
        mutableStateOf(initialItem?.recurrence ?: "NONE")
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showCreateCategoryDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }

    val defaultCategories = if (selectedType == TransactionType.EXPENSE) {
        CategoryRegistry.defaultExpenseCategories
    } else {
        CategoryRegistry.defaultIncomeCategories
    }

    val typeString = if (selectedType == TransactionType.EXPENSE) "EXPENSE" else "INCOME"
    val userCustomForType = customCategories.filter { it.type.equals(typeString, ignoreCase = true) }
        .map { CategoryInfo(it.name, IconMapper.mapEmojiToIcon(it.emoji), selectedType, it.colorHex) }

    val allCategories = defaultCategories + userCustomForType

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("add_transaction_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isEditMode) "Edit Transaction" else "Record Transaction",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isEditMode) "Update ledger entry details" else "Track income or expense in your ledger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Type Toggle (Expense vs Income)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp)
            ) {
                val isExpense = selectedType == TransactionType.EXPENSE
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isExpense) Color(0xFFFF6B6B) else Color.Transparent)
                        .clickable {
                            selectedType = TransactionType.EXPENSE
                            selectedCategory = CategoryRegistry.defaultExpenseCategories.first().name
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccountBalanceWallet,
                            contentDescription = null,
                            tint = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Expense",
                            fontWeight = FontWeight.Bold,
                            color = if (isExpense) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isIncome = selectedType == TransactionType.INCOME
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isIncome) Color(0xFF10B981) else Color.Transparent)
                        .clickable {
                            selectedType = TransactionType.INCOME
                            selectedCategory = CategoryRegistry.defaultIncomeCategories.first().name
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Payments,
                            contentDescription = null,
                            tint = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Income",
                            fontWeight = FontWeight.Bold,
                            color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Amount Input Field
            Text(
                text = "AMOUNT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    if (it.count { c -> c == '.' } <= 1 && it.all { c -> c.isDigit() || c == '.' }) {
                        amountText = it
                        errorMessage = null
                    }
                },
                placeholder = { Text("0.00", fontSize = 28.sp, fontWeight = FontWeight.Bold) },
                prefix = {
                    Text(
                        text = "$currency ",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 28.sp
                ),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_input")
            )

            // Quick add amount pills
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 25, 50, 100, 500).forEach { bump ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                amountText = String.format(Locale.US, "%.2f", current + bump)
                            }
                    ) {
                        Text(
                            text = "+$bump",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Description / Merchant
            Text(
                text = "DESCRIPTION / MERCHANT",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    errorMessage = null
                },
                placeholder = {
                    Text(
                        if (selectedType == TransactionType.EXPENSE)
                            "e.g. Grocery store, Gas station, Netflix"
                        else
                            "e.g. Monthly Salary, Freelance project, Bonus"
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("title_input")
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Category Chips + Add Custom Category Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "+ Custom Category",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clickable { showCreateCategoryDialog = true }
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                allCategories.forEach { category ->
                    val isSelected = selectedCategory.equals(category.name, ignoreCase = true)
                    val catColor = try {
                        Color(android.graphics.Color.parseColor(category.colorHex))
                    } catch (_: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) catColor else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedCategory = category.name }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                tint = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isSelected) catColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Date Selection
            Text(
                text = "DATE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Today chip
                val isToday = remember(selectedDate) {
                    val c1 = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    val c2 = Calendar.getInstance()
                    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (isToday) 2.dp else 1.dp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedDate = System.currentTimeMillis() }
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Yesterday chip
                val isYesterday = remember(selectedDate) {
                    val c1 = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    val c2 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isYesterday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (isYesterday) 2.dp else 1.dp,
                        color = if (isYesterday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                            selectedDate = c.timeInMillis
                        }
                ) {
                    Text(
                        text = "Yesterday",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (isYesterday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                // Date Picker Button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (!isToday && !isYesterday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        width = if (!isToday && !isYesterday) 2.dp else 1.dp,
                        color = if (!isToday && !isYesterday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showDatePickerDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday,
                            contentDescription = "Pick Date",
                            modifier = Modifier.size(16.dp),
                            tint = if (!isToday && !isYesterday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = dateFormatter.format(Date(selectedDate)),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (!isToday && !isYesterday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (showDatePickerDialog) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = selectedDate
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePickerDialog = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    selectedDate = millis
                                }
                                showDatePickerDialog = false
                            }
                        ) {
                            Text("Select", fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePickerDialog = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Payment Method
            Text(
                text = "PAYMENT METHOD",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "CARD" to ("Card" to Icons.Outlined.CreditCard),
                    "CASH" to ("Cash" to Icons.Outlined.Payments),
                    "BANK" to ("Bank" to Icons.Outlined.AccountBalance),
                    "UPI" to ("UPI" to Icons.Outlined.Smartphone)
                ).forEach { (code, pair) ->
                    val isSelected = paymentMethod == code
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { paymentMethod = code }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = pair.second,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = pair.first,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Recurring or One-Time
            Text(
                text = "FREQUENCY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(
                    "NONE" to "One-time",
                    "MONTHLY" to "Recurring (Monthly)",
                    "WEEKLY" to "Recurring (Weekly)"
                ).forEach { (code, label) ->
                    val isSelected = recurrence == code
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { recurrence = code }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSelected && code != "NONE") {
                                Icon(
                                    imageVector = Icons.Outlined.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Optional Notes
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Extra notes (optional)") },
                shape = RoundedCornerShape(14.dp),
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Action Button
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        errorMessage = "Please enter a valid amount greater than 0"
                        return@Button
                    }
                    val finalTitle = titleText.ifBlank { selectedCategory }
                    onSaveTransaction(
                        selectedType,
                        finalTitle,
                        amount,
                        selectedCategory,
                        notesText,
                        paymentMethod,
                        selectedDate,
                        recurrence
                    )
                },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedType == TransactionType.EXPENSE)
                        Color(0xFFFF6B6B)
                    else
                        Color(0xFF10B981)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("submit_transaction_button")
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Outlined.Check else Icons.Outlined.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditMode) {
                        "Update Transaction"
                    } else if (selectedType == TransactionType.EXPENSE) {
                        "Log Expense"
                    } else {
                        "Log Income"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Custom Category Creation Dialog
    if (showCreateCategoryDialog) {
        var newCatName by remember { mutableStateOf("") }
        var newCatEmoji by remember { mutableStateOf("🏷️") }
        var newCatColor by remember { mutableStateOf("#6366F1") }
        val sampleColors = listOf("#FF6B6B", "#10B981", "#3B82F6", "#F59E0B", "#8B5CF6", "#EC4899", "#14B8A6")

        AlertDialog(
            onDismissRequest = { showCreateCategoryDialog = false },
            title = {
                Text(
                    text = "New ${if (selectedType == TransactionType.EXPENSE) "Expense" else "Income"} Category",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { newCatName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Selected Icon:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.LocalOffer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Text("Pick a Color:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        sampleColors.forEach { colHex ->
                            val c = try { Color(android.graphics.Color.parseColor(colHex)) } catch (_: Exception) { Color.Gray }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable { newCatColor = colHex }
                                    .then(
                                        if (newCatColor == colHex)
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        else
                                            Modifier
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.isNotBlank()) {
                            // We still pass an emoji to the repo to maintain DB compatibility, 
                            // but UI will show the icon via IconMapper.
                            onCreateCustomCategory?.invoke(
                                newCatName.trim(),
                                "🏷️", 
                                if (selectedType == TransactionType.EXPENSE) "EXPENSE" else "INCOME",
                                newCatColor
                            )
                            selectedCategory = newCatName.trim()
                            showCreateCategoryDialog = false
                        }
                    },
                    enabled = newCatName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
