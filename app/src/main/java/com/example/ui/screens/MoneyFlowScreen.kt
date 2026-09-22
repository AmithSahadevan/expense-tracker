package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.UserEntity
import com.example.data.model.MoneyFlowCalculator
import com.example.data.model.MoneyFlowFilter
import com.example.data.model.MoneyFlowInput
import com.example.data.model.RelativeDates
import com.example.ui.components.ChoicePill
import com.example.ui.components.FunkyEmptyState
import com.example.ui.components.IncomingAccent
import com.example.ui.components.MoneyFlowExclusionNote
import com.example.ui.components.MoneyFlowFormSheet
import com.example.ui.components.MoneyFlowTotals
import com.example.ui.components.OutgoingAccent
import com.example.ui.components.formatMoney
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MoneyFlowScreen(
    currentUser: UserEntity?,
    moneyFlows: List<MoneyFlowEntity>,
    onAddMoneyFlow: (MoneyFlowInput) -> Unit,
    onUpdateMoneyFlow: (id: Long, input: MoneyFlowInput) -> Unit,
    onDeleteMoneyFlow: (MoneyFlowEntity) -> Unit,
    onToggleSettled: (MoneyFlowEntity) -> Unit,
    modifier: Modifier = Modifier,
    // Set by the dock's add button; the screen opens its add form and reports back.
    addRequested: Boolean = false,
    onAddRequestHandled: () -> Unit = {}
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    var filter by rememberSaveable { mutableStateOf(MoneyFlowFilter.PENDING) }
    var showForm by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<MoneyFlowEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<MoneyFlowEntity?>(null) }

    LaunchedEffect(addRequested) {
        if (addRequested) {
            editingItem = null
            showForm = true
            onAddRequestHandled()
        }
    }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val summary = remember(moneyFlows) { MoneyFlowCalculator.summarize(moneyFlows) }
    val owedToMe = remember(moneyFlows, filter) {
        MoneyFlowCalculator.section(moneyFlows, MoneyFlowDirection.OWED_TO_ME, filter)
    }
    val iOwe = remember(moneyFlows, filter) {
        MoneyFlowCalculator.section(moneyFlows, MoneyFlowDirection.I_OWE, filter)
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.testTag("money_flow_screen")
    ) { innerPadding ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = 140.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                Column {
                    Text(
                        text = "Money Flow",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            item(key = "totals") {
                MoneyFlowTotals(summary = summary, currency = currency)
            }

            if (moneyFlows.isEmpty()) {
                item(key = "empty") {
                    Box(modifier = Modifier.padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
                        FunkyEmptyState(
                            icon = Icons.Outlined.Handshake,
                            headline = "No money owed either way",
                            subtext = "Track what you lent and what you borrowed, so nobody has to remember it awkwardly.",
                            actionButtonText = "+ Record money flow",
                            onActionClick = {
                                editingItem = null
                                showForm = true
                            },
                            badgeText = "All Square",
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                item(key = "filters") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MoneyFlowFilter.entries.forEach { option ->
                            ChoicePill(
                                label = option.label,
                                selected = filter == option,
                                accent = MaterialTheme.colorScheme.primary,
                                onClick = { filter = option },
                                modifier = Modifier.testTag("money_flow_filter_${option.name.lowercase()}")
                            )
                        }
                    }
                }

                moneyFlowSection(
                    key = "owed_to_me",
                    title = "OWED TO ME",
                    total = summary.expectedIncoming,
                    items = owedToMe,
                    accent = IncomingAccent,
                    currency = currency,
                    filter = filter,
                    dateFormat = dateFormat,
                    onEdit = {
                        editingItem = it
                        showForm = true
                    },
                    onDelete = { pendingDelete = it },
                    onToggleSettled = onToggleSettled
                )

                moneyFlowSection(
                    key = "i_owe",
                    title = "I OWE",
                    total = summary.pendingObligations,
                    items = iOwe,
                    accent = OutgoingAccent,
                    currency = currency,
                    filter = filter,
                    dateFormat = dateFormat,
                    onEdit = {
                        editingItem = it
                        showForm = true
                    },
                    onDelete = { pendingDelete = it },
                    onToggleSettled = onToggleSettled
                )
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.navigationBarsPadding().height(24.dp))
            }
        }
    }

    if (showForm) {
        MoneyFlowFormSheet(
            initialItem = editingItem,
            currency = currency,
            onDismiss = {
                showForm = false
                editingItem = null
            },
            onSave = { input ->
                val editing = editingItem
                if (editing != null) onUpdateMoneyFlow(editing.id, input) else onAddMoneyFlow(input)
                showForm = false
                editingItem = null
            }
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this record?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "The record with ${item.personName} (${formatMoney(currency, item.amount)}) will be removed. " +
                        "Your income and expenses aren't affected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMoneyFlow(item)
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

private fun LazyListScope.moneyFlowSection(
    key: String,
    title: String,
    total: Double,
    items: List<MoneyFlowEntity>,
    accent: Color,
    currency: String,
    filter: MoneyFlowFilter,
    dateFormat: SimpleDateFormat,
    onEdit: (MoneyFlowEntity) -> Unit,
    onDelete: (MoneyFlowEntity) -> Unit,
    onToggleSettled: (MoneyFlowEntity) -> Unit
) {
    item(key = "${key}_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                // Read as one unit by screen readers: "OWED TO ME, 2,000 pending".
                .semantics(mergeDescendants = true) {}
                .testTag("money_flow_section_$key"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${formatMoney(currency, total)} pending",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = accent
            )
        }
    }

    if (items.isEmpty()) {
        item(key = "${key}_empty") {
            Text(
                text = when (filter) {
                    MoneyFlowFilter.PENDING -> "Nothing pending here."
                    MoneyFlowFilter.SETTLED -> "Nothing settled yet."
                    MoneyFlowFilter.ALL -> "No records yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    } else {
        items(items, key = { "${key}_${it.id}" }) { item ->
            MoneyFlowCard(
                item = item,
                accent = accent,
                currency = currency,
                dateText = dateFormat.format(Date(item.date)),
                onEdit = { onEdit(item) },
                onDelete = { onDelete(item) },
                onToggleSettled = { onToggleSettled(item) }
            )
        }
    }
}

@Composable
private fun MoneyFlowCard(
    item: MoneyFlowEntity,
    accent: Color,
    currency: String,
    dateText: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleSettled: () -> Unit
) {
    val isOwedToMe = item.direction == MoneyFlowDirection.OWED_TO_ME
    val isOverdue = MoneyFlowCalculator.isOverdue(item)
    val amountColor = if (item.isSettled) MaterialTheme.colorScheme.onSurfaceVariant else accent

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSettled) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("money_flow_item_${item.id}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = if (item.isSettled) 0.08f else 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOwedToMe) Icons.Outlined.ArrowDownward else Icons.Outlined.ArrowUpward,
                            contentDescription = null,
                            tint = amountColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.personName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOwedToMe) "Owes you · $dateText" else "You owe · $dateText",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.notes.isNotBlank()) {
                            Text(
                                text = item.notes,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = formatMoney(currency, item.amount),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                    color = amountColor
                )
            }

            if (item.isSettled || item.dueDate != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.isSettled) {
                        StatusChip(
                            text = "Settled",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            background = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                    item.dueDate?.let { due ->
                        StatusChip(
                            text = if (item.isSettled) "Was due ${RelativeDates.describe(due)}" else RelativeDates.describeDue(due),
                            color = if (isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            background = if (isOverdue) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onToggleSettled,
                    modifier = Modifier.testTag("money_flow_settle_${item.id}")
                ) {
                    Icon(
                        imageVector = if (item.isSettled) Icons.AutoMirrored.Outlined.Undo else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (item.isSettled) "Mark as pending" else "Mark as settled",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("money_flow_edit_${item.id}")) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit record",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("money_flow_delete_${item.id}")) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete record",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: Color, background: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
