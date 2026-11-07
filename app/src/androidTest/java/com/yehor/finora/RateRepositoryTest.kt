package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.rates.FetchedQuote
import com.yehor.finora.data.rates.FrankfurterClient
import com.yehor.finora.data.rates.RateFetchException
import com.yehor.finora.data.rates.RateFetchReason
import com.yehor.finora.data.repo.RateRepository
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.rate.CurrencyPair
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RateRepositoryTest {

    private class FakeClient : FrankfurterClient("http://unused.invalid") {
        val calls = mutableListOf<Pair<String, Set<String>>>()
        var failing = false
        var unsupported = emptySet<String>()

        override suspend fun fetchRates(base: String, quotes: Set<String>): List<FetchedQuote> {
            calls += base to quotes
            if (failing) throw RateFetchException(RateFetchReason.NO_NETWORK)
            return quotes.filter { it !in unsupported }.map {
                FetchedQuote(base, it, BigDecimal("2.5"), LocalDate.of(2026, 11, 6), "frankfurter")
            }
        }
    }

    private lateinit var db: FinoraDatabase
    private lateinit var client: FakeClient
    private lateinit var repo: RateRepository
    private var now = 1_000_000_000_000L

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        client = FakeClient()
        repo = RateRepository(db, SettingsRepository(db.appSettingDao(), db.accountDao()), client) { now }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(code: String, status: AccountStatus = AccountStatus.ACTIVE) {
        db.accountDao().insert(
            AccountEntity(
                name = code, currencyCode = code, type = "CASH", icon = null, color = null,
                description = null, status = status, createdAt = 0,
            ),
        )
    }

    @Test
    fun requestsAllAccountCurrenciesIncludingArchivedGroupedByBase() = runBlocking {
        account("UAH"); account("USD"); account("EUR", AccountStatus.ARCHIVED)
        repo.settingsDisplay("PLN")
        repo.refreshIfNeeded()
        val calls = client.calls.associate { it.first to it.second }
        assertEquals(
            mapOf(
                "UAH" to setOf("PLN", "USD", "EUR"),
                "USD" to setOf("PLN", "UAH", "EUR"),
                "EUR" to setOf("PLN", "UAH", "USD"),
                "PLN" to setOf("UAH", "USD", "EUR"),
            ),
            calls,
        )
        val rows = db.rateDao().getAll()
        assertEquals(12, rows.size)
        assertTrue(rows.all { it.source == "frankfurter" && it.fetchedAt == now })
        assertEquals(12, repo.observeTable().first().quotes.size)
    }

    @Test
    fun notMoreThanOncePer24Hours() = runBlocking {
        account("UAH"); account("USD")
        repo.refreshIfNeeded()
        val first = client.calls.size
        now += 24L * 3600_000 - 60_000
        repo.refreshIfNeeded()
        assertEquals(first, client.calls.size)
        now += 60_000
        repo.refreshIfNeeded()
        assertTrue(client.calls.size > first)
    }

    @Test
    fun failedRefreshKeepsCacheAndWaitsFifteenMinutes() = runBlocking {
        account("UAH"); account("USD")
        repo.refreshIfNeeded()
        val before = db.rateDao().getAll()
        client.failing = true
        now += 25L * 3600_000
        repo.refreshIfNeeded()
        assertEquals(before, db.rateDao().getAll())
        val callsAfterFailure = client.calls.size
        now += 5 * 60_000
        repo.refreshIfNeeded()
        assertEquals(callsAfterFailure, client.calls.size)
        now += 11 * 60_000
        client.failing = false
        repo.refreshIfNeeded()
        assertTrue(client.calls.size > callsAfterFailure)
    }

    @Test
    fun concurrentRequestsMakeOneCallPerBase() = runBlocking {
        account("UAH"); account("USD")
        (1..10).map { async { repo.refreshIfNeeded() } }.awaitAll()
        assertEquals(client.calls.size, client.calls.map { it.first }.toSet().size)
    }

    @Test
    fun unsupportedPairIsNotAskedAgainWithin24Hours() = runBlocking {
        account("UAH"); account("USD")
        client.unsupported = setOf("USD")
        repo.refreshIfNeeded()
        val first = client.calls.size
        now += 60 * 60_000
        repo.refreshIfNeeded()
        assertEquals(first, client.calls.size)
    }

    @Test
    fun badRowsAreDroppedNeverZero() = runBlocking {
        account("USD"); account("PLN")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO exchange_rate VALUES ('USD', 'PLN', 'abc', 'frankfurter', '2026-11-06', 1)",
        )
        assertFalse(repo.observeTable().first().quotes.containsKey(
            CurrencyPair(CurrencyRegistry.get("USD"), CurrencyRegistry.get("PLN")),
        ))
    }

    @Test
    fun foreignKeysAreEnforced() {
        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL("PRAGMA foreign_keys = ON")
        val failed = runCatching {
            sqlite.execSQL("INSERT INTO exchange_rate VALUES ('XXX', 'PLN', '1', 'frankfurter', '2026-11-06', 1)")
        }.isFailure
        assertTrue(failed)
    }

    private suspend fun RateRepository.settingsDisplay(code: String) {
        db.appSettingDao().upsert(com.yehor.finora.data.db.AppSettingEntity("display_currency", code, 0))
    }
}
