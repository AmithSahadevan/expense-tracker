package com.example.data.remote

import com.example.data.model.ExtractedProduct
import com.example.data.model.StoreNames
import com.example.data.model.WebLinks
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okio.Buffer

internal data class ParsedProductPage(val product: ExtractedProduct, val blocked: Boolean)

/**
 * Reads product details from a store page using standard metadata, in order of reliability:
 * schema.org JSON-LD `Product`, Open Graph / Twitter tags, microdata, then the page title.
 * Nothing site-specific and nothing that needs an API key.
 */
internal object ProductPageParser {
    private val jsonAdapter = Moshi.Builder().build().adapter(Any::class.java)
    private val productTypes = setOf("Product", "ProductGroup", "IndividualProduct", "ProductModel", "SomeProducts")
    private val titleSeparators = listOf(" | ", " - ", " – ", " — ", " : ", ": ", " · ")
    private const val MAX_DESCRIPTION_LENGTH = 600

    private val blockedTitlePhrases = listOf(
        "just a moment", "attention required", "access denied", "robot check", "are you a robot",
        "are you a human", "captcha", "security check", "verify you are human", "pardon our interruption",
        "request blocked", "bot verification"
    )
    private val blockedMarkers = listOf(
        "validatecaptcha", "cf-challenge", "cf_chl_", "challenge-platform", "px-captcha",
        "_incapsula_resource", "captcha-delivery.com", "g-recaptcha", "h-captcha"
    )

    fun parse(html: String, pageUrl: String): ParsedProductPage {
        val meta = HtmlMetadataReader.read(html)
        val products = meta.jsonLdBlocks.flatMap(::readJsonValues).flatMap { collectProducts(it) }
        val mainProduct = products.firstOrNull()

        val store = storeName(meta, pageUrl)
        val storeTokens = listOfNotNull(store, StoreNames.registrableLabel(WebLinks.displayHost(pageUrl)))

        val rawTitle = (mainProduct?.get("name") as? String)?.let { HtmlText.plain(it) }
            ?: listOf("og:title", "twitter:title", "title").firstNotNullOfOrNull(meta::meta)?.let { HtmlText.plain(it, decode = false) }
            ?: HtmlText.plain(meta.title)
        val title = rawTitle?.let { cleanTitle(it, storeTokens) }?.takeUnless { isGenericTitle(it, store, pageUrl) }

        val priced = products.firstNotNullOfOrNull { offerPrice(it["offers"]) }
            ?: PriceText.parse(meta.meta("product:price:amount") ?: meta.meta("og:price:amount"))
                ?.let { it to (meta.meta("product:price:currency") ?: meta.meta("og:price:currency")) }
            ?: meta.itemProp("price").firstNotNullOfOrNull(PriceText::parse)
                ?.let { it to meta.itemProp("pricecurrency").firstOrNull() }
            ?: twitterPrice(meta)?.let { it to null }

        val imageCandidates = listOfNotNull(mainProduct?.let { firstImage(it["image"]) }?.let(HtmlText::decodeEntities)) +
            listOf("og:image:secure_url", "og:image", "og:image:url", "twitter:image", "twitter:image:src").mapNotNull(meta::meta) +
            listOfNotNull(meta.linkHref("image_src")) +
            meta.itemProp("image")
        val imageUrl = imageCandidates.firstNotNullOfOrNull { resolveImageUrl(pageUrl, it) }

        val description = ((mainProduct?.get("description") as? String)?.let { HtmlText.plain(it) }
            ?: listOf("og:description", "twitter:description", "description").firstNotNullOfOrNull(meta::meta)
                ?.let { HtmlText.plain(it, decode = false) })
            ?.let(::truncateDescription)

        val looksLikeProduct = products.isNotEmpty() || priced != null ||
            meta.meta("og:type")?.contains("product", ignoreCase = true) == true

        val blocked = !looksLikeProduct && imageUrl == null && looksBlocked(html, rawTitle)
        if (blocked) return ParsedProductPage(ExtractedProduct(store = store), blocked = true)

        return ParsedProductPage(
            ExtractedProduct(
                title = title,
                price = priced?.first,
                currencyCode = priced?.second?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{3}")) },
                imageUrl = imageUrl,
                description = description,
                store = store,
                looksLikeProductPage = looksLikeProduct
            ),
            blocked = false
        )
    }

