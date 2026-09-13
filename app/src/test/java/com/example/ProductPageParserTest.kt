package com.example

import com.example.data.model.CurrencySymbols
import com.example.data.model.StoreNames
import com.example.data.model.WebLinks
import com.example.data.remote.HtmlText
import com.example.data.remote.PriceText
import com.example.data.remote.ProductPageParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductPageParserTest {

    @Test
    fun jsonLdProductIsPreferredAndFullyRead() {
        val html = """
            <!doctype html><html><head>
            <title>Sony WH-1000XM5 Price in India - Buy Online at Flipkart.com</title>
            <meta property="og:title" content="Should not win over JSON-LD">
            <script type="application/ld+json">
            [{"@context":"https://schema.org","@type":"BreadcrumbList","itemListElement":[{"@type":"ListItem","item":{"@type":"Product","name":"Breadcrumb decoy"}}]},
             {"@context":"https://schema.org","@graph":[{"@type":"WebPage","name":"page"},
               {"@type":"Product","name":"Sony WH-1000XM5 &amp; Case","description":"<p>Industry-leading   noise cancellation</p>",
                "image":[{"@type":"ImageObject","contentUrl":"//rukminim.flixcart.com/image/xm5.jpeg"}],
                "offers":[{"@type":"Offer","price":"28,999.00","priceCurrency":"INR"}]}]}]
            </script>
            </head><body></body></html>
        """.trimIndent()

        val page = ProductPageParser.parse(html, "https://www.flipkart.com/sony-xm5/p/itm123")
        val product = page.product

        assertFalse(page.blocked)
        assertEquals("Sony WH-1000XM5 & Case", product.title)
        assertEquals(28_999.0, product.price!!, 0.001)
        assertEquals("INR", product.currencyCode)
        assertEquals("https://rukminim.flixcart.com/image/xm5.jpeg", product.imageUrl)
        assertEquals("Industry-leading noise cancellation", product.description)
        assertEquals("Flipkart", product.store)
        assertTrue(product.looksLikeProductPage)
    }

    @Test
    fun openGraphTagsAreUsedWhenThereIsNoStructuredData() {
        val html = """
            <html><head>
            <meta property="og:type" content="product">
            <meta property="og:title" content="Handmade Mug | Clay &amp; Co">
            <meta property="og:site_name" content="Clay &amp; Co">
            <meta property="og:image" content="/images/mug.jpg?w=800&amp;h=800">
            <meta property="product:price:amount" content="1499">
            <meta property="product:price:currency" content="inr">
            <meta name="description" content="A hand-thrown stoneware mug.">
            </head></html>
        """.trimIndent()

        val product = ProductPageParser.parse(html, "http://shop.clayandco.example.com/products/mug").product

        assertEquals("Handmade Mug", product.title)
        assertEquals(1_499.0, product.price!!, 0.001)
        assertEquals("INR", product.currencyCode)
        assertEquals("https://shop.clayandco.example.com/images/mug.jpg?w=800&h=800", product.imageUrl)
        assertEquals("A hand-thrown stoneware mug.", product.description)
        assertEquals("Clay & Co", product.store)
        assertTrue(product.looksLikeProductPage)
    }

    @Test
    fun microdataAndPlainTitlesWorkAndDecoysInScriptsOrCommentsAreIgnored() {
        val html = """
            <html><head>
            <title>Amazon.in: Buy boAt Airdopes 141 Online at Low Prices</title>
            <meta name="title" content="boAt Airdopes 141 : Amazon.in: Electronics">
            </head><body>
            <div itemscope itemtype="https://schema.org/Product">
              <span itemprop="price" content="1,299.00">₹1,299</span>
              <meta itemprop="priceCurrency" content="INR">
              <img alt="x > y" itemprop="image" src="https://m.media-amazon.com/images/I/airdopes.jpg">
            </div>
            <script>var decoy = "<meta property='og:title' content='Script decoy'>";</script>
            <!-- <meta property="og:title" content="Comment decoy"> -->
            </body></html>
        """.trimIndent()

        val product = ProductPageParser.parse(html, "https://www.amazon.in/dp/B09N3ZNHTY").product

        assertEquals("boAt Airdopes 141", product.title)
        assertEquals(1_299.0, product.price!!, 0.001)
        assertEquals("INR", product.currencyCode)
        assertEquals("https://m.media-amazon.com/images/I/airdopes.jpg", product.imageUrl)
        assertEquals("Amazon", product.store)
    }

    @Test
    fun twitterPriceLabelsAndAggregateOffersAreRead() {
        val twitter = """
            <meta name="twitter:title" content="Trail Shoes">
            <meta name="twitter:label1" content="Price"><meta name="twitter:data1" content="${'$'}89.99">
            <meta name="twitter:image" content="https://cdn.example.com/shoe.png">
        """.trimIndent()
        val shoes = ProductPageParser.parse(twitter, "https://trailgear.example.com/shoes").product
        assertEquals("Trail Shoes", shoes.title)
        assertEquals(89.99, shoes.price!!, 0.001)
        assertNull(shoes.currencyCode)

        val aggregate = """<script type="application/ld+json">
            {"@type":["Product","Thing"],"name":"Desk Lamp","offers":{"@type":"AggregateOffer","lowPrice":2499,"highPrice":3999,"priceCurrency":"INR"}}
            </script>"""
        assertEquals(2_499.0, ProductPageParser.parse(aggregate, "https://lamps.example.com/p/1").product.price!!, 0.001)

        val specification = """<script type="application/ld+json">
            {"@type":"Product","name":"Chair","offers":{"@type":"Offer","priceSpecification":{"price":"12.345,67","priceCurrency":"EUR"}}}
            </script>"""
        val chair = ProductPageParser.parse(specification, "https://chairs.example.de/p/1").product
        assertEquals(12_345.67, chair.price!!, 0.001)
        assertEquals("EUR", chair.currencyCode)
    }

    @Test
    fun malformedJsonLdFallsBackToOtherMetadata() {
        val html = """
            <script type="application/ld+json">{"@type":"Product","name":"Broken", </script>
            <meta property="og:title" content="Fallback Title">
        """.trimIndent()
        val product = ProductPageParser.parse(html, "https://example.com/p").product
        assertEquals("Fallback Title", product.title)
        assertFalse(product.looksLikeProductPage)
    }

    @Test
    fun categoryPagesAreNotMistakenForOneProduct() {
        val html = """
            <title>Headphones - Best Deals</title>
            <script type="application/ld+json">
            {"@type":"ItemList","itemListElement":[
              {"@type":"ListItem","item":{"@type":"Product","name":"A","offers":{"price":"100"}}},
              {"@type":"ListItem","item":{"@type":"Product","name":"B","offers":{"price":"200"}}}]}
            </script>
        """.trimIndent()
        val product = ProductPageParser.parse(html, "https://gadgets.example.com/headphones").product
        assertNull(product.price)
        assertFalse(product.looksLikeProductPage)
    }

    @Test
    fun botChallengePagesAreReportedAsBlocked() {
        val amazonCaptcha = """<html><head><title>Amazon.in</title></head>
            <body><form action="/errors/validateCaptcha">Type the characters you see</form></body></html>"""
        val captcha = ProductPageParser.parse(amazonCaptcha, "https://www.amazon.in/dp/B0C8")
        assertTrue(captcha.blocked)
        assertNull(captcha.product.title)

        val cloudflare = "<html><head><title>Just a moment...</title></head><body></body></html>"
        assertTrue(ProductPageParser.parse(cloudflare, "https://shop.example.com/p").blocked)

        val normal = "<html><head><title>Kettle</title></head><body></body></html>"
        assertFalse(ProductPageParser.parse(normal, "https://shop.example.com/p").blocked)
    }

    @Test
    fun storeNamesAreRemovedFromTitles() {
        val amazon = listOf("Amazon", "amazon")
        assertEquals("Sony WH-1000XM5 - Black", ProductPageParser.cleanTitle("Sony WH-1000XM5 - Black - Amazon.in", amazon))
        assertEquals("Sony WH-1000XM5", ProductPageParser.cleanTitle("Amazon.com : Sony WH-1000XM5", amazon))
        assertEquals("Plain Title - Blue", ProductPageParser.cleanTitle("Plain Title - Blue", amazon))
    }

    @Test
    fun pricesInCommonFormatsAreParsed() {
        assertEquals(128_999.0, PriceText.parse("₹1,28,999.00")!!, 0.001)
        assertEquals(28_999.0, PriceText.parse("Rs.28,999")!!, 0.001)
        assertEquals(1_299.99, PriceText.parse("$1,299.99")!!, 0.001)
        assertEquals(1_299.99, PriceText.parse("1.299,99 €")!!, 0.001)
        assertEquals(1_299.99, PriceText.parse("1\u00A0299,99 €")!!, 0.001)
        assertEquals(19.99, PriceText.parse("19,99")!!, 0.001)
        assertNull(PriceText.parse("Free"))
        assertNull(PriceText.parse("0"))
    }

    @Test
    fun entitiesAreDecoded() {
        assertEquals("Tom & Jerry ₹500 ₹5 &unknown;", HtmlText.decodeEntities("Tom &amp; Jerry &#8377;500 &#x20B9;5 &unknown;"))
    }

    @Test
    fun linksStoresAndCurrenciesAreRecognized() {
        assertEquals("https://amzn.in/d/abc123", WebLinks.findInText("Check this out! https://amzn.in/d/abc123."))
        assertEquals("https://www.flipkart.com/item", WebLinks.findInText("www.flipkart.com/item"))
        assertNull(WebLinks.findInText("no link here"))

        assertEquals("Amazon", StoreNames.fromUrl("https://www.amazon.co.uk/dp/1"))
        assertEquals("Flipkart", StoreNames.fromUrl("https://dl.flipkart.com/s/x"))
        assertEquals("Some Shop", StoreNames.fromUrl("https://some-shop.com/p"))
        assertEquals("Flipkart", StoreNames.cleanSiteName("Flipkart.com"))

        assertTrue(CurrencySymbols.matches("INR", "₹"))
        assertTrue(CurrencySymbols.matches(null, "₹"))
        assertTrue(CurrencySymbols.matches("USD", "$"))
        assertFalse(CurrencySymbols.matches("USD", "₹"))
        assertFalse(CurrencySymbols.matches("BRL", "₹"))
    }
}
