# Finora: Transfers Design

Date: 2026-11-05
Status: Draft for user review (revision 2: simplified v1 transfers)
Sub-project: 4 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD sections 8, 28, 33). References like "Foundation 6.4" refer to the Foundation design, "Accounts 4.7" to `2026-11-04-finora-accounts-categories-design.md`, and "Operations T12" to `2026-11-04-finora-operations-tags-design.md`.

## 1. Scope

In scope:
1. **Transfers**: create, edit, delete, duplicate (PRD 8.1 to 8.4, 28).
2. **Two independently entered amounts**: every transfer—both same-currency and cross-currency—requires the user to enter two independent amounts:
   - **Amount Sent**: amount debited from the source account.
   - **Amount Received**: amount credited to the destination account.
   - No automatic syncing or copying of amounts between fields (e.g. allows 100 PLN → 100 PLN or 100 PLN → 95 PLN where an intermediary deducted a fee).
3. **Cross-currency effective exchange rate**: for transfers between accounts with different currencies, display the effective exchange rate calculated on the fly from the two user-entered amounts (`1 Source = X Dest` and inverse). Purely informational.
4. **Transfer categories**: independent category tree of type `TRANSFER`, required on creation, supporting hierarchy up to 3 levels, direct assignment, and aggregation (PRD 6.1, 8.6, 28.7, 29.1).
5. **Transfer integrity**: atomic representation of source debit and destination credit. Both sides are always updated and deleted together in one transaction (PRD 8.7, 28.8, NFR-07).
6. **History, search, and filter integration**: clickable transfer rows opening the transfer editor, distinct visual styling (swap icon, "From → To" subtitle, stacked debited and credited amounts), filtering by type `TRANSFER`, tag filtering on transfers, and single-currency amount filtering/sorting (PRD 5.5, 8.1, 15).
7. **UI flows**: dedicated Transfer editor (`TransferEditScreen`), "Transfer" segment in transaction entry, "More → Transfers" navigation route, account swapping shortcut, and per-side account pickers respecting archived account rules (Accounts 4.7).

Out of scope here, with the sub-project that owns it:
- Separate transfer fee entity/operation linking (explicitly excluded for v1; any difference between sent and received amounts simply affects account balances without separate expense records).
- External exchange rate retrieval, rates caching, and rate provider integration (5).
- Display Currency conversion, multi-currency valuation estimates, and "estimate" badges (5).
- Recurring transfer schedules (6).
- Transfer-specific analytics, cash flow vs transfer volume charts, and account transfer drill-down screens (7).
- CSV/database import and export of transfers (8).

Foundation, Accounts/Categories, and Operations/Tags already ship: `account`, `category`, `operation`, `ledger_entry`, `tag`, `operation_tag` tables and DAOs, `OperationRepository`, `TransactionRepository`, `TagRepository`, `InvariantChecker`, `AccountPicker`, `CategoryTreePicker`, `TagPicker`, and `TransactionRow`.

## 2. Decisions

