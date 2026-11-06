# Finora: Currency Design

Date: 2026-11-06
Status: Revision 2, simplified. Draft for user review.
Sub-project: 5 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD sections 10, 11.5, 12.7, 26, 32, 33, NFR-03, NFR-12 to NFR-16, NFR-20, NFR-24). References like "Foundation 5.2" refer to the Foundation design, "Accounts 4.7" to `2026-11-04-finora-accounts-categories-design.md`, "Operations 5.6" to `2026-11-04-finora-operations-tags-design.md`, and "Transfers R5" to `2026-11-05-finora-transfers-design.md`.

## 1. Purpose and scope

This is a personal finance tracker, not a trading application. Exchange rates exist for one reason: to show **approximate** totals in the Display Currency. The user does not manage rates and does not need to know where they come from.

In scope:
1. **Rates from Frankfurter** (`https://api.frankfurter.dev`, v2 API, default blended rates, no API key), fetched directly by one data-layer class.
2. **Direct currency-pair rates** cached in one Room table, `exchange_rate`.
3. **Automatic refresh**, at most once per 24 hours per pair, only for the pairs the app needs. No user control.
4. **`Estimate` and `RateConverter`**: the only way to turn `Money` into another currency. The result is a different type (Foundation 5.2, PRD NFR-14, NFR-15).
5. **Total valuation card** on the Accounts tab (PRD 11.5, 33 CURRENCY).
6. **Converted income, expense and net line** in the Transactions summary bar when the summary spans several currencies.
7. **Approximate (`≈`), outdated and missing-rate markers.**
8. **Schema v2** with one new table and an `@AutoMigration` (section 8).

Not changed by this sub-project:
- **Transactions and transfers.** Creation, editing, deletion, storage and calculations stay exactly as they are. The transfer effective-rate calculation stays exactly as it is and never uses market rates (Transfers R5). No market-rate conversion enters any transaction or transfer code path.
- `Money`, native account balances, and the `account`, `category`, `tag`, `operation`, `ledger_entry`, `operation_tag` and `app_setting` schemas.
- The Display Currency setting, its resolver and its picker. Nothing in Settings changes.

Out of scope, now and in v1:
- Manual rates, overrides, an Exchange Rates screen, a refresh button, an auto-refresh setting.
- Any second provider, a provider interface or registry, inverse-rate fallback, EUR or any other pivot, multi-step routing.
- Historical rates, historical transaction conversion, bid/ask spreads, trading-grade precision.
- WorkManager or any periodic background refresh.
- Dashboard cards and analytics screens (7). They reuse `RateConverter` and `Estimate` from here.
- Sorting or filtering history by converted amount. Amount filter and sort stay single-currency (Operations 5.6).

## 2. Decisions

