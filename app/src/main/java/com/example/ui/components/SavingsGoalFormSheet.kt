package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entities.SavingsActionType
import com.example.data.local.entities.SavingsGoalEntity
import com.example.data.model.GoalFunding
import com.example.data.model.GoalProgress
import com.example.data.model.SavingsGoalInput
import com.example.data.model.SavingsGoalValidator
import com.example.ui.theme.MintGreen
import com.example.ui.theme.PunchyCoral

private val goalEmojis = listOf("🎯", "📱", "💻", "🎮", "✈️", "🚗", "🏠", "🎧", "📷", "🚲", "⌚", "🎓")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavingsGoalFormSheet(
    initialGoal: SavingsGoalEntity?,
    funding: GoalFunding,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (SavingsGoalInput, startingAmount: Double) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("savings_goal_form_sheet")
    ) {
        SavingsGoalForm(
            initialGoal = initialGoal,
            funding = funding,
            currency = currency,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/**
 * Creates or edits a goal. A new goal may start with money already set aside, but only from
 * unallocated Adult Money — never from the Emergency Fund.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavingsGoalForm(
    initialGoal: SavingsGoalEntity?,
    funding: GoalFunding,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (SavingsGoalInput, startingAmount: Double) -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isEdit = initialGoal != null
    var title by remember { mutableStateOf(initialGoal?.title ?: "") }
    var targetText by remember { mutableStateOf(initialGoal?.goalAmount?.let(::plainBudgetAmount) ?: "") }
    var startingText by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf(initialGoal?.targetDate) }
    var description by remember { mutableStateOf(initialGoal?.notes ?: "") }
    var emoji by remember { mutableStateOf(initialGoal?.emoji ?: "🎯") }
    var showErrors by remember { mutableStateOf(false) }

    val input = SavingsGoalInput(
        title = title,
        targetAmount = targetText.toDoubleOrNull() ?: 0.0,
        targetDate = targetDate,
        description = description,
        emoji = emoji
    )
    val errors = SavingsGoalValidator.validate(input)
    val room = funding.unallocated.coerceAtLeast(0.0)
    val starting = startingText.toDoubleOrNull() ?: 0.0
    val startingTooBig = starting > room + 0.005

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEdit) "Edit Goal" else "New Savings Goal",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Funded from Adult Money",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What are you saving for?") },
            placeholder = { Text("New phone, GPU, Trip…") },
            singleLine = true,
            isError = showErrors && errors.title != null,
            supportingText = {
                if (showErrors && errors.title != null) {
                    Text(errors.title!!, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goal_title_field")
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            goalEmojis.forEach { option ->
                ChoicePill(
                    label = option,
                    selected = emoji == option,
                    accent = MintGreen,
                    onClick = { emoji = option }
                )
            }
        }

        OutlinedTextField(
            value = targetText,
            onValueChange = { entry -> targetText = entry.filter { it.isDigit() || it == '.' } },
            label = { Text("Target amount ($currency)") },
            placeholder = { Text("45000") },
            singleLine = true,
            isError = showErrors && errors.targetAmount != null,
            supportingText = {
                if (showErrors && errors.targetAmount != null) {
                    Text(errors.targetAmount!!, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goal_target_field")
        )

        // Only on create: afterwards money moves through the contribution sheet, which keeps
        // the ledger (and so the contribution rate) honest.
        if (!isEdit) {
            OutlinedTextField(
                value = startingText,
                onValueChange = { entry -> startingText = entry.filter { it.isDigit() || it == '.' } },
                label = { Text("Already saved (optional)") },
                placeholder = { Text("0") },
                singleLine = true,
                isError = startingTooBig,
                supportingText = {
                    Text(
                        text = if (startingTooBig) {
                            "Only ${formatMoney(currency, room)} of Adult Money is unallocated"
                        } else {
                            "${formatMoney(currency, room)} of Adult Money is free to earmark"
                        },
                        color = if (startingTooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goal_starting_field")
            )
        }

        DatePickerField(
            label = "Target date (optional)",
            date = targetDate,
            onDateChange = { targetDate = it },
            placeholder = "No deadline",
            clearable = true
        )

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            placeholder = { Text("Which model, why, anything worth remembering") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goal_description_field")
        )

        Button(
            onClick = {
                showErrors = true
                if (startingTooBig) return@Button
                val clean = SavingsGoalValidator.clean(input)
                if (clean != null) onSave(clean, starting)
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("goal_save_button")
        ) {
            Text(
                text = if (isEdit) "Save Changes" else "Create Goal",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
            )
        }

        if (onDelete != null) {
            Button(
                onClick = onDelete,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = PunchyCoral
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("goal_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text("Delete Goal", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * Moves money into or out of a goal's earmark. Every deposit is checked against unallocated
 * Adult Money before it is accepted, so a goal can never be funded by money that isn't there
 * — and never by the Emergency Fund.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalContributionSheet(
    progress: GoalProgress,
    funding: GoalFunding,
    currency: String,
    onDismiss: () -> Unit,
    /** Returns the reason the contribution was refused, or null once it is recorded. */
    onContribute: (transactionType: String, amount: Double, note: String) -> String?,
    onEdit: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var transactionType by remember { mutableStateOf(SavingsActionType.DEPOSIT) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var rejection by remember { mutableStateOf<String?>(null) }

    val isWithdrawal = transactionType == SavingsActionType.WITHDRAWAL
    val ceiling = if (isWithdrawal) progress.currentAmount else funding.unallocated.coerceAtLeast(0.0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("goal_contribution_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${progress.goal.emoji} ${progress.title}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatMoney(currency, progress.currentAmount)} of ${formatMoney(currency, progress.target)} · ${formatMoney(currency, progress.remaining)} to go",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.testTag("goal_edit_button")) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit goal")
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill(
                    label = "Set aside",
                    selected = !isWithdrawal,
                    accent = MintGreen,
                    onClick = {
                        transactionType = SavingsActionType.DEPOSIT
                        rejection = null
                    },
                    modifier = Modifier.testTag("goal_contribute_deposit")
                )
                ChoicePill(
                    label = "Take back",
                    selected = isWithdrawal,
                    accent = PunchyCoral,
                    onClick = {
                        transactionType = SavingsActionType.WITHDRAWAL
                        rejection = null
                    },
                    modifier = Modifier.testTag("goal_contribute_withdraw")
                )
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { entry ->
                    amountText = entry.filter { it.isDigit() || it == '.' }
                    rejection = null
                },
                label = { Text("Amount ($currency)") },
                singleLine = true,
                isError = rejection != null,
                supportingText = {
                    Text(
                        text = rejection ?: if (isWithdrawal) {
                            "This goal holds ${formatMoney(currency, ceiling)}"
                        } else {
                            "${formatMoney(currency, ceiling)} of Adult Money is unallocated"
                        },
                        color = if (rejection != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("goal_contribute_amount")
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    rejection = onContribute(transactionType, amount, note.trim())
                    if (rejection == null) onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("goal_contribute_save")
            ) {
                Text(
                    text = if (isWithdrawal) "Take Back" else "Set Aside",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Black)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
