package com.example

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.ExtractedProduct
import com.example.data.model.ProductLookupFailure
import com.example.data.model.ProductLookupResult
import com.example.data.model.WishlistItemInput
import com.example.ui.components.WishlistItemForm
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
class WishlistAddFlowTest {

    @get:Rule val composeTestRule = createComposeRule()

    private val lookedUp = mutableListOf<String>()
    private val saved = mutableListOf<WishlistItemInput>()

    /** Adult Money is ₹20,000 (the Emergency Fund is never given to the form). */
    private fun showAddForm(lookup: (String) -> ProductLookupResult) {
        composeTestRule.setContent {
            MyApplicationTheme {
                WishlistItemForm(
                    initialItem = null,
                    currency = "₹",
                    adultMoneyBalance = 20_000.0,
                    onLookupProduct = { link ->
                        lookedUp += link
                        lookup(link)
                    },
                    onDismiss = {},
                    onSave = { saved += it }
                )
            }
        }
    }

    private fun pasteAndAdd(text: String) {
        composeTestRule.onNodeWithTag("wishlist_link_input").performTextInput(text)
        composeTestRule.onNodeWithTag("wishlist_add_from_link").performClick()
    }

    @Test
    fun completeProductIsAddedStraightFromTheLink() {
        showAddForm { link ->
            ProductLookupResult.Found(
                link,
                ExtractedProduct(
                    title = "Sony WH-1000XM5",
                    price = 28_000.0,
                    currencyCode = "INR",
                    imageUrl = "https://m.media-amazon.com/sony.jpg",
                    description = "Noise cancelling",
                    store = "Amazon",
                    looksLikeProductPage = true
                )
            )
        }

        pasteAndAdd("Look at this https://www.amazon.in/dp/B0C8")

        composeTestRule.onNodeWithTag("wishlist_incomplete_dialog").assertDoesNotExist()
        assertEquals(listOf("https://www.amazon.in/dp/B0C8"), lookedUp)
        val input = saved.single()
        assertEquals("Sony WH-1000XM5", input.title)
        assertEquals(28_000.0, input.price, 0.001)
        assertEquals("https://www.amazon.in/dp/B0C8", input.url)
        assertEquals("Amazon", input.store)
        assertEquals("Noise cancelling", input.description)
    }

    @Test
    fun incompleteDetailsAskBeforeAddingAnyway() {
        showAddForm { link ->
            ProductLookupResult.Found(link, ExtractedProduct(title = "Echo Dot", store = "Amazon", looksLikeProductPage = true))
        }

        pasteAndAdd("https://www.amazon.in/dp/B0C8")

        composeTestRule.onNodeWithText("Some details are incomplete").assertExists()
        composeTestRule.onNodeWithText("couldn't retrieve the price and image for this product from amazon.in", substring = true)
            .assertExists()
        assertTrue(saved.isEmpty())

        composeTestRule.onNodeWithTag("wishlist_add_anyway").performClick()

        val input = saved.single()
        assertEquals("Echo Dot", input.title)
        assertEquals(0.0, input.price, 0.001)
        assertEquals("https://www.amazon.in/dp/B0C8", input.url)
    }

    @Test
    fun reviewDetailsOpensTheEditableForm() {
        showAddForm { link ->
            ProductLookupResult.Found(
                link,
                ExtractedProduct(title = "Sony WH-1000XM5", price = 28_000.0, currencyCode = "INR", store = "Amazon", looksLikeProductPage = true)
            )
        }

        pasteAndAdd("https://www.amazon.in/dp/B0C8")
        composeTestRule.onNodeWithTag("wishlist_review_details").performClick()

        composeTestRule.onNodeWithText("Couldn't find the image", substring = true).assertExists()
        composeTestRule.onNodeWithTag("wishlist_form_name").assertTextContains("Sony WH-1000XM5")
        composeTestRule.onNodeWithTag("wishlist_form_price").assertTextContains("28000")
        // Affordability is unchanged: Adult Money only.
        composeTestRule.onNodeWithText("₹8,000 more needed", substring = true).assertExists()

        composeTestRule.onNodeWithTag("wishlist_form_name").performTextReplacement("Sony XM5 (Black)")
        composeTestRule.onNodeWithTag("wishlist_form_save").performScrollTo().performClick()

        val input = saved.single()
        assertEquals("Sony XM5 (Black)", input.title)
        assertEquals(28_000.0, input.price, 0.001)
        assertEquals("Amazon", input.store)
    }

    @Test
    fun blockedPageCanBeAddedAsJustTheLink() {
        showAddForm { link -> ProductLookupResult.Failed(ProductLookupFailure.BLOCKED, link) }

        pasteAndAdd("https://www.amazon.in/dp/B0C8")

        composeTestRule.onNodeWithText("Product details unavailable").assertExists()
        composeTestRule.onNode(
            hasText("doesn't let apps read its product pages", substring = true) and
                hasAnyAncestor(hasTestTag("wishlist_incomplete_dialog"))
        ).assertExists()

        composeTestRule.onNodeWithTag("wishlist_add_anyway").performClick()

        val input = saved.single()
        assertEquals("Product from Amazon", input.title)
        assertEquals(0.0, input.price, 0.001)
        assertEquals("https://www.amazon.in/dp/B0C8", input.url)
        assertEquals("Amazon", input.store)
    }

    @Test
    fun blockedPageCanBeFilledInManuallyKeepingTheLink() {
        showAddForm { link -> ProductLookupResult.Failed(ProductLookupFailure.BLOCKED, link) }

        pasteAndAdd("https://www.amazon.in/dp/B0C8")
        composeTestRule.onNodeWithTag("wishlist_review_details").performClick()

        composeTestRule.onNodeWithTag("wishlist_form_url").assertTextContains("https://www.amazon.in/dp/B0C8")
        composeTestRule.onNodeWithTag("wishlist_form_store").assertTextContains("Amazon")

        // Nothing is saved without a name.
        composeTestRule.onNodeWithTag("wishlist_form_save").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Product name is required").assertExists()
        assertTrue(saved.isEmpty())

        composeTestRule.onNodeWithTag("wishlist_form_name").performScrollTo().performTextInput("Echo Dot")
        composeTestRule.onNodeWithTag("wishlist_form_price").performScrollTo().performTextInput("4499")
        composeTestRule.onNodeWithTag("wishlist_form_save").performScrollTo().performClick()

        val input = saved.single()
        assertEquals("Echo Dot", input.title)
        assertEquals(4_499.0, input.price, 0.001)
        assertEquals("Amazon", input.store)
    }

    @Test
    fun invalidLinkIsRejectedWithoutFetching() {
        showAddForm { error("lookup must not run for an invalid link") }

        pasteAndAdd("not a link")

        composeTestRule.onNodeWithText("doesn't look like a web link", substring = true).assertExists()
        composeTestRule.onNodeWithTag("wishlist_incomplete_dialog").assertDoesNotExist()
        assertTrue(lookedUp.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test
    fun priceInAnotherCurrencyIsLeftOutAndFlagged() {
        showAddForm { link ->
            ProductLookupResult.Found(
                link,
                ExtractedProduct(
                    title = "Keyboard", price = 99.0, currencyCode = "USD",
                    imageUrl = "https://keys.example.com/k.jpg", looksLikeProductPage = true
                )
            )
        }

        pasteAndAdd("https://keys.example.com/k1")

        composeTestRule.onNodeWithText("listed in USD, so it wasn't added", substring = true).assertExists()
        composeTestRule.onNodeWithTag("wishlist_add_anyway").performClick()
        assertEquals(0.0, saved.single().price, 0.001)
    }
}