| Topic | Decision |
|---|---|
| Provider | Frankfurter v2, default blended rates, no key. The only provider in v1. |
| Provider abstraction | **None.** No `RateProvider` interface, no `FrankfurterRateProvider`, no registry, no replacement mechanism. One data-layer class, `FrankfurterClient`, does the HTTP and JSON work. Replacing Frankfurter later means editing that class and the repository that calls it. |
| Domain isolation | `domain` has no import of Frankfurter, HTTP or JSON types. It sees only `CurrencyPair`, `RateQuote`, `RateTable`, `Estimate` and `RateConverter`. |
| Conversion | **Direct pair only.** Same currency returns the original amount unchanged. If the direct pair `A → B` has a cached rate, multiply by it. Otherwise the result is `Unavailable`. No inverse rate, no EUR or other pivot, no routing. |
| Rounding | One exact `BigDecimal` expression, rounded once to the target currency's scale with `HALF_UP` (ties go away from zero). Section 4. |
| Totals | Each amount is converted and rounded once. The rounded estimates are then added with `Math.addExact`. The total equals the sum of the numbers the user can see. |
| Required pairs | Let `S` be the currencies of all **active** accounts plus the Display Currency. The required pairs are every ordered pair `(X, Y)` with `X, Y ∈ S` and `X ≠ Y`. Section 6.3 explains why. |
| Fetching | Only required pairs that are due are requested, grouped by base currency: one `GET /v2/rates?base={X}&quotes={Y1,Y2,...}` per base. Example for `S = {UAH, PLN, USD, EUR}`: four requests, each returning three quotes. |
| Unsupported pair or currency | Frankfurter answers 422 for the whole grouped request if one code is unknown (checked 2026-11-06). The client then retries that group one pair at a time with `GET /v2/rate/{base}/{quote}`. A 404 or 422 on a single pair means "no rate for that pair". It is not an error. |
| Cache | Room table `exchange_rate`, one row per directed pair, latest value only. |
| Refresh rule | A pair is **due** when it has no cached row or its row's `fetched_at` is 24 hours old or more. Refresh runs automatically while the app is running (section 6.4). |
| Offline | Cached rates are used immediately and always. A failed refresh keeps every existing row and never blocks the UI or any financial operation. |
| Outdated | A rate is **outdated** when `now − fetched_at > 72 hours`. It is still used, and the estimate is marked outdated. `fetched_at` is the time this device last got the rate. |
| Missing rate | Never treated as zero. The amount is excluded from the total and its currency is named. If nothing converts the card shows "Unavailable". |
| Rate representation | `BigDecimal` in domain and data. Stored as a canonical plain decimal string in a `TEXT` column. JSON numbers are read from their text (`JsonPrimitive.content`), never as `Double`. No `Float` or `Double` anywhere in the money or rate path. |
| Source identifier | `"frankfurter"`, stored in `exchange_rate.source`. |
| Schema | **Version 2**, one new table, `@AutoMigration(from = 1, to = 2)`. `1.json` is not edited. |
| Network | HTTPS, `HttpURLConnection`, 10 s connect and 15 s read timeout, 1 MiB response cap. No new library. JSON parsing uses the `kotlinx-serialization-json` tree API already in the build. Only the `INTERNET` permission is added. |

## 3. Changes to existing code

| Change | Reason |
|---|---|
| `FinoraDatabase`: `VERSION = 2`, add `ExchangeRateEntity`, add `autoMigrations = [AutoMigration(from = 1, to = 2)]`, add `rateDao()`. `ALL_MIGRATIONS` stays empty. | Section 8. |
| Commit generated `app/schemas/.../2.json` and a pinned `src/test/resources/schema-v2.expected.json`. `1.json` and `schema-v1.expected.json` are not touched. | Frozen v1 baseline for the migration test. |
| `SchemaUnchangedTest`: keeps comparing `1.json` with `schema-v1.expected.json`. A second test does the same for v2. | The v1 file never changes again. |
| `MigrationInfrastructureTest`: `productionV1CanBeCreatedAndValidated` creates v1 and validates the migration to `FinoraDatabase.VERSION`. The chain test asserts `VERSION == 2` and that no migration targets a higher version. | The Foundation assertions were written for version 1. |
| `ScratchDatabase` is **not** changed. It keeps its own copy of the v1 entities and its own `schemas-test` folder. | It proves the infrastructure and is independent of the production chain. |
| `AndroidManifest.xml`: add `<uses-permission android:name="android.permission.INTERNET" />`. Cleartext stays disabled. | Rate download. |
| `FinoraApp`: inject `RateRepository` and call `rates.start(appScope)` in `onCreate`, where `appScope` is a private `CoroutineScope(SupervisorJob() + Dispatchers.Default)` field. | Section 6.4. |
| `MainActivity.onStart`: call `rates.requestRefresh()`. | Re-check staleness when the app returns to the foreground. |
| `InvariantChecker`: add the rate checks of section 7. | Import validation (8) reuses them. |
| `AccountsViewModel` and `AccountsScreen`: add the valuation card above the account list. | Section 5.1. |
| `TransactionsViewModel` and `TransactionsScreen.SummaryBar`: add a converted line for multi-currency summaries. | Section 5.2. |
| `strings.xml`: strings for the card and the summary line. | Existing convention. |
| `Money`, `SettingsRepository`, `SettingsViewModel`, `SettingsScreen`, `FinoraNavHost`, `Routes`, `TransferRepository`, `OperationRepository`, `TransactionRepository` write paths: **unchanged**. | Sections 1 and 2. |

## 4. Domain (pure Kotlin, JVM-testable)

Package `domain/rate`, plus `Estimate` in `domain/money`. No Android, Room, HTTP or JSON imports.

