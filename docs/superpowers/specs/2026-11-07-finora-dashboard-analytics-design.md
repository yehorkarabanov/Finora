# Finora: Dashboard and Analytics Design

Date: 2026-11-07
Status: Draft for user review
Sub-project: 7 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD sections 6.5, 7.5, 7.6, 11, 12, 15, 33 DASHBOARD & ANALYTICS, 33 TAGS, 33 CATEGORIES, NFR-14 to NFR-16, NFR-18, NFR-19). References like "Foundation 6.4" refer to the Foundation design, "Accounts 5.5" to `2026-11-04-finora-accounts-categories-design.md`, "Operations 6.6" to `2026-11-04-finora-operations-tags-design.md`, "Transfers R2" to `2026-11-05-finora-transfers-design.md`, "Currency 4" to `2026-11-06-finora-currency-design.md` and "Recurring 4.1" to `2026-11-06-finora-recurring-design.md`.

## 1. Scope

In scope:
1. **Dashboard** (replaces the placeholder on the Dashboard tab): total valuation, native account balances, income, expenses and net cash flow for the selected period, expense distribution by category, recent transactions, upcoming recurring transactions (PRD 11).
2. **Period filter** shared by Dashboard and Statistics: Today, This week, This month, This year, Custom range (PRD 11.3).
3. **Statistics** (replaces the placeholder on the Statistics tab), four sections:
   - **Expenses** and **Income**: totals, by category with drill-down to individual transactions, by account, by tag, monthly and yearly trends, comparison with the previous period (PRD 12.2 to 12.4).
   - **Tags**: tag analytics with Match ANY / ALL and account, category, transaction type and date filters (PRD 7.5, 7.6, 12.5).
   - **Accounts**: one statistics view per account (PRD 12.6).
4. **Display Currency presentation** of every aggregate through `RateConverter` and `Estimate`, with `≈`, outdated and "Not included" markers, plus an exact **native-currency view** (PRD 12.7, NFR-14).
5. Hand-offs from analytics rows into the existing Transactions history through a richer filter preset (section 8).

Not changed by this sub-project:
- Transactions, transfers, recurring schedules, accounts, categories, tags, settings: no write path is added or modified. Everything here is read-only.
- The database. **Version stays 3, no schema change, no migration** (section 9).
- `Money`, `Estimate`, `RateConverter`, `RateTable`, `CategoryRollup`, `CategoryTree`, `TransactionRow`, `ValuationCardLogic`.

Out of scope, now and in v1:
- Budgets (PRD 13) and templates (PRD 14), advanced reports (PRD Part IX, P2).
- Total-balance-over-time across accounts, forecasts, savings goals, net worth history. Balance over time exists per account only (PRD 12.6).
- Historical exchange rates. Converted figures use today's rates (Currency 1, PRD 12.7).
- Exporting charts or reports. CSV export belongs to sub-project 8 and reuses `TransactionFilter`.
- Home screen widgets, notifications, a chart library, any new permission or network call.
- Persisting the selected period, scope or tab across process death (section 13).
- A setting for the first day of the week (PRD 17.1). The locale decides, and the calculator takes the day as a parameter so the setting can plug in later.

Foundation to Recurring already ship what this sub-project reads: `operation`, `ledger_entry`, `operation_tag`, `category`, `tag`, `account`, `exchange_rate`, `recurring_*`, `TransactionFilter`, `TransactionQuery`, `TransactionRepository.observeRecent`, `CategoryRollup`, `RateRepository.observeTable`, `SettingsRepository.displayCurrency`, `ValuationCardLogic`, `RecurringRepository.observeSchedules` and `RecurrenceCalculator.between`.

## 2. Decisions

| Topic | Decision |
|---|---|
| Schema | **None.** Version stays 3. Aggregates run over existing indexes. A plan-time `EXPLAIN QUERY PLAN` and 50,000-row test gate this. If they fail, the fallback is one new index through a real `@AutoMigration(3, 4)` (section 13). |
| Charts | Drawn with Compose `Canvas` in `ui/common/charts`. **No chart library.** Every chart has a text list or table next to it that carries the same numbers, and a content description. |
| Period | One shared `PeriodSelection` (`@Singleton`, in memory) used by Dashboard, Statistics and Account statistics, so a period chosen on one screen applies on the others. Default is This month. Not persisted. |
| Period boundaries | Half-open `[from, toExclusive)` in epoch milliseconds, computed in the **device time zone at query time** (Foundation 6). A week starts on `WeekFields.of(Locale.getDefault()).firstDayOfWeek`. |
| Measures | `EXPENSE`, `INCOME`, `TRANSFER`. Income, expense and net **never** include transfers or opening balances (PRD 11.5, 28.2, 28.9, Foundation 6.3). Transfers appear only in the dedicated "Transferred" figure (section 5, R2). |
| Net cash flow | Total income minus total expenses (PRD 11.5). |
| Currency scope | `Display(currency)`: every amount converted to the Display Currency and shown as `Estimate`. `Native(currency)`: only amounts of accounts in that currency, exact `Money`-based numbers, no `≈`, no rates read. Default is `Display`. Not persisted. |
| Native scope implementation | A native scope is the filter restricted to the accounts of that currency (`accountIds ∩ accounts(currency)`). Because an entry's currency equals its account's (Foundation 5.3), no extra currency predicate is needed, and every drill-down into Transactions shows exactly the rows that were counted. |
| Conversion unit | The native sum of one group, for example `(category, currency)`, is converted and rounded **once** into an `Estimate`. Estimates are then added with `Math.addExact`. A parent category's total equals the sum of the numbers shown for its children and its direct amount, to the last digit (PRD 6.5). Within one screen, every total equals the sum of its visible parts. |
| Required rates | Widened: the required currency set `S` (Currency 2) becomes the currencies of **all** accounts, active and archived, plus the Display Currency. Historical reports include archived accounts (PRD 11.4), and an unconverted archived currency would otherwise always appear under "Not included". |
| Category exact filter | `TransactionFilter` gets `categoryExact: Boolean = false`. When true, the selected categories are not expanded to their descendants. It lets the "direct" row of a parent open exactly the transactions that make up its number. |
| Tag overlap | A transaction with two tags counts toward both tags. Tag rows therefore do not sum to the period total, and the screen says so. |
| Comparison | Statistics can compare the displayed period with the **previous full period** of the same kind (for Custom, the preceding range of equal length). Both use the same current rates. |
| Trends | Monthly (12 months) and Yearly (5 years), ending at the displayed period's end. Tag analytics and Account statistics use an automatic bucket size inside the period (section 4.4). |
| Transfer amount in analytics | "Transferred" is the sum of the **sent** side (negative entries) of matching transfers. The received side is not added, so a 100 PLN to 95 PLN transfer counts 100. |
| Recent transactions | The latest 5 across all types except opening balances, regardless of period. |
| Upcoming recurring | Next occurrences of `ACTIVE` schedules within the next 30 days, at most 5 (section 7.1). |
| Live updates | All figures are `Flow`s that re-emit when `operation`, `ledger_entry`, `operation_tag`, `category`, `account`, `exchange_rate` or settings change. A day ticker re-resolves the period at local midnight. |