| Topic | Decision |
|---|---|
| Schema | **No schema change.** Database stays at version 1, `app/schemas/1.json` is unchanged. Foundation already provides `operation`, `ledger_entry`, `category`, and `operation_tag`. No new column, index, or table is added. |
| Transfer data model | One `operation` row with `type = TRANSFER`, `category_id` (must be `TRANSFER` type), `occurred_at`, `description`. Exactly two `ledger_entry` rows: source (`amount_minor < 0`) and destination (`amount_minor > 0`). |
| Transfer fees | **No separate fee entity or linked operation in v1.** The user enters Amount Sent and Amount Received independently. Any difference (e.g. 100 PLN sent, 95 PLN received) directly affects the respective account balances and is not recorded as a separate expense. |
| Amount entry | **Always two independent amount fields**: Amount Sent (debited from source) and Amount Received (credited to destination). Never automatically copied or forced equal, even when source and destination share the same currency. |
| Account pairing | Source and destination accounts must be **different accounts** (`source_id != dest_id`). Enforced in UI and repository (PRD 28.1). |
| Exchange rates | For cross-currency transfers, calculated on the fly from user inputs for display only (`1 Source = X Dest` and inverse). Never fetched externally or overwritten by external rates (PRD 8.4, 28.5). Not displayed for same-currency transfers. |
| Category | Required when creating or duplicating a transfer. Picker is filtered to `CategoryType.TRANSFER`. Default seeded transfer categories from Accounts 5.4 are used. Uncategorized allowed only on edits if category was previously deleted. |
| Account swap | A quick one-tap action on the form swaps source and destination accounts. In cross-currency transfers, amounts swap positions to match their account currencies. |
| Account reassignment on edit | Allowed to any active account (or keeping the existing account even if archived, Accounts 4.7). Moving to another currency keeps the nominal number and replaces the currency after explicit confirmation (T12). |
| Date & time | Stored as instant, defaults to now. **Future dates and times are refused** (`occurred_at <= now`, `FutureDateException`). Dated before opening balance of either account triggers `OpeningBalanceOrderException` with prompt to move opening balance(s) back. |
| Tags | Supported on transfers (PRD Part X). Transferred operations link to `operation_tag`. |
| Cash flow impact | Transfers between accounts do not affect net cash flow or income/expense totals (PRD 8.1, 11.5, 28.2, 28.9). `summarize()` excludes `TRANSFER` operations. |

## 3. Changes to existing code

| Change | Reason |
|---|---|
| `OperationRepository`: add `insertTransfer(draft, ...)` and `updateTransfer(id, draft, ...)`; widen `deleteOperation(id)` to support `TRANSFER`. | Dedicated, atomic write path for transfers and their 2 ledger entries. |
| `OperationRepository.validate`: verify transfer rules (2 entries on different accounts, 1 negative and 1 positive, category type TRANSFER). | Enforces transfer data integrity at the repository layer. |
| `TransactionRepository`: add `observeTransfer(id)`, `createTransfer(draft)`, `updateTransfer(id, draft)`, and `deleteTransfer(id)`. | Clean read and mutation API for transfers. |
| `TransactionRow`: enable click action on `TRANSFER` rows to open the transfer edit screen. Display source and destination accounts and both debited/credited amounts. | Makes transfer history interactive and clear. |
| `TransactionEditScreen`: add "Transfer" segment to the type selector when creating a transaction, navigating to `TransferEditScreen`. | Unified transaction entry experience (PRD 18, 19). |
| `Routes`: add `Routes.TRANSFER_NEW`, `Routes.TRANSFER_EDIT`, and wire `Routes.TRANSFERS` in `FinoraNavHost`. | Navigation support. |
| `InvariantChecker`: add SQL checks for transfer invariants (exactly 2 entries, opposite signs, different accounts, correct category type, timestamp match). | Data integrity verification. |

## 4. Domain (pure Kotlin, JVM-testable)

```kotlin
data class TransferDraft(
    val sourceAccountId: Long,
    val destAccountId: Long,
    val sentAmountMinor: Long,          // positive magnitude in source currency
    val receivedAmountMinor: Long,      // positive magnitude in dest currency
    val categoryId: Long?,              // must be CategoryType.TRANSFER
    val occurredAt: Long,
    val description: String?,
    val tagIds: Set<Long> = emptySet(),
    val newTagNames: List<String> = emptyList(),
)

data class TransferItem(
    val id: Long,
    val occurredAt: Long,
    val description: String?,
    val category: Category?,
    val categoryPath: List<Category>,
    val tags: List<Tag>,
    val sourceAccount: Account,
    val destAccount: Account,
    val sentMoney: Money,
    val receivedMoney: Money,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class TransferDraftError {
    SAME_ACCOUNTS,
    SOURCE_ACCOUNT_MISSING,
    DEST_ACCOUNT_MISSING,
    SENT_AMOUNT_EMPTY,
    SENT_AMOUNT_INVALID,
    SENT_AMOUNT_TOO_PRECISE,
    SENT_AMOUNT_NOT_POSITIVE,
    SENT_AMOUNT_OUT_OF_RANGE,
    RECEIVED_AMOUNT_EMPTY,
    RECEIVED_AMOUNT_INVALID,
    RECEIVED_AMOUNT_TOO_PRECISE,
    RECEIVED_AMOUNT_NOT_POSITIVE,
    RECEIVED_AMOUNT_OUT_OF_RANGE,
    CATEGORY_MISSING,
    DESCRIPTION_TOO_LONG,
    DATE_IN_FUTURE,
}
```

