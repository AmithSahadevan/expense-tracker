package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import com.example.data.local.entities.BudgetEntity
import com.example.data.local.entities.BudgetScope
import com.example.data.model.BudgetInput
import com.example.data.model.BudgetValidator
import com.example.ui.theme.PunchyCoral
import com.example.ui.theme.SkyAzure

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetFormSheet(
    initialBudget: BudgetEntity?,
    existingBudgets: List<BudgetEntity>,
    suggestedCategories: List<String>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (BudgetInput) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("budget_form_sheet")
    ) {
        BudgetForm(
            initialBudget = initialBudget,
            existingBudgets = existingBudgets,
            suggestedCategories = suggestedCategories,
            currency = currency,
            onDismiss = onDismiss,
            onSave = onSave,
            onDelete = onDelete,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/**
 * Creates or edits one envelope. An overall budget covers every expense in the month; a
 * category budget covers one category's expenses.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BudgetForm(
    initialBudget: BudgetEntity?,
    existingBudgets: List<BudgetEntity>,
    suggestedCategories: List<String>,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (BudgetInput) -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isEdit = initialBudget != null
    var scope by remember { mutableStateOf(initialBudget?.scope ?: BudgetScope.CATEGORY) }
    var category by remember {
        mutableStateOf(if (initialBudget?.scope == BudgetScope.CATEGORY) initialBudget.category else "")
    }
    var amountText by remember { mutableStateOf(initialBudget?.allocatedAmount?.let(::plainBudgetAmount) ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val overallTaken = existingBudgets.any { it.scope == BudgetScope.OVERALL && it.id != initialBudget?.id }
    val input = BudgetInput(
        scope = scope,
        category = category,
        allocatedAmount = amountText.toDoubleOrNull() ?: 0.0
    )
    val errors = BudgetValidator.validate(input, existingBudgets, initialBudget?.id)

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
                    text = if (isEdit) "Edit Budget" else "New Budget",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Resets every calendar month",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Scope picker. Editing keeps the scope fixed: moving an envelope between
        // "everything" and "one category" would silently change what it measures.
        if (!isEdit) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "WHAT IT COVERS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill(
                        label = "A category",
                        selected = scope == BudgetScope.CATEGORY,
                        accent = SkyAzure,
                        onClick = { scope = BudgetScope.CATEGORY },
                        modifier = Modifier.testTag("budget_scope_category")
                    )
                    ChoicePill(
                        label = "Everything",
                        selected = scope == BudgetScope.OVERALL,
                        accent = SkyAzure,
                        onClick = { if (!overallTaken) scope = BudgetScope.OVERALL },
                        modifier = Modifier.testTag("budget_scope_overall")
                    )
                }
                if (overallTaken) {
                    Text(
                        text = "An overall budget already exists. Edit that one to change it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (scope == BudgetScope.CATEGORY) {
            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                placeholder = { Text("Food, Fuel, Subscriptions…") },
                singleLine = true,
                isError = showErrors && errors.category != null,
                supportingText = {
                    if (showErrors && errors.category != null) {
                        Text(errors.category!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("budget_category_field")
            )

            if (suggestedCategories.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    suggestedCategories.take(8).forEach { suggestion ->
                        ChoicePill(
                            label = suggestion,
                            selected = category.equals(suggestion, ignoreCase = true),
                            accent = SkyAzure,
                            onClick = { category = suggestion }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = amountText,
            onValueChange = { entry -> amountText = entry.filter { it.isDigit() || it == '.' } },
            label = { Text("Monthly cap ($currency)") },
            placeholder = { Text("6000") },
            singleLine = true,
            isError = showErrors && errors.amount != null,
            supportingText = {
                if (showErrors && errors.amount != null) {
                    Text(errors.amount!!, color = MaterialTheme.colorScheme.error)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("budget_amount_field")
        )

        if (showErrors && errors.category != null && scope == BudgetScope.OVERALL) {
            Text(
                text = errors.category!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = {
                showErrors = true
                val clean = BudgetValidator.clean(input, existingBudgets, initialBudget?.id)
                if (clean != null) onSave(clean)
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("budget_save_button")
        ) {
            Text(
                text = if (isEdit) "Save Changes" else "Create Budget",
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
                    .testTag("budget_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text("Delete Budget", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** Editable text for an amount: "6000", not "6,000.00". */
internal fun plainBudgetAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else amount.toString()