## 3. Changes to existing code

| Change | Reason |
|---|---|
| `RateRepository.requiredCurrencies` (used by `start` and `refreshIfNeeded`): include currencies of **all** accounts. The Currency test "an archived account's currency is not requested" is replaced by "an archived account's currency is requested". | Section 2, "Required rates". The Accounts tab valuation card is unaffected, because it still sums active accounts only. |
| `TransactionFilter`: add `categoryExact: Boolean = false`, counted in `isActive`. `TransactionRepository.categoryClosure` returns `filter.categoryIds` unchanged when it is true. `FilterScope.normalize` and `pruneMissing` carry it. The filter chip reads "<name> (direct only)". | Exact drill-down (section 7.3). |
| `TransactionQuery`: `buildWhereClauses` becomes `internal`, so `AnalyticsQuery` reuses every predicate of the filter. Its output for all existing calls is unchanged. | One definition of "what a filter matches". |
| New `FilterCodec` (`domain/operation`). `Routes.TRANSACTIONS_PRESET` gains `&filter={filter}` (default empty). `TransactionsViewModel` decodes it when present, over the existing `accountId`, `tagId`, `categoryId`, `types` presets. | Analytics rows open Transactions with period, type, accounts, categories, tags and match mode set (section 8). |
| `TransactionFilterSheet` gets a `sections: Set<FilterSection>` parameter (`TYPE`, `ACCOUNT`, `CATEGORY`, `TAG`, `DATE`, `AMOUNT`, `TEXT`), default all. Analytics passes everything except `AMOUNT` and `TEXT`. | Tag analytics reuses the filter sheet instead of a second one. |
| `Routes`: `STATISTICS` stays the tab route and gains `STATISTICS_PRESET = "statistics?tab={tab}&tagId={tagId}"` and `ACCOUNT_ANALYTICS = "statistics/accounts/{accountId}"`. `FinoraNavHost` replaces the two placeholders. `isRouteUnder` already highlights the Statistics tab for both. | Section 8. |
| `AccountDetailScreen`: a "Statistics" action that opens `statistics/accounts/{id}`. `TagsScreen`: row menu action "Analyze" that opens `statistics?tab=TAGS&tagId={id}`. | PRD 7.5, 12.6 reachability. |
| `ValuationCard` is reused on the Dashboard. If it does not already accept a `Modifier`, it gets an optional one so the Dashboard can make it tappable (opens Accounts). Its behaviour on the Accounts tab is unchanged. | Section 7.1. |
| `strings.xml`: strings and plurals for all new text. | Existing convention. |
| `MainActivity`, `FinoraApp`, `OperationRepository`, `TransferRepository`, `RecurringRepository`, `AccountRepository`, `CategoryRepository`, `TagRepository` write paths, `InvariantChecker`, `FinoraDatabase`: **unchanged**. | Read-only sub-project. |

## 4. Domain (pure Kotlin, JVM-testable)

Package `domain/analytics`. No Android, Room, HTTP or `System` clock imports. No `Float` or `Double`.

### 4.1 Periods

```kotlin
enum class PeriodKind { TODAY, WEEK, MONTH, YEAR, CUSTOM }

/** For presets [anchor] is any date inside the period. For CUSTOM, [anchor] is the first day and [customEnd] the last day, both inclusive. */
data class Period(val kind: PeriodKind, val anchor: LocalDate, val customEnd: LocalDate? = null)

data class ResolvedPeriod(
    val period: Period,
    val fromDate: LocalDate, val toDateInclusive: LocalDate,
    val from: Long, val toExclusive: Long,       // epoch ms, half-open
)

object PeriodCalculator {
    fun preset(kind: PeriodKind, today: LocalDate): Period
    fun resolve(period: Period, zone: ZoneId, firstDayOfWeek: DayOfWeek): ResolvedPeriod
    fun shift(period: Period, steps: Int): Period                 // -1 previous, +1 next. CUSTOM shifts by its own length in days
    fun previous(period: Period): Period = shift(period, -1)
    fun canShiftForward(period: Period, today: LocalDate, zone: ZoneId, firstDayOfWeek: DayOfWeek): Boolean
}
```

- `from` is `fromDate.atStartOfDay(zone)` and `toExclusive` is the start of the day after `toDateInclusive`, so a zone whose midnight does not exist (for example `America/Sao_Paulo` on 2018-11-04) is handled by `java.time`.
- `shift` moves months and years with `plusMonths` and `plusYears` on the first day of the period, so March shifted back is February, not a day count.
- A period starting after today cannot be reached: `canShiftForward` is false when the next period's first day is later than today. Custom ranges may end after today (no transaction can be there, T11).
- `Custom` requires `fromDate <= toDateInclusive`. The picker enforces it and `resolve` coerces an invalid pair to a one-day period instead of throwing.

`DayTicker` (`domain/analytics`, takes an injectable clock and zone): `Flow<LocalDate>` that emits today on collection and again at each local midnight plus one second, and recomputes after a zone change. It is how an open Dashboard moves from "today" to the next day.

