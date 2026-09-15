package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
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
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.model.MoneyFlowInput
import com.example.data.model.MoneyFlowValidator
import java.math.BigDecimal
import java.math.RoundingMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyFlowFormSheet(
    initialItem: MoneyFlowEntity?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (MoneyFlowInput) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("money_flow_form_sheet")
    ) {
        MoneyFlowForm(
            initialItem = initialItem,
            currency = currency,
            onDismiss = onDismiss,
            onSave = onSave,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}

/**
 * Records money between the user and someone else. Recording it never touches income, expenses
 * or Available Money; it is tracked on its own until the user marks it settled.
 */
@Composable
fun MoneyFlowForm(
    initialItem: MoneyFlowEntity?,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (MoneyFlowInput) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEdit = initialItem != null
    var direction by remember { mutableStateOf(initialItem?.direction ?: MoneyFlowDirection.OWED_TO_ME) }
    var personName by remember { mutableStateOf(initialItem?.personName ?: "") }
    var amountText by remember { mutableStateOf(initialItem?.amount?.let(::plainAmount) ?: "") }
    var date by remember { mutableStateOf(initialItem?.date ?: System.currentTimeMillis()) }
    var dueDate by remember { mutableStateOf(initialItem?.dueDate) }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }
    var showErrors by remember { mutableStateOf(false) }

    val isOwedToMe = direction == MoneyFlowDirection.OWED_TO_ME
    val input = MoneyFlowInput(
        personName = personName,
        direction = direction,
        amount = amountText.toDoubleOrNull() ?: 0.0,
        date = date,
        dueDate = dueDate,
        notes = notes
    )
    val errors = MoneyFlowValidator.validate(input)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEdit) "Edit Record" else "Record Money Flow",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Kept separate from your income and expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ChoicePill(
                label = "They owe me",
                selected = isOwedToMe,
                accent = IncomingAccent,
                onClick = { direction = MoneyFlowDirection.OWED_TO_ME },
                modifier = Modifier.testTag("money_flow_direction_owed_to_me")
            )
            ChoicePill(
                label = "I owe them",
                selected = !isOwedToMe,
                accent = OutgoingAccent,
                onClick = { direction = MoneyFlowDirection.I_OWE },
                modifier = Modifier.testTag("money_flow_direction_i_owe")
            )
        }

        OutlinedTextField(
            value = personName,
            onValueChange = { personName = it },
            label = { Text("Person's name *") },
            placeholder = { Text(if (isOwedToMe) "Who owes you?" else "Who do you owe?") },
            isError = showErrors && errors.personName != null,
            supportingText = if (showErrors && errors.personName != null) ({ Text(errors.personName) }) else null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("money_flow_form_person")
        )

        OutlinedTextField(
            value = amountText,
            onValueChange = { raw ->
                val cleaned = raw.filter { it.isDigit() || it == '.' }
                if (cleaned.count { it == '.' } <= 1) amountText = cleaned
            },
            label = { Text("Amount *") },
            prefix = { Text("$currency ") },
            isError = showErrors && errors.amount != null,
            supportingText = if (showErrors && errors.amount != null) ({ Text(errors.amount) }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("money_flow_form_amount")
        )

        DatePickerField(
            label = if (isOwedToMe) "Date lent" else "Date borrowed",
            date = date,
            onDateChange = { picked -> picked?.let { date = it } }
        )

        DatePickerField(
            label = "Due date (optional)",
            date = dueDate,
            onDateChange = { dueDate = it },
            placeholder = "No due date",
            clearable = true
        )

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Reason / note") },
            placeholder = { Text("e.g. concert tickets") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("money_flow_form_notes")
        )

        Button(
            onClick = {
                val clean = MoneyFlowValidator.clean(input)
                if (clean == null) showErrors = true else onSave(clean)
            },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("money_flow_form_save")
        ) {
            Text(
                text = if (isEdit) "Save Changes" else "Save Record",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** "1500" for whole amounts, "1500.5" otherwise; never scientific notation. */
private fun plainAmount(value: Double): String =
    BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