Pure helpers in `domain/operation`:

- `TransferDraftValidator.validate(draft, sourceCurrency, destCurrency, now)`: returns a set of `TransferDraftError`. Ensures valid positive amounts for both fields, differing source/destination accounts, required transfer category, and no future dates.
- `EffectiveRateCalculator.calculate(sentMoney, receivedMoney)`: returns a pair of formatted rates (e.g. `1 EUR = 1.0850 USD` and `1 USD = 0.9217 EUR`) using `BigDecimal` division. Returns `null` if source and destination currencies are identical, or if either amount is zero.
- `TransferAmountParser`: parses and validates positive decimal amounts against respective currency scales using `AmountParser`.

## 5. Transfer Operations & Business Rules

### 5.1 Rules

| # | Rule | Source |
|---|---|---|
| R1 | A transfer connects two distinct accounts (`sourceAccountId != destAccountId`). | PRD 8.2, 28.1 |
| R2 | Transfers are not income or expenses and do not affect net cash flow. Source balance decreases by sent amount; destination balance increases by received amount. | PRD 8.1, 11.5, 28.2, 28.9 |
| R3 | Every transfer requires two independently entered amounts: Amount Sent (source currency) and Amount Received (dest currency). | Product decision |
| R4 | For same-currency transfers, sent and received amounts may be equal (e.g. 100 PLN → 100 PLN) or different (e.g. 100 PLN → 95 PLN). Any difference directly affects account balances and is not recorded as an expense. | Product decision |
| R5 | For cross-currency transfers, both native amounts are preserved as entered. Neither is overwritten by external exchange rates. | PRD 8.4, 28.4, 28.5 |
| R6 | Transfers must belong to a category of type `TRANSFER`. | PRD 8.6, 28.7, 29.1 |
| R7 | Deleting or editing a transfer updates both accounts together in one atomic transaction. | PRD 8.7, 28.8, NFR-07 |
| R8 | Transfers cannot be dated in the future (`occurredAt <= now`, `FutureDateException`). | Operations T11 |
| R9 | Transfer date cannot precede the opening balance date of either the source or destination account, unless `moveOpeningBalanceBack` is confirmed. | Accounts 4.6 |
| R10 | Moving an entry to an account of another currency retains the nominal number and replaces the currency after explicit user confirmation (T12). | Operations T12 |
| R11 | Tagging a transfer attaches tags to the transfer operation (`operation_tag`). Tags do not affect balances. | PRD 7.3, Part X |
| R12 | Pre-check balance overflow with `Math.addExact` before executing writes. Overflow throws `MoneyOverflowException` and writes nothing. | Foundation 5.2 |

### 5.2 Write Path in `OperationRepository`

All operations run inside `db.withTransaction`:

| Function | Behaviour |
|---|---|
| `insertTransfer(draft, moveOpeningBalanceBack, now)` | 1. Validate `sourceId != destId`, active accounts, no future date, valid positive amounts.<br>2. Check opening balance dates on source and dest accounts; move back if confirmed.<br>3. Pre-check balance overflow on source (`balance - sent`) and dest (`balance + received`).<br>4. Insert `TRANSFER` operation with `occurred_at`, `category_id`, `description`, `created_at = now`, `updated_at = now`.<br>5. Insert 2 `ledger_entry` rows: source (`-sentAmountMinor`) and dest (`+receivedAmountMinor`).<br>6. Link tags to the transfer operation.<br>7. Return transfer operation ID. |
| `updateTransfer(id, draft, moveOpeningBalanceBack, confirmCurrencyChange, now)` | 1. Load existing transfer and its 2 entries.<br>2. Validate source/dest accounts (active or own original archived accounts), dates, amounts.<br>3. If currency changed on either side without confirmation, throw `CurrencyChangeNotConfirmedException`.<br>4. Update transfer operation row (`occurred_at`, `category_id`, `description`, `updated_at = now`).<br>5. Update the 2 ledger entries with new account IDs, currency codes, amounts, and `occurred_at`.<br>6. Replace tag links. |
| `deleteTransfer(id)` | 1. Load transfer operation (must be `type == TRANSFER`).<br>2. Delete operation row (cascades both entries and tag links).<br>3. Recalculates both affected account balances cleanly. |