### 4.2 Measures, scope and conversion

```kotlin
enum class Measure { EXPENSE, INCOME, TRANSFER }

sealed interface CurrencyScope {
    data class Display(val currency: Currency) : CurrencyScope
    data class Native(val currency: Currency) : CurrencyScope
}

/** One converted group. [estimate] is null when nothing in the group could be converted. */
data class Cell(
    val estimate: Estimate?,
    val operationCount: Int,
    val excluded: Set<Currency>,        // currencies of this group with no direct rate
    val overflowed: Boolean,
    val oldestFetchedAt: Long?,         // over the rates actually used
) { val partial: Boolean get() = excluded.isNotEmpty() }

class ScopeConverter(scope: CurrencyScope, table: RateTable, now: Long) {
    fun cell(nativeRows: List<NativeAmount>): Cell      // NativeAmount(money: Money, operationCount: Int)
}
```

`cell` for `Display` calls `RateConverter.value` on the group's amounts: same currency stays native (`usesConversion = false`), a direct pair converts, a missing pair goes to `excluded`, never to zero. For `Native(X)`, rows of other currencies are skipped (they cannot occur, because the query was restricted to X's accounts), and there are no rate reads. No function in `domain/analytics` turns an `Estimate` into a `Money`.

### 4.3 Roll-up, shares and comparison

```kotlin
/** Additive roll-up: node = direct cell + all child nodes, using Estimate.plus. */
object CategoryEstimateRollup {
    fun rollUp(tree: CategoryTree, direct: Map<Long?, Cell>): Map<Long?, Cell>   // null key = Uncategorized
}

data class Share(val permille: Int)                       // 0..1000, HALF_UP, from BigDecimal
fun share(part: Estimate, total: Estimate): Share?        // null when total is 0 or currencies differ

data class Delta(val amount: Estimate, val percentTimesTen: Int?)   // null when previous is 0 ("new")
fun delta(current: Estimate?, previous: Estimate?): Delta?
```

- `CategoryEstimateRollup` uses the same bottom-up order as `CategoryRollup` and tolerates the same corrupt trees. `partial`, `overflowed` and `excluded` propagate to ancestors. A category the tree does not know keeps its own bucket, like `CategoryRollup`.
- The existing `CategoryRollup` (native `Long` sums) stays as it is. It is used for operation counts and for the native view check in tests: with one currency and no conversion, both roll-ups must agree.
- `share` and the percentage in `Delta` use `BigDecimal` and one `HALF_UP` rounding. Displayed shares are not forced to add up to 100.
- Comparison deltas use the same `RateTable` snapshot for both periods.

### 4.4 Buckets and series

```kotlin
enum class BucketUnit { DAY, MONTH, YEAR }
data class Bucket(val index: Int, val fromDate: LocalDate, val toDateInclusive: LocalDate, val from: Long, val toExclusive: Long)

object BucketPlan {
    fun auto(period: ResolvedPeriod, zone: ZoneId): Pair<BucketUnit, List<Bucket>>   // DAY if <= 62 days, MONTH if <= 36 months, else YEAR
    fun months(endDate: LocalDate, count: Int = 12, zone: ZoneId): List<Bucket>
    fun years(endDate: LocalDate, count: Int = 5, zone: ZoneId): List<Bucket>
}
```

- `auto` clips the first and last bucket to the period (a custom range 15 Mar to 10 Jun starts with a bucket 15 to 31 Mar).
- `months` and `years` end at the bucket containing `min(period end, today)` and are zero-filled where nothing happened. At most 100 buckets anywhere.

```kotlin
data class BalancePoint(val bucket: Bucket, val balance: Money)

object BalanceSeries {
    /** [startMinor] is the balance before the first bucket. Uses Math.addExact. Throws MoneyOverflowException. */
    fun build(startMinor: Long, currency: Currency, buckets: List<Bucket>, deltaByBucket: Map<Int, Long>): List<BalancePoint>
}
```

Balance at time `t` is the sum of the account's entries with `occurred_at <= t` (Accounts O3). Because the opening balance is dated on or before every other entry (Accounts O1), the series starts at the initial balance and never shows a partial history.

### 4.5 Account figures

```kotlin
data class AccountFigures(
    val currency: Currency,
    val balanceAtStart: Money, val balanceAtEnd: Money,
    val income: Money, val expense: Money,
    val transfersIn: Money, val transfersOut: Money,
    val initialBalanceInPeriod: Money,      // zero unless the opening balance is dated inside the period
) {
    fun reconciles(): Boolean   // start + income - expense + in - out + initialInPeriod == end
}
```

### 4.6 Other pure helpers

- `FilterCodec.encode(filter): String` and `decode(text): TransactionFilter`: the subset used by analytics (`types`, `accountIds`, `categoryIds`, `categoryExact`, `includeUncategorized`, `tagIds`, `tagMode`, `from`, `toExclusive`). Compact text such as `t=EXPENSE;a=1,2;c=7;x=1;g=4,5;m=ALL;f=1730419200000;e=1733011200000`. `decode` ignores unknown keys and bad values and never throws, so a malformed route argument yields a default filter.
- `UpcomingOccurrences.build(schedules, blocked: Set<Long>, today, horizonDays = 30, limit = 5): List<UpcomingItem>` over `RecurrenceCalculator.between`, using `nextDate` as the start. Only `displayState() == ACTIVE` schedules, and none that has a generation issue.
- `ScopedFilter.forCurrency(filter, accounts, currency): TransactionFilter` restricts `accountIds` to the accounts of that currency (all of them when the filter had none; the filter matches nothing when the intersection is empty).
- `CategoryDrill.rows(tree, rolled, path)`: rows for the current drill level, section 7.3.

## 5. Rules

