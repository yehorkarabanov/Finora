# Finora: Operations and Tags Design

Date: 2026-11-04
Status: Revision 2. The nine decisions in section 14 were answered by the user on 2026-11-04 and are applied throughout. Ready for the implementation plan.
Sub-project: 3 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD). Section numbers like "PRD 27.8" refer to it. "Foundation 6.4" refers to the Foundation design, "Accounts 4.7" to `2026-11-04-finora-accounts-categories-design.md`.

## 1. Scope

In scope:
1. **Income and expense**: create, edit, delete, duplicate (PRD 5.1 to 5.4, 19, 27).
2. **Transaction history**: paged chronological list, search, filters (type, account, category with subcategories, tags ANY/ALL, date range, amount range, description), sorting (PRD 5.5, 15).
3. **Tags**: create (also inline while entering a transaction), rename, recolor, delete, assign many per transaction, filter ANY/ALL (PRD 7, 30).
4. A data-layer **summary primitive** over any filter (per-currency income, expense, count) and a compact summary bar in the history screen when a filter is active. It is the base for tag analytics (section 6.6).
5. Reusable UI pieces for later sub-projects: tag picker, tag filter picker, transaction row.
6. The account detail "transactions" section reserved by Accounts 4.5.

Out of scope here, with the sub-project that owns it:
- Transfer entry, editing and deletion, transfer fees (4). The history list already **shows** transfers, read-only, because they exist in the data model (section 5.5).
- Currency conversion, Display Currency totals, estimate markers, sorting or filtering by converted amount (5). Every amount here is shown in its native currency.
- Recurring schedules (6). Templates and budgets (later, P2).
- Dedicated analytics screens: tag analytics with account, category and date distributions, charts, dashboard period filters (7). This sub-project delivers the data primitive and the filter model they reuse.
- CSV export (8).

Foundation and Accounts/Categories already ship: `operation`, `ledger_entry`, `tag`, `operation_tag` tables and DAOs, `OperationRepository` (insert, `changeOccurredAt`, `setOpeningBalance`, `addTag`, balance), `InvariantChecker`, `CategoryTree`, `CategoryRules`, `AccountPicker`, `CategoryTreePicker`, icon and color pickers, and placeholder Transactions and Tags screens. This sub-project replaces those placeholders.

## 2. Decisions