## 6. UI & User Experience

### 6.1 Transfer Entry Screen (`TransferEditScreen`)

`TransferEditScreen` + `TransferEditViewModel` (immutable `StateFlow` state):

- **Header / Navigation**: Title "New Transfer" or "Edit Transfer". Back navigation, Save action in top bar, and overflow menu in edit mode (Duplicate, Delete).
- **Account Pickers**:
  - **From (Source Account)**: `AccountPicker(originalAccountId = sourceId)`.
  - **Swap Button**: Centered icon button between From and To to swap source and destination with one tap.
  - **To (Destination Account)**: `AccountPicker(originalAccountId = destId)`.
  - Selecting the same account for From and To immediately highlights an error and disables Save.
- **Amount Section (Always Two Fields)**:
  - **Amount Sent**: Input field displaying the source account currency symbol/code.
  - **Amount Received**: Input field displaying the destination account currency symbol/code.
  - **Effective Rate Banner**: Displayed only when source and destination currencies differ and both amounts are non-zero (e.g. `1 EUR = 1.0850 USD · 1 USD = 0.9217 EUR`). Hidden for same-currency transfers.
- **Category Picker**: `CategoryTreePicker` in `Assign` mode filtered to `CategoryType.TRANSFER`. Default transfer category preselected or chosen from recent chips.
- **Date & Time**: `DateTimeField` defaulting to now. Rejects future dates/times.
- **Description & Tags**: Optional description text field (max 500 chars) and `TagPicker`.
- **Save Flow**:
  1. Validates all fields using `TransferDraftValidator`.
  2. If currency reassignment occurred on an edited side without manual amount change: prompts confirmation dialog (T12).
  3. If date is before an account's opening balance: prompts `OpeningBalanceOrderException` dialog to move opening balance(s) back.
  4. Saves atomically and navigates back.

### 6.2 Entry Navigation & Routing

- In `TransactionEditScreen`, the top kind selector has segments: **Expense | Income | Transfer**.
- Selecting "Transfer" navigates to `TransferEditScreen`.
- FAB on Transactions tab: standard tap opens Expense entry; extended/speed-dial FAB offers "Add Transaction" and "Add Transfer".
- Route `more/transfers` in More menu opens Transactions preset with `types = setOf(OperationType.TRANSFER)`.

### 6.3 Transaction History Integration

- `TransactionRow` displays transfer items:
  - Leading icon: `Icons.Default.SwapHoriz` in primary/neutral tint.
  - Title: Transfer category name (or description if set).
  - Subtitle: `Source Account → Dest Account`.
  - Trailing amounts: Stacked amounts showing `−<sentMoney>` in error/expense color and `+<receivedMoney>` in income/green color.
  - Tapping a transfer row opens `TransferEditScreen` with `operationId`.

## 7. Data Layer Details

### 7.1 Schema

Database schema remains at version 1 with no alterations.

`OperationEntity` and `LedgerEntryEntity` are used as defined in Foundation:
- `operation`: `id`, `type`, `occurred_at`, `category_id`, `description`, `opening_account_id`, `created_at`, `updated_at`.
- `ledger_entry`: `id`, `operation_id`, `account_id`, `currency_code`, `amount_minor`, `occurred_at`.

### 7.2 DAO Methods (`Daos.kt`)

Existing `OperationDao` and `LedgerEntryDao` methods are utilized:
- `OperationDao.insert`, `updateFields`, `deleteById`, `getById`.
- `LedgerEntryDao.insert`, `updateEntry`, `entriesOf`.

