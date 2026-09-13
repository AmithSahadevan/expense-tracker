package com.example

import com.example.data.local.entities.WishlistItemEntity
import com.example.data.model.WebLinks
import com.example.data.model.WishlistAffordability
import com.example.data.model.WishlistAffordabilityCalculator
import com.example.data.model.WishlistBrowser
import com.example.data.model.WishlistDates
import com.example.data.model.WishlistFilter
import com.example.data.model.WishlistInputValidator
import com.example.data.model.WishlistItemInput
import com.example.ui.components.formatMoney
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WishlistLogicTest {

    private fun item(id: Long, price: Double, dateAdded: Long = id, purchased: Boolean = false) = WishlistItemEntity(
        id = id,
        userId = 1,
        title = "Item $id",
        estimatedCost = price,
        isPurchased = purchased,
        dateAdded = dateAdded
    )

    // --- Affordability (Adult Money only) ---

    @Test
    fun userExampleNeedsEightThousandMoreFromAdultMoney() {
        // Adult Money = 20,000; the 40,000 Emergency Fund is never an input.
        val result = WishlistAffordabilityCalculator.evaluate(price = 28_000.0, adultMoneyBalance = 20_000.0)

        result as WishlistAffordability.MoreNeeded
        assertEquals(8_000.0, result.amountNeeded, 0.001)
        assertEquals(20f / 28f, result.progress, 0.001f)
        assertEquals("₹8,000 more needed", "${formatMoney("₹", result.amountNeeded)} more needed")
    }

    @Test
    fun canAffordReportsWhatRemainsAfterPurchase() {
        val result = WishlistAffordabilityCalculator.evaluate(price = 28_000.0, adultMoneyBalance = 30_000.0)
        assertEquals(WishlistAffordability.CanAfford(remainingAfterPurchase = 2_000.0), result)
    }

    @Test
    fun exactBalanceCanAffordWithNothingLeft() {
        assertEquals(WishlistAffordability.CanAfford(0.0), WishlistAffordabilityCalculator.evaluate(28_000.0, 28_000.0))
        // Floating point noise must not flip the result.
        assertEquals(WishlistAffordability.CanAfford(0.0), WishlistAffordabilityCalculator.evaluate(0.1 + 0.2, 0.3))
    }

    @Test
    fun negativeAdultMoneyCountsAsZero() {
        val result = WishlistAffordabilityCalculator.evaluate(1_000.0, -500.0) as WishlistAffordability.MoreNeeded
        assertEquals(1_000.0, result.amountNeeded, 0.001)
        assertEquals(0f, result.progress)
    }

    // --- Filters ---

    private val laptop = item(id = 1, price = 28_000.0, dateAdded = 100)
    private val mouse = item(id = 2, price = 1_500.0, dateAdded = 300)
    private val phone = item(id = 3, price = 60_000.0, dateAdded = 200)
    private val boughtShoes = item(id = 4, price = 5_000.0, dateAdded = 400, purchased = true)
    private val wishlist = listOf(laptop, mouse, phone, boughtShoes)

    private fun ids(filter: WishlistFilter, adultMoney: Double = 30_000.0) =
        WishlistBrowser.apply(wishlist, filter, adultMoney).map { it.id }

    @Test
    fun allShowsNewestFirstWithPurchasedItemsLast() {
        assertEquals(listOf(2L, 3L, 1L, 4L), ids(WishlistFilter.ALL))
    }

    @Test
    fun canAffordAndNeedMoreSplitUnpurchasedItemsByAdultMoney() {
        assertEquals(listOf(2L, 1L), ids(WishlistFilter.CAN_AFFORD))
        assertEquals(listOf(3L), ids(WishlistFilter.NEED_MORE))

        // With only 20,000 of Adult Money the laptop moves to "Need More".
        assertEquals(listOf(2L), ids(WishlistFilter.CAN_AFFORD, adultMoney = 20_000.0))
        assertEquals(listOf(3L, 1L), ids(WishlistFilter.NEED_MORE, adultMoney = 20_000.0))
    }

    @Test
    fun priceSortsKeepPurchasedItemsLast() {
        assertEquals(listOf(3L, 1L, 2L, 4L), ids(WishlistFilter.HIGHEST_PRICE))
        assertEquals(listOf(2L, 1L, 3L, 4L), ids(WishlistFilter.LOWEST_PRICE))
    }

    // --- Links & validation ---

    @Test
    fun linksAreNormalizedOrRejected() {
        assertEquals("", WebLinks.normalize("   "))
        assertEquals("https://amazon.in/dp/B0C8", WebLinks.normalize(" amazon.in/dp/B0C8 "))
        assertEquals("http://shop.example.com/a?b=c", WebLinks.normalize("http://shop.example.com/a?b=c"))
        assertNull(WebLinks.normalize("not a link"))
        assertNull(WebLinks.normalize("headphones"))
        assertNull(WebLinks.normalize("ftp://files.example.com/x"))
        assertEquals("amazon.in", WebLinks.displayHost("https://www.amazon.in/dp/B0C8?ref=x"))
    }

    @Test
    fun validatorRequiresNameAndPriceAndValidLinks() {
        val errors = WishlistInputValidator.validate(
            WishlistItemInput(title = " ", price = 0.0, url = "bad link", imageUrl = "nope")
        )
        assertFalse(errors.isValid)
        assertTrue(errors.title != null && errors.price != null && errors.url != null && errors.imageUrl != null)
        assertNull(WishlistInputValidator.clean(WishlistItemInput(title = "", price = 10.0)))
    }

    @Test
    fun cleanTrimsTextAndNormalizesLinks() {
        val clean = WishlistInputValidator.clean(
            WishlistItemInput(
                title = "  Sony WH-1000XM5 ",
                price = 28_000.0,
                url = "amazon.in/dp/B0C8",
                imageUrl = "",
                store = " Amazon ",
                notes = " birthday "
            )
        )!!
        assertEquals("Sony WH-1000XM5", clean.title)
        assertEquals("https://amazon.in/dp/B0C8", clean.url)
        assertEquals("", clean.imageUrl)
        assertEquals("Amazon", clean.store)
        assertEquals("birthday", clean.notes)
    }

    // --- Target purchase date ---

    @Test
    fun targetDateIsDescribedInCalendarDays() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 14, 23, 30) }.timeInMillis
        fun day(month: Int, date: Int) = Calendar.getInstance().apply { set(2026, month, date, 0, 5) }.timeInMillis

        assertEquals(0, WishlistDates.daysUntil(day(Calendar.SEPTEMBER, 14), now))
        assertEquals("tomorrow", WishlistDates.describe(day(Calendar.SEPTEMBER, 15), now))
        assertEquals("in 17 days", WishlistDates.describe(day(Calendar.OCTOBER, 1), now))
        assertEquals("3 days ago", WishlistDates.describe(day(Calendar.SEPTEMBER, 11), now))
    }
}