```kotlin
data class CurrencyPair(val base: Currency, val quote: Currency)   // base ≠ quote

data class RateQuote(
    val rate: BigDecimal,     // 1 unit of base = rate units of quote, always > 0
    val quoteDate: LocalDate, // as published; informational
    val fetchedAt: Long,      // epoch ms, when this device got it
)

data class RateTable(val quotes: Map<CurrencyPair, RateQuote>)

object RateFreshness {
    const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000
    const val OUTDATED_AFTER_MS = 72L * 60 * 60 * 1000
    fun isDue(quote: RateQuote?, now: Long): Boolean       // null, or age >= 24 h
    fun isOutdated(quote: RateQuote, now: Long): Boolean   // age > 72 h
}

/** A converted value. Not a [Money]: it cannot be passed where Money is required. */
data class Estimate(
    val amountMinor: Long,
    val currency: Currency,
    val basis: EstimateBasis,
) {
    operator fun plus(other: Estimate): Estimate   // same currency required, Math.addExact, merges basis
    fun format(): String                            // "≈ 3912.60 zł" when basis.usesConversion
}

data class EstimateBasis(val usesConversion: Boolean, val hasOutdatedRate: Boolean) // merge = field-wise OR

sealed interface ConversionResult {
    data class Converted(val estimate: Estimate) : ConversionResult
    data class Unavailable(val from: Currency, val to: Currency) : ConversionResult
}

data class Valuation(
    val total: Estimate?,              // null when nothing converted, or on overflow
    val excluded: Set<Currency>,       // source currencies with no direct rate
    val overflowed: Boolean,
    val oldestFetchedAt: Long?,        // over the rates actually used; for "last updated"
)

class RateConverter(private val table: RateTable, private val now: Long) {
    fun convert(money: Money, to: Currency): ConversionResult
    fun value(amounts: List<Money>, to: Currency): Valuation
}
```

Conversion, in this order:
1. `money.currency == to`: return `Converted` with the same minor amount, `usesConversion = false`, no table lookup.
2. Direct pair `(money.currency, to)` in the table: apply it.
3. Otherwise `Unavailable(from, to)`.

There is no step for inverse rates or a pivot currency.

Formula. With `rate` as `BigDecimal`, `scaleFrom` and `scaleTo` the currencies' minor-unit scales:

```
result = BigDecimal(amountMinor).multiply(rate)
             .movePointRight(scaleTo - scaleFrom)
             .setScale(0, RoundingMode.HALF_UP)
             .longValueExact()
```

It is exact until the single final `setScale`. There is no division, so no precision parameter is needed. `longValueExact` failing becomes `MoneyOverflowException`.

Rules:
- `value` converts every amount, adds the rounded estimates with `Math.addExact`, and collects the currencies of `Unavailable` results into `excluded`. Overflow in a conversion or in the sum sets `overflowed = true` and `total = null`. The failing amount is never replaced by zero.
- `value(emptyList(), to)` returns a zero total with no flags.
- `Estimate` has no `toMoney()` and no constructor accepting `Money`. A test enforces that no function in `domain` turns an `Estimate` into a `Money`.

## 5. UI

There is no new screen, route, dialog, button or setting.

### 5.1 Accounts tab: total valuation card

Shown above the active accounts when there is at least one active account. Only active accounts are included (PRD 11.4, 11.5). Per-account rows keep their native balance and nothing else.

| State | Card content |
|---|---|
| Every active account is in the Display Currency | "Total balance", plain amount, no `≈`. |
| At least one amount was converted | "Total valuation (estimate)", amount shown as `≈ 12 345.67 zł`. |
| Some rates used are outdated | Warning tone and the line "Rates may be outdated. Last updated 3 Nov 2026" (oldest `fetched_at` among the rates used). |
| Some amounts could not be converted | The partial total plus the line "Not included: USD, THB (no rate)". |
| Nothing could be converted | "Unavailable" instead of a number, plus the "Not included" line. |
| An account balance overflows | That account is listed in "Not included", like a missing rate. |

The card is not tappable. It does not navigate anywhere.

### 5.2 Transactions summary bar

When the summary holds more than one currency, one extra line shows income, expense and net converted to the Display Currency, each with `≈` and the same outdated and "Not included" markers. A single-currency summary stays native, even if that currency differs from the Display Currency. Transfers are already excluded from the summary (Transfers 7.4).

Currencies that belong only to archived accounts are not in the required set, so they usually appear under "Not included" (section 13).