    internal fun looksBlocked(html: String, title: String?): Boolean {
        val lowerTitle = title?.lowercase().orEmpty()
        if (blockedTitlePhrases.any { it in lowerTitle }) return true
        val sample = if (html.length > 300_000) html.substring(0, 300_000) else html
        return blockedMarkers.any { sample.contains(it, ignoreCase = true) }
    }

    /** Removes the store's name from a page title: "Sony WH-1000XM5 : Amazon.in: Electronics" -> "Sony WH-1000XM5". */
    internal fun cleanTitle(title: String, storeTokens: List<String>): String {
        val tokens = storeTokens.map { it.lowercase() }.filter { it.length >= 3 }
        if (tokens.isEmpty()) return title
        var result = title

        // Leading "Amazon.com: Product name"
        for (separator in titleSeparators) {
            val index = result.indexOf(separator)
            if (index in 1..24 && tokens.any { result.substring(0, index).lowercase().contains(it) }) {
                result = result.substring(index + separator.length).trim()
                break
            }
        }

        // Trailing "Product name | Flipkart.com": cut at the last separator followed by the store name.
        var cutAt = -1
        for (separator in titleSeparators) {
            var index = result.indexOf(separator)
            while (index > 0) {
                val tail = result.substring(index + separator.length).lowercase()
                if (index > cutAt && tokens.any { tail.contains(it) }) cutAt = index
                index = result.indexOf(separator, index + 1)
            }
        }
        return if (cutAt >= 3) result.substring(0, cutAt).trim() else result
    }

    private fun isGenericTitle(title: String, store: String?, pageUrl: String): Boolean {
        val lower = title.lowercase()
        return store != null && StoreNames.cleanSiteName(title).equals(store, ignoreCase = true) ||
            lower == WebLinks.displayHost(pageUrl) ||
            blockedTitlePhrases.any { it in lower }
    }

    private fun storeName(meta: HtmlMetadata, pageUrl: String): String? {
        if (StoreNames.isKnownStore(pageUrl)) return StoreNames.fromUrl(pageUrl)
        val siteName = (meta.meta("og:site_name") ?: meta.meta("application-name"))?.let { HtmlText.plain(it, decode = false) }
        return siteName?.let(StoreNames::cleanSiteName) ?: StoreNames.fromUrl(pageUrl)
    }

    private fun readJsonValues(block: String): List<Any> {
        val cleaned = block
            .replace(Regex("""//\s*<!\[CDATA\["""), "")
            .replace(Regex("""//\s*]]>"""), "")
            .replace("<![CDATA[", "")
            .replace("]]>", "")
            .trim()
            .removePrefix("<!--")
            .removeSuffix("-->")
            .trim()
        if (cleaned.isEmpty()) return emptyList()
        val values = mutableListOf<Any>()
        try {
            val reader = JsonReader.of(Buffer().writeUtf8(cleaned))
            reader.isLenient = true
            while (reader.peek() != JsonReader.Token.END_DOCUMENT) {
                jsonAdapter.fromJson(reader)?.let { values += it }
            }
        } catch (e: Exception) {
            // Malformed JSON-LD is common on real pages; keep whatever parsed and fall back to other metadata.
        }
        return values
    }

    private fun collectProducts(node: Any?, found: MutableList<Map<*, *>> = mutableListOf(), depth: Int = 0): List<Map<*, *>> {
        if (depth > 12) return found
        when (node) {
            is Map<*, *> -> {
                if (isProductType(node["@type"])) found += node
                // Skip item lists (category and search pages list many different products).
                node.forEach { (key, value) -> if (key != "itemListElement") collectProducts(value, found, depth + 1) }
            }
            is List<*> -> node.forEach { collectProducts(it, found, depth + 1) }
        }
        return found
    }

