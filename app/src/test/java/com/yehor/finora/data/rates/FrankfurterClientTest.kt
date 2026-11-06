package com.yehor.finora.data.rates

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList

class FrankfurterClientTest {

    private lateinit var server: HttpServer
    private val requests = CopyOnWriteArrayList<String>()
    private var handler: (String) -> Pair<Int, String> = { 404 to "" }

    private val client get() = FrankfurterClient("http://localhost:${server.address.port}")

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        server.createContext("/") { ex ->
            val target = ex.requestURI.toString()
            requests += target
            val (status, body) = handler(target)
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) }
            ex.close()
        }
        server.start()
    }

    @After
    fun tearDown() = server.stop(0)

    private fun fetch(base: String, vararg quotes: String) =
        runBlocking { client.fetchRates(base, quotes.toSet()) }

    private fun assertBadPayload(body: String, base: String = "UAH", vararg quotes: String = arrayOf("PLN")) {
        handler = { 200 to body }
        val e = runCatching { fetch(base, *quotes) }.exceptionOrNull()
        assertTrue("expected BAD_PAYLOAD for $body but got $e", e is RateFetchException && e.reason == RateFetchReason.BAD_PAYLOAD)
    }

    @Test
    fun parsesMultipleQuotesExactlyWithDifferentDates() {
        handler = {
            200 to """[
                {"date":"2026-11-05","base":"UAH","quote":"EUR","rate":0.019557},
                {"date":"2026-11-06","base":"UAH","quote":"PLN","rate":0.086029},
                {"date":"2026-11-06","base":"UAH","quote":"USD","rate":0.021988}]"""
        }
        val result = fetch("UAH", "EUR", "PLN", "USD")
        assertEquals(3, result.size)
        val pln = result.single { it.quote == "PLN" }
        assertEquals(BigDecimal("0.086029"), pln.rate)
        assertEquals(LocalDate.of(2026, 11, 6), pln.quoteDate)
        assertEquals("frankfurter", pln.source)
        assertEquals(LocalDate.of(2026, 11, 5), result.single { it.quote == "EUR" }.quoteDate)
    }

    @Test
    fun requestShapeUsesV2PathAndQuery() {
        handler = { 200 to "[]" }
        fetch("USD", "PLN", "EUR")
        assertEquals(listOf("/v2/rates?base=USD&quotes=EUR,PLN"), requests.toList())
    }

    @Test
    fun missingRequestedQuoteIsAbsent() {
        handler = { 200 to """[{"date":"2026-11-06","base":"UAH","quote":"PLN","rate":0.086029}]""" }
        assertEquals(listOf("PLN"), fetch("UAH", "PLN", "XXX").map { it.quote })
    }

    @Test
    fun groupFailureFallsBackToSinglePairs() {
        handler = { target ->
            when {
                target.startsWith("/v2/rates") -> 422 to """{"message":"unknown"}"""
                target == "/v2/rate/USD/PLN" -> 200 to """{"date":"2026-11-06","base":"USD","quote":"PLN","rate":3.9126}"""
                else -> 404 to ""
            }
        }
        val result = fetch("USD", "PLN", "ZZZ")
        assertEquals(1, result.size)
        assertEquals(BigDecimal("3.9126"), result.single().rate)
    }

    @Test
    fun malformedPayloadsAreRejected() {
        assertBadPayload("not json")
        assertBadPayload("""{"date":"2026-11-06","base":"UAH","quote":"PLN","rate":1}""")
        assertBadPayload("""[{"date":"2026-11-06","base":"UAH","quote":"PLN"}]""")
        assertBadPayload("""[{"date":"2026-11-06","base":"EUR","quote":"PLN","rate":1}]""")
        assertBadPayload("""[{"date":"2026-11-06","base":"UAH","quote":"USD","rate":1}]""")
        assertBadPayload("""[{"date":"yesterday","base":"UAH","quote":"PLN","rate":1}]""")
        assertBadPayload(
            """[{"date":"2026-11-06","base":"UAH","quote":"PLN","rate":1},{"date":"2026-11-06","base":"UAH","quote":"PLN","rate":2}]""",
        )
    }

    @Test
    fun invalidRatesAreRejected() {
        listOf("0", "-1", "null", "\"abc\"", "\"NaN\"").forEach { rate ->
            assertBadPayload("""[{"date":"2026-11-06","base":"UAH","quote":"PLN","rate":$rate}]""")
        }
    }

    @Test
    fun oversizedBodyIsRejected() {
        assertBadPayload("[" + " ".repeat(FrankfurterClient.MAX_BODY_BYTES + 10) + "]")
    }

    @Test
    fun httpFailuresMapToHttpError() {
        listOf(500, 503).forEach { status ->
            handler = { status to "oops" }
            val e = runCatching { fetch("UAH", "PLN") }.exceptionOrNull()
            assertTrue(e is RateFetchException && e.reason == RateFetchReason.HTTP_ERROR)
        }
    }

    @Test
    fun refusedConnectionIsNoNetwork() {
        val port = ServerSocket(0).use { it.localPort }
        val e = runCatching { runBlocking { FrankfurterClient("http://localhost:$port").fetchRates("UAH", setOf("PLN")) } }
            .exceptionOrNull()
        assertTrue("got $e", e is RateFetchException && e.reason == RateFetchReason.NO_NETWORK)
    }
}
