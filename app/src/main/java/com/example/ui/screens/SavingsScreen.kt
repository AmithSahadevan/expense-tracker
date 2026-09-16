package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsTransactionEntity
import com.example.data.local.entities.SavingsType
import com.example.data.local.entities.UserEntity
import com.example.data.model.MonthlySavingsContribution
import com.example.data.model.SavingsCalculator
import com.example.data.model.SavingsTransactionInput
import com.example.ui.components.AdultMoneyCard
import com.example.ui.components.ChoicePill
import com.example.ui.components.DatePickerField
import com.example.ui.components.EmergencyFundCard
import com.example.ui.components.formatMoney
import com.example.ui.theme.MintGreen
import com.example.ui.theme.EmergencyChartAmber
import com.example.ui.theme.EmergencyGold
import com.example.ui.theme.EmergencyShield
import com.example.ui.theme.EmergencyVault
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class HistoryFilter(val label: String, val savingsType: String?) {
    ALL("All", null),
    ADULT("Adult Money", SavingsType.ADULT_MONEY),
    EMERGENCY("Emergency Fund", SavingsType.EMERGENCY_FUND)
}

private data class DialogRequest(
    val existing: SavingsTransactionEntity?,
    val savingsType: String,
    val transactionType: String
)

@Composable
fun SavingsScreen(
    currentUser: UserEntity?,
    savingsTransactions: List<SavingsTransactionEntity>,
    adultMoneyBalance: Double,
    emergencyFundBalance: Double,
    onAddSavingsTransaction: (SavingsTransactionInput) -> Unit,
    onUpdateSavingsTransaction: (id: Long, input: SavingsTransactionInput) -> Unit,
    onDeleteSavingsTransaction: (id: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val currency = currentUser?.currencySymbol ?: "₹"
    var dialogRequest by remember { mutableStateOf<DialogRequest?>(null) }
    var pendingDelete by remember { mutableStateOf<SavingsTransactionEntity?>(null) }
    var historyFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val monthly = remember(savingsTransactions) { SavingsCalculator.monthlyContributions(savingsTransactions) }
    val filteredHistory = historyFilter.savingsType?.let { type ->
        savingsTransactions.filter { it.savingsType == type }
    } ?: savingsTransactions

    fun openNew(type: String, action: String) {
        dialogRequest = DialogRequest(existing = null, savingsType = type, transactionType = action)
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 120.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag("savings_screen")
    ) {
        item {
            Column {
                Text(
                    text = "Savings",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Two separate pots: one to enjoy, one to protect",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            TotalSavingsCard(
                adultMoney = adultMoneyBalance,
                emergencyFund = emergencyFundBalance,
                currency = currency
            )
        }

        item {
            AdultMoneyCard(
                balance = adultMoneyBalance,
                currency = currency,
                onAdd = { openNew(SavingsType.ADULT_MONEY, SavingsActionType.DEPOSIT) },
                onWithdraw = { openNew(SavingsType.ADULT_MONEY, SavingsActionType.WITHDRAWAL) }
            )
        }

        item {
            EmergencyFundCard(
                balance = emergencyFundBalance,
                currency = currency,
                onAdd = { openNew(SavingsType.EMERGENCY_FUND, SavingsActionType.DEPOSIT) },
                onWithdraw = { openNew(SavingsType.EMERGENCY_FUND, SavingsActionType.WITHDRAWAL) }
            )
        }

        item {
            ContributionsChartCard(monthly = monthly, currency = currency)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SAVINGS HISTORY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HistoryFilter.entries.forEach { filter ->
                        ChoicePill(
                            label = filter.label,
                            selected = historyFilter == filter,
                            accent = MaterialTheme.colorScheme.primary,
                            onClick = { historyFilter = filter },
                            modifier = Modifier.testTag("savings_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredHistory.isEmpty()) {
            item {
                Text(
                    text = if (savingsTransactions.isEmpty())
                        "No savings activity yet. Use + Add on either card to start."
                    else
                        "No ${historyFilter.label} activity yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        } else {
            items(filteredHistory, key = { it.id }) { tx ->
                SavingsHistoryRow(
                    transaction = tx,
                    currency = currency,
                    dateText = dateFormat.format(Date(tx.date)),
                    onEdit = {
                        dialogRequest = DialogRequest(tx, tx.savingsType, tx.transactionType)
                    },
                    onDelete = { pendingDelete = tx }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.navigationBarsPadding().height(48.dp))
        }
    }

    dialogRequest?.let { request ->
        SavingsTransactionDialog(
            request = request,
            currency = currency,
            allTransactions = savingsTransactions,
            onDismiss = { dialogRequest = null },
            onSave = { input ->
                val existing = request.existing
                if (existing != null) onUpdateSavingsTransaction(existing.id, input) else onAddSavingsTransaction(input)
                dialogRequest = null
            }
        )
    }

    pendingDelete?.let { tx ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete savings record?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Remove \"${tx.title}\" (${formatMoney(currency, tx.amount)}) from " +
                        "${savingsTypeName(tx.savingsType)} history? The balance will be recalculated."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSavingsTransaction(tx.id)
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

private fun savingsTypeName(type: String) =
    if (type == SavingsType.EMERGENCY_FUND) "Emergency Fund" else "Adult Money"

@Composable
private fun adultSeriesColor(): Color = MintGreen

@Composable
private fun TotalSavingsCard(adultMoney: Double, emergencyFund: Double, currency: String) {
    val total = adultMoney + emergencyFund
    val adultColor = adultSeriesColor()
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("total_savings_card")
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "TOTAL SAVINGS",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatMoney(currency, total),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black, fontSize = 34.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("total_savings_amount")
            )

            // Split bar: share of each pot, with a 2dp surface gap between segments.
            if (total > 0) {
                val adultShare = (adultMoney.coerceAtLeast(0.0) / total).toFloat().coerceIn(0f, 1f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (adultShare > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(adultShare)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(adultColor)
                        )
                    }
                    if (adultShare < 1f) {
                        Box(
                            modifier = Modifier
                                .weight(1f - adultShare)
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(EmergencyChartAmber)
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendAmount(
                    color = adultColor,
                    label = "Adult Money",
                    sublabel = "Spendable on wishlist",
                    amount = formatMoney(currency, adultMoney),
                    modifier = Modifier.weight(1f)
                )
                LegendAmount(
                    color = EmergencyChartAmber,
                    label = "🔒 Emergency Fund",
                    sublabel = "Protected · not spendable",
                    amount = formatMoney(currency, emergencyFund),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendAmount(color: Color, label: String, sublabel: String, amount: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
            Text(amount, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black), color = MaterialTheme.colorScheme.onSurface)
            Text(sublabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContributionsChartCard(monthly: List<MonthlySavingsContribution>, currency: String) {
    var selectedIndex by remember(monthly.size) { mutableIntStateOf(monthly.lastIndex) }
    val adultColor = adultSeriesColor()
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    val highlightColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val hasData = monthly.any { it.adultMoney != 0.0 || it.emergencyFund != 0.0 }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("savings_contributions_chart")
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column {
                Text(
                    text = "Contributions over time",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Net added per month (deposits minus withdrawals), last 6 months",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendSwatch(adultColor, "Adult Money")
                LegendSwatch(EmergencyChartAmber, "Emergency Fund")
            }

            if (!hasData) {
                Text(
                    text = "Your monthly contributions will appear here once you add savings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val maxPositive = monthly.maxOf { maxOf(it.adultMoney, it.emergencyFund, 0.0) }
                val maxNegative = monthly.maxOf { maxOf(-it.adultMoney, -it.emergencyFund, 0.0) }
                val range = (maxPositive + maxNegative).takeIf { it > 0 } ?: 1.0

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(monthly) {
                            detectTapGestures { offset ->
                                val slot = size.width / monthly.size.toFloat()
                                selectedIndex = (offset.x / slot).toInt().coerceIn(0, monthly.lastIndex)
                            }
                        }
                        .semantics {
                            contentDescription = monthly.joinToString("; ") {
                                "${it.label}: Adult Money ${signedMoney(currency, it.adultMoney)}, " +
                                    "Emergency Fund ${signedMoney(currency, it.emergencyFund)}"
                            }
                        }
                ) {
                    val slotWidth = size.width / monthly.size
                    val baselineY = (size.height * (maxPositive / range)).toFloat()
                    val barWidth = minOf(slotWidth * 0.26f, 14.dp.toPx())
                    val gap = 2.dp.toPx()
                    val radius = 4.dp.toPx()

                    fun drawBar(centerX: Float, value: Double, color: Color) {
                        if (value == 0.0) return
                        val length = (abs(value) / range * size.height).toFloat().coerceAtLeast(2.dp.toPx())
                        val top = if (value > 0) baselineY - length else baselineY
                        val rect = androidx.compose.ui.geometry.Rect(Offset(centerX - barWidth / 2, top), Size(barWidth, length))
                        // Rounded at the data end only; square where the bar meets the baseline.
                        val corner = CornerRadius(radius, radius)
                        val roundRect = if (value > 0) {
                            RoundRect(rect, topLeft = corner, topRight = corner, bottomRight = CornerRadius.Zero, bottomLeft = CornerRadius.Zero)
                        } else {
                            RoundRect(rect, topLeft = CornerRadius.Zero, topRight = CornerRadius.Zero, bottomRight = corner, bottomLeft = corner)
                        }
                        drawPath(Path().apply { addRoundRect(roundRect) }, color)
                    }

                    monthly.forEachIndexed { index, month ->
                        val slotLeft = index * slotWidth
                        if (index == selectedIndex) {
                            drawRoundRect(
                                color = highlightColor,
                                topLeft = Offset(slotLeft + 2.dp.toPx(), 0f),
                                size = Size(slotWidth - 4.dp.toPx(), size.height),
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )
                        }
                        val center = slotLeft + slotWidth / 2
                        drawBar(center - gap / 2 - barWidth / 2, month.adultMoney, adultColor)
                        drawBar(center + gap / 2 + barWidth / 2, month.emergencyFund, EmergencyChartAmber)
                    }

                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, baselineY),
                        end = Offset(size.width, baselineY),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    monthly.forEachIndexed { index, month ->
                        Text(
                            text = month.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (index == selectedIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedIndex = index }
                        )
                    }
                }

                monthly.getOrNull(selectedIndex)?.let { month ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = month.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ReadoutRow(adultColor, "Adult Money", signedMoney(currency, month.adultMoney))
                            ReadoutRow(EmergencyChartAmber, "Emergency Fund", signedMoney(currency, month.emergencyFund))
                        }
                    }
                }
            }
        }
    }
}

private fun signedMoney(currency: String, amount: Double): String = when {
    amount > 0 -> "+${formatMoney(currency, amount)}"
    amount < 0 -> "−${formatMoney(currency, amount)}"
    else -> formatMoney(currency, 0.0)
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReadoutRow(color: Color, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendSwatch(color, label)
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SavingsHistoryRow(
    transaction: SavingsTransactionEntity,
    currency: String,
    dateText: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isEmergency = transaction.savingsType == SavingsType.EMERGENCY_FUND
    val isDeposit = transaction.transactionType == SavingsActionType.DEPOSIT

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("savings_tx_${transaction.id}")
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isEmergency) EmergencyVault else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (isEmergency) "🛡️" else "💳", fontSize = 18.sp)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = transaction.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${savingsTypeName(transaction.savingsType)} • ${if (isDeposit) "Deposit" else "Withdrawal"} • $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!transaction.affectsAvailableMoney) {
                        Text(
                            text = if (isDeposit) "Existing savings · not from Available Money" else "Spent directly · not returned to Available Money",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (transaction.notes.isNotBlank()) {
                        Text(
                            text = "“${transaction.notes}”",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${if (isDeposit) "+" else "−"}${formatMoney(currency, transaction.amount)}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black),
                    color = if (isDeposit) Color(0xFF10B981) else Color(0xFFFF6B6B)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit savings record",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete savings record",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SavingsTransactionDialog(
    request: DialogRequest,
    currency: String,
    allTransactions: List<SavingsTransactionEntity>,
    onDismiss: () -> Unit,
    onSave: (SavingsTransactionInput) -> Unit
) {
    val existing = request.existing
    var savingsType by remember { mutableStateOf(request.savingsType) }
    var transactionType by remember { mutableStateOf(request.transactionType) }
    var amountText by remember {
        mutableStateOf(existing?.amount?.toLong()?.toString() ?: "")
    }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: System.currentTimeMillis()) }
    var affectsAvailable by remember { mutableStateOf(existing?.affectsAvailableMoney ?: true) }
    var confirmEmergencyWithdrawal by remember { mutableStateOf(false) }

    val isEmergency = savingsType == SavingsType.EMERGENCY_FUND
    val isDeposit = transactionType == SavingsActionType.DEPOSIT
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val typeName = savingsTypeName(savingsType)

    // Balance of the selected pot as if this record did not exist yet (so edits can reuse their own amount).
    val balanceWithoutThis = SavingsCalculator.balance(
        allTransactions.filter { it.id != existing?.id },
        savingsType
    )
    val candidate = SavingsTransactionEntity(
        id = existing?.id ?: 0,
        userId = existing?.userId ?: 0,
        savingsType = savingsType,
        transactionType = transactionType,
        amount = amount,
        title = title,
        date = date
    )
    val negativeType = if (amount > 0) {
        SavingsCalculator.typeThatWouldGoNegative(allTransactions, existing?.id, candidate)
    } else null
    val error = when {
        amountText.isNotEmpty() && amount <= 0 -> "Enter an amount greater than zero"
        negativeType != null && negativeType == savingsType && !isDeposit ->
            "Only ${formatMoney(currency, balanceWithoutThis)} available in $typeName"
        negativeType != null -> "This change would make ${savingsTypeName(negativeType)} negative"
        else -> null
    }
    val canSave = amount > 0 && error == null

    fun buildInput() = SavingsTransactionInput(
        savingsType = savingsType,
        transactionType = transactionType,
        amount = amount,
        title = title.trim().ifBlank { if (isDeposit) "Deposit" else "Withdrawal" },
        notes = notes.trim(),
        date = date,
        affectsAvailableMoney = affectsAvailable
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when {
                    existing != null -> "Edit savings record"
                    isDeposit -> "Add to $typeName"
                    else -> "Withdraw from $typeName"
                },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill(
                        label = "💳 Adult Money",
                        selected = !isEmergency,
                        accent = MaterialTheme.colorScheme.primary,
                        onClick = { savingsType = SavingsType.ADULT_MONEY },
                        modifier = Modifier.testTag("dialog_type_adult")
                    )
                    ChoicePill(
                        label = "🛡️ Emergency",
                        selected = isEmergency,
                        accent = EmergencyShield,
                        selectedContentColor = EmergencyVault,
                        onClick = { savingsType = SavingsType.EMERGENCY_FUND },
                        modifier = Modifier.testTag("dialog_type_emergency")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill(
                        label = "+ Add",
                        selected = isDeposit,
                        accent = Color(0xFF10B981),
                        onClick = { transactionType = SavingsActionType.DEPOSIT }
                    )
                    ChoicePill(
                        label = "− Withdraw",
                        selected = !isDeposit,
                        accent = Color(0xFFFF6B6B),
                        onClick = { transactionType = SavingsActionType.WITHDRAWAL }
                    )
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() }) amountText = it
                    },
                    label = { Text("Amount") },
                    prefix = { Text("$currency ") },
                    supportingText = {
                        Text(error ?: "$typeName balance: ${formatMoney(currency, balanceWithoutThis)}")
                    },
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_amount")
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text(if (isDeposit) "e.g. Monthly transfer" else "e.g. New headphones") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                DatePickerField(
                    label = "Date",
                    date = date,
                    onDateChange = { picked -> picked?.let { date = it } }
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isDeposit) "Take from Available Money" else "Return to Available Money",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isDeposit) {
                                if (affectsAvailable) "Your Available Money goes down by this amount"
                                else "Money you already had saved elsewhere"
                            } else {
                                if (affectsAvailable) "Your Available Money goes up by this amount"
                                else "Spent directly from savings"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = affectsAvailable, onCheckedChange = { affectsAvailable = it })
                }

                if (isEmergency && !isDeposit) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = EmergencyVault,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🔒 The Emergency Fund is protected. Only withdraw for a genuine emergency.",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmergencyGold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isEmergency && !isDeposit) confirmEmergencyWithdrawal = true else onSave(buildInput())
                },
                enabled = canSave,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("dialog_save")
            ) { Text(if (existing != null) "Save changes" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (confirmEmergencyWithdrawal) {
        AlertDialog(
            onDismissRequest = { confirmEmergencyWithdrawal = false },
            containerColor = EmergencyVault,
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f),
            title = { Text("🛡️ Use your Emergency Fund?", fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "You are about to withdraw ${formatMoney(currency, amount)} from protected savings. " +
                        "This money is meant for real emergencies, not wishlist purchases."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmEmergencyWithdrawal = false
                        onSave(buildInput())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyShield, contentColor = EmergencyVault),
                    modifier = Modifier.testTag("confirm_emergency_withdrawal")
                ) { Text("Yes, it's an emergency", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmergencyWithdrawal = false }) {
                    Text("Keep it protected", color = EmergencyGold)
                }
            }
        )
    }
}
