package com.example

import com.example.data.model.ProductLookupFailure
import com.example.data.model.ProductLookupResult
import com.example.data.remote.ProductLookupService
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

/** Uses a fake network (an interceptor that answers every request) so tests never touch the internet. */
class ProductLookupServiceTest {

    private val requests = mutableListOf<Request>()

    private fun serviceAnswering(answer: (Request) -> Response) = ProductLookupService {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                synchronized(requests) { requests += request }
                answer(request)
            }
            .build()
    }

    private fun Request.respond(body: String, code: Int = 200, contentType: String = "text/html; charset=utf-8") =
        Response.Builder()
            .request(this)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("status $code")
            .body(body.toResponseBody(contentType.toMediaType()))
            .build()

    private val productPage = """
        <html><head>
        <meta property="og:title" content="Kettle | Kitchen Co">
        <meta property="og:site_name" content="Kitchen Co">
        <meta property="product:price:amount" content="1999">
        <meta property="og:image" content="https://cdn.kitchen.example.com/kettle.jpg">
        </head></html>
    """.trimIndent()

    @Test
    fun productPageIsReadFromTheSharedLink() = runBlocking {
        val service = serviceAnswering { it.respond(productPage) }

        val result = service.lookup("Look at this https://kitchen.example.com/kettle on Kitchen Co")

        result as ProductLookupResult.Found
        assertEquals("https://kitchen.example.com/kettle", result.url)
        assertEquals("Kettle", result.product.title)
        assertEquals(1_999.0, result.product.price!!, 0.001)
        assertEquals("Kitchen Co", result.product.store)

        val request = requests.single()
        assertEquals("https://kitchen.example.com/kettle", request.url.toString())
        assertTrue(request.header("User-Agent")!!.startsWith("Mozilla/5.0"))
        assertNull("no cookies or credentials are sent", request.header("Cookie"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun invalidLinksFailWithoutAnyNetworkRequest() = runBlocking {
        val service = serviceAnswering { error("network must not be used") }

        assertEquals(ProductLookupResult.Failed(ProductLookupFailure.INVALID_LINK), service.lookup("not a link"))
        assertEquals(ProductLookupResult.Failed(ProductLookupFailure.INVALID_LINK), service.lookup("ftp://files.example.com/a"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun httpErrorsBecomeFriendlyReasons() = runBlocking {
        suspend fun reasonFor(code: Int): ProductLookupResult.Failed =
            serviceAnswering { it.respond("error", code = code) }.lookup("https://shop.example.com/p") as ProductLookupResult.Failed

        assertEquals(ProductLookupFailure.PAGE_NOT_FOUND, reasonFor(404).reason)
        assertEquals(ProductLookupFailure.BLOCKED, reasonFor(403).reason)
        assertEquals(ProductLookupFailure.BLOCKED, reasonFor(503).reason)
        val serverError = reasonFor(500)
        assertEquals(ProductLookupFailure.SITE_ERROR, serverError.reason)
        assertEquals(500, serverError.httpStatus)
    }

    @Test
    fun connectionProblemsBecomeFriendlyReasons() = runBlocking {
        val offline = serviceAnswering { throw UnknownHostException("no network") }
        assertEquals(ProductLookupFailure.NO_CONNECTION, (offline.lookup("https://shop.example.com/p") as ProductLookupResult.Failed).reason)

        val slow = serviceAnswering { throw SocketTimeoutException("timeout") }
        assertEquals(ProductLookupFailure.TIMED_OUT, (slow.lookup("https://shop.example.com/p") as ProductLookupResult.Failed).reason)
    }

    @Test
    fun pagesWithoutUsableDetailsFallBackToManualEntry() = runBlocking {
        val image = serviceAnswering { it.respond("binary", contentType = "image/jpeg") }
        assertEquals(ProductLookupFailure.NOT_A_WEB_PAGE, (image.lookup("https://cdn.example.com/a.jpg") as ProductLookupResult.Failed).reason)

        val empty = serviceAnswering { it.respond("<html><body>Hello</body></html>") }
        assertEquals(ProductLookupFailure.NO_PRODUCT_DETAILS, (empty.lookup("https://example.com/") as ProductLookupResult.Failed).reason)

        val captcha = serviceAnswering { it.respond("<html><head><title>Robot Check</title></head><body></body></html>") }
        assertEquals(ProductLookupFailure.BLOCKED, (captcha.lookup("https://www.amazon.in/dp/B0C8") as ProductLookupResult.Failed).reason)
    }

    @Test
    fun plainHttpLinksAreRetriedOverHttpsWhenCleartextIsNotAllowed() = runBlocking {
        val service = serviceAnswering { request ->
            if (!request.isHttps) throw UnknownServiceException("CLEARTEXT communication not permitted")
            request.respond(productPage)
        }

        val result = service.lookup("http://kitchen.example.com/kettle")

        assertTrue(result is ProductLookupResult.Found)
        assertEquals(listOf("http", "https"), requests.map { it.url.scheme })
    }

    @Test
    fun hugePagesAreReadUpToTheCapWithoutFailing() = runBlocking {
        val filler = "<p>" + "x".repeat(3_000_000) + "</p>"
        val service = serviceAnswering { it.respond(productPage + filler) }

        val result = service.lookup("https://kitchen.example.com/kettle")

        assertEquals("Kettle", (result as ProductLookupResult.Found).product.title)
    }
}