| # | Rule | Source |
|---|---|---|
| R1 | Dashboard and Statistics are read-only. No analytics code writes a table, changes an amount, or calls an `OperationRepository` write function. | PRD 10.2, 32.1 |
| R2 | Income, expense, net cash flow, category statistics and tag statistics use `INCOME` and `EXPENSE` operations only. Transfers and opening balances are never included. A transfer appears only as "Transferred" (sent side) and in the account statistics lines "Incoming/Outgoing transfers". | PRD 8.1, 11.5, 28.2, 28.9, Foundation 6.3 |
| R3 | Net cash flow = total income − total expenses, in the same scope. | PRD 11.5 |
| R4 | A converted number is an `Estimate`, shown with `≈`. A native number is never marked `≈`. Native balances and converted values are always distinguishable. | PRD 10.6, 32.8, NFR-14 |
| R5 | A missing rate is never zero. The amount is left out of the total, the row shows a warning, and the screen names the currencies ("Not included: USD, THB (no rate)"). If nothing converts, the figure reads "Unavailable". | PRD 10.5, 32.5 |
| R6 | An outdated rate (older than 72 hours) is used and the screen says so, with the oldest `fetched_at` among the rates used. | PRD 10.5, 32.6 |
| R7 | A parent category's number is its direct amount plus all descendants, and equals the sum of the numbers shown beneath it. Navigation is category → subcategory → transactions at any depth up to 3. | PRD 6.5, 12.4 |
| R8 | Archived accounts keep their history in every period-based statistic and have their own statistics view. They are left out of the Dashboard total valuation and account overview. | PRD 4.4, 11.4, 26.5 |
| R9 | Converted historical figures use today's rates and say so on screen: "Converted at today's rates, updated <date>". | PRD 12.7 |
| R10 | Period filters apply to income, expense, net, category, account, tag and trend figures. They do not apply to current balances, the total valuation, recent transactions or upcoming recurring transactions. | PRD 11.3 |
| R11 | Switching the Display Currency, scope or period changes presentation only. No stored value is touched. | PRD 10.3, 32.7 |
| R12 | A dashboard or statistics screen never blocks on the network. With no rates, converted figures degrade per R5 and native figures are unaffected. | PRD 10.4, NFR-20, Acceptance CURRENCY |
| R13 | A sum that does not fit in a `Long` is reported as "Unavailable" for that figure. It is never wrapped or replaced by zero. | Foundation 5.2 |
| R14 | Tag rows may overlap and then do not add up to the total (section 2). Untagged transactions get their own "No tag" row. | PRD 7.3 |
| R15 | Every drill-down opens exactly the transactions that were counted: same period, type, accounts (native scope included), categories (direct-only where the row is a direct amount), tags and match mode. | PRD 12.4 |

## 6. Data layer

### 6.1 `AnalyticsQuery` (`data/db`, pure SQL builder like `TransactionQuery`)

All builders take a `TransactionFilter` (already scoped by `ScopedFilter`) and a `Measure`, reuse `TransactionQuery.buildWhereClauses`, and bind every user value as an argument. The measure adds:

| Measure | Added predicates | Aggregated value |
|---|---|---|
| `EXPENSE` | `o.type = 'EXPENSE'` | `ABS(e.amount_minor)` |
| `INCOME` | `o.type = 'INCOME'` | `ABS(e.amount_minor)` |
| `TRANSFER` | `o.type = 'TRANSFER' AND e.amount_minor < 0` | `ABS(e.amount_minor)` |

When `filter.accountIds` is not empty, the aggregated entry is also restricted with `e.account_id IN (...)`, so a transfer between two selected-account sets counts only the outgoing side that belongs to the selection. The filter's own `types` clause still applies, so asking for income while the filter holds only expenses returns nothing.

| Builder | SQL shape |
|---|---|
| `byCategory` | `SELECT o.category_id, e.currency_code, SUM(ABS(e.amount_minor)), COUNT(DISTINCT o.id) FROM operation o JOIN ledger_entry e ON e.operation_id = o.id WHERE … GROUP BY o.category_id, e.currency_code` (NULL = Uncategorized) |
| `byAccount` | same, `GROUP BY e.account_id` |
| `byTag` | `LEFT JOIN operation_tag ot ON ot.operation_id = o.id`, `GROUP BY ot.tag_id, e.currency_code` (NULL = "No tag") |
| `byBucket` | `WITH b(idx, lo, hi) AS (VALUES (?,?,?),(?,?,?),…) SELECT b.idx, e.currency_code, SUM(ABS(e.amount_minor)) FROM b JOIN operation o ON o.occurred_at >= b.lo AND o.occurred_at < b.hi JOIN ledger_entry e ON e.operation_id = o.id WHERE … GROUP BY b.idx, e.currency_code`. The filter's own `from` and `toExclusive` are replaced by the bucket bounds. At most 100 buckets, so 300 bound values plus filter values stay far below SQLite's limit. |
| `accountFigures` | `SELECT o.type, CASE WHEN e.amount_minor > 0 THEN 1 ELSE 0 END, SUM(ABS(e.amount_minor)) FROM ledger_entry e JOIN operation o ON o.id = e.operation_id WHERE e.account_id = ? AND e.occurred_at >= ? AND e.occurred_at < ? GROUP BY o.type, 2` |
| `accountsOverview` | the same grouped by `e.account_id` too, for the Accounts section |
| `balanceBefore` | `SELECT COALESCE(SUM(amount_minor),0) FROM ledger_entry WHERE account_id = ? AND occurred_at < ?` |
| `balanceDeltas` | per-bucket `SUM(amount_minor)` of the account's entries, all operation types (the opening balance counts toward balance) |

Opening balances are excluded from every flow measure by the type predicate. Stored magnitudes exclude `Long.MIN_VALUE`, so `ABS` cannot overflow (Operations 8). `SUM` overflow raises "integer overflow"; the repository maps it to `MoneyOverflowException` (Foundation 5.2).

Indexes used, all existing: `operation(type, occurred_at)` and `operation(occurred_at, id)` for the period and bucket joins, `ledger_entry(operation_id)`, `ledger_entry(account_id, occurred_at, amount_minor)` for account figures and balance series, `operation_tag(tag_id, operation_id)` for tag filters, `operation(category_id, occurred_at)` for category filters.

### 6.2 `AnalyticsRepository` (`data/repo`, `@Singleton`)