### 5.3 Settings

Unchanged. Display Currency stays the only currency-related setting.

## 6. Data layer

### 6.1 `exchange_rate` table

| Column | Type | Notes |
|---|---|---|
| `base_currency` | TEXT | FK → `currency(code)` |
| `quote_currency` | TEXT | FK → `currency(code)` |
| `rate` | TEXT NOT NULL | canonical plain decimal, > 0 |
| `source` | TEXT NOT NULL | `"frankfurter"` |
| `quote_date` | TEXT NOT NULL | ISO `yyyy-MM-dd`, as published |
| `fetched_at` | INTEGER NOT NULL | epoch ms |

Primary key `(base_currency, quote_currency)`. Index on `quote_currency` for the foreign key. The primary key serves lookups by base. Any pair of registry currencies can be stored, so the table needs no change when a new currency is used. Room cannot declare CHECK constraints, so positivity and `base ≠ quote` are enforced in the repository and verified by the invariant checker (section 7).

`RateDao`: `observeAll(): Flow<List<ExchangeRateEntity>>`, `getAll()`, `upsertAll(rows)` (`INSERT OR REPLACE`).

### 6.2 `FrankfurterClient` (`data/rates`)

A plain class, the only place that knows the Frankfurter URL, HTTP and JSON. It is declared `open` so that tests can subclass it. That is a test seam only, not a provider abstraction.

```kotlin
/** Normalized quote returned by the client. Lives in the data layer. */
data class FetchedQuote(
    val base: String, val quote: String,
    val rate: BigDecimal, val quoteDate: LocalDate, val source: String,
)

open class FrankfurterClient(private val baseUrl: String = "https://api.frankfurter.dev") {
    open suspend fun fetchRates(base: String, quotes: Set<String>): List<FetchedQuote>
}
```

Behaviour of `fetchRates`:
1. `GET {baseUrl}/v2/rates?base={base}&quotes={comma separated}` on `Dispatchers.IO`.
2. 200: parse the body. The expected shape is a JSON array of `{"date","base","quote","rate"}` objects, for example `[{"date":"2026-11-06","base":"UAH","quote":"PLN","rate":0.086029}]`.
3. 404 or 422: retry each quote with `GET {baseUrl}/v2/rate/{base}/{quote}` (response is a single object of the same shape). A 404 or 422 on a single pair skips that pair. Other failures propagate.
4. A requested pair that the response does not contain is simply absent from the result.

Parsing rules (any violation rejects the whole response, because a half-trusted answer is worse than a stale cache):
- The top level is an array (or an object for the single-pair call).
- Every item has `base` equal to the requested base, `quote` among the requested quotes, a valid ISO `date`, and a `rate` that is a JSON number or numeric string parsing to a positive `BigDecimal` from its text.
- A duplicate pair in one response is rejected.
- Rates are normalized with `stripTrailingZeros().toPlainString()`.

Failures are thrown as `RateFetchException(reason)` with `reason` in `NO_NETWORK`, `TIMEOUT`, `HTTP_ERROR`, `BAD_PAYLOAD`. Mapping: `UnknownHostException` and `ConnectException` give `NO_NETWORK`, `SocketTimeoutException` gives `TIMEOUT`, any other non-200 status (after the 404/422 handling above) gives `HTTP_ERROR`, a response over 1 MiB or invalid content gives `BAD_PAYLOAD`. Reasons are used for tests and logging only. No UI shows them.

The request contains only currency codes. It carries no amounts, account names or identifiers (NFR-03, section 10).

### 6.3 Why every ordered pair in `S`

Valuation alone needs `X → Display` for each account currency `X`. With only that, every request would contain a single quote and grouping by base would do nothing. Requesting all pairs among `S` costs at most `|S|` requests (typically 3 to 5), keeps one request per base, and has two benefits: switching the Display Currency to another currency the user already holds needs no new download, and later sub-projects can convert between any two held currencies. No fallback path is involved. Each pair is a direct Frankfurter quote.

### 6.4 `RateRepository` (`data/repo`, `@Singleton`)

Depends on `RateDao`, `AccountDao`, `SettingsRepository`, `FrankfurterClient` and an injectable clock `() -> Long`.

