package com.yehor.finora.data.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.LocalDate
import javax.inject.Inject

/** Normalized quote returned by the client. Lives in the data layer. */
data class FetchedQuote(
    val base: String,
    val quote: String,
    val rate: BigDecimal,
    val quoteDate: LocalDate,
    val source: String,
)

enum class RateFetchReason { NO_NETWORK, TIMEOUT, HTTP_ERROR, BAD_PAYLOAD }

/** Used for tests and logging only. No UI shows the reason. */
class RateFetchException(val reason: RateFetchReason, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: reason.name, cause)

/**
 * The only place that knows the Frankfurter URL, HTTP and JSON. It is `open` so tests can subclass
 * it. That is a test seam only, not a provider abstraction.
 */
open class FrankfurterClient(private val baseUrl: String) {

    @Inject
    constructor() : this(DEFAULT_BASE_URL)

    /**
     * Requests `base -> quotes`. A quote the server does not know or return is simply absent from
     * the result. Any other failure throws [RateFetchException]; nothing is partially trusted.
     */
    open suspend fun fetchRates(base: String, quotes: Set<String>): List<FetchedQuote> =
        withContext(Dispatchers.IO) {
            if (quotes.isEmpty()) return@withContext emptyList()
            val ordered = quotes.sorted()
            val response = get("$baseUrl/v2/rates?base=$base&quotes=${ordered.joinToString(",")}")
            when (response.status) {
                200 -> parseFrankfurterPayload(response.body, base, quotes, array = true)
                404, 422 -> ordered.flatMap { quote -> fetchSingle(base, quote) }
                else -> throw RateFetchException(RateFetchReason.HTTP_ERROR, "HTTP ${response.status}")
            }
        }

    private fun fetchSingle(base: String, quote: String): List<FetchedQuote> {
        val response = get("$baseUrl/v2/rate/$base/$quote")
        return when (response.status) {
            200 -> parseFrankfurterPayload(response.body, base, setOf(quote), array = false)
            404, 422 -> emptyList()
            else -> throw RateFetchException(RateFetchReason.HTTP_ERROR, "HTTP ${response.status}")
        }
    }

    private class Response(val status: Int, val body: String)

    private fun get(url: String): Response {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw RateFetchException(RateFetchReason.NO_NETWORK, cause = e)
        }
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            val status = connection.responseCode
            val stream = if (status == 200) connection.inputStream else connection.errorStream
            val body = stream?.use { readCapped(it) }.orEmpty()
            return Response(status, body)
        } catch (e: RateFetchException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw RateFetchException(RateFetchReason.TIMEOUT, cause = e)
        } catch (e: UnknownHostException) {
            throw RateFetchException(RateFetchReason.NO_NETWORK, cause = e)
        } catch (e: ConnectException) {
            throw RateFetchException(RateFetchReason.NO_NETWORK, cause = e)
        } catch (e: IOException) {
            throw RateFetchException(RateFetchReason.HTTP_ERROR, cause = e)
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BODY_BYTES) {
                throw RateFetchException(RateFetchReason.BAD_PAYLOAD, "Response too large")
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.frankfurter.dev"
        const val SOURCE = "frankfurter"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 1024 * 1024
    }
}

/**
 * Strict parser. Any violation rejects the whole response with [RateFetchReason.BAD_PAYLOAD],
 * because a half-trusted answer is worse than a stale cache. Rates are read from their JSON text,
 * never through a floating-point type.
 */
internal fun parseFrankfurterPayload(
    body: String,
    base: String,
    quotes: Set<String>,
    array: Boolean,
): List<FetchedQuote> {
    fun bad(message: String): Nothing = throw RateFetchException(RateFetchReason.BAD_PAYLOAD, message)

    val root: JsonElement = try {
        Json.parseToJsonElement(body)
    } catch (e: Exception) {
        throw RateFetchException(RateFetchReason.BAD_PAYLOAD, "Not JSON", e)
    }
    val items: List<JsonElement> = if (array) {
        (root as? JsonArray ?: bad("Top level is not an array")).toList()
    } else {
        listOf(root)
    }

    fun text(obj: JsonObject, key: String): String {
        val primitive = obj[key] as? JsonPrimitive ?: bad("Missing $key")
        if (primitive is JsonNull) bad("Null $key")
        return primitive.content
    }

    val seen = HashSet<String>()
    return items.map { item ->
        val obj = item as? JsonObject ?: bad("Item is not an object")
        if (text(obj, "base") != base) bad("Unexpected base")
        val quote = text(obj, "quote")
        if (quote !in quotes) bad("Unrequested quote $quote")
        if (!seen.add(quote)) bad("Duplicate quote $quote")
        val date = try {
            LocalDate.parse(text(obj, "date"))
        } catch (e: java.time.format.DateTimeParseException) {
            bad("Bad date")
        }
        val rate = try {
            BigDecimal(text(obj, "rate"))
        } catch (e: NumberFormatException) {
            bad("Bad rate")
        }
        if (rate.signum() <= 0) bad("Non-positive rate")
        FetchedQuote(base, quote, BigDecimal(rate.stripTrailingZeros().toPlainString()), date, FrankfurterClient.SOURCE)
    }
}