Returns **native** rows only. Conversion needs the rate table and the Display Currency, so it happens in the ViewModel layer through `ScopeConverter`, the same split as `ValuationCardLogic`.

| Function | Result |
|---|---|
| `observeByCategory(filter, measure): Flow<List<NativeCategoryRow>>` | `categoryId?`, `currencyCode`, `totalMinor`, `operationCount` |
| `observeByAccount(filter, measure): Flow<List<NativeAccountRow>>` | `accountId`, `currencyCode`, … |
| `observeByTag(filter, measure): Flow<List<NativeTagRow>>` | `tagId?`, … |
| `observeByBucket(filter, measure, buckets): Flow<List<NativeBucketRow>>` | `bucketIndex`, `currencyCode`, `totalMinor` |
| `observeAccountFigures(accountId, period): Flow<AccountFigures>` | Section 4.5, includes the opening-balance-in-period check |
| `observeBalanceSeries(accountId, buckets): Flow<List<BalancePoint>>` | `balanceBefore` plus `balanceDeltas` through `BalanceSeries.build` |
| `observeAccountsOverview(period): Flow<List<AccountPeriodRow>>` | One row per account, all accounts including archived |

Each flow is built with `db.invalidationTracker.createFlow("operation", "ledger_entry", "operation_tag", "category", "account")` combined with `distinctUntilChanged()`, like `TransactionRepository.observeSummary`. Queries run on Room's query dispatcher, never on the main thread (NFR-20).

### 6.3 What the ViewModels combine

Shared by Dashboard and Statistics: `AnalyticsRepository` rows, `CategoryRepository.observeAll()` (for `CategoryTree`), `AccountRepository.observeAll()`, `TagRepository.observeAllWithUsage()`, `RateRepository.observeTable()`, `SettingsRepository.displayCurrency`, `PeriodSelection`, `DayTicker`. `now` for freshness comes from the emission, so an outdated mark appears on the next emission after the 72-hour boundary (Currency 13).

## 7. UI

All user-visible text goes in `strings.xml`. Icon-only buttons have content descriptions. Colors never carry meaning alone: every chart series has a label and a number. Charts use the category or tag color, falling back to the palette default.

### 7.1 Dashboard (`DashboardScreen`, `DashboardViewModel`, route `dashboard`)

One scrolling column, top to bottom (PRD 11.2):

1. **Period selector**: chips Today, This week, This month, This year, Custom. Custom opens a Material date range picker. Below the chips, the resolved range as text ("1 to 30 Nov 2026"). Selecting a chip resets the anchor to today (no arrows here).
2. **Total valuation**: the Accounts tab card (`ValuationCard`, section 3), same states (native, `≈`, outdated, partial, unavailable). Active accounts only. Tapping it opens the Accounts tab.
3. **Income, Expenses, Net cash flow** for the period, three compact cards in the Display Currency, with `≈` when converted and the "Not included" line when needed. Net is colored by sign and also carries `+` or `−`. A "By currency" link opens a bottom sheet with exact native income, expense and net per currency. Each card opens Transactions with the period and the matching type; Net opens it with both types.
4. **Accounts**: each active account with icon, name and **native balance** (negative in the error color). Tap opens that account's transaction history (`transactions?accountId=`). No cross-currency total here; that is item 2. Archived accounts are not listed. "Manage accounts" opens the Accounts tab.
5. **Expense distribution by category**: donut (top 5 root categories plus "Other" and "Uncategorized" where present) and a list of those rows with amount and share. "Details" opens Statistics, Expenses tab. Same engine as Statistics, so the figures are identical.
6. **Recent transactions**: five `TransactionRow`s, "See all" opens Transactions. Rows open the existing edit screens.
7. **Upcoming recurring**: up to 5 rows (date, name, native amount with sign, account). A row opens the schedule detail. When `RecurringRepository.issues` is not empty, a line "N schedules need attention" opens the Recurring list. The whole card is hidden when there are no active schedules (PRD 11.2 "if applicable").

Plus a **floating "Add transaction"** button that opens `transactions/new?kind=EXPENSE` (PRD 19).

States:
- No accounts: items 3 to 7 are replaced by one card, "Add your first account", opening `accounts/new`.
- Accounts but no transactions in the period: the three cards show 0, the distribution shows "No expenses in this period".
- Converted figures carry the transparent note of R9 under the cards when any conversion was used.

### 7.2 Statistics shell (`StatisticsScreen`, `StatisticsViewModel`, route `statistics?tab=`)

- Top: segmented tabs **Expenses | Income | Tags | Accounts**, then the period selector with previous and next arrows (next disabled by `canShiftForward`), and a **scope selector**: "Display currency (PLN)" or "<code> only" for each currency held by any account.
- A **Compare** switch on Expenses, Income and Tags (not on Accounts).
- Scope `Native` shows the note "Showing <code> accounts only. Exact amounts, no conversion." Scope `Display` shows the R9 note and the outdated or "Not included" lines.
- Tab, drill-down path, scope and compare are held in the ViewModel with `SavedStateHandle`, so rotation keeps them. Changing tab, scope or period clears the drill-down path.

### 7.3 Expenses and Income tabs

One composable parameterized by `Measure` (`EXPENSE` or `INCOME`).

- **Total card**: the period total (sum of the root category rows plus Uncategorized), operation count, and with Compare on, the previous period's total and the `Delta` ("+120.00 zł, +12.4%" or "new").
- **By category** with drill-down (PRD 12.2, 12.4):
  - Level 0 lists root categories of the measure's type plus "Uncategorized", each with rolled-up amount, share, an expand chevron when it has children, and a warning icon when `partial`. Sorted by amount, largest first; rows with no amount are hidden.
  - Tapping a category with children enters it: the breadcrumb shows `All › Food`, and the list shows its children plus, when its own direct amount is non-zero, one **"Food (direct)"** row. The numbers satisfy PRD 6.5: Food 1,250 = Groceries 700 + Restaurants 450 + direct 100.
  - Tapping a category without children, or a "(direct)" row, opens Transactions with `types = {measure}`, the period, and that category (with `categoryExact = true` for a direct row, otherwise the normal closure). Uncategorized opens it with `includeUncategorized`.
  - Back (system or breadcrumb) goes up one level. Depth follows the 3-level tree (Accounts C11).
  - With Compare on, each row also shows its `Delta`. Rows present only in the previous period are listed with "—" and the negative delta.
