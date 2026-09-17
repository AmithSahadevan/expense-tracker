package com.example.data.model

import com.example.data.local.entities.WishlistItemEntity

/** A wishlist product being added or edited. [price] is 0 when no price has been added yet. */
data class WishlistItemInput(
    val title: String,
    val price: Double,
    val url: String = "",
    val imageUrl: String = "",
    val store: String = "",
    val description: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val targetPurchaseDate: Long? = null,
    val notes: String = "",
    val priority: String = WishlistPriority.MEDIUM
)

/** A price of 0 means it hasn't been added yet, e.g. the store page didn't show one. */
object WishlistPrice {
    fun isSet(price: Double): Boolean = price > 0
}

object WishlistPriority {
    const val LOW = "LOW"
    const val MEDIUM = "MEDIUM"
    const val HIGH = "HIGH"
    const val MUST_HAVE = "MUST_HAVE"

    val all = listOf(LOW, MEDIUM, HIGH, MUST_HAVE)

    fun label(priority: String): String = when (priority) {
        MUST_HAVE -> "Must have"
        HIGH -> "High"
        LOW -> "Low"
        else -> "Medium"
    }
}

sealed interface WishlistAffordability {
    data class CanAfford(val remainingAfterPurchase: Double) : WishlistAffordability
    data class MoreNeeded(val amountNeeded: Double, val progress: Float) : WishlistAffordability
}

object WishlistAffordabilityCalculator {
    /**
     * Business rule: wishlist items are paid for with Adult Money ONLY.
     * The Emergency Fund is protected and must never be passed in here.
     */
    fun evaluate(price: Double, adultMoneyBalance: Double): WishlistAffordability {
        val spendableCents = SavingsCalculator.toCents(adultMoneyBalance.coerceAtLeast(0.0))
        val priceCents = SavingsCalculator.toCents(price)
        if (spendableCents >= priceCents) {
            return WishlistAffordability.CanAfford(remainingAfterPurchase = (spendableCents - priceCents) / 100.0)
        }
        val progress = if (priceCents > 0) (spendableCents.toFloat() / priceCents).coerceIn(0f, 1f) else 0f
        return WishlistAffordability.MoreNeeded(amountNeeded = (priceCents - spendableCents) / 100.0, progress = progress)
    }
}

enum class WishlistFilter(val label: String) {
    ALL("All"),
    CAN_AFFORD("Can Afford"),
    NEED_MORE("Need More"),
    HIGHEST_PRICE("Highest Price"),
    LOWEST_PRICE("Lowest Price")
}

object WishlistBrowser {
    /**
     * Filters and orders wishlist items for display, judging affordability with Adult Money only.
     * Items still wanted come before purchased ones; purchased items are neither "can afford" nor "need more".
     * Items without a price are also in neither group, and come after priced items in the price sorts.
     */
    fun apply(
        items: List<WishlistItemEntity>,
        filter: WishlistFilter,
        adultMoneyBalance: Double
    ): List<WishlistItemEntity> {
        val newestFirst = items.sortedWith(
            compareByDescending<WishlistItemEntity> { it.dateAdded }.thenByDescending { it.id }
        )
        fun canAfford(item: WishlistItemEntity) =
            WishlistAffordabilityCalculator.evaluate(item.estimatedCost, adultMoneyBalance) is WishlistAffordability.CanAfford
        fun isOpenAndPriced(item: WishlistItemEntity) = !item.isPurchased && WishlistPrice.isSet(item.estimatedCost)

        return when (filter) {
            WishlistFilter.ALL -> newestFirst.sortedBy { it.isPurchased }
            WishlistFilter.CAN_AFFORD -> newestFirst.filter { isOpenAndPriced(it) && canAfford(it) }
            WishlistFilter.NEED_MORE -> newestFirst.filter { isOpenAndPriced(it) && !canAfford(it) }
            WishlistFilter.HIGHEST_PRICE -> newestFirst.sortedWith(
                compareBy<WishlistItemEntity> { it.isPurchased }
                    .thenBy { !WishlistPrice.isSet(it.estimatedCost) }
                    .thenByDescending { it.estimatedCost }
            )
            WishlistFilter.LOWEST_PRICE -> newestFirst.sortedWith(
                compareBy<WishlistItemEntity> { it.isPurchased }
                    .thenBy { !WishlistPrice.isSet(it.estimatedCost) }
                    .thenBy { it.estimatedCost }
            )
        }
    }
}

object WebLinks {
    /**
     * Normalizes a typed or pasted web link: blank stays blank, a missing scheme becomes https://.
     * Returns null when the text doesn't look like an http(s) address.
     */
    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.any { it.isWhitespace() }) return null

        val scheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*)://").find(trimmed)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https") return null
        val withScheme = if (scheme == null) "https://$trimmed" else trimmed

        val host = hostOf(withScheme)
        val labels = host.split('.')
        val looksLikeHost = labels.size >= 2 &&
            labels.all { label -> label.isNotEmpty() && label.all { it.isLetterOrDigit() || it == '-' } }
        return if (looksLikeHost) withScheme else null
    }

    /**
     * Finds the link in pasted text, e.g. "Check this out https://amzn.in/d/abc" from a store app's share
     * button, and normalizes it. Returns null when the text contains no usable web link.
     */
    fun findInText(text: String): String? {
        val embedded = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).find(text)?.value
            ?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':')
        return normalize(embedded ?: text)?.takeIf { it.isNotEmpty() }
    }

    /** "https://www.amazon.in/dp/123" -> "amazon.in" */
    fun displayHost(url: String): String = hostOf(url).removePrefix("www.")

    private fun hostOf(url: String): String =
        url.substringAfter("://")
            .takeWhile { it != '/' && it != '?' && it != '#' }
            .substringAfterLast('@')
            .substringBefore(':')
            .lowercase()
}

data class WishlistInputErrors(
    val title: String? = null,
    val price: String? = null,
    val url: String? = null,
    val imageUrl: String? = null
) {
    val isValid: Boolean get() = title == null && price == null && url == null && imageUrl == null
}

object WishlistInputValidator {
    fun validate(input: WishlistItemInput): WishlistInputErrors = WishlistInputErrors(
        title = if (input.title.isBlank()) "Product name is required" else null,
        price = if (input.price < 0) "Price can't be negative" else null,
        url = if (WebLinks.normalize(input.url) == null) "Enter a valid link, e.g. amazon.in/…" else null,
        imageUrl = if (WebLinks.normalize(input.imageUrl) == null) "Enter a valid image link" else null
    )

    /** Trimmed input with normalized links, or null if the input is invalid. */
    fun clean(input: WishlistItemInput): WishlistItemInput? {
        if (!validate(input).isValid) return null
        return input.copy(
            title = input.title.trim(),
            url = WebLinks.normalize(input.url).orEmpty(),
            imageUrl = WebLinks.normalize(input.imageUrl).orEmpty(),
            store = input.store.trim(),
            description = input.description.trim(),
            notes = input.notes.trim()
        )
    }
}
