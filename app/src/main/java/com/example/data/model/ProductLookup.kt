package com.example.data.model

/** Product details read from a store page. Every field is optional; the user reviews them before saving. */
data class ExtractedProduct(
    val title: String? = null,
    val price: Double? = null,
    val currencyCode: String? = null,
    val imageUrl: String? = null,
    val description: String? = null,
    val store: String? = null,
    val looksLikeProductPage: Boolean = false
) {
    val hasDetails: Boolean get() = title != null || price != null || imageUrl != null

    /** Core fields the page didn't provide, in the order the form shows them. */
    val missingFields: List<String>
        get() = buildList {
            if (title == null) add("name")
            if (price == null) add("price")
            if (imageUrl == null) add("image")
        }
}

sealed interface ProductLookupResult {
    val url: String?

    data class Found(override val url: String, val product: ExtractedProduct) : ProductLookupResult

    data class Failed(
        val reason: ProductLookupFailure,
        override val url: String? = null,
        val httpStatus: Int? = null
    ) : ProductLookupResult
}

enum class ProductLookupFailure {
    INVALID_LINK,
    NO_CONNECTION,
    TIMED_OUT,
    PAGE_NOT_FOUND,
    SITE_ERROR,
    BLOCKED,
    NOT_A_WEB_PAGE,
    NO_PRODUCT_DETAILS
}

object StoreNames {
    private val knownStores = mapOf(
        "amazon" to "Amazon", "amzn" to "Amazon", "flipkart" to "Flipkart", "myntra" to "Myntra",
        "ajio" to "AJIO", "croma" to "Croma", "reliancedigital" to "Reliance Digital", "tatacliq" to "Tata CLiQ",
        "nykaa" to "Nykaa", "meesho" to "Meesho", "snapdeal" to "Snapdeal", "jiomart" to "JioMart",
        "bigbasket" to "BigBasket", "lenskart" to "Lenskart", "pepperfry" to "Pepperfry", "firstcry" to "FirstCry",
        "decathlon" to "Decathlon", "ikea" to "IKEA", "apple" to "Apple", "samsung" to "Samsung",
        "bestbuy" to "Best Buy", "walmart" to "Walmart", "target" to "Target", "ebay" to "eBay", "etsy" to "Etsy",
        "aliexpress" to "AliExpress", "nike" to "Nike", "adidas" to "adidas", "zara" to "Zara", "hm" to "H&M",
        "uniqlo" to "Uniqlo", "boat-lifestyle" to "boAt"
    )
    private val secondLevelSuffixes = setOf("co", "com", "net", "org", "gov", "ac", "edu", "ltd", "plc")

    /** "https://www.amazon.in/dp/x" -> "Amazon"; unknown shops become "Some Shop" from "some-shop.com". */
    fun fromUrl(url: String): String? {
        val label = registrableLabel(WebLinks.displayHost(url)) ?: return null
        return knownStores[label] ?: label.split('-').filter { it.isNotEmpty() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            .takeIf { it.isNotEmpty() }
    }

    /** The store's own site name, e.g. og:site_name "Flipkart.com" -> "Flipkart". */
    fun cleanSiteName(siteName: String): String {
        val trimmed = siteName.trim()
        val domainLike = Regex("""^([\w-]+)\.(com|in|co\.in|co\.uk|net|org|shop|store)$""", RegexOption.IGNORE_CASE)
        return domainLike.find(trimmed)?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() } ?: trimmed
    }

    fun isKnownStore(url: String): Boolean = registrableLabel(WebLinks.displayHost(url)) in knownStores

    internal fun registrableLabel(host: String): String? {
        val labels = host.lowercase().split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return null
        val suffixLength = if (labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in secondLevelSuffixes) 2 else 1
        return labels.getOrNull(labels.size - suffixLength - 1)
    }
}

object CurrencySymbols {
    private val symbolsByCode = mapOf(
        "INR" to setOf("₹", "Rs", "Rs.", "INR"),
        "USD" to setOf("$", "US$", "USD"),
        "EUR" to setOf("€", "EUR"),
        "GBP" to setOf("£", "GBP"),
        "JPY" to setOf("¥", "JPY"),
        "CNY" to setOf("¥", "CN¥", "CNY"),
        "AUD" to setOf("$", "A$", "AUD"),
        "CAD" to setOf("$", "C$", "CAD"),
        "SGD" to setOf("$", "S$", "SGD"),
        "AED" to setOf("AED", "د.إ")
    )

    /** False only when the page clearly prices the product in a different currency than the user tracks. */
    fun matches(currencyCode: String?, userSymbol: String): Boolean {
        val code = currencyCode?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return true
        val symbols = symbolsByCode[code] ?: return userSymbol.trim().equals(code, ignoreCase = true)
        return userSymbol.trim() in symbols
    }
}