| Topic | Decision |
|---|---|
| Schema | **Version stays 1, no migration.** The app is in development with no release, so the v1 schema is edited in place: one new index `operation(occurred_at, id)` so the unfiltered history can be read newest-first without a sort (section 9). `1.json` and its pinned test copy are regenerated. Development devices clear app data or reinstall. |
| Transaction types here | `INCOME` and `EXPENSE` are created and edited. `TRANSFER` rows appear in history, read-only. `OPENING_BALANCE` never appears in history (it is the account's "Initial balance", Foundation 6.3). |
| Type of an existing transaction | Fixed. Changing income to expense would invalidate its category. The user deletes and re-enters, or duplicates. |
| Amount input | Positive amount in the account's currency, `Money.parse` rules, never rounded. The repository writes the sign from the type (income +, expense −). |
| Account change on edit | Allowed to **any active account, whatever its currency**, or keeping the current one even if archived (Accounts 4.7). Another archived account is never allowed. Moving to a different currency keeps the **nominal number** and replaces the currency, after an explicit confirmation. It is a reassignment, not an FX conversion, and no exchange rate is used (T12, 5.7). |
| Category | Required when creating or duplicating (PRD 5.1, 5.2). Uncategorized (`category_id IS NULL`) is a valid stored state that arises mainly when a category is deleted (5.8, Accounts C6 and C9). An already-uncategorized operation can be edited and saved without a category. The entry form never offers Uncategorized as a choice. |
| Date | Stored as an instant. Defaults to now. **Future dates and times are refused** by the pickers and by the repository (T11). |
| Description | Optional, trimmed, blank becomes `NULL`, at most 500 characters. |
| Tag names | Trimmed, inner whitespace collapsed, 1 to 30 characters, **unique ignoring case** (enforced in the repository, section 6.2). |
| Tag on transfers | Allowed (PRD Part X). The repository accepts tags for INCOME, EXPENSE and TRANSFER, never OPENING_BALANCE. |
| Tag changes and `updated_at` | Renaming, recoloring or deleting a tag does not touch any operation's `updated_at`. Changing a transaction's tags in its edit form does. |
| Pending tags | A tag typed in the transaction form is created **inside the save transaction**, not when typed. Cancelling the form creates nothing. |
| History paging | Paging 3 over a dynamic Room query, 50 rows per page. |
| Amount filter and amount sort | Amounts of different currencies are never compared. Both work in a **single-currency scope** (section 5.6). |
| Search | Description only, case-insensitive including non-ASCII, gated by a test (section 13). |

## 3. Changes to existing code

| Change | Reason |
|---|---|
| `OperationEntity` gets `Index(value = ["occurred_at", "id"])`. `FinoraDatabase.VERSION` stays 1 and `ALL_MIGRATIONS` stays empty. `app/schemas/.../1.json` is regenerated. | Section 9. |
| `OperationRepository.insertOperation` gains `tagIds: Set<Long> = emptySet()` and `newTagNames: List<String> = emptyList()`. Tags are written in the same transaction. | One atomic write path for an operation with its tags. |
| New `OperationRepository.updateOperation(...)`, `deleteOperation(id)`, `setTags(operationId, tagIds)`. | Sections 5.3 and 6.4. `updateOperation` and `deleteOperation` accept INCOME and EXPENSE only until sub-project 4 widens them. |
| The opening-order handling inside `insertOperation` (Accounts 4.6) moves into a private `enforceOpeningOrder(accountId, occurredAt, move, now)` shared with `updateOperation`. Behaviour of `insertOperation` is unchanged. | Same rule, one implementation. |
| `OperationRepository` pre-checks the resulting account balance with `Math.addExact` and throws `MoneyOverflowException` instead of writing a row that makes `SUM` overflow. | Foundation 5.2. |
| `insertOperation`, `updateOperation` and `changeOccurredAt` reject an INCOME or EXPENSE dated after `now` with `FutureDateException(occurredAt, now)`, a subclass of `InvariantViolationException`. `now` is the existing injectable parameter, so tests control the clock. | T11. Transfers are covered when sub-project 4 widens these functions. |
| New `TransactionRepository` (read side, summaries, typed income/expense drafts) and `TagRepository`. | Sections 5.4 and 6.3. |
| New DAO methods and one `@RawQuery` (section 8). | Needed by the repositories. |
| `AccountPicker` is used unchanged. In edit mode the form passes `originalAccountId`, so every active account is offered plus the archived original. | Accounts 4.7. No currency filter, because T12 allows cross-currency reassignment. |
| `AccountDetailScreen`: the reserved section shows the latest 10 transactions of the account and "Show all", which opens Transactions with the account filter preset. | PRD 4.3 "View account transaction history". |
| `AccountDraftValidator.parseAmount` logic is extracted into `domain/money/AmountParser` and the account validator delegates to it. Existing account tests stay unchanged. | The transaction form needs the same unsigned/signed decimal parsing. |
| Category delete dialog (`CategoriesScreens.kt`, `CategoryViewModels.kt`) gets a "Review transactions" action and states that the shown count is direct transactions only (5.8). `CategoryRepository.delete` and its rules are unchanged. | Decision 1. |
| `FinoraNavHost`: real Transactions and Tags screens and the routes in section 10. | Replaces placeholders. |
| `SchemaUnchangedTest` keeps its name. `src/test/resources/schema-v1.expected.json` is replaced by the regenerated v1 schema in the same commit as the entity change, and the test KDoc says the pinned file now includes the history index. | Keeps the schema guard meaningful. |
| `MigrationInfrastructureTest` and the scratch v1 → v2 test are not touched: the version stays 1, and the scratch test copies the regenerated `1.json` through the existing Gradle copy task and byte-identity check. | Foundation 7. |

`InvariantChecker` gets **no new SQL checks**: every invariant this sub-project relies on (income > 0, expense < 0, category type match, `occurred_at` match, no tag on an opening balance) is already checked. Tests assert that every write path in this sub-project leaves the checker clean.

## 4. Domain (pure Kotlin, JVM-testable)

```kotlin
enum class TransactionKind { INCOME, EXPENSE }

data class TransactionDraft(
    val kind: TransactionKind, val accountId: Long,
    val amountMinor: Long,              // positive, in the account's currency
    val categoryId: Long?, val occurredAt: Long, val description: String?,
    val tagIds: Set<Long>, val newTagNames: List<String>,
)

data class Tag(val id: Long, val name: String, val color: Long?, val createdAt: Long)
data class TagWithUsage(val tag: Tag, val usageCount: Int)

enum class TagMatchMode { ANY, ALL }
data class AmountRange(val currency: Currency, val minMinor: Long?, val maxMinor: Long?)   // magnitudes, >= 0

data class TransactionFilter(
    val types: Set<OperationType> = emptySet(),          // subset of INCOME, EXPENSE, TRANSFER. Empty = all three.
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(),             // each includes its descendants
    val includeUncategorized: Boolean = false,
    val tagIds: Set<Long> = emptySet(), val tagMode: TagMatchMode = TagMatchMode.ANY,
    val from: Long? = null, val toExclusive: Long? = null,
    val amount: AmountRange? = null,
    val text: String = "",
)

enum class TransactionSort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }

data class TransactionEntryView(val account: Account, val money: Money)   // signed
data class TransactionItem(
    val id: Long, val type: OperationType, val occurredAt: Long, val description: String?,
    val category: Category?, val categoryPath: List<Category>, val tags: List<Tag>,
    val entries: List<TransactionEntryView>, val createdAt: Long, val updatedAt: Long,
)

data class CurrencySummary(val currency: Currency, val income: Money, val expense: Money, val operationCount: Int)
```

Pure helpers in `domain/operation` and `domain/tag`:

- `TransactionDraftValidator.validate(draftFields, currency, now)`: field errors `AMOUNT_EMPTY`, `AMOUNT_INVALID`, `AMOUNT_TOO_PRECISE`, `AMOUNT_OUT_OF_RANGE`, `AMOUNT_NOT_POSITIVE`, `ACCOUNT_MISSING`, `CATEGORY_MISSING`, `DESCRIPTION_TOO_LONG`, `DATE_IN_FUTURE`. The amount is always parsed in the **selected account's** currency. The form maps errors to messages without parsing exception text. `CATEGORY_MISSING` is raised for create and duplicate, and for an edit only when the operation had a category before.
- `TagNameRules`: `normalize(raw)` (trim, collapse whitespace runs), `key(name)` (`normalize(name).lowercase()` with `Locale.ROOT`, used for case-insensitive equality), `validate(raw)` returning `TAG_NAME_EMPTY` or `TAG_NAME_TOO_LONG`.
- `TransactionDateTime`: `combine(pickerUtcMidnight, hour, minute, zone)` returns the instant (seconds and millis are zero), `split(instant, zone)` returns the local date and time for preselecting the pickers, `isFuture(instant, now)`, and `latestSelectableDay(now, zone)` for the date picker. A local time that does not exist on a DST day moves forward (the `ZonedDateTime.of` rule).
- `DayGrouping`: label key (today, yesterday, date) for a timestamp in the device zone.
- `FilterScope` (section 5.6): `scopeCurrency(filter, accounts): Currency?`, `pruneMissing(filter, accounts, categories, tags): TransactionFilter`.

## 5. Operations

### 5.1 Rules

| # | Rule | Source |
|---|---|---|
| T1 | An income operation has one ledger entry with a positive amount, an expense one with a negative amount, both in the account's currency. | PRD 27.1, 27.2 |
| T2 | Category type must match the operation type. A category may be a parent with children and may hold operations directly. | PRD 27.3, 6.4 |
| T3 | Changing the category, tags or date never changes the amount or currency. | PRD 27.6, 27.7, 27.9 |
| T4 | An edit may keep the entry on its current account, even if archived. It may move it to **any active account**, in any currency. It may never move it onto a different archived account. | Accounts 4.7 |
| T5 | New operations cannot use an archived account. Editing and deleting existing ones on an archived account is allowed. | Accounts A4 |
| T6 | An entry dated before its account's opening balance is refused with `OpeningBalanceOrderException`. The form offers to move the opening balance back (`moveOpeningBalanceBack`). Applies to create and to edit (account or date change). | Accounts 4.6 |
| T7 | Deleting a transaction removes its entry and tag links and so recalculates the balance. Nothing else is deleted. | PRD 27.8 |
| T8 | A write that would make the balance of any affected account exceed the `Long` range is refused with `MoneyOverflowException`, and nothing is written. | Foundation 5.2 |
| T9 | Creation and modification timestamps: `created_at` is set once, `updated_at` changes on every edit of the operation row, entry or tag set. | PRD 5.3 |
| T10 | Duplicate opens a new-transaction form prefilled with the source's type, account, category, amount, description and tags, **dated now** (the source date is not copied, so the result is never in the future). It saves nothing by itself. If the source's account is archived the account field starts empty. If the source is Uncategorized the category field starts empty and must be chosen. | PRD 5.4 |
| T11 | **No future dates.** An INCOME or EXPENSE cannot have `occurred_at` after `now`. The date picker offers no later day, the time picker rejects a time later than now on today's date, and the repository throws `FutureDateException` on create and on edit. | Decision 5 |
| T12 | **Cross-currency reassignment keeps the nominal number.** Moving an operation to an active account of another currency keeps `amount_minor`'s decimal value and replaces the currency: 100.00 PLN becomes 100.00 USD. No exchange rate is read or computed. It happens only after an explicit confirmation, and the repository refuses it without the confirmation flag. | Decision 2, PRD 27.10 |

### 5.2 Write path

`OperationRepository` stays the single write path. All functions run in one Room transaction and validate through the existing `validate`, extended for tags.

| Function | Behaviour |
|---|---|
| `insertOperation(..., tagIds, newTagNames)` | As today, then `resolveTags` (6.2): create missing tags, link all. Rejects tags for OPENING_BALANCE. Balance pre-check (T8). |
| `updateOperation(id, accountId, amountMinor, categoryId, occurredAt, description, tagIds, newTagNames, moveOpeningBalanceBack, confirmCurrencyChange, now)` | Loads the operation; type must be INCOME or EXPENSE. Checks T4 and T6 for the target account and date, T11, T8 (target account, and the source account when it differs), sign by type, category type. If the target account's currency differs from the entry's current currency and `confirmCurrencyChange` is false, throws `CurrencyChangeNotConfirmedException(fromCode, toCode)` and writes nothing (T12). Updates the operation row (`occurred_at`, `category_id`, `description`, `updated_at`), the single entry with **`account_id`, `currency_code` and `amount_minor` in one UPDATE** so the composite FK holds, and replaces the tag links. `created_at` is untouched. |
| `deleteOperation(id)` | INCOME or EXPENSE only. Deletes the operation row, entries and links cascade. |
| `setTags(operationId, tagIds, newTagNames)` | Replaces the tag set of a non-opening operation. Used by `updateOperation` and by sub-project 4. |

`updateOperation` takes `amountMinor` as the new number **already scaled to the target account's currency** by the caller. The repository does not convert and has no rate input. A value with more fractional digits than the target currency allows cannot reach it, because the form's parser rejects it (5.7).

The entry's `occurred_at` stays equal to the operation's (Foundation 6.4), and `InvariantChecker` stays clean after every path above.

### 5.3 Transaction entry screen

`TransactionEditScreen` + `TransactionEditViewModel` (immutable `StateFlow` state), one screen for create, edit and duplicate. The user can record a simple expense without leaving it (PRD 19).

- **Type**: segmented Expense | Income, default Expense. Disabled on edit. Switching type clears a category that no longer fits. Sub-project 4 adds a Transfer segment that routes to its own form.
- **Amount**: large field, digits and one decimal separator (`.` or `,`), currency symbol and code shown next to it. Errors per `TransactionDraftValidator`, shown on the field.
- **Account**: `AccountPicker`. New transaction: active accounts only. Default is the account of the most recent income or expense if it is active, otherwise the lowest-id active account. Edit: `originalAccountId` = current account, so all active accounts are offered, plus the archived original (T4). With an archived original selected the note from Accounts 4.7 is shown. Choosing an account of another currency changes the currency label next to the amount at once and keeps the typed number (5.7).
- **Category**: a row of up to 5 recent categories of the type (chips) plus "All categories", which opens `CategoryTreePicker` in `Assign` mode. The chosen category is shown as a path, for example "Food › Groceries". With no categories of the type, the field offers "Create category" (opens the category form). A new or duplicated transaction cannot be saved without a category, and **Uncategorized is not offered as a choice** (section 2). When editing an operation that is already Uncategorized, the field shows "Uncategorized", saving without choosing one is allowed, and the user may pick a category.
- **Date and time**: two buttons (date, time), default now, via `TransactionDateTime`. The date picker disables every day after today (T11). On today's date the time picker rejects a time later than now with an inline error and the Save button stays disabled. If the clock passes the chosen time while the form is open nothing changes, and the repository check at Save time is the final authority: it reports `FutureDateException`, and the form shows the date error and keeps the data.
- **Description**: optional, with a character counter near the limit.
- **Tags**: `TagPicker` (section 7). Optional.
- **Save** in the top bar, disabled while invalid. Save runs these gates in order, each dialog shown only when it applies, and nothing is written until all are confirmed:
  1. **Currency reassignment** (edit mode, target account currency differs from the stored one): the dialog in 5.7.
  2. **Opening order**: on `OpeningBalanceOrderException` the dialog defined in Accounts 4.6: "This account's initial balance is dated <D>. Move it to <entry date> so this transaction comes after it?" Confirm repeats the write with `moveOpeningBalanceBack = true`, cancel changes nothing. The dialog states that the current balance does not change.
- **Overflow** (`MoneyOverflowException`): error on the amount field, nothing written.
- **Edit mode menu**: Duplicate, Delete. Delete asks for confirmation and states that the account balance changes by the shown amount.
- **Duplicate** (T10) navigates to the same screen in create mode with `duplicateOf=<id>`.
- Rotation and process recreation keep the form state (ViewModel plus `SavedStateHandle` for the id and duplicate source).

### 5.7 Changing the account of an existing operation

T12 in practice. The user edits a transaction and picks another active account.

- **Same currency**: no extra step.
- **Different currency**: the typed number stays exactly as typed, only the currency label changes. On Save the form shows a confirmation dialog:
  - Title: "Change currency without converting?"
  - Body: "<old amount with currency> will become <new amount with currency>. This is not a currency conversion. No exchange rate is used and the number stays the same. The balance of <old account> changes by <+/− old amount> and the balance of <new account> changes by <−/+ new amount>."
  - Example: "100.00 PLN will become 100.00 USD."
  - Actions: "Change currency" (confirms, repeats the write with `confirmCurrencyChange = true`) and "Cancel" (nothing changes, the form keeps its state).
- **Scale differences**: the amount text is parsed with the target currency's scale. Moving 12.50 PLN to a JPY account gives `AMOUNT_TOO_PRECISE` on the field, and Save stays disabled until the user enters a valid amount. Nothing is rounded. Moving 100 JPY to a PLN account becomes 100.00 PLN.
- **Only on an actual change**: the dialog appears only when the target currency differs from the currency stored on the operation. Switching the picker back to the original account removes the dialog and the warning.
- **Display Currency, rates and the rates cache are never read** on this path. The operation's history in the old currency is not kept. The old amount appears only in the confirmation text.
- **Statistics**: the operation is reported in the new currency from then on. Aggregations are per-currency (Accounts 5.5), so nothing else changes.
- **Transfers**: read-only here. Sub-project 4 applies the same principle to a side of a transfer (nominal number kept, currency replaced, explicit confirmation) and decides the consequences for the other side (section 15).

### 5.8 Uncategorized and category deletion

Uncategorized is a valid state (`operation.category_id IS NULL`, Accounts C9). It arises when a category is deleted and the user chooses Uncategorized. Behaviour that already exists in Accounts C6 to C10 is kept:
- The category delete dialog tells the user how many transactions are assigned directly to the category and lets them either pick another category of the same type for all of them or move them to Uncategorized. Nothing is deleted, and the choice is made before the delete runs.
- Immediate subcategories move up one level with their descendants and operations.

What this sub-project adds:
- The dialog gets a "Review transactions" action that opens the history preset to that category (route `transactions?categoryId=`, section 10), so the user can edit individual transactions before deleting. The preset filter includes descendants (5.6), so the shown count can be larger than the number in the dialog. The dialog's own number counts direct operations only and says so.
- Uncategorized transactions are shown with a neutral icon and the label "Uncategorized", can be found with the "Uncategorized" category filter, and are edited like any other (5.3).

### 5.4 Reading

`TransactionRepository` (`data/repo`, `@Singleton`):

| Function | Behaviour |
|---|---|
| `observePage(filter, sort): Flow<PagingData<TransactionItem>>` | Dynamic query (section 8) with `@Relation` loading of entries and tags per page. Account, category and tag names come from in-memory lookups that are combined in, so renames show without reloading pages. |
| `observeRecent(filter, limit): Flow<List<TransactionItem>>` | Non-paged, newest first. Used by account detail and, later, the dashboard. |
| `observeTransaction(id): Flow<TransactionItem?>` | For the edit screen. |
| `summarize(filter): List<CurrencySummary>` | Section 6.6. Overflow in a sum is mapped to `MoneyOverflowException`. |
| `recentCategoryIds(kind, limit)`, `defaultAccountId()` | Entry form helpers. Both read the newest 200 income/expense operations, so they stay cheap on large histories. |
| `create(draft, moveOpeningBalanceBack): Long`, `update(id, draft, moveOpeningBalanceBack, confirmCurrencyChange)`, `delete(id)` | Translate a `TransactionDraft` (positive amount in the target account's currency, kind) to the signed `EntryDraft` and call `OperationRepository`. `create` requires a non-null category (Accounts C9 leaves that decision to this sub-project). `OperationRepository.insertOperation` keeps accepting a null category so earlier tests and later sub-projects are not affected. |

### 5.5 History screen

`TransactionsScreen` + `TransactionsViewModel` replace the placeholder on the Transactions tab.

- **Top**: search field (300 ms debounce), a filter button, a sort menu, and a row of removable chips for every active filter with "Clear all".
- **List**: grouped by local day with sticky headers ("Today", "Yesterday", then the date). Days come from `DayGrouping`.
- **Row** (`TransactionRow`, reusable): category icon in the category color (neutral icon for Uncategorized), title = description or category name, subtitle = category path and account name (muted for archived accounts), up to 2 tag chips plus "+N", trailing signed amount in the account's currency.
  - Income: `+` and the income color. Expense: `−` and the error/expense color. Transfer: a swap icon, neutral color, "From → To" subtitle, amount of each side. Type is conveyed by sign and icon as well as color. The row has a content description such as "Expense, 12.50 PLN, Groceries, Santander, 4 November".
- **Tap**: income and expense open the edit screen. Transfer rows do nothing until sub-project 4 attaches its form. Long press is not used.
- **FAB**: "Add transaction", opens the entry screen as an expense.
- **Empty states**: no transactions at all (with the Add action), and no matches for the current filter (with "Clear filters").
- **Summary bar**: shown only when at least one filter or search text is active. It shows the operation count and, per currency, income and expenses from `summarize`. Currencies are never added together. A currency whose sum overflows shows "—".
- Filter state lives in the ViewModel and survives rotation. It is not persisted across process death in this version.

### 5.6 Filters, search, sorting

Filter sheet (modal bottom sheet) edits one `TransactionFilter`:

| Filter | Behaviour |
|---|---|
| Type | Income, Expense, Transfer, multi-select. None selected means all three. |
| Account | Multi-select over **all** accounts including archived (history stays reachable, PRD 4.4). An operation matches if any of its entries is on a selected account. |
| Category | `CategoryTreePicker` multi-select. A selected category matches itself **and all its descendants** (PRD 6.5, 12.4), so filtering Food includes Groceries. "Uncategorized" is a separate option. |
| Tags | Multi-select plus the mode switch **Match ANY / Match ALL** (PRD 7.4). |
| Date | From and to dates, inclusive of the end day. The end is stored as the start of the next local day (half-open), computed in the device zone. |
| Amount | Optional minimum and maximum **magnitude**, so `10` finds both a +10 income and a −10 expense. Parsed with the scope currency's scale, never rounded. |
| Text | Case-insensitive substring of the description. `%` and `_` in the input are escaped. |

All filters combine with AND (PRD 15). The example "expenses from Santander, in Food, with the Debt tag, in November" is covered by account + type + category + tag + date.

**Single-currency scope** (amount filter and amount sort). Amounts in different currencies are not comparable, and no conversion is introduced for these features, so:
- The scope currency is the single currency shared by the selected accounts, or by all accounts when none is selected. If more than one currency is in scope, there is no scope currency.
- With a scope currency, the amount filter and amount sort are enabled and use it. An operation matches the amount filter if any of its entries **in that currency** has a magnitude in range. The amount sort key is the largest entry magnitude in that currency. Transfers follow the same rule through their entries: a transfer is matched and sorted by its entry in the scope currency, and a transfer with no entry in it is excluded by an active amount filter and sorts last.
- Without a scope currency, the amount fields and the amount sort options are **disabled** with the text "Select accounts that share one currency to filter or sort by amount." If an amount sort was active it falls back to date newest first. A converted comparison belongs to sub-project 5.
- If the user has set an amount range and then makes the scope multi-currency, the range is dropped and a snackbar says so.

**Sorting**: date newest first (default), date oldest first, amount high to low, amount low to high. Ties break on `occurred_at DESC, id DESC` (or the reverse for oldest first), so paging is stable.

**Stale ids**: when an account, category or tag in the filter is deleted, `pruneMissing` removes it from the filter on the next emission, so a deleted entity never leaves the list empty without explanation.

## 6. Tags

### 6.1 Rules

| # | Rule | Source |
|---|---|---|
| G1 | Tags are flat. No hierarchy, no icon. Optional color. | PRD 7.1, 7.2, 30.1 |
| G2 | A transaction has zero or more tags. A tag can be on any number of transactions. | PRD 7.3, 30.2, 30.3 |
| G3 | Tags never change an amount or a balance. Tag code paths do not touch `ledger_entry`. | PRD 7.6, 30.7 |
| G4 | Deleting a tag removes its links only. No operation is deleted or edited, and their `updated_at` is unchanged. | PRD 30.4 |
| G5 | Renaming a tag shows everywhere at once, because operations reference the tag by id. | PRD 30.5 |
| G6 | Filtering supports ANY (at least one selected tag) and ALL (every selected tag). | PRD 30.6 |
| G7 | A tag name is unique ignoring case. Creating or renaming to a name that another tag already has is refused. A case-only rename of the same tag is allowed. | Section 2 |
| G8 | Tags may be attached to income, expense and transfer, never to an opening balance. | Foundation 6.3 |
| G9 | Removing a tag from a transaction does not delete the tag. | PRD 7.3 |

### 6.2 Data layer

`TagRepository` (`data/repo`, `@Singleton`):

| Function | Behaviour |
|---|---|
| `observeAllWithUsage(): Flow<List<TagWithUsage>>` | `LEFT JOIN` count, so unused tags appear with 0. Sorted by name case-insensitively in Kotlin with a `Collator`, because SQL `ORDER BY` is not locale-aware. |
| `create(name, color, now): Long` | `TagNameRules` then G7 against all tags inside the transaction. Throws `InvalidTagNameException(error)` or `DuplicateTagNameException(existingId)`. |
| `rename(id, name)`, `setColor(id, color)` | G5, G7. Do not touch operations. |
| `delete(id)` | G4. `operation_tag` rows cascade through the FK. |
| `usageCount(id): Int` | For the delete dialog. |

Pending tags: `OperationRepository.resolveTags(tagIds, newTagNames)` runs inside the save transaction. Each new name goes through `TagNameRules.validate` first (1 to 30 characters, otherwise `InvalidTagNameException` and the whole save rolls back), then is looked up by `TagNameRules.key`. If a tag with that key exists, it is reused (another screen or an earlier save may have created it), otherwise it is created. Duplicate names inside one draft collapse to one tag. Unknown `tagIds` throw `InvariantViolationException`. The length and uniqueness rules therefore hold on every path that can create or rename a tag, not only in the Tags screen or the form.

The unique index on `tag.name` stays exact-match. Case-insensitive uniqueness is a repository rule, safe because Room serializes writes in one transaction. SQL cannot check it reliably for non-ASCII text, so `InvariantChecker` does not report case-variant duplicates. The consequence of such a file is cosmetic.

### 6.3 Tag management screen

`TagsScreen` + `TagsViewModel` replace the placeholder (route `more/tags`):
- List with a color dot (neutral when the tag has no color), name, and usage count ("12 transactions").
- FAB "Add tag" opens a dialog with name and `ColorPicker` (with a "No color" choice).
- Row menu: Rename or recolor (same dialog), Delete, Show transactions.
- Tap a row or "Show transactions" opens the Transactions tab with that tag preset as the only tag filter.
- Delete dialog: "Used in N transactions. They stay, and lose this tag." Confirm deletes. Unused tags delete without the extra text.
- Name errors from `TagNameRules` and the duplicate error show on the field, and the dialog stays open.
- Empty state explains what tags are and offers the Add action.

### 6.4 Tags in the transaction form

`TagPicker` shows the selected tags as chips with a remove icon, a text field to search existing tags, and the list of matches. Typing a name that matches no existing tag (by `key`) offers "Create '<name>'". Choosing it adds a **pending** chip (visually marked as new). On save the pending names go to the repository (6.2). Typing an existing name selects the existing tag instead of offering a duplicate. The most used tags are shown as suggestions when the field is empty.

### 6.5 Tag filter

`TagFilterPicker` is the Tags section of the filter sheet: multi-select over existing tags with the ANY/ALL switch and a one-line explanation of each mode. The ANY/ALL switch is shown only when two or more tags are selected.

### 6.6 Summary primitive and the debt example

`summarize(filter)` returns, per currency, the income sum, the expense sum (as positive money) and the number of operations, over INCOME and EXPENSE operations that match the filter. Transfers and opening balances are excluded, so transfers never inflate income or expense (PRD 8.1, 28.2). The history summary bar uses it.

With it, the PRD 7.6 scenario works at list level in this sub-project: select the Debt tag, choose November, optionally add accounts, categories and further tags with ALL, and read the total, the individual transactions, and (via the account and category filters) the split. The dedicated tag analytics view (distribution by account, category and date, charts) is sub-project 7 and reuses `TransactionFilter` and `summarize` unchanged.

## 7. Shared UI components (`ui/common`)

| Component | Notes |
|---|---|
| `TagPicker(selectedIds, pendingNames, onChange)` | Section 6.4. Used by the transaction form here and by the transfer form in sub-project 4. |
| `TagFilterPicker(selectedIds, mode, onChange)` | Section 6.5. Reused by analytics in sub-project 7. |
| `TagChip(tag)` | Color dot or tint, name. Neutral when no color. |
| `TransactionRow(item, onClick)` | Section 5.5. Reused by account detail, and later by dashboard and tag analytics. |
| `DateField`, `TimeField` | Material pickers wrapped with `TransactionDateTime`. |

All user-visible text goes in `strings.xml`. Icon-only buttons have content descriptions. Amounts and signs are readable by screen readers.

## 8. Data layer details

DAO additions:

| DAO | Additions |
|---|---|
| `OperationDao` | `updateFields(id, occurredAt, categoryId, description, updatedAt)`, `@RawQuery(observedEntities = [OperationEntity, LedgerEntryEntity, OperationTagEntity, TagEntity]) fun page(query: SupportSQLiteQuery): PagingSource<Int, OperationWithDetails>`, `@RawQuery list(query)` for `observeRecent`, `recentCategoryIds(type, limit)`, `latestAccountId()`, `observeWithDetails(id)` |
| `LedgerEntryDao` | `updateEntry(operationId, accountId, currencyCode, amountMinor, occurredAt)` |
| `OperationTagDao` | `deleteAllFor(operationId)` |
| `TagDao` | `getAll()`, `getById(id)`, `observeAllWithUsage()`, `updateDetails(id, name, color)`, `setColor(id, color)`, `deleteById(id)`, `usageCount(id)` |

`OperationWithDetails` is `@Embedded OperationEntity` plus `@Relation` lists of `LedgerEntryEntity` and of `TagEntity` (through `OperationTagEntity` as a junction). The page query is wrapped in `@Transaction` so relations are loaded in batches per page, not per row.

**Query builder.** `TransactionQuery.build(filter, sort, categoryClosure): SimpleSQLiteQuery` in `data/db`, pure (SQL text plus bind arguments, no Android types), so its output is unit-testable. `categoryClosure` is the expanded id set computed from `CategoryTree.descendantIds` by the repository. All user values are bind arguments, never concatenated.

```sql
SELECT o.* FROM operation o
WHERE o.type IN (:types)                                  -- never contains OPENING_BALANCE
  AND o.occurred_at >= :from AND o.occurred_at < :to      -- only when set
  AND (o.category_id IN (:closure) OR o.category_id IS NULL)   -- IS NULL only with includeUncategorized
  AND EXISTS (SELECT 1 FROM ledger_entry e
              WHERE e.operation_id = o.id AND e.account_id IN (:accounts))
  AND EXISTS (SELECT 1 FROM operation_tag t                -- ANY
              WHERE t.operation_id = o.id AND t.tag_id IN (:tags))
  -- ALL replaces the line above with:
  -- AND (SELECT COUNT(DISTINCT t.tag_id) FROM operation_tag t
  --      WHERE t.operation_id = o.id AND t.tag_id IN (:tags)) = :tagCount
  AND o.description LIKE :pattern ESCAPE '\'
  AND EXISTS (SELECT 1 FROM ledger_entry e
              WHERE e.operation_id = o.id AND e.currency_code = :scopeCurrency
                AND ABS(e.amount_minor) >= :min AND ABS(e.amount_minor) <= :max)
ORDER BY o.occurred_at DESC, o.id DESC
```

- Each predicate is present only when its filter is set.
- Amount sort replaces the `ORDER BY` with `(SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = :scopeCurrency) DESC, o.occurred_at DESC, o.id DESC`. `ABS` cannot overflow because stored magnitudes exclude `Long.MIN_VALUE` (Accounts validator keeps the range symmetric, and the transaction validator does the same).
- The number of bound ids is bounded by what the user selects. It stays far below SQLite's variable limit.
- `summarize` uses the same `WHERE` through a shared builder function, restricted to INCOME and EXPENSE, grouped by `e.currency_code`.

Indexes used: `operation(occurred_at, id)` (new, section 9) for the default ordering, `operation(type, occurred_at)` and `operation(category_id, occurred_at)` for selective type and category filters, `ledger_entry(operation_id)` and `ledger_entry(account_id, occurred_at, amount_minor)` for the account and amount subqueries, `operation_tag(tag_id, operation_id)` for tag filters.

## 9. Schema change (no migration)

The app has no release and development data is disposable, so the schema is changed in place and **no migration is written**.

- `OperationEntity` gets `Index(value = ["occurred_at", "id"])`, which Room names `index_operation_occurred_at_id`.
- `FinoraDatabase.VERSION` stays 1 and `ALL_MIGRATIONS` stays empty. `fallbackToDestructiveMigration` is still never used.
- `app/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json` is regenerated by the build and committed. `src/test/resources/schema-v1.expected.json` is replaced with the same content in the same commit, so `SchemaUnchangedTest` guards the new baseline. Its KDoc is updated to mention the history index.
- The scratch migration test copies the regenerated `1.json` and keeps comparing it byte for byte, so it needs no change.
- **Existing development installs** hold a v1 file without the index. Room's identity hash no longer matches, so those devices must uninstall or clear app data once. This is the same situation as the default-category seeding in Accounts 5.4. Nothing in the app tries to repair such a file.
- Why the index: without it SQLite cannot return all three types in time order from `operation(type, occurred_at)` and must sort every row for each page. With it the default history stops after the first page (NFR-18). A plan-time `EXPLAIN QUERY PLAN` test gates it (section 11).
- Once a version is released, every later schema change needs a real migration (Foundation 7). This section is an exception for the development period only.

## 10. Navigation

Routes added to `Routes`:

| Route | Screen |
|---|---|
| `transactions?accountId={accountId}&tagId={tagId}&categoryId={categoryId}` (exists as `transactions`) | History. Optional arguments preset one account, tag or category filter. |
| `transactions/new?kind={kind}&duplicateOf={duplicateOf}` | Entry screen in create mode. |
| `transactions/{operationId}` | Entry screen in edit mode. |
| `more/tags` (exists) | Tag management. |

`transactions/new` is a literal segment and wins over `transactions/{operationId}`, as `accounts/new` already does. The tab highlight uses the existing `isRouteUnder`. From Tags and Account detail, navigation to the history uses `launchSingleTop` and `restoreState = false` so presets always apply.

## 11. Testing

JVM unit tests:
- `TransactionDraftValidator`: empty, zero, negative, `.` and `,` separators, too many fractional digits for scales 0, 2 and 3, `Long` range edges including the `MIN_VALUE` exclusion, missing account or category (create versus edit of an already-uncategorized operation), description length 500 and 501, `DATE_IN_FUTURE` at now, now + 1 ms and now − 1 ms.
- `CurrencyReassignment` (pure helper that builds the confirmation text data): same currency needs no confirmation, different currency does, 100.00 PLN → 100.00 USD keeps the number, 100 JPY → 100.00 PLN, 12.50 PLN → JPY is `AMOUNT_TOO_PRECISE`, and the helper never takes a rate argument.
- `TagNameRules`: trim, whitespace collapse, empty and whitespace-only, exactly 30 and 31 characters (including after collapse and with surrogate pairs counted by code points), `key` equality for `Debt`/`debt`/`DEBT`, Cyrillic and Polish letters (`Дебет`/`дебет`, `Żabka`/`żabka`), and that a `Locale` such as Turkish does not change `key`.
- `TransactionDateTime`: combine and split round trip, a DST gap time, a DST overlap time, picker UTC midnight to local day, `isFuture` boundaries, `latestSelectableDay` around local midnight in a non-UTC zone.
- `DayGrouping`: today, yesterday, older, across midnight in a non-UTC zone.
- `TransactionQuery.build`: SQL and arguments for each filter alone and combined, ANY versus ALL shape, `%` and `_` escaping, amount sort expression, no user text in the SQL string, argument count equals placeholder count.
- `FilterScope`: single currency for selected accounts, for no selection with one currency overall, none with mixed currencies, `pruneMissing` removing deleted ids.
- `AmountParser` extraction keeps all existing `AccountDraftValidatorTest` cases green.

Instrumented Room tests (`TransactionRepositoryTest`, `TagRepositoryTest`, `OperationRepositoryEditTest`):

Write path
- Create income and expense: signs are +/−, balance changes, `InvariantChecker` clean, `created_at = updated_at = now`.
- Create with existing tags and with a new tag name: tag rows and links exist, all in one transaction. A failure after tag creation (for example category type mismatch) rolls the new tag back.
- Update: amount, category, date, description and tags change, `created_at` unchanged, `updated_at` changes, entry `occurred_at` follows the operation, balance reflects the new amount, checker clean.
- Update account to another active account of the same currency succeeds without the confirmation flag.
- **Cross-currency reassignment (T12)**: moving 100.00 PLN to a USD account without `confirmCurrencyChange` throws `CurrencyChangeNotConfirmedException` and changes nothing. With the flag, `amount_minor` keeps its number scaled to the new currency (10000 stays 10000 for two-decimal currencies), `currency_code` is USD, the PLN balance drops by 100.00 and the USD balance rises by 100.00 (income) or the reverse (expense), `InvariantChecker` is clean, and no table other than `operation` and `ledger_entry` and `operation_tag` is written (rates are untouched). A scale mismatch (a JPY target with a fractional amount) never reaches the repository and is covered in the validator tests. The composite FK is satisfied in the same UPDATE.
- Update to another archived account throws, even with the confirmation flag. Keeping an archived account succeeds. Switching from an archived original to an active account succeeds.
- **Future dates (T11)**: create and update with `occurredAt = now + 1` throw `FutureDateException` and change nothing, `occurredAt = now` succeeds, and `changeOccurredAt` on an income/expense behaves the same. A back-dated operation with `moveOpeningBalanceBack` still works. Opening-balance operations keep their own date rules.
- Update date before the account's opening date throws `OpeningBalanceOrderException`, changes nothing, and succeeds with `moveOpeningBalanceBack = true` (balance unchanged, checker clean). Same for create.
- Combined: a cross-currency move to an account whose opening balance is dated after the operation's date throws `OpeningBalanceOrderException` and writes nothing. With both `confirmCurrencyChange` and `moveOpeningBalanceBack` it succeeds in one transaction, and the checker is clean.
- `updateOperation` and `deleteOperation` refuse TRANSFER and OPENING_BALANCE.
- Delete removes entry and links, balance recalculated, tag rows remain.
- Balance overflow on create and on update throws `MoneyOverflowException` and writes nothing.
- Creating on an archived account throws (existing rule), editing an operation that already lives there succeeds.
- Changing category, tags or date leaves `amount_minor` and `currency_code` identical (T3). Only an account change with confirmation may change `currency_code` (T12).
- **Category deletion with operations (5.8)**: using `CategoryRepository.delete`, operations of a deleted category become Uncategorized (`NULL`) or move to the chosen category, none is deleted, amounts are unchanged, an uncategorized operation appears in the history with the Uncategorized filter and can be updated without a category, and a create or duplicate without a category is refused (`CATEGORY_MISSING` at validator level, and `TransactionRepository.create` throws `InvariantViolationException` for a null category).

History queries (seeded data, several currencies, accounts, categories, tags)
- Default list is newest first and excludes OPENING_BALANCE. Equal timestamps order by id.
- Each filter alone: type, account (incl. an archived account), date range (half-open boundary at exactly `to`), text (including `%`, `_`, and non-ASCII case), amount range with a scope currency.
- **Category closure**: filtering a parent returns its own operations and those of children and grandchildren. Uncategorized option returns only NULL-category operations.
- **Tags**: ANY returns the union, ALL returns the intersection, ALL with one tag equals ANY with one tag, an operation with no tags is never returned by a tag filter, a deleted tag is pruned.
- Combined filters: the PRD 15 example (Santander, Food, Debt, November) returns exactly the expected rows.
- Amount sort in a single-currency scope orders by magnitude across income, expense and transfer entries of that currency. With mixed currencies the repository refuses the amount sort and the amount filter (`IllegalArgumentException`), and the UI never offers them.
- A transfer is matched by the amount filter only through its entry in the scope currency.
- Transfers appear in the list when no type filter is set and are excluded by an income/expense type filter. They are never part of `summarize`.
- `summarize`: per-currency income and expense, transfers and opening balances excluded, two currencies kept apart, overflow mapped to `MoneyOverflowException`, equals the sum of the listed rows for the same filter.
- Renaming a category, account or tag changes the item shown by `observePage` without reloading.
- `EXPLAIN QUERY PLAN` for the unfiltered newest-first page uses `index_operation_occurred_at_id` and no temp B-tree. For a tag filter it uses `operation_tag(tag_id, operation_id)`.
- Performance sanity: 50,000 operations with entries and tags, first page and a tag-filtered first page each complete within a generous bound (asserted loosely, for example under 500 ms on the test device) to catch accidental full sorts.
- **Unicode search gate**: with rows "Кофе", "Żabka", "Straße", searching `кофе`, `żabka` and `ZABKA` behaves as specified in section 13. This test decides whether the fallback in section 13 is needed.

Tags
- Create, duplicate by case (`Debt` then `debt`) throws `DuplicateTagNameException`, exact duplicate throws, create with the same name inside one draft yields one tag. The same checks hold when the second tag arrives through `newTagNames` of an operation write (the whole write rolls back on failure).
- Name length enforced at repository level: 0 and whitespace-only throw `InvalidTagNameException`, 30 characters succeed, 31 throw, for `create`, `rename` and `newTagNames`.
- Rename updates all transactions' displayed tag, rename to another tag's name (any case) throws, case-only rename of itself succeeds.
- **Tag management does not touch operations (decision 7)**: with seeded operations carrying a tag, `rename`, `setColor` and `delete` leave every `operation.updated_at`, amount and balance unchanged. Changing a transaction's tag set through `updateOperation` does update that operation's `updated_at`.
- Delete removes links, keeps operations, balance unchanged.
- Tag on an OPENING_BALANCE operation is rejected by `setTags`, `insertOperation` and `addTag`.
- Tag on a TRANSFER operation is accepted and filterable.
- Usage counts include unused tags as 0.

Schema
- `SchemaUnchangedTest` passes against the regenerated baseline. A JVM or instrumented check asserts `FinoraDatabase.VERSION == 1`, `ALL_MIGRATIONS` is empty and no `fallbackToDestructiveMigration` is used (the existing `NoDestructiveMigrationTest` and `MigrationInfrastructureTest` stay unchanged). No new migration test is added.

UI/ViewModel tests (JVM with fake repositories)
- Entry form: default account selection rules, type switch clears a mismatched category, save disabled until valid, duplicate prefill including archived-account case and uncategorized source, opening-order dialog flow.
- Cross-currency edit: choosing an account of another currency keeps the typed text, shows the currency-change dialog on Save, cancel writes nothing and keeps the form, confirm writes with `confirmCurrencyChange = true`, switching back to the original account removes the dialog, a scale mismatch disables Save with `AMOUNT_TOO_PRECISE`, the dialog text contains both amounts and the "not a conversion" statement.
- Date and time: picker maximum is today, a future time on today disables Save, a `FutureDateException` from the repository is shown on the date field without losing form data.
- Filter state: preset arguments (`accountId`, `tagId`, `categoryId`), drop of the amount range and fall-back of the amount sort when the scope becomes multi-currency, pruning of deleted ids.
- Category delete dialog: "Review transactions" navigates with the category preset.

Manual checklist (device or emulator with freshly cleared app data, because the schema changed without a migration):
1. Add an expense with three taps and a number (default account, recent category chip, amount, Save). Balance on Accounts changes by that amount.
2. Add an income. Edit its amount, category, date and account. Move it to another account of the same currency (no dialog), then to an account of another currency: the number stays, the currency label changes, and Save shows "100.00 PLN will become 100.00 USD. This is not a currency conversion." Cancel leaves it unchanged, confirm applies it and both balances change by the nominal amount. Try moving 12.50 PLN to a JPY account: Save is disabled with a precision error. Another archived account is never offered.
3. Archive an account and confirm: new transaction cannot select it, an existing one on it can still be edited and keeps it.
4. Back-date a transaction before an account's initial balance and confirm the dialog and its effect.
5. Create tags Debt, Shared and Travel, one of them directly from the entry form. Cancel a form after typing a new tag name: the tag does not exist. Try `debt` after creating `Debt`, an empty name and a 31-character name: each is refused.
6. Tag several transactions. Filter by Debt, then Debt + Shared with ANY and ALL, and compare counts. Add account, category and a November date range, then check the summary bar against the visible rows.
7. Rename and recolor a tag, check that the history shows the new name and that the transactions' modified time is unchanged. Delete a tag and check the transactions remain and balances are unchanged.
8. Search for a description in Cyrillic and Polish with different letter case.
9. Sort by amount with accounts of one currency, then select accounts of two currencies and check the amount controls are disabled with the explanation and the sort falls back to date.
10. Delete a category that has direct transactions and subcategories: the dialog states the number of direct transactions, offers "Review transactions", another category, or Uncategorized, and subcategories move up one level. Choose Uncategorized: the transactions show as Uncategorized, remain editable and are found by the Uncategorized filter. Creating a new transaction still requires a category.
11. Duplicate a transaction and cancel, then duplicate and save. The copy is dated now.
12. Try to pick a future day in the date picker (not selectable) and a later time today (rejected).
13. View a transfer in the history (read-only, no tap action, not in the summary bar).
14. Create 500+ transactions (debug seeder or a test build), scroll the history and check smooth paging. Large font, dark theme, rotation during a form edit.

## 12. Done when

1. The Transactions tab, the entry screen and the Tags screen replace their placeholders, and all flows in sections 5 and 6 work. Account detail shows its transactions.
2. All tests in section 11 pass, together with every earlier test.
3. The database stays at version 1 with the regenerated `1.json` and its pinned test copy committed, no migration exists, and `SchemaUnchangedTest` passes against the new baseline.
4. PRD acceptance items:
   - TRANSACTIONS: all five (add income and expense, automatic balance, edit and delete, history, search and filters).
   - TAGS: create tags, several tags per transaction, ANY/ALL filtering are met. "View statistics by tag" and "analyze tags by accounts and categories" are met at list and summary level through the filter combinations in 6.6. The dedicated analytics view stays open until sub-project 7.
   - CATEGORIES item "add transactions directly to a parent category" is now exercised end to end.
5. Cross-currency reassignment, future-date refusal, tag name rules and the category-delete review flow behave as specified in 5.7, 5.3, 6.2 and 5.8.

## 13. Risks

- **Unicode case-insensitive search.** SQLite's built-in `LIKE` folds ASCII only. Android builds SQLite with ICU, which may replace `LIKE` with a Unicode-aware version, but this must be verified on the minimum supported API (33) and on a current one. The Unicode search gate test decides. If it fails, the fallback is a `description_search` column on `operation` (lower-cased with `Locale.ROOT` folding in Kotlin on every write) searched with `LIKE`. Because there is no migration during development, that column is added to the same v1 schema. That is why the search test is written first at plan time.
- **Schema changed without a migration.** Any development device that ran an earlier build must clear app data or reinstall. This is acceptable only before the first release. Before that release, the schema is frozen as the real v1 and the rule "every change gets a migration" applies from then on.
- **Cross-currency reassignment changes the meaning of money.** T12 is deliberate and user-confirmed, but it silently rewrites the real-world value of a transaction. The confirmation dialog is the only protection, so its text is part of the acceptance criteria and is covered by tests. The old currency is not preserved on the operation. A user who confirms by mistake must edit the transaction again.
- **Cross-currency reassignment and transfers.** Applying the same principle to transfer sides (decision 2, "where applicable") affects the pairing rules of Transfer Rules 28.3 and 28.4 and the effective rate display. It is deferred to sub-project 4, which must define what happens to the other side and to the fee.
- **Amount comparisons across currencies.** The single-currency scope is a deliberate limit. Users with mixed accounts cannot sort or range-filter by amount until sub-project 5 offers estimates, and the UI says so.
- **Large OFFSET paging.** Deep scrolling in a very long history walks the index to the offset. Acceptable for a personal ledger, and the stable ordering allows a later switch to keyset paging without a schema change.
- **Sort by amount cost.** The correlated subquery sorts all matching rows. Fine for the expected sizes and covered by the performance test. If measurements disagree, a per-entry magnitude column can be added (a schema change, so a migration after release).
- **Existing tests with future dates.** Foundation and Accounts tests may insert income or expense with timestamps after the injected `now`. The plan must review them and fix the test setup, not the rule (T11).
- **Clock and time zone.** "Future" is judged against the device clock at save time. A user who moves the clock or changes zones can create dates that look odd later. No guard is added.
- **Opening-balance order on edit.** Editing the date or account of an old transaction can hit `OpeningBalanceOrderException`. The form handles it (5.3). Sub-project 4 and 6 forms must do the same.
- **Tag name case rule is repository-only.** An imported file with `Debt` and `debt` is accepted by the checker. Harmless but visible. Sub-project 8 may add a Kotlin-side check.
- **Filter state not persisted.** It resets after process death. Acceptable for v1.
- **Category and tag deletes while a filter is active.** Handled by pruning, but the user sees the filter chip disappear. Acceptable.
- **Delete without undo.** Deletion is confirmed but not undoable. Undo would need a soft-delete or snapshot and is not in v1.
- **Tag merge.** Renaming into an existing name is refused. Users with accidental near-duplicates must retag manually. A merge action can be added later without schema change.

## 14. Confirmed decisions

Answered by the user on 2026-11-04. Each is applied in the section shown.

1. **Uncategorized and category deletion** (2, 5.3, 5.8). Development database can be recreated. Deleting a category with direct operations tells the user, who either reassigns them or moves them to Uncategorized. Operations are never deleted. Immediate subcategories move up one level with their descendants and operations (existing Accounts rule). Creating or duplicating an income or expense still requires a category.
2. **Changing an operation's account and currency** (2, T4, T12, 5.7). Any active account, any currency. A different currency keeps the nominal number and replaces the currency after an explicit "not an FX conversion" confirmation (100 PLN becomes 100 USD). No rates are fetched or used. The operation may keep an archived account it already has, but cannot move to another archived account, and new operations cannot use archived accounts. The same principle for transfers is deferred to sub-project 4.
3. **Amount filter and sort** (5.6). Only in a single-currency scope, disabled otherwise, no conversion.
4. **Database** (2, 9). No v1 → v2 migration. The v1 schema is edited in place, the version stays 1, and tests and the pinned schema are updated directly.
5. **Future dates** (T11, 5.3, 11). Not allowed. Pickers refuse them and the repository enforces it with `FutureDateException`.
6. **Tag names** (2, G7, 6.2). Unique ignoring case, 1 to 30 characters, enforced in the repository on every create, rename and inline-create path.
7. **Tag changes and timestamps** (2, G4, 6.2). Rename, recolor and delete do not touch `operation.updated_at`.
8. **Transfers** (1, 5.5, 5.6, 6.6). Visible in history, read-only here, excluded from income and expense summaries, and subject to the single-currency rule for amount filter and sort.
9. **Tag analytics** (1, 6.6). Dedicated screens stay in sub-project 7. This sub-project provides tag assignment, ANY/ALL filtering and data access.

## 15. Hand-offs

- Sub-project 4 (transfers): add a Transfer segment to the entry screen that routes to its own form, attach row taps for TRANSFER in `TransactionRow`, widen `updateOperation`, `deleteOperation` and the future-date check to TRANSFER together with the fee link, reuse `TagPicker`, `AccountPicker` (with per-side `originalAccountId`) and the opening-order dialog. Apply the nominal-number currency reassignment principle (T12, 5.7) to a transfer side where it makes sense, including its confirmation text, and define what happens to the other side, the effective rate and the fee. Define the transfer amount shown in the list and used by amount filter and sort for cross-currency transfers (the entry in the scope currency, 5.6).
- Sub-project 5: add estimate-based amount comparison and sorting across currencies, and mark converted totals in the summary bar.
- Sub-project 6: generate recurring transactions through `OperationRepository.insertOperation` with `tagIds`. Generated dates are never in the future (T11), so a schedule produces an occurrence only when its date is reached. Define behaviour for generated dates before an opening balance.
- Sub-project 7: build tag and category analytics on `TransactionFilter` and `summarize`, add the distribution queries, and use `observeRecent` for the dashboard.
- Sub-project 8: export uses the same filter model for CSV, and import validation may add a Kotlin-side check for case-variant tag names.