| Member | Behaviour |
|---|---|
| `observeTable(): Flow<RateTable>` | Maps `RateDao.observeAll()` to a `RateTable`. Rows with an unknown currency, a non-positive rate or an unparseable rate or date are dropped, never turned into zero. |
| `start(scope)` | Stores `scope` and launches a collector of the required set `S`: `combine(accounts.observeAll(), settings.displayCurrency)` mapped to the currency set, `distinctUntilChanged()`, calling `requestRefresh()` on each change. This covers the first launch, a new account and a Display Currency change. |
| `requestRefresh()` | Launches `refreshIfNeeded()` in the stored scope. Returns immediately. |
| `refreshIfNeeded()` | Section below. Suspends only inside the repository's own scope. |

`refreshIfNeeded()`, under a `Mutex`:
1. Compute the required pairs from `S` (section 2).
2. A pair is **due** if `RateFreshness.isDue(cachedQuote, now)` and it was not answered by the server during the last 24 hours in this process (an in-memory `answeredAt` map). The second condition stops a permanently unsupported pair from being asked again on every trigger.
3. If a previous attempt failed with a network-level error, do nothing until `retryNotBefore` (15 minutes after that failure, in memory).
4. Group the due pairs by base. For each base call `client.fetchRates(base, quotes)`.
5. A group that succeeds is written in **one** `db.withTransaction` with `fetched_at = now`. Pairs the server definitively did not return are recorded in `answeredAt`.
6. A group that throws writes nothing, keeps its existing rows, sets `retryNotBefore`, and does not stop the other groups.
7. If nothing is due, make no network call.

Because the due check runs inside the lock, concurrent callers share one round trip: the second caller waits, finds nothing due, and returns.

The collector fires on set changes and `MainActivity.onStart` covers a long-running app coming back to the foreground. There is no timer and no background worker. A refresh therefore happens only while the app is running (PRD NFR-20: it never blocks, it runs on `Dispatchers.IO`).

## 7. Invariant checker additions

Added to `InvariantChecker.checks`. String tests only, no floating point.

| Check | Assertion |
|---|---|
| `exchange_rate same currency` | Rows where `base_currency = quote_currency`. |
| `exchange_rate invalid rate` | Rows where `rate GLOB '*[^0-9.]*'`, or `rate GLOB '*.*.*'`, or `rate NOT GLOB '*[1-9]*'`. |
| `exchange_rate invalid quote date` | Rows where `quote_date NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'`. |
| `exchange_rate blank source` | Rows where `trim(source) = ''`. |

## 8. Schema and migration

- `FinoraDatabase.VERSION` becomes 2 with `@AutoMigration(from = 1, to = 2)`. The migration only creates `exchange_rate`. No existing table is touched.
- `app/schemas/.../1.json` and `schema-v1.expected.json` stay byte-identical. `2.json` is generated and committed with `schema-v2.expected.json` as its pinned copy.
- `fallbackToDestructiveMigration` stays forbidden (`NoDestructiveMigrationTest` unchanged).
- Fresh installs create v2 directly. `FinoraDatabaseCallback.onCreate` seeds `currency` as before and the new table starts empty.
- The schema contains no manual-rate table and no related column. A test asserts that the only rate table is `exchange_rate`.

Import and export (8): the `.db` file carries `exchange_rate`. Import validation runs the section 7 checks. A v1 file migrates through the chain and arrives with an empty cache that refills automatically on first use. A restored cache older than 72 hours is simply marked outdated until the next refresh.

## 9. Business rules

| # | Rule | Source |
|---|---|---|
| R1 | Rates and Display Currency never change a stored amount, balance, transaction or transfer. Only `RateConverter` reads them. | PRD 10.2, 26.3, 26.4, 32.7 |
| R2 | A converted amount is always an `Estimate`. Native balances stay `Money` in their own currency. | PRD 10.6, NFR-14, NFR-15 |
| R3 | A missing rate is never zero. The amount is excluded and its currency named. | PRD 10.5, 32.5 |
| R4 | An outdated rate is used and visibly marked. | PRD 10.5, 32.6 |
| R5 | Conversion uses a direct cached pair only. There is no inverse, pivot or routing. | Product decision |
| R6 | One rounding per conversion, `HALF_UP`, in the target currency's scale. Totals add already-rounded estimates. | NFR-16 |
| R7 | A failed refresh never deletes or overwrites rows. A group is written in one transaction or not at all. | PRD 10.4, NFR-06 |
| R8 | Refresh never blocks the UI or any financial operation, and works with no network. | PRD 10.4, NFR-20, Acceptance CURRENCY |
| R9 | Only active accounts are included in the total valuation. | PRD 11.4, 11.5 |
| R10 | Only the latest rate per pair exists. Converted figures use the current rate and are labelled approximate. | PRD 12.7 |
| R11 | Transfer amounts and the transfer effective-rate display are never touched by market rates. | PRD 8.4, 28.5, Transfers R5 |