- **By account**: all accounts with activity in the period (archived included, muted), each in its own currency converted per scope. Tap opens the account statistics (7.5).
- **By tag** (the measure's transactions grouped by tag, plus "No tag"): note "A transaction with several tags counts under each of them." Tap opens Tags tab with that tag preselected.
- **Trend**: chips **Monthly** (12 months) and **Yearly** (5 years); grouped bars of the measure per bucket, bar labels and a list below. The trend follows the current drill-down category and scope but ignores the period's own range except for where it ends. Tapping a bar opens Transactions for that bucket.
- **Donut** above the category list for the current level (top 5 plus "Other").

### 7.4 Tags tab (tag analytics, PRD 7.5, 7.6, 12.5)

- **Tag picker** (`TagFilterPicker`, Operations 6.5) with ANY/ALL when two or more tags are selected, and a **Filter** button that opens `TransactionFilterSheet` with sections `TYPE`, `ACCOUNT`, `CATEGORY`, `DATE`. The period selector sets the date range; the sheet's date section edits the same `PeriodSelection` (as a Custom range).
- With no tag selected: an explanation and the tag list; nothing is computed.
- With tags selected, all figures come from one `TransactionFilter`:
  - **Totals**: Income, Expenses, Net (R3), and **Transferred** (sent side). A figure for a type that the filter excludes shows "—".
  - **Distribution** with a measure switch **Expenses | Income | Transfers** (default Expenses, or the only type present in the filter): by account, by category (with the same drill-down as 7.3, using the measure's own tree), and by date (`BucketPlan.auto` inside the period).
  - **Transactions**: the five most recent matching rows and "Show all N", which opens Transactions with the identical filter (R15).
  - Compare works as on the other tabs.
- The PRD 7.6 scenario: select Debt, choose November, and read the total spent, the accounts, the categories, the individual transactions and the spending over the month. Adding Shared and Travel with ALL narrows the result.

### 7.5 Accounts tab and Account statistics

**Accounts tab**: every account (active first, archived below and muted), each row with icon, name, currency, and for the period: income, expenses, and net change, all native. Tap opens Account statistics. No cross-currency total.

**Account statistics** (`AccountAnalyticsScreen`, route `statistics/accounts/{accountId}`), always native, never converted (PRD 12.6):
- **Current balance** and **Initial balance** with its date ("none" when absent). Both ignore the period.
- For the period: **Income**, **Expenses**, **Incoming transfers**, **Outgoing transfers**, balance at start, balance at end, net change. The opening balance is not income; when it is dated inside the period it appears as a separate "Initial balance" line (Foundation 6.3). A reconciliation row is not shown, but `AccountFigures.reconciles()` is enforced by tests.
- **Balance over time**: line chart over `BucketPlan.auto` buckets, with the list of points underneath.
- **Transaction history**: the latest 10 rows (`observeRecent` with the account and the period) and "Show all", which opens Transactions with the account and period.
- Archived accounts show the "Archived" banner of Accounts 4.5.

### 7.6 Shared components (`ui/common`)

| Component | Notes |
|---|---|
| `PeriodSelector(selection, arrows)` | Chips, range picker, optional arrows, resolved range text. Reused by Dashboard, Statistics, Account statistics. |
| `ScopeSelector` | Display currency or one native currency. |
| `ConversionNote(cell)` | R5, R6, R9 lines: `≈` explanation, "Rates may be outdated. Last updated <date>", "Not included: …". |
| `AmountText(cell)` | `Estimate.format()` or "Unavailable", with the partial warning. |
| `charts/DonutChart`, `charts/TrendBars`, `charts/BalanceLine` | `Canvas` drawings with a content description such as "Expenses 1,250.00 zł. Food 1,250.00, 100 percent." They take plain data classes, so they are previewable and JVM-testable at the data level. |
| `AnalyticsRow(label, icon, color, cell, share, delta, onClick)` | Row used by every breakdown list. |

## 8. Navigation

| Route | Screen |
|---|---|
| `dashboard` (exists) | Dashboard |
| `statistics?tab={tab}&tagId={tagId}` (`statistics` exists) | Statistics. `tab` is `EXPENSES`, `INCOME`, `TAGS`, `ACCOUNTS` (default `EXPENSES`). `tagId` preselects a tag on the Tags tab. |
| `statistics/accounts/{accountId}` | Account statistics |
| `transactions?…&filter={filter}` (exists, gains `filter`) | History preset from any analytics row |

- Analytics rows build a `TransactionFilter` and call `Routes.transactions(filter)`, which writes `FilterCodec.encode(filter)` URI-encoded into the `filter` argument. The existing `accountId`, `tagId`, `categoryId`, `types` presets keep working for their current callers. If both are present, `filter` wins.
- A native scope is applied before encoding, through `ScopedFilter.forCurrency` (R15).
- Navigation into Transactions uses `launchSingleTop` and `restoreState = false`, as in Operations 10, so the preset always applies.
- `isRouteUnder` already highlights Statistics for `statistics/…` and `statistics?…`.

## 9. Schema and migration

None. `FinoraDatabase.VERSION` stays 3, `ALL_MIGRATIONS` stays empty, `1.json`, `2.json`, `3.json` and their pinned copies are untouched, and `fallbackToDestructiveMigration` stays forbidden. A test asserts `VERSION == 3` and that the generated `3.json` equals the pinned copy.

`InvariantChecker` gets no new check: this sub-project stores nothing.

## 10. Privacy and security

No network call, no new permission, no new exported component. The only external data are the cached rates fetched by sub-project 5, which now cover all account currencies. The request still contains currency codes only (Currency 10). Analytics reads follow the Foundation backup exclusions.

## 11. Testing

JVM unit tests:
- `PeriodCalculatorTest`: each kind; week start for Monday and Sunday locales; month lengths (28, 29, 30, 31); year; Custom one day and many days; `shift` across month and year ends (31 Mar back is February); Custom shift by its own length; `canShiftForward` exactly at the boundary; `resolve` in a zone with a missing midnight and with a DST overlap; an invalid custom pair is coerced.
- `DayTickerTest`: emits today, emits again after midnight plus one second, recomputes after a zone change (fake clock and delay).
- `ScopeConverterTest`: display conversion (direct pair), same currency stays native, missing pair goes to `excluded` and never to zero, outdated flag, overflow sets `overflowed`, native scope never reads the table.
- `CategoryEstimateRollupTest`: the PRD example 1,250 = 700 + 450 + 100; a 3-level tree; Uncategorized stays separate; `partial` and `overflowed` propagate to ancestors; **additivity**: a data set where converting per currency after summing would differ by one minor unit from the shown children, and the roll-up still equals the sum of the shown parts; with one currency and no conversion it equals `CategoryRollup`.
- `ShareAndDeltaTest`: share of a zero total is null, `HALF_UP` at a tie, delta against zero is "new", negative delta, different currencies rejected, flags merge.
- `BucketPlanTest`: `auto` thresholds at 62 days and 36 months, clipped first and last bucket, month and year plans end at the right bucket, zero-fill, the 100-bucket cap, DST day lengths.
- `BalanceSeriesTest` and `AccountFiguresTest`: cumulative values, `addExact` overflow throws, `reconciles()` for a normal case, a case with the opening balance inside the period, and a transfer in each direction.
- `FilterCodecTest`: round trip of every field, unknown keys ignored, garbage and empty strings give a default filter, no exception for any input, values containing `;`, `=`, `,` cannot inject keys.
- `ScopedFilterTest`: intersection with selected accounts, empty intersection, no selection.
- `UpcomingOccurrencesTest`: horizon and limit, ordering across schedules, paused, ended and blocked schedules excluded, month-end anchoring.
- Static check: `domain/analytics` has no `Float`, `Double`, `.toDouble()`, Android, Room or `System.currentTimeMillis`, and no function turns an `Estimate` into a `Money`.
- `AnalyticsQueryTest`: SQL and arguments for each builder, measure predicates, account restriction on the aggregated entry, argument count equals placeholder count, no user value inside the SQL text.

Instrumented Room tests (`AnalyticsRepositoryTest`, `AnalyticsPlanTest`, `RateRequiredCurrenciesTest`), seeded with PLN, USD, EUR and JPY accounts, one archived account, expenses, incomes, same- and cross-currency transfers, opening balances, nested categories, Uncategorized, and tags:

Correctness
- Expense and income rows never include transfers or opening balances. Net equals income minus expense for every period tested.
- Category rows equal manually computed sums. The PRD 6.5 example holds on real rows.
- Archived-account transactions appear in period statistics and in the account overview rows. The Dashboard valuation excludes them.
- `categoryExact = true` returns only direct operations of the category. The default returns descendants as well. Both agree with the numbers shown for the same row (R15).
- Tags: ANY returns the union, ALL the intersection, a multi-tag transaction counts under each tag, the "No tag" row holds exactly the untagged ones, and tag rows can sum above the total.
- Transfer measure: a transfer counts its sent side once; with an account filter only outgoing entries of the selected accounts count; a same-currency 100 to 95 transfer counts 100.
- Buckets: half-open boundaries at exactly `hi`, a DST day, 12 monthly buckets with zero fill, yearly buckets, bucket sums equal the period total for the same filter.
- Native scope: `ScopedFilter` plus the query returns only that currency, equals the manual sum, and the drilled-down Transactions list (`observePage`) shows the same operations.
- Account figures: income, expense, in, out, start, end, `reconciles()`; moving the opening date does not change the current balance or the end balance; the last point of `observeBalanceSeries` equals `OperationRepository.balanceMinor`; an account without entries has a zero series.
- Overflow: a `SUM` that exceeds `Long` surfaces as `MoneyOverflowException`, and the figure becomes "Unavailable" at the ViewModel level.
- Live updates: adding, editing or deleting a transaction, a tag assignment, a category rename and a rate change each re-emit the relevant flow.
- `RateRepository` now requests pairs for archived-account currencies (replaces the old Currency test).

Performance and plans
- `EXPLAIN QUERY PLAN` for `byCategory`, `byBucket` and `accountFigures` uses `operation(type, occurred_at)` or `operation(occurred_at, id)` and `ledger_entry(operation_id)` or the covering account index, with no full scan of `ledger_entry`.
- 50,000 operations with entries and tags: Dashboard queries (income and expense by category, 5 recents, account balances) and a Statistics year view each complete under a generous bound (for example 500 ms on the test device). Failing this triggers the index fallback in section 13.

UI and ViewModel tests (JVM, fake repositories and a fixed clock):
- Dashboard: every card in each state (no account, no transactions, converted, outdated, partial, unavailable, native only), recent and upcoming sections, hidden upcoming card, "needs attention" line, period chips change only the period-based cards, midnight tick moves "Today".
- Shared `PeriodSelection`: choosing This year on the Dashboard shows This year in Statistics, and the reverse.
- Statistics: drill-down path and back, the "direct" row opens an exact-category filter, Compare totals and deltas including "new", scope switch clears the path, Tags tab requires a tag, ANY/ALL, tab and path survive recreation.
- Hand-off: each row type produces the expected `TransactionFilter`, and decoding the encoded filter yields the same filter.
- Account statistics for an archived account and for an account with no history.

Manual checklist (device or emulator with accounts in several currencies):
1. Fresh install, no accounts: the Dashboard shows "Add your first account". After adding one, the cards appear.
2. Add PLN, USD and EUR accounts with balances, offline: the valuation shows "Unavailable" or a partial total with "Not included". Go online: it becomes `≈` within seconds. The native balance list is correct throughout.
3. Add expenses and incomes in several categories and currencies. Compare the Dashboard totals with Statistics, and with the sum of the visible category rows.
4. Switch the period through every chip, then Custom. Income, expense, net and the distribution change; balances, recent transactions and upcoming recurring do not.
5. Statistics → Expenses: drill Food → Groceries → transactions. Open the "Food (direct)" row and confirm the list matches its number. Use the back button at each level.
6. Turn Compare on in the middle of the month and confirm the label says "previous full month".
7. Switch the scope to "PLN only": `≈` and the conversion note disappear, other currencies vanish, drill-down shows only PLN accounts' transactions.
8. Tags: select Debt, then Debt + Shared with ANY and ALL; add an account and a category filter; check totals, distribution, and "Show all".
9. Create a transfer between PLN and USD accounts: income, expense and net do not move; the account statistics show it as outgoing and incoming; "Transferred" appears on the Tags tab for a tagged transfer.
10. Archive an account with history: it disappears from the Dashboard account list and valuation, stays in Statistics → Accounts and in period statistics.
11. Open an account's statistics: balance line, income, expenses, transfers, and the initial balance line when dated in the period.
12. Move the device clock past midnight with the Dashboard open: "Today" updates.
13. Light and dark theme, large font scale, rotation on each tab, TalkBack on a chart and a row.
14. Seed 50,000 transactions (debug seeder): Dashboard and Statistics open without visible delay.

## 12. Done when

1. The Dashboard and Statistics tabs replace their placeholders and all flows in section 7 work, including account statistics and the hand-offs to Transactions.
2. PRD 33 DASHBOARD & ANALYTICS holds:
   - an estimated total of finances is shown;
   - income and expenses are shown;
   - there is a distribution by category;
   - there are statistics by account;
   - there are statistics by tag;
   - several time periods are supported.
3. PRD 33 TAGS items "view statistics by tag" and "analyze tags by accounts and categories" hold in full. PRD 33 CATEGORIES "parent categories aggregate sub-category totals" holds on screen with drill-down to transactions.
4. PRD 11.5 holds: net cash flow is income minus expenses, transfers excluded; total valuation is a labelled estimate.
5. Database is still at version 3 with committed schemas untouched, and no write path changed.
6. All tests in section 11 pass, the invariant checker is clean, and the build has no new lint warnings. The manifest is unchanged.

## 13. Risks

- **Rounding differs slightly between screens.** Statistics and the Dashboard sum category-level estimates; the Transactions summary bar (Currency 5.2) converts per-currency totals. The same data can differ by a few minor units in converted mode. Each screen is internally exact, and everything converted is marked `≈`. Aligning them would mean changing the Currency design.
- **Converted history moves with the rate.** Last month's converted total changes tomorrow when the rate changes (PRD 12.7). The note under every converted figure says so, and the native scope gives a stable view.
- **Widened rate set means more downloads.** Archived accounts' currencies add pairs. The count stays small (one request per base, Currency 6.3).
- **Tag totals overlap.** Users may expect tag rows to add up. The note and the "No tag" row explain it.
- **Selected period is not persisted.** After process death the app returns to This month. Persisting it needs an `app_setting` key and is easy to add later.
- **Locale decides the first weekday** until the settings sub-project adds the PRD 17.1 setting.
- **Comparing a running period with the previous full one** (decision in section 14) can look like a drop early in the month.
- **Transfer volume counts the sent side only.** A fee-bearing 100 to 95 transfer shows 100. The difference is not reported anywhere in v1 (Transfers R4).
- **Hand-rolled charts.** `Canvas` charts cost accessibility work: the text lists next to each chart and the content descriptions are required for that reason, and are part of the tests.
- **Bucket query shape.** The `VALUES` join relies on SQLite using `operation(occurred_at, id)` per bucket. The plan test gates it. If it, or the 50,000-row test, fails, the fallback is a new index through `@AutoMigration(3, 4)` (Foundation 7), which then needs `4.json` and a migration test.
- **A new field on `TransactionFilter`.** `categoryExact` touches the filter, its normalization and its chip text. Existing behaviour is unchanged while it is false, and tests cover both values.
- **Native scope hides other currencies by design.** Totals in a native view do not include transfers' received sides in other currencies. The scope note states it.
- **No undo, no export** of any analytics figure in v1. CSV export (8) covers transactions, not reports.
- **Clock and time zone.** The period is judged against the device calendar. Travelling across zones can shift a day boundary, consistent with Operations 13 and Recurring 13.

## 14. Assumptions to confirm

These details were not specified and are set to the simplest reasonable choice:
1. **One shared period** across Dashboard, Statistics and Account statistics (section 2), held in memory only.
2. **Default period is This month**, and Compare uses the previous **full** period (not the same elapsed days of it).
3. **Required rates cover all accounts' currencies**, including archived ones (section 3). This changes one Currency test.
4. **Native scope** is "accounts of one currency", chosen from the currencies held by accounts. It is not a per-transaction currency filter.
5. **Transferred = sent side.** The received side and the difference are not shown.
6. **Trends are Monthly (12) and Yearly (5)** on the Expenses and Income tabs, with an automatic bucket size on the Tags tab and in Account statistics.
7. **Dashboard totals include archived accounts' transactions** in the period, but the balance valuation and account list exclude archived accounts.
8. **Charts are drawn with `Canvas`** and no library is added.
9. **Upcoming recurring window** is 30 days and 5 items, excluding paused, ended and blocked schedules.
10. **Dashboard has an "Add transaction" button.** This is small and optional; remove it if the Transactions tab button is enough.

## 15. Hand-offs

- **Sub-project 8 (Import / Export):** CSV export reuses `TransactionFilter` (and `FilterCodec` if the export screen is opened from an analytics view). No analytics table exists, so the `.db` export carries nothing new. After an import, the Dashboard and Statistics recompute from the imported rows, and converted figures wait for rates like a fresh install.
- **Later:** budgets (PRD 13) can reuse `AnalyticsQuery.byCategory`, `CategoryEstimateRollup` and `PeriodCalculator` for "spent against limit". A first-day-of-week setting plugs into `PeriodCalculator`. A persisted period and total-balance history can be added without a schema change to the transaction model.
