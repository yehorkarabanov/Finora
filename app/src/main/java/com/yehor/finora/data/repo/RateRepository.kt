package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.ExchangeRateEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.rates.FetchedQuote
import com.yehor.finora.data.rates.FrankfurterClient
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.rate.CurrencyPair
import com.yehor.finora.domain.rate.RateFreshness
import com.yehor.finora.domain.rate.RateQuote
import com.yehor.finora.domain.rate.RateTable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache and refresh of market rates. Rates are used only to show approximate totals; they never
 * touch a stored amount, transaction or transfer.
 */
@Singleton
class RateRepository(
    private val db: FinoraDatabase,
    private val settings: SettingsRepository,
    private val client: FrankfurterClient,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        db: FinoraDatabase,
        settings: SettingsRepository,
        client: FrankfurterClient,
    ) : this(db, settings, client, System::currentTimeMillis)

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    /** In memory: when the server last definitively answered for a pair (even without a quote). */
    private val answeredAt = HashMap<CurrencyPair, Long>()

    /** In memory: no network attempt before this time after a failed attempt. */
    private var retryNotBefore = 0L

    /** Rows with an unknown currency, a bad rate or a bad date are dropped, never turned into zero. */
    fun observeTable(): Flow<RateTable> = db.rateDao().observeAll().map { rows -> rows.toTable() }

    /** Refreshes whenever the required currency set changes, which includes the first launch. */
    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            combine(db.accountDao().observeAll(), settings.displayCurrency) { accounts, display ->
                requiredCurrencies(accounts.filter { it.status == AccountStatus.ACTIVE }.map { it.currencyCode }, display.currency)
            }
                .distinctUntilChanged()
                .collect { requestRefresh() }
        }
    }

    /** Returns immediately. Does nothing before [start]. */
    fun requestRefresh() {
        scope?.launch { refreshIfNeeded() }
    }

    suspend fun refreshIfNeeded() = mutex.withLock {
        val now = clock()
        if (now < retryNotBefore) return@withLock

        val accounts = db.accountDao().observeAll().first()
        val display = settings.displayCurrency.first().currency
        val currencies = requiredCurrencies(
            accounts.filter { it.status == AccountStatus.ACTIVE }.map { it.currencyCode },
            display,
        )
        val cached = db.rateDao().getAll().toTable()

        val due = currencies.flatMap { x -> currencies.filter { it != x }.map { y -> CurrencyPair(x, y) } }
            .filter { pair ->
                RateFreshness.isDue(cached.quotes[pair], now) && !answeredRecently(pair, now)
            }
        if (due.isEmpty()) return@withLock

        for ((base, pairs) in due.groupBy { it.base }) {
            try {
                val wanted = pairs.map { it.quote.code }.toSet()
                val fetched = client.fetchRates(base.code, wanted)
                val rows = fetched.map { it.toEntity(now) }
                db.withTransaction { db.rateDao().upsertAll(rows) }
                val returned = fetched.map { it.quote }.toSet()
                pairs.filter { it.quote.code !in returned }.forEach { answeredAt[it] = now }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing was written for this group; its existing rows stay. Try again later.
                retryNotBefore = now + RETRY_AFTER_NETWORK_FAILURE_MS
            }
        }
    }

    private fun answeredRecently(pair: CurrencyPair, now: Long): Boolean {
        val at = answeredAt[pair] ?: return false
        return now - at < RateFreshness.REFRESH_AFTER_MS
    }

    private fun requiredCurrencies(activeAccountCodes: List<String>, display: Currency): Set<Currency> =
        (activeAccountCodes.mapNotNull { CurrencyRegistry.findOrNull(it) } + display).toSet()

    private fun FetchedQuote.toEntity(now: Long) = ExchangeRateEntity(
        baseCurrency = base,
        quoteCurrency = quote,
        rate = rate.stripTrailingZeros().toPlainString(),
        source = source,
        quoteDate = quoteDate.toString(),
        fetchedAt = now,
    )

    private fun List<ExchangeRateEntity>.toTable(): RateTable {
        val quotes = HashMap<CurrencyPair, RateQuote>()
        for (row in this) {
            val base = CurrencyRegistry.findOrNull(row.baseCurrency) ?: continue
            val quote = CurrencyRegistry.findOrNull(row.quoteCurrency) ?: continue
            if (base == quote) continue
            val rate = try {
                BigDecimal(row.rate)
            } catch (e: NumberFormatException) {
                continue
            }
            if (rate.signum() <= 0) continue
            val date = try {
                LocalDate.parse(row.quoteDate)
            } catch (e: DateTimeParseException) {
                continue
            }
            quotes[CurrencyPair(base, quote)] = RateQuote(rate, date, row.fetchedAt)
        }
        return RateTable(quotes)
    }

    companion object {
        const val RETRY_AFTER_NETWORK_FAILURE_MS = 15L * 60 * 1000
    }
}