## 10. Privacy and security

- Only `https://api.frankfurter.dev` is contacted. A request contains currency codes and nothing else. This does reveal which currencies the user holds, which is the minimum needed to ask for rates. Frankfurter states that it does not collect or log personal data, IP addresses or request URLs. PRD NFR-03 explicitly allows requests for exchange rates.
- Cleartext traffic stays disabled. There is no certificate pinning: the data is public and affects presentation only (R1). A wrong rate could mislead an estimate, but never a stored amount, and every converted figure is marked `≈`.
- Response size, structure and rate values are validated before anything is written (section 6.2).
- The cache stays excluded from cloud backup and device transfer with the rest of the database (Foundation 8).
- Frankfurter permits caching rates in a local database for its default blended feed.

## 11. Testing

JVM unit tests. Fixtures use the Frankfurter responses captured on 2026-11-06 as **test data**, not live calls.

`RateConverterTest`:
- **Direct:** USD → PLN at 3.9126: 1000.00 USD = 3912.60 PLN.
- **Direct, scale 0 to 2:** JPY → PLN at 0.024721: 10000 JPY = 247.21 PLN.
- **Rounding at a tie:** UAH → PLN at 0.086029: 5000.00 UAH is 43014.5 minor units and rounds to 430.15 PLN. The same amount negative rounds to −430.15 PLN (away from zero). A value just below the tie rounds down.
- **Scales:** 2 → 0 (a 100.00 amount at rate 3.5 gives 350 in a scale-0 currency), 0 → 2, 2 → 3 (JPY → BHD at 0.0023757: 10000 JPY = 23.757 BHD) and 2 → 4 (CLF).
- **Single rounding:** a case whose result would differ if the amount were rounded twice.
- **Same currency:** returns the original amount, `usesConversion = false`, and works with an empty table.
- **Inverse not used:** with only `PLN → USD` cached, `USD → PLN` is `Unavailable`.
- **Pivot not used:** with only `USD → EUR` and `EUR → PLN` cached, `USD → PLN` is `Unavailable`.
- **Missing pair:** `Unavailable` names the source currency. It is never zero.
- **Overflow:** a conversion whose result exceeds `Long` throws `MoneyOverflowException` from `convert`. `value` returns `overflowed = true` and no total. An overflow while summing does the same.
- **Outdated flag:** a result using a rate older than 72 hours has `hasOutdatedRate = true`.

`ValuationTest`:
- All amounts in the target currency: no `usesConversion`.
- Mixed currencies: total equals the sum of the individually rounded estimates.
- Some currencies missing: partial total, `excluded` lists exactly those currencies.
- Nothing convertible: `total = null`, all currencies excluded.
- Empty list: zero total.
- `oldestFetchedAt` is the minimum over the rates actually used.

`RateFreshnessTest`: exactly 24 h is due and 24 h minus 1 ms is not; exactly 72 h is not outdated and 72 h plus 1 ms is; a missing quote is due.

`EstimateTest`: `plus` merges the flags, rejects different currencies, and `format` shows `≈` only when a conversion was used. A reflection check that `Estimate` is not a `Money` and that no `domain` function converts an `Estimate` into `Money`.

Static check, extending the Foundation money check: `domain/money`, `domain/rate` and `data/rates` contain no `Float`, `Double`, `.toDouble()` or `.double`. A second check asserts that `domain` has no import from `data.rates` and contains no occurrence of `frankfurter`.