    private fun isProductType(type: Any?): Boolean = when (type) {
        is String -> type.substringAfterLast('/').substringAfterLast(':') in productTypes
        is List<*> -> type.any(::isProductType)
        else -> false
    }

    private fun offerPrice(offers: Any?, depth: Int = 0): Pair<Double, String?>? {
        if (depth > 4) return null
        return when (offers) {
            is List<*> -> offers.firstNotNullOfOrNull { offerPrice(it, depth + 1) }
            is Map<*, *> -> {
                val currency = (offers["priceCurrency"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                val direct = PriceText.fromJson(offers["price"]) ?: PriceText.fromJson(offers["lowPrice"])
                if (direct != null) {
                    direct to currency
                } else {
                    (offerPrice(offers["priceSpecification"], depth + 1) ?: offerPrice(offers["offers"], depth + 1))
                        ?.let { (price, nestedCurrency) -> price to (nestedCurrency ?: currency) }
                }
            }
            else -> null
        }
    }

    private fun firstImage(value: Any?): String? = when (value) {
        is String -> value.trim().takeIf { it.isNotEmpty() }
        is List<*> -> value.firstNotNullOfOrNull(::firstImage)
        is Map<*, *> -> firstImage(value["contentUrl"]) ?: firstImage(value["url"])
        else -> null
    }

    private fun twitterPrice(meta: HtmlMetadata): Double? = (1..4).firstNotNullOfOrNull { n ->
        meta.meta("twitter:label$n")
            ?.takeIf { it.contains("price", ignoreCase = true) }
            ?.let { PriceText.parse(meta.meta("twitter:data$n")) }
    }

    /** Absolute https image link, resolving relative paths against the page. */
    private fun resolveImageUrl(pageUrl: String, reference: String): String? {
        val ref = reference.trim()
        if (ref.isEmpty() || ref.startsWith("data:", ignoreCase = true)) return null
        val resolved = pageUrl.toHttpUrlOrNull()?.resolve(ref) ?: return null
        // Android blocks plain-http images by default, so ask for the https version.
        val secure = if (resolved.scheme == "http") resolved.newBuilder().scheme("https").build() else resolved
        return secure.toString()
    }

    private fun truncateDescription(text: String): String {
        if (text.length <= MAX_DESCRIPTION_LENGTH) return text
        val cut = text.lastIndexOf(' ', MAX_DESCRIPTION_LENGTH).takeIf { it > MAX_DESCRIPTION_LENGTH / 2 } ?: MAX_DESCRIPTION_LENGTH
        return text.substring(0, cut).trimEnd() + "…"
    }
}

internal object PriceText {
    private val number = Regex("""\d[\d.,\u00A0\u202F']*""")

    /** Numbers from JSON-LD may be JSON numbers or strings like "28,999.00". */
    fun fromJson(value: Any?): Double? = when (value) {
        is Number -> value.toDouble().takeIf { it > 0 && it.isFinite() }
        is String -> parse(value)
        else -> null
    }

    /** Parses "₹1,28,999.00", "Rs.28,999", "$1,299.99", "1.299,99 €" and similar. Null if no positive amount. */
    fun parse(text: String?): Double? {
        val token = number.find(text ?: return null)?.value
            ?.filterNot { it == '\u00A0' || it == '\u202F' || it == '\'' }
            ?.trimEnd('.', ',')
            ?: return null
        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        val normalized = when {
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) token.replace(",", "") else token.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                val decimals = token.length - lastComma - 1
                if (token.count { it == ',' } == 1 && decimals in 1..2) token.replace(',', '.') else token.replace(",", "")
            }
            lastDot >= 0 && token.count { it == '.' } > 1 -> token.replace(".", "")
            else -> token
        }
        return normalized.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    }
}