### 7.3 `TransactionRepository` & `TransferRepository`

`TransferRepository` (`data/repo`, `@Singleton`):

| Method | Description |
|---|---|
| `observeTransfer(id: Long): Flow<TransferItem?>` | Loads transfer operation, its 2 ledger entries, category path, and tags. |
| `create(draft: TransferDraft, moveOpeningBalanceBack: Boolean): Long` | Delegates to `OperationRepository.insertTransfer`. |
| `update(id: Long, draft: TransferDraft, moveOpeningBalanceBack: Boolean, confirmCurrencyChange: Boolean)` | Delegates to `OperationRepository.updateTransfer`. |
| `delete(id: Long)` | Delegates to `OperationRepository.deleteTransfer`. |

### 7.4 History & Summary Integration

- `TransactionQuery.build`: transfer operations are queried by `o.type = 'TRANSFER'`.
- `summarize(filter)`: aggregates `INCOME` and `EXPENSE` only; `TRANSFER` operations are excluded and do not inflate income or expenses.

## 8. Invariant Checker Additions

Added to `InvariantChecker.checks`:

| Check | Query / Assertion |
|---|---|
| `transfer has exactly two entries` | Any `operation` with `type = 'TRANSFER'` that does not have exactly 2 `ledger_entry` rows. |
| `transfer entries on same account` | Any `operation` with `type = 'TRANSFER'` whose 2 `ledger_entry` rows have `e1.account_id = e2.account_id`. |
| `transfer entries have invalid signs` | Any `TRANSFER` operation whose entries are not (one negative and one positive). |
| `transfer category type mismatch` | Any `TRANSFER` operation whose `category_id` references a category with `type != 'TRANSFER'`. |

## 9. Migrations & Schema

- **No migration and no schema changes.**
- `FinoraDatabase.VERSION` stays 1, `ALL_MIGRATIONS` stays empty.
- `app/schemas/.../1.json` and `src/test/resources/schema-v1.expected.json` remain unchanged.
- `SchemaUnchangedTest` passes against the existing baseline.

## 10. Navigation

Routes in `Routes`:

| Route | Screen |
|---|---|
| `transfers/new?duplicateOf={duplicateOf}` | `TransferEditScreen` in create mode. |
| `transfers/{operationId}` | `TransferEditScreen` in edit mode. |
| `more/transfers` | `TransactionsScreen` preset with `types = setOf(TRANSFER)`. |

Helper navigation extensions:
- `Routes.transferNew(duplicateOf: Long? = null) = "transfers/new" + (duplicateOf?.let { "?duplicateOf=$it" } ?: "")`
- `Routes.transferEdit(id: Long) = "transfers/$id"`

## 11. Testing

JVM Unit Tests:
- `TransferDraftValidatorTest`: same accounts error, missing accounts, sent/received amount parsing, negative/zero/overflow values, scale mismatch per currency, missing transfer category, future date rejection.
- `EffectiveRateCalculatorTest`: equal currencies return null, zero amounts return null, cross-currency calculations (PLN/USD, EUR/USD, JPY/PLN) produce exact formatted rates in both directions.
- `TransferAmountParserTest`: parsing for currencies with scale 0, 2, 3.

Instrumented Room Tests (`TransferRepositoryTest`, `OperationRepositoryTransferTest`):
- **Same-currency transfer with equal amounts**: 100 PLN sent, 100 PLN received. Source decreases by 100, dest increases by 100. `InvariantChecker` clean.
- **Same-currency transfer with differing amounts**: 100 PLN sent, 95 PLN received. Source decreases by 100, dest increases by 95. `InvariantChecker` clean.
- **Cross-currency transfer**: 1000 PLN sent, 250 USD received. PLN balance decreases by 1000, USD balance increases by 250. Native values preserved.
- **Update transfer**: change sent/received amounts, date, category, tags. Both balances recalculate correctly.
- **Delete transfer**: deleting transfer removes both entries; both account balances revert cleanly.
- **Opening balance order**: transfer dated before source or destination opening balance throws `OpeningBalanceOrderException`; succeeds with `moveOpeningBalanceBack = true`.
- **Archived account rules**: creating transfer on archived account rejected; editing existing transfer on archived account allowed; moving to another archived account rejected.
- **Future date rejection**: transfer dated after `now` throws `FutureDateException`.
- **History & summaries**: transfer appears in transaction history list; `summarize()` excludes transfer principal from income/expense totals.
- **Invariant checks**: raw SQL violations (same account, wrong entry count, wrong category type) flagged by `InvariantChecker`.