`FrankfurterClientTest`, against a JDK `com.sun.net.httpserver.HttpServer` on localhost (the constructor takes the base URL), plus parser tests on captured payloads:
- **Valid direct pair:** `/v2/rate/USD/PLN` object parsed to one `FetchedQuote` with the exact `BigDecimal` and date.
- **Multiple quotes in one response:** the UAH group returns EUR, PLN and USD. The values round-trip exactly (`0.019557`, `0.086029`, `0.021988`). Quote dates may differ per pair (`2026-11-05` and `2026-11-06` in the same response).
- **Missing pair:** a requested quote absent from the response is simply absent from the result.
- **Request shape:** base and quotes in the query, `Accept` accepted, only `/v2/` paths used.
- **Unknown currency in a group:** the server answers 422 for the group. The client retries pair by pair and returns the supported ones. A 404 or 422 single pair is skipped.
- **Malformed response:** not JSON, not an array, an item missing `rate`, a wrong `base`, a quote that was not requested, a bad date, a duplicate pair. Each gives `BAD_PAYLOAD`.
- **Invalid rate:** `0`, negative, `null`, non-numeric string, `NaN`. Each gives `BAD_PAYLOAD`.
- **Oversized body** over 1 MiB gives `BAD_PAYLOAD`.
- **HTTP failure:** 500 and 503 give `HTTP_ERROR`. A server that accepts and never answers gives `TIMEOUT`. A refused connection gives `NO_NETWORK`.

Instrumented Room tests (`RateRepositoryTest`, `MigrationV2Test`, `InvariantCheckerRateTest`), using a test subclass of `FrankfurterClient` that records calls and a controllable clock:
- **Successful refresh:** rows written, one `fetched_at` per refresh, `source = "frankfurter"`. A later refresh replaces them.
- **Failed refresh preserves the cache:** the client throws, existing rows and their `fetched_at` are unchanged and `observeTable` still returns them.
- **Offline use:** with the client always failing, the table is served from the cache and nothing throws to the caller.
- **Partial failure:** one base succeeds and another throws. The first is stored, the second keeps its old rows, and the next trigger within 15 minutes does not retry.
- **Not more than once per 24 hours:** a second `refreshIfNeeded` at 23 h 59 min makes no client call. At 24 h it refreshes.
- **Concurrent requests:** many simultaneous `refreshIfNeeded` calls make exactly one client call per base.
- **Only required pairs:** with active accounts in UAH and USD and Display Currency PLN, the client is asked for `UAH → {PLN, USD}`, `USD → {UAH, PLN}` and `PLN → {UAH, USD}` and for nothing else. An archived account's currency is not requested. Fresh pairs are not requested again.
- **Set changes:** adding an account in a new currency, or changing the Display Currency to a new one, requests only the newly missing pairs.
- **Unsupported pair:** a pair the server never returns is not asked again for 24 hours in the same process.
- **Atomic group:** an exception thrown while writing a group leaves that group's old rows intact.
- **Outdated detection:** with the clock at 72 h plus 1 ms after `fetched_at`, `RateConverter` marks estimates outdated. Within 72 h it does not.
- **Bad rows are dropped:** a row with an unparseable rate or unknown currency (inserted with raw SQL) is absent from `observeTable`, never zero.
- **Foreign keys** on both currency columns are enforced.
- **Transactions and transfers untouched:** creating, editing and deleting a transaction and a transfer produce the same balances and rows as before, and none of those paths reads `exchange_rate`.

Migration tests:
- **`migration1To2KeepsData`:** seed accounts, categories, tags, operations, ledger entries, transfers, tags on operations and settings in v1. Migrate, validate against `2.json`, and compare every existing table row for row. `exchange_rate` exists and is empty.
- **Cache works after migration:** insert and read rate rows on the migrated database.
- **No manual-rate artifacts:** the migrated schema has no `manual_rate` table, and `2.json` contains exactly one rate table.
- **Full chain:** migrate from 1 to the latest version.
- `InvariantChecker` is clean on normal data and flags a raw-SQL violation of each check in section 7.

UI and ViewModel tests (JVM, fake repositories): the valuation card for each state in 5.1 (native only, converted, outdated, partial, unavailable, overflow), and the Transactions summary line for single-currency and multi-currency summaries.

