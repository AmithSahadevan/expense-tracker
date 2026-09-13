package com.example.data.remote

import com.example.data.model.ProductLookupFailure
import com.example.data.model.ProductLookupResult
import com.example.data.model.WebLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownServiceException
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads product details from a link the user pasted. The page is fetched directly from the device,
 * like opening it in a browser: no scraping service, API keys or credentials are involved.
 * Never throws for bad links or unreachable pages; every problem comes back as [ProductLookupResult.Failed].
 */
class ProductLookupService(clientFactory: () -> OkHttpClient = ::defaultClient) {

    private val client by lazy(clientFactory)

    suspend fun lookup(input: String): ProductLookupResult {
        val url = WebLinks.findInText(input)?.takeIf { it.toHttpUrlOrNull() != null }
            ?: return ProductLookupResult.Failed(ProductLookupFailure.INVALID_LINK)

        return withContext(Dispatchers.IO) {
            try {
                classify(url, fetchWithHttpsFallback(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: InterruptedIOException) {
                ProductLookupResult.Failed(ProductLookupFailure.TIMED_OUT, url)
            } catch (e: IOException) {
                ProductLookupResult.Failed(ProductLookupFailure.NO_CONNECTION, url)
            } catch (e: Exception) {
                // A page we can't make sense of must never break adding an item.
                ProductLookupResult.Failed(ProductLookupFailure.NO_PRODUCT_DETAILS, url)
            }
        }
    }

    private suspend fun fetchWithHttpsFallback(url: String): FetchedPage = try {
        fetch(url)
    } catch (e: UnknownServiceException) {
        // Android refuses plain-http traffic by default; most stores serve the same page over https.
        if (url.startsWith("http://", ignoreCase = true)) fetch("https://" + url.substring("http://".length)) else throw e
    }

    private suspend fun fetch(url: String): FetchedPage = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "${Locale.getDefault().toLanguageTag()},en;q=0.8")
            .get()
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val page = try {
                    response.use(::readPage)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                    return
                }
                continuation.resume(page)
            }
        })
    }

    private fun readPage(response: Response): FetchedPage {
        if (!response.isSuccessful) return FetchedPage.HttpError(response.code)
        val body = response.body ?: return FetchedPage.HttpError(response.code)
        val mediaType = body.contentType()
        val isHtml = mediaType == null || mediaType.subtype.contains("html", ignoreCase = true)
        if (!isHtml) return FetchedPage.NotHtml

        // Metadata lives near the top of the page; cap the download so huge pages can't exhaust memory.
        val source = body.source()
        source.request(MAX_PAGE_BYTES)
        val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_PAGE_BYTES))
        val charset = mediaType?.charset() ?: sniffCharset(bytes) ?: Charsets.UTF_8
        return FetchedPage.Html(finalUrl = response.request.url.toString(), html = String(bytes, charset))
    }

    private fun classify(url: String, page: FetchedPage): ProductLookupResult = when (page) {
        is FetchedPage.HttpError -> ProductLookupResult.Failed(
            reason = when (page.status) {
                404, 410 -> ProductLookupFailure.PAGE_NOT_FOUND
                401, 403, 429, 503, 999 -> ProductLookupFailure.BLOCKED
                else -> ProductLookupFailure.SITE_ERROR
            },
            url = url,
            httpStatus = page.status
        )
        FetchedPage.NotHtml -> ProductLookupResult.Failed(ProductLookupFailure.NOT_A_WEB_PAGE, url)
        is FetchedPage.Html -> {
            val parsed = ProductPageParser.parse(page.html, page.finalUrl)
            when {
                parsed.blocked -> ProductLookupResult.Failed(ProductLookupFailure.BLOCKED, url)
                parsed.product.hasDetails -> ProductLookupResult.Found(url, parsed.product)
                else -> ProductLookupResult.Failed(ProductLookupFailure.NO_PRODUCT_DETAILS, url)
            }
        }
    }

    private fun sniffCharset(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = Regex("""charset\s*=\s*["']?([\w.:-]+)""", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
        return name?.let { runCatching { Charset.forName(it) }.getOrNull() }
    }

    private sealed interface FetchedPage {
        data class Html(val finalUrl: String, val html: String) : FetchedPage
        data class HttpError(val status: Int) : FetchedPage
        data object NotHtml : FetchedPage
    }

    companion object {
        internal const val MAX_PAGE_BYTES = 2_500_000L

        // A standard mobile browser identity: stores serve their normal product page (and its metadata) to browsers.
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}