UI / ViewModel Tests (JVM with fake repositories):
- `TransferEditViewModelTest`: account selection, account swap action, effective rate calculation for cross-currency, rate banner hidden for same-currency, form validation, and save handling.

Manual Checklist:
1. Create a same-currency transfer (PLN to PLN with equal amounts: 100 PLN → 100 PLN): verify balances update.
2. Create a same-currency transfer with differing amounts (100 PLN → 95 PLN): verify source decreases by 100 and dest increases by 95.
3. Create a cross-currency transfer (1000 PLN → 250 USD): verify effective rate banner shows `1 PLN = 0.2500 USD · 1 USD = 4.0000 PLN`, both native balances update.
4. Tap "Swap accounts" button: verify source and destination accounts swap cleanly.
5. Check Transactions tab: transfer row displays swap icon, `From → To`, and stacked `−sent` / `+received` amounts.
6. Check Summary bar on Transactions tab: verify transfer amounts are not counted as income or expense.
7. Tap transfer row in history: opens transfer editor. Edit amount and save: verify balances update.
8. Delete transfer: verify both balances revert.
9. Try setting date in future: verify date picker disables future days.
10. Navigate "More → Transfers": opens transactions list filtered to transfers.

## 12. Done When

1. All acceptance criteria for TRANSFERS from PRD 33 (as simplified for v1) are satisfied:
   - Money can be transferred between accounts.
   - Same and cross currencies are supported.
   - Two independently entered amounts (Sent and Received) are preserved.
   - Transfers are not counted as income or expenses.
   - Transfer categories are supported and enforced.
   - Both sides of a transfer are always synchronized.
2. `TransferEditScreen` is fully functional and reachable from transaction entry, history rows, and direct routes.
3. Database stays at version 1 with unchanged schema JSON and existing baseline tests passing.
4. All unit, instrumented, and UI tests pass with no lint warnings.

## 13. Risks

- **User entering mismatched amounts by mistake in same currency**: The UI clearly labels "Amount Sent" and "Amount Received" so the user is always aware that both fields are required and explicit.
- **Cross-currency account reassignment during edit**: If a user edits a transfer and changes one account's currency, the form prompts confirmation (T12) and recalculates the effective rate banner.
- **Back-dating across two accounts**: A transfer touches two accounts, so `OpeningBalanceOrderException` identifies which account's opening balance needs to be moved back.

## 14. Confirmed Decisions

1. **No Transfer Fees in v1**: Removed all fee-specific entities, linking, and schema changes. Any difference between sent and received amounts affects account balances directly.
2. **Two Independent Amounts**: Both same-currency and cross-currency transfers require independently entered Amount Sent and Amount Received fields.
3. **Effective Rate**: Calculated and displayed purely as informational text for cross-currency transfers. Hidden for same-currency transfers.
4. **No Schema Changes**: Database remains at version 1 without any modifications to `OperationEntity` or other tables.
5. **Category Requirement**: Transfers require a category of type `TRANSFER` on creation and duplication.

## 15. Hand-offs

- **Sub-project 5 (Currency)**: Display currency conversion can estimate transfer totals in display currency for reports, while leaving native transfer amounts intact.
- **Sub-project 6 (Recurring)**: Recurring transfers will generate `TRANSFER` operations through `OperationRepository.insertTransfer`.
- **Sub-project 7 (Analytics)**: Account analytics and transfer statistics will query transfer volumes and category rollups.
- **Sub-project 8 (Import / Export)**: Invariant checker and schema ensure transfer integrity across database export and import.