Manual checklist:
1. Fresh install in airplane mode with a PLN and a USD account. The card says "Unavailable" with "Not included: USD (no rate)". Adding and editing transactions and transfers works normally.
2. Turn the network on and bring the app to the foreground. Within a few seconds the card shows a `≈` total with no warning.
3. Airplane mode again and restart the app. The last total is still shown.
4. Set Display Currency to EUR. Totals change and no balance, transaction or transfer amount changes.
5. Add an account in a new currency. Only the missing pairs are downloaded (check logcat).
6. Set the device clock four days forward. The card shows the outdated warning.
7. Filter Transactions by something that spans PLN and USD. The summary bar shows native lines plus a converted `≈` line.
8. Open a cross-currency transfer. Its effective-rate banner is unchanged.

## 12. Done when

1. All PRD 33 CURRENCY criteria hold:
   - Several currencies are supported.
   - Display Currency can be chosen.
   - Rates are used to display a total.
   - Updating rates does not change real balances.
   - Cached rates are used.
   - No internet never blocks a financial operation.
2. The Accounts tab shows a total with correct `≈`, outdated and not-included markers.
3. There is no Exchange Rates screen, route, manual rate, refresh control or refresh setting anywhere in the app.
4. Database is at version 2 with committed `2.json`, an untouched `1.json`, and passing migration tests.
5. Transaction and transfer code and schemas are unchanged.
6. All tests in section 11 pass, the invariant checker is clean, and the build has no new lint warnings. The manifest adds only `INTERNET`.

## 13. Risks

- **Frankfurter outage or API change.** The parser rejects anything unexpected and the cache stays. After 72 hours estimates are marked outdated. Replacing the provider means editing `FrankfurterClient` and `RateRepository`. That is a deliberate trade-off against building an abstraction now.
- **Unsupported currencies.** Some registry currencies are not covered by Frankfurter, and one unknown code makes a grouped request fail with 422. The per-pair retry limits the damage to the unsupported pair. That currency's amounts show under "Not included" until a later change.
- **Direct pairs only.** Frankfurter computes each pair itself. If a pair is missing, no value is guessed from other pairs. The user sees "Not included" rather than a derived number. This is the intended behaviour.
- **Blended rates are not bank rates.** The last decimals can shift as new data arrives, and some pairs are a day behind others. Every converted figure is marked `≈` and nothing implies a transaction price.
- **Network request reveals held currencies.** Section 10. Accepted because it is the minimum needed and Frankfurter keeps no request logs.
- **Archived-account currencies are not requested.** Required currencies come from active accounts and the Display Currency. A history summary containing only a currency from an archived account may therefore show it as "Not included". Including all accounts' currencies would be a one-line change if you prefer it.
- **First-launch flicker.** Before the first download completes the card shows "Unavailable" and then updates. No loading state is added, to keep the UI small.
- **Outdated check is evaluated on emission.** A screen left open across the 72-hour boundary updates on its next emission. Acceptable for a threshold measured in days.
- **`fetched_at` rather than quote date drives "outdated".** If Frankfurter keeps returning an old quote date for a pair, the device still counts it as fresh. The data is daily and blended, so this is accepted for v1.

## 14. Assumptions to confirm

These details were not specified and are set to the simplest reasonable choice:
1. **Required pairs are every ordered pair in `S`** (section 6.3), not only `X → Display Currency`. It is what makes grouping by base meaningful and avoids downloads when the Display Currency changes.
2. **Network-failure retry after 15 minutes** (in memory). Without it an offline launch would never retry while the app stays open, and with a naive retry it would hammer the API. It is one constant.
3. **Per-pair retry after a 422 on a group** (section 6.2). It is needed because one unsupported code otherwise blocks every valid pair in its group.
4. **`FrankfurterClient` is an `open` class** so repository tests can substitute it. There is no interface.
5. **Refresh starts from `FinoraApp.onCreate`** through `rates.start(appScope)`, plus `MainActivity.onStart`. If you prefer ViewModel-triggered refresh, only the call sites move.

## 15. Hand-offs

- **Sub-project 6 (Recurring):** no dependency. Generated transactions stay in the account currency.
- **Sub-project 7 (Dashboard and analytics):** use `RateConverter.value` for the total valuation and for converted income, expense and net cash flow, and carry `EstimateBasis` into every label. Converted history uses today's rate and must say so (PRD 12.7). Pairs outside `S` stay unavailable unless that sub-project widens the required set. Converted amount sorting or filtering, if wanted, belongs there.
- **Sub-project 8 (Import / Export):** the `.db` file carries `exchange_rate`. Import validation runs the section 7 checks. A v1 file migrates with an empty cache that refills on first use.
