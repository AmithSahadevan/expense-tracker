package com.example

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.local.entities.MoneyFlowDirection
import com.example.data.local.entities.MoneyFlowEntity
import com.example.data.local.entities.UserEntity
import com.example.data.model.MoneyFlowInput
import com.example.ui.components.MoneyFlowForm
import com.example.ui.screens.MoneyFlowScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w420dp-h2400dp")
class MoneyFlowScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val user = UserEntity(id = 1, username = "u", email = "u@t.com", displayName = "U", currencySymbol = "₹")
    private val sarah = MoneyFlowEntity(
        id = 1, userId = 1, personName = "Sarah", direction = MoneyFlowDirection.OWED_TO_ME,
        amount = 2_000.0, notes = "Concert tickets"
    )
    private val rahul = MoneyFlowEntity(
        id = 2, userId = 1, personName = "Rahul", direction = MoneyFlowDirection.I_OWE, amount = 1_500.0
    )
    private val settledLoan = MoneyFlowEntity(
        id = 3, userId = 1, personName = "Nina", direction = MoneyFlowDirection.OWED_TO_ME,
        amount = 9_000.0, isSettled = true
    )

    private val settled = mutableListOf<MoneyFlowEntity>()
    private val deleted = mutableListOf<MoneyFlowEntity>()

    private fun showScreen(flows: List<MoneyFlowEntity>) {
        composeTestRule.setContent {
            MyApplicationTheme {
                MoneyFlowScreen(
                    currentUser = user,
                    moneyFlows = flows,
                    onAddMoneyFlow = {},
                    onUpdateMoneyFlow = { _, _ -> },
                    onDeleteMoneyFlow = { deleted += it },
                    onToggleSettled = { settled += it }
                )
            }
        }
    }

    @Test
    fun bothSidesAreShownWithTheirPendingTotals() {
        showScreen(listOf(sarah, rahul, settledLoan))

        // Section headings; the totals tiles above them use the same words, so match by tag.
        composeTestRule.onNodeWithTag("money_flow_section_owed_to_me").assertTextContains("OWED TO ME")
        composeTestRule.onNodeWithTag("money_flow_section_i_owe").assertTextContains("I OWE")
        // Settled records are excluded from the totals.
        composeTestRule.onNodeWithTag("money_flow_expected_total").assertTextContains("₹2,000")
        composeTestRule.onNodeWithTag("money_flow_owe_total").assertTextContains("₹1,500")
        composeTestRule.onNodeWithText("1 person owes you").assertExists()
        composeTestRule.onNodeWithText("1 payment pending").assertExists()
        composeTestRule.onNodeWithText("Expected money isn't part of your Available Money", substring = true).assertExists()

        composeTestRule.onNodeWithTag("money_flow_item_1").assertExists()
        composeTestRule.onNodeWithTag("money_flow_item_2").assertExists()
        composeTestRule.onNodeWithText("Concert tickets").assertExists()
        // Pending filter is the default, so the settled record is hidden.
        composeTestRule.onNodeWithTag("money_flow_item_3").assertDoesNotExist()
    }

    @Test
    fun settledRecordsAppearUnderTheSettledFilter() {
        showScreen(listOf(sarah, rahul, settledLoan))

        composeTestRule.onNodeWithTag("money_flow_filter_settled").performClick()

        composeTestRule.onNodeWithTag("money_flow_item_3").assertExists()
        composeTestRule.onNodeWithTag("money_flow_item_1").assertDoesNotExist()
        composeTestRule.onNodeWithTag("money_flow_item_3").assertTextContains("Settled")
    }

    @Test
    fun recordsCanBeSettledAndDeleted() {
        showScreen(listOf(sarah, rahul))

        composeTestRule.onNodeWithTag("money_flow_settle_1").performScrollTo().performClick()
        assertEquals(listOf(sarah), settled)

        composeTestRule.onNodeWithTag("money_flow_delete_2").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Delete this record?").assertExists()
        assertTrue("nothing is deleted before confirming", deleted.isEmpty())
        composeTestRule.onNodeWithText("Delete").performClick()
        assertEquals(listOf(rahul), deleted)
    }

    @Test
    fun theFormCapturesEverySupportedField() {
        val saved = mutableListOf<MoneyFlowInput>()
        composeTestRule.setContent {
            MyApplicationTheme {
                MoneyFlowForm(initialItem = null, currency = "₹", onDismiss = {}, onSave = { saved += it })
            }
        }

        // Nothing is saved until a person and an amount are given.
        composeTestRule.onNodeWithTag("money_flow_form_save").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Whose money is this?").assertExists()
        assertTrue(saved.isEmpty())

        composeTestRule.onNodeWithTag("money_flow_direction_i_owe").performClick()
        composeTestRule.onNodeWithTag("money_flow_form_person").performScrollTo().performTextInput("Rahul")
        composeTestRule.onNodeWithTag("money_flow_form_amount").performScrollTo().performTextInput("1500")
        composeTestRule.onNodeWithTag("money_flow_form_notes").performScrollTo().performTextInput("Concert tickets")
        composeTestRule.onNodeWithTag("money_flow_form_save").performScrollTo().performClick()

        val input = saved.single()
        assertEquals("Rahul", input.personName)
        assertEquals(MoneyFlowDirection.I_OWE, input.direction)
        assertEquals(1_500.0, input.amount, 0.001)
        assertEquals("Concert tickets", input.notes)
    }
}
