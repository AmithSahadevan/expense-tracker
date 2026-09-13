package com.example

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.local.entities.UserEntity
import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.ProductLookupFailure
import com.example.data.model.ProductLookupResult
import com.example.ui.screens.WishlistScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w420dp-h1800dp")
class WishlistScreenTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val user = UserEntity(id = 1, username = "u", email = "u@t.com", displayName = "U", currencySymbol = "₹")
    private val laptop = WishlistItemEntity(id = 1, userId = 1, title = "Laptop", estimatedCost = 28_000.0, store = "Croma", dateAdded = 200)
    private val mouse = WishlistItemEntity(id = 2, userId = 1, title = "Mouse", estimatedCost = 1_500.0, store = "Amazon", dateAdded = 100)

    private fun showWishlist(items: List<WishlistItemEntity>, adultMoney: Double) {
        composeTestRule.setContent {
            MyApplicationTheme {
                WishlistScreen(
                    currentUser = user,
                    wishlistItems = items,
                    adultMoneyBalance = adultMoney,
                    onAddWishlistItem = {},
                    onUpdateWishlistItem = { _, _ -> },
                    onDeleteWishlistItem = {},
                    onTogglePurchased = {},
                    onLookupProduct = { ProductLookupResult.Failed(ProductLookupFailure.NO_CONNECTION) }
                )
            }
        }
    }

    @Test
    fun cardShowsAmountNeededFromAdultMoneyNotCanAfford() {
        // Adult Money = ₹20,000; the ₹40,000 Emergency Fund is never given to the wishlist.
        showWishlist(listOf(laptop), adultMoney = 20_000.0)

        composeTestRule.onNodeWithTag("wishlist_card_1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Croma", substring = true).assertExists()
        composeTestRule.onNodeWithText("₹8,000 more needed", substring = true).assertExists()
        composeTestRule.onNodeWithText("Can afford", substring = true).assertDoesNotExist()
    }

    @Test
    fun cardShowsCanAffordAndWhatRemains() {
        showWishlist(listOf(laptop), adultMoney = 30_000.0)

        composeTestRule.onNodeWithText("Can afford", substring = true).assertExists()
        composeTestRule.onNodeWithText("₹2,000 left after purchase", substring = true).assertExists()
        composeTestRule.onNodeWithText("more needed", substring = true).assertDoesNotExist()
    }

    @Test
    fun filtersSplitItemsByAdultMoney() {
        showWishlist(listOf(laptop, mouse), adultMoney = 20_000.0)

        composeTestRule.onNodeWithTag("wishlist_filter_can_afford").performClick()
        composeTestRule.onNodeWithTag("wishlist_card_2").assertExists()
        composeTestRule.onNodeWithTag("wishlist_card_1").assertDoesNotExist()

        composeTestRule.onNodeWithTag("wishlist_filter_need_more").performClick()
        composeTestRule.onNodeWithTag("wishlist_card_1").assertExists()
        composeTestRule.onNodeWithTag("wishlist_card_2").assertDoesNotExist()

        composeTestRule.onNodeWithTag("wishlist_filter_all").performClick()
        composeTestRule.onNodeWithTag("wishlist_card_1").assertExists()
        composeTestRule.onNodeWithTag("wishlist_card_2").assertExists()
    }

    @Test
    fun detailViewBreaksDownAffordability() {
        showWishlist(listOf(laptop, mouse), adultMoney = 20_000.0)

        // Affordable item: shows what's left after buying it.
        composeTestRule.onNodeWithTag("wishlist_card_2").performClick()
        composeTestRule.onNodeWithTag("wishlist_affordability_breakdown").assertExists()
        composeTestRule.onNodeWithText("Current Adult Money").assertExists()
        composeTestRule.onNodeWithText("Product price").assertExists()
        composeTestRule.onNodeWithText("Remaining after purchase").assertExists()
        composeTestRule.onNodeWithText("₹18,500").assertExists()

        // Back to the grid, then an item that needs more.
        composeTestRule.onNodeWithTag("wishlist_detail_back").performClick()
        composeTestRule.onNodeWithTag("wishlist_card_1").performClick()
        composeTestRule.onNodeWithText("₹8,000 more needed", substring = true).assertExists()
        composeTestRule.onNodeWithText("Remaining after purchase").assertDoesNotExist()
        // Shown as the headline price and again in the "Product price" row.
        composeTestRule.onAllNodesWithText("₹28,000").assertCountEquals(2)
    }
}
