package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.export.ExportMonth
import com.example.ui.screens.ExportExcelScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w420dp-h2400dp")
class ExportExcelScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    private val lastYear = thisYear - 1

    private val march = ExportMonth(thisYear, Calendar.MARCH)
    private val february = ExportMonth(thisYear, Calendar.FEBRUARY)
    private val january = ExportMonth(thisYear, Calendar.JANUARY)
    private val december = ExportMonth(lastYear, Calendar.DECEMBER)
    private val november = ExportMonth(lastYear, Calendar.NOVEMBER)

    /** Newest first, as the view model hands them over. */
    private val months = listOf(march, february, january, december, november)

    /** The months the export was asked for, captured through the file-name callback. */
    private val requested = mutableListOf<Set<ExportMonth>>()
    private var backPresses = 0

    private fun show(available: List<ExportMonth> = months) {
        composeTestRule.setContent {
            MyApplicationTheme {
                ExportExcelScreen(
                    months = available,
                    fileNameFor = { chosen -> requested += chosen; "Kyash.xlsx" },
                    onBuildFile = { null },
                    onBack = { backPresses++ }
                )
            }
        }
    }

    // -------------------------------------------------------------------- year

    @Test
    fun theCurrentYearIsChosenToStartWith() {
        show()

        composeTestRule.onNodeWithTag("export_year_$thisYear").assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_year_$lastYear").assertIsDisplayed()
        // Only this year's months are listed until another year is picked.
        composeTestRule.onNodeWithText("March").assertIsDisplayed()
        composeTestRule.onNodeWithText("January").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("December").assertCountEquals(0)
    }

    @Test
    fun pickingAYearSwapsInThatYearsMonths() {
        show()

        composeTestRule.onNodeWithTag("export_year_$lastYear").performClick()

        composeTestRule.onNodeWithText("December").assertIsDisplayed()
        composeTestRule.onNodeWithText("November").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("March").assertCountEquals(0)
    }

    @Test
    fun onlyYearsThatHoldRecordsAreOffered() {
        show(available = listOf(december, november))

        composeTestRule.onNodeWithTag("export_year_$lastYear").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("export_year_$thisYear").assertCountEquals(0)
        // With nothing in the current year it falls back to the latest year that has records.
        composeTestRule.onNodeWithText("December").assertIsDisplayed()
    }

    @Test
    fun changingYearDropsASelectionThatWouldHaveBecomeInvisible() {
        show()

        composeTestRule.onNodeWithTag("export_month_${march.key}").performClick()
        composeTestRule.onNodeWithText("MONTHS · 1 SELECTED").assertIsDisplayed()

        composeTestRule.onNodeWithTag("export_year_$lastYear").performClick()

        composeTestRule.onNodeWithText("MONTHS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("export_confirm_button").assertIsNotEnabled()
    }

    // ------------------------------------------------------------------ months

    @Test
    fun nothingCanBeExportedUntilAMonthIsChosen() {
        show()

        composeTestRule.onNodeWithTag("export_confirm_button").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("export_month_${march.key}").performClick()
        composeTestRule.onNodeWithTag("export_confirm_button").assertIsEnabled()
    }

    @Test
    fun theChosenMonthsAreWhatGetsExported() {
        show()

        composeTestRule.onNodeWithTag("export_month_${march.key}").performClick()
        composeTestRule.onNodeWithTag("export_month_${january.key}").performClick()
        composeTestRule.onNodeWithTag("export_confirm_button").performClick()

        assertEquals(listOf(setOf(march, january)), requested)
    }

    @Test
    fun aMonthCanBeUnpicked() {
        show()

        composeTestRule.onNodeWithTag("export_month_${march.key}").performClick()
        composeTestRule.onNodeWithTag("export_month_${march.key}").performClick()

        composeTestRule.onNodeWithTag("export_confirm_button").assertIsNotEnabled()
    }

    @Test
    fun selectAllTakesEveryMonthOfTheYearAndThenClearsThem() {
        show()

        composeTestRule.onNodeWithTag("export_toggle_all_months").performClick()
        composeTestRule.onNodeWithText("MONTHS · 3 SELECTED").assertIsDisplayed()

        composeTestRule.onNodeWithTag("export_toggle_all_months").performClick()
        composeTestRule.onNodeWithTag("export_confirm_button").assertIsNotEnabled()
    }

    @Test
    fun selectAllStopsAtTheYearOnScreen() {
        show()

        composeTestRule.onNodeWithTag("export_toggle_all_months").performClick()
        composeTestRule.onNodeWithTag("export_confirm_button").performClick()

        assertEquals("last year's months must not be swept in", listOf(setOf(march, february, january)), requested)
    }

    // ------------------------------------------------------------------- shell

    @Test
    fun thereIsNoWholeHistoryShortcutToOutgrow() {
        show()

        composeTestRule.onAllNodesWithText("All time").assertCountEquals(0)
    }

    @Test
    fun anEmptyAccountSaysThereIsNothingToExport() {
        show(available = emptyList())

        composeTestRule.onNodeWithTag("export_nothing_recorded").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag("export_confirm_button").assertCountEquals(0)
    }

    @Test
    fun theBackArrowLeavesThePage() {
        show()

        composeTestRule.onNodeWithTag("export_back_button").performClick()

        assertEquals(1, backPresses)
    }
}
