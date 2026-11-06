# Finora: Recurring Transactions Design

Date: 2026-11-06
Status: Draft for user review
Sub-project: 6 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD sections 9, 11.2, 31, 33 RECURRING, NFR-06, NFR-08, NFR-09, NFR-20). References like "Foundation 6.4" refer to the Foundation design, "Accounts 4.6" to `2026-11-04-finora-accounts-categories-design.md`, "Operations T11" to `2026-11-04-finora-operations-tags-design.md`, "Transfers" to `2026-11-05-finora-transfers-design.md` and "Currency" to `2026-11-06-finora-currency-design.md`.

## 1. Scope

In scope:
1. **Recurring schedules** for income and expense: create, edit, pause, resume, skip one occurrence, delete (PRD 9.1 to 9.4).
2. **Frequencies**: daily, weekly, monthly, yearly and a custom interval ("every N days, weeks, months or years") (PRD 9.3).
3. **Generation of ordinary transactions** through `OperationRepository.insertOperation`, with category, tags and the account's currency (PRD 9.5, 31.1, 31.8).
4. **Missed occurrences**: every occurrence that came due while the app was closed is created when the app runs again (PRD 9.6, 31.7).
5. **Idempotency**: one occurrence date of one schedule is handled at most once, enforced by a unique index (PRD NFR-08, 31.2).
6. **Historical integrity**: editing or deleting a schedule never changes or deletes generated transactions (PRD 9.7, 31.3, 31.5, 31.9, NFR-09).
7. **Generated marker** in the history list (PRD 9.5 "distinguishable as automatically generated").
8. Recurring screens: list, detail with occurrence history, create/edit form (replaces the `more/recurring` placeholder).
9. **Schema v3**: three new tables and an `@AutoMigration(from = 2, to = 3)` (section 8).

Out of scope, with the sub-project that owns it (or "later"):
- **Recurring transfers.** PRD 9.1 and 31.1 limit schedules to income and expense. The Transfers design (15) mentioned recurring transfers as a possible hand-off; this design does not build them (section 14, item 1).
- Dashboard card "Upcoming recurring transactions" and analytics on generated transactions (7). This sub-project provides the data and the pure occurrence calculator they need (section 15).
- Notifications and reminders, WorkManager or any generation while the app is not running (section 14, item 6).
- Time of day, "last business day", "nth weekday", variable amounts, end-after-N-occurrences.
- "Make this transaction recurring" shortcut in the transaction form, and a "created by schedule X" history filter.
- Currency conversion of any kind. A schedule always runs in its account's currency (Currency R1 is untouched).
- Budgets, templates (later, P2).

Already shipped and reused unchanged: `OperationRepository.insertOperation` (archived-account check, opening-order check, balance overflow check, future-date check, tag linking), `InvariantChecker`, `AccountPicker`, `CategoryTreePicker`, `TagPicker`, `DateField`, `AmountParser`, `OpeningDatePolicy`, `TransactionRow`.

## 2. Decisions

| Topic | Decision |
|---|---|
| Schedule types | `INCOME` and `EXPENSE` (`TransactionKind`). The kind is fixed after creation, like a transaction's type (Operations 2), because it fixes the category type. |
| Occurrence date | A **calendar date** (`LocalDate`, stored as epoch day) with no time of day. The generated transaction is dated at the **start of that local day** in the device time zone at generation time. Dates float with the device zone. |
| Recurrence model | `unit` (`DAY`, `WEEK`, `MONTH`, `YEAR`) plus `interval` (1 to 365). Daily, Weekly, Monthly, Yearly are interval 1 of their unit. Custom is any other pair. One representation in the database. |
| Anchoring | Occurrence number `k` is `anchor + k × interval` units, always computed from the anchor, never from the previous occurrence. Jan 31 monthly gives Feb 28 (or 29), Mar 31, Apr 30. Feb 29 yearly gives Feb 28 in non-leap years. No drift (section 4). |
| Stored state | `start_date` (the anchor of the current rule), `end_date` (optional), `next_date` (first occurrence not yet handled, `NULL` when the schedule has ended), `status` (`ACTIVE` or `PAUSED`). "Ended" is derived from `next_date IS NULL`. |
| Idempotency | Table `recurring_occurrence` with a **unique index on `(schedule_id, occurrence_date)`**. The row is claimed, the operation is created, and `next_date` advances in **one Room transaction**. |
| Skipped | A skipped occurrence is a `recurring_occurrence` row with status `SKIPPED` and no operation. |
| Deleting a generated transaction | The occurrence row stays (`operation_id` becomes `NULL`), so the occurrence is **never created again** (PRD 31.2). |
| Generated description | The schedule's name. The schedule has no separate description field. |
| Missed occurrences | All of them are created, oldest first, in batches of 100 per Room transaction. No cap. |
| Resume after a pause | If occurrences came due while paused, the user chooses: create them, or skip them and continue from the next future date. |
| Blocked generation | An occurrence that cannot be created (archived account, date before the account's opening balance, balance overflow) **stays pending**. It is not skipped, clamped or lost, and the opening balance is never moved automatically. The UI shows the reason and the fix (section 6.4). |
| Trigger | App process start, every `MainActivity.onStart`, and a timer to the next local midnight while the process lives. No WorkManager. |
| Past start dates | Allowed. Saving a form with N occurrences already due creates them after a confirmation. At most 1000 per save (section 5.3). |
| Account change on a schedule | Any active account, or the current one even if archived (Accounts 4.7). A different currency keeps the typed number and needs an explicit confirmation (same principle as Operations T12), because the amount means something else afterwards. |
| Schedule deletion | Removes the schedule, its tag links and its occurrence rows. Generated transactions stay, and lose their recurring marker. |
| Schema | **Version 3**, `@AutoMigration(from = 2, to = 3)`. `1.json` and `2.json` are not edited. |

## 3. Changes to existing code

| Change | Reason |
|---|---|
| `FinoraDatabase`: `VERSION = 3`, add `RecurringScheduleEntity`, `RecurringScheduleTagEntity`, `RecurringOccurrenceEntity`, `autoMigrations = [AutoMigration(1, 2), AutoMigration(2, 3)]`, `recurringDao()`. `ALL_MIGRATIONS` stays empty. | Section 8. |
| Commit generated `app/schemas/.../3.json` and pinned `src/test/resources/schema-v3.expected.json`. `1.json`, `2.json` and their pinned copies are not touched. | Frozen baselines for the migration tests. |
| `SchemaUnchangedTest`: one more case for v3. `MigrationInfrastructureTest`: the chain test asserts `VERSION == 3` and that no migration targets a higher version. `ScratchDatabase` is not changed. | Existing assertions were written for version 2. |
| `OperationRepository`: split the private tag resolution into a public `resolveTagIds(tagIds, newTagNames, now): Set<Long>` (must run inside a transaction). `resolveAndLinkTags` calls it. Behaviour of every existing function is unchanged. | The schedule form creates pending tags exactly like the transaction form (Operations 6.2). |
| `OperationWithDetails` gets a third `@Relation` to `RecurringOccurrenceEntity` (by `operation_id`). `TransactionItem` gets `recurringScheduleId: Long?`. `TransactionRepository` maps it. The `@RawQuery(observedEntities = ...)` lists include the occurrence entity. | Generated marker (section 7.5). |
| `TransactionRow`: a small repeat icon with content description "Recurring" when `recurringScheduleId != null`. `TransactionEditScreen`: for such a transaction, a note "Created by recurring schedule <name>" that opens the schedule. | PRD 9.5. |
| `AccountRepository.delete`: delete the account's schedules (their occurrences cascade) before the existing steps. The delete dialog states how many schedules go with the account. The archive dialog states that active schedules on the account will wait. | Accounts 12 "delete hooks for later references". |
| `CategoryRepository.delete`: in the same transaction, `RecurringDao.reassignCategory(fromId, toId)` for the chosen `ReassignTarget`. `DeletionImpact` gets `scheduleCount`, and the dialog states it. | Same hook. A schedule has a category foreign key. |
| `InvariantChecker`: the recurring checks of section 7. | Import validation (8) reuses them. |
| `FinoraApp`: inject `RecurringRepository` and call `recurring.start(appScope)` after `rates.start(appScope)`. `MainActivity.onStart`: call `recurring.requestGenerate()`. | Section 6.3. |
| `Routes`, `FinoraNavHost`: replace the `more/recurring` placeholder, add the routes of section 10. | Navigation. |
| `strings.xml`: strings for all new text, plurals for frequency labels. | Existing convention. |
| `Money`, rates, `TransferRepository`, `SettingsRepository`, and all write rules of `OperationRepository` other than the tag split: **unchanged**. | Sections 1 and 2. |

## 4. Domain (pure Kotlin, JVM-testable)

Package `domain/recurring`. No Android, Room or `System` clock imports.

```kotlin
enum class RecurrenceUnit { DAY, WEEK, MONTH, YEAR }
data class RecurrenceRule(val unit: RecurrenceUnit, val interval: Int)   // interval in 1..365

enum class ScheduleStatus { ACTIVE, PAUSED }
enum class OccurrenceStatus { GENERATED, SKIPPED }

data class ScheduleDraft(
    val name: String,
    val kind: TransactionKind,
    val accountId: Long,
    val amountMinor: Long,              // positive, in the account's currency
    val categoryId: Long?,
    val tagIds: Set<Long>, val newTagNames: List<String>,
    val rule: RecurrenceRule,
    val firstDate: LocalDate,           // create: "Starts on". edit: "Next occurrence"
    val endDate: LocalDate?,
)

data class RecurringSchedule(
    val id: Long, val name: String, val kind: TransactionKind,
    val account: Account, val money: Money,                 // money = positive amount in account currency
    val category: Category?, val categoryPath: List<Category>, val tags: List<Tag>,
    val rule: RecurrenceRule, val startDate: LocalDate, val endDate: LocalDate?,
    val nextDate: LocalDate?, val status: ScheduleStatus,
    val createdAt: Long, val updatedAt: Long,
)

data class OccurrenceItem(val date: LocalDate, val status: OccurrenceStatus, val operationId: Long?, val handledAt: Long)

enum class GenerationIssue { ACCOUNT_ARCHIVED, BEFORE_OPENING_BALANCE, BALANCE_OVERFLOW }
```

Derived state (`ScheduleDisplayState`): `ACTIVE`, `PAUSED`, `ENDED` (`nextDate == null`). The generation issue, when there is one, comes from the repository (section 6.3).

### 4.1 `RecurrenceCalculator`

```kotlin
object RecurrenceCalculator {
    fun nth(anchor: LocalDate, rule: RecurrenceRule, k: Long): LocalDate       // anchor.plus(k * interval, unit)
    fun firstAfter(anchor: LocalDate, rule: RecurrenceRule, after: LocalDate): LocalDate    // smallest nth(k) > after, k >= 0
    fun next(anchor: LocalDate, rule: RecurrenceRule, current: LocalDate, end: LocalDate?): LocalDate?   // firstAfter, or null when past end
    fun between(anchor: LocalDate, rule: RecurrenceRule, from: LocalDate, toInclusive: LocalDate, limit: Int): List<LocalDate>
    fun startOfDay(date: LocalDate, zone: ZoneId): Long                          // epoch ms, DST-safe
}
```

- `nth` uses `LocalDate.plusDays/plusWeeks/plusMonths/plusYears` on the **anchor** with `k × interval`, so the month-end and leap-day clamping of `java.time` never accumulates.
- `firstAfter` starts from an estimate of `k` from `ChronoUnit.between(anchor, after)` divided by the interval, then steps `k` down and up until `nth(k) > after` and `nth(k − 1) <= after`. `nth` is monotone in `k`, so this ends in a few steps.
- `startOfDay` is `date.atStartOfDay(zone)`, which handles zones whose midnight does not exist. The result is never after a timestamp within the same day, the property Accounts 4.6 relies on.
- All arithmetic on epoch days and `k` uses `Math.*Exact`, and dates are limited to years 2000 to 2100 by the validator, so overflow cannot occur.

### 4.2 `ScheduleDates.reschedule`

Pure rule for "what is the next date after an edit":

```kotlin
object ScheduleDates {
    fun reschedule(current: Anchor, edited: Edited, lastHandled: LocalDate?): Result
}
```

- If the user changed the **rule or the next-occurrence date**: `startDate := edited.firstDate` (the anchor moves to the chosen date) and `nextDate := edited.firstDate`. `edited.firstDate` must be **after** `lastHandled` (the largest handled `occurrence_date`), otherwise `Result.Rejected(NEXT_NOT_AFTER_HANDLED)`. This is what keeps edits from re-creating or duplicating handled dates.
- If neither changed (amount, category, tags, name, account, end date only): `startDate` is kept and `nextDate := firstAfter(anchor, rule, lastHandled)` clipped by `endDate`, or the existing `nextDate` when nothing was handled yet. This is how an ended schedule is revived by extending its end date, and how a shortened end date ends it.
- `nextDate` is `null` exactly when no occurrence remains on or before `endDate`.

### 4.3 `ScheduleDraftValidator`

`validate(draft fields, currency, now, zone, hadCategory)` returns a set of errors: `NAME_EMPTY`, `NAME_TOO_LONG` (over 60 characters after normalization), `AMOUNT_EMPTY`, `AMOUNT_INVALID`, `AMOUNT_TOO_PRECISE`, `AMOUNT_NOT_POSITIVE`, `AMOUNT_OUT_OF_RANGE`, `ACCOUNT_MISSING`, `CATEGORY_MISSING` (create always, edit only when the schedule had a category), `INTERVAL_OUT_OF_RANGE`, `DATE_OUT_OF_RANGE` (outside 2000-01-01 to 2100-12-31), `END_BEFORE_START`, `TOO_MANY_PAST_OCCURRENCES` (more than 1000 occurrences already due). The amount is parsed in the **selected account's** currency with `AmountParser`, never rounded. Name normalization reuses the trim and whitespace-collapse part of `TagNameRules`; names need not be unique.

## 5. Rules

### 5.1 Business rules

| # | Rule | Source |
|---|---|---|
| R1 | A schedule generates ordinary INCOME or EXPENSE operations through `OperationRepository.insertOperation`, so every operation invariant (Foundation 6.4, Operations T1 to T11, Accounts 4.6, 4.7) holds for generated rows. There is no second write path to `operation` or `ledger_entry`. | PRD 9.5, 31.1 |
| R2 | One occurrence date of one schedule is handled at most once. The claim row, the operation and the advance of `next_date` commit together or not at all. | PRD 31.2, NFR-08, NFR-06 |
| R3 | An occurrence is generated only when its date is today or earlier in the device's local calendar, so a generated transaction is never in the future. | Operations T11 |
| R4 | Every missed occurrence is created, oldest first. The app never silently drops one. | PRD 9.6, 31.7 |
| R5 | Editing a schedule changes only occurrences that have not been handled. Generated transactions are never read back, modified or deleted by schedule edits. | PRD 9.7, 31.3, 31.9, NFR-09 |
| R6 | Deleting a schedule keeps all of its generated transactions. | PRD 9.7, 31.5 |
| R7 | Deleting a generated transaction does not recreate it, and editing one does not affect the schedule. | PRD 31.2, 31.3 |
| R8 | A paused schedule creates nothing. Resuming offers to create or skip what came due. | PRD 31.4 |
| R9 | Skipping handles exactly one occurrence without creating a transaction. The schedule continues. | PRD 31.6 |
| R10 | The generated amount is the schedule's amount in the account's currency, with that currency's scale. Nothing is converted. | PRD 31.8, NFR-13 |
| R11 | Generated category and tags are the schedule's at generation time. The category type matches the kind. | PRD 9.5 |
| R12 | A schedule on an archived account creates nothing, and its occurrences stay pending. | Accounts A4 |
| R13 | Generation never moves an account's opening balance. A blocked occurrence stays pending until the user decides (6.4). | Accounts 4.6 item 4, 12 |
| R14 | After `end_date`, the schedule is ended: no further occurrence. | PRD 9.2 |
| R15 | Occurrences are computed from the anchor, so month-end and leap-day dates do not drift. | PRD 9.3 |
| R16 | Recurring code paths never read exchange rates or Display Currency. | Currency R1 |

### 5.2 What an occurrence becomes

For occurrence date `d` of schedule `s`, `insertOperation` is called with:

| Field | Value |
|---|---|
| `type` | `INCOME` or `EXPENSE` from `s.kind` |
| `occurredAt` | `startOfDay(d, zone)` |
| `entries` | one `EntryDraft(s.accountId, +amount)` for income, `−amount` for expense |
| `categoryId` | `s.categoryId` (may be `NULL` if the category was deleted with Uncategorized, Operations 5.8) |
| `description` | `s.name` |
| `tagIds` | the schedule's tag ids |
| `moveOpeningBalanceBack` | `false` for background runs. `true` only when the user confirmed it (6.4) |
| `now` | generation time. `created_at = updated_at = now` |

### 5.3 Creating with past dates

When the form's first date is today or earlier, the occurrences from that date up to today are due immediately.
- The form shows "N transactions dated from <first> to <last> will be created now." and Save asks for confirmation before writing.
- More than 1000 due occurrences is refused with `TOO_MANY_PAST_OCCURRENCES` ("Choose a later start date."). This bounds one write. Missed occurrences found later by the app have no such cap (6.3).
- `create` writes the schedule and generates these occurrences **in the same Room transaction**, so a failure (for example `OpeningBalanceOrderException`) leaves nothing behind, and the form can ask and retry with `moveOpeningBalanceBack = true`.

## 6. Data layer

### 6.1 DAO additions (`RecurringDao`)

Insert, update and delete of the three entities, plus:

| Function | Purpose |
|---|---|
| `observeSchedules()`, `observeSchedule(id)`, `getSchedule(id)` | Reading. |
| `dueSchedules(todayEpochDay)` | `status = 'ACTIVE' AND next_date IS NOT NULL AND next_date <= :today`, ordered by `id`. |
| `tagIdsOf(scheduleId)`, `deleteTagsOf(scheduleId)` | Tag links. |
| `insertOccurrence(row): Long` with `OnConflictStrategy.IGNORE` | Returns `-1` when `(schedule_id, occurrence_date)` already exists. This is the idempotency gate. |
| `setOccurrenceOperation(occurrenceId, operationId)` | Link after the operation is created. |
| `lastHandledDate(scheduleId): Long?` | `MAX(occurrence_date)`. |
| `observeOccurrences(scheduleId, limit)` | Newest first. |
| `updateNextDate(id, nextDate)` | Generation, skip: does **not** change `updated_at`. |
| `updateSchedule(...)`, `setStatus(id, status, updatedAt)` | User edits. |
| `reassignCategory(fromId, toId)`, `deleteForAccount(accountId)`, `scheduleCountForAccount`, `scheduleCountForCategory` | Delete hooks (section 3). |

### 6.2 `RecurringRepository` (`data/repo`, `@Singleton`)

Depends on `FinoraDatabase`, `OperationRepository`, an injectable clock `() -> Long` and zone provider `() -> ZoneId`, like `RateRepository`.

| Function | Behaviour |
|---|---|
| `observeSchedules(): Flow<List<RecurringSchedule>>`, `observeSchedule(id)`, `observeOccurrences(id, limit)` | Names of accounts, categories and tags come from joins and in-memory lookups so renames show without reloading. |
| `create(draft, moveOpeningBalanceBack, now): Long` | One transaction: validate, resolve tags (`resolveTagIds`), insert the schedule (`start_date = next_date = firstDate`, `ACTIVE`), link tags, then generate everything due (6.3) with no batch limit. |
| `update(id, draft, moveOpeningBalanceBack, confirmCurrencyChange, now)` | One transaction. Account rule (Accounts 4.7: current account even if archived, otherwise an active one). A different currency without `confirmCurrencyChange` throws `CurrencyChangeNotConfirmedException` and writes nothing. `kind` cannot change. Dates via `ScheduleDates.reschedule` (4.2). Replaces tags. Updates `updated_at`. Then generates what became due, if the schedule is `ACTIVE`. |
| `pause(id)` | `status = PAUSED`. Nothing else. |
| `resume(id, catchUp: Boolean, moveOpeningBalanceBack, now)` | `ACTIVE`. If `catchUp`, generate what is due. Otherwise mark every due occurrence `SKIPPED` and move `next_date` to the first future date, in the same transaction. |
| `skipNext(id, now)` | Claims `next_date` as `SKIPPED` and advances. Allowed while `ACTIVE` or `PAUSED` and not ended. Repeating it skips successive occurrences, including future ones. |
| `skipBlocked(id, now)` | Marks every due occurrence `SKIPPED` (used by the blocked-state action, 6.4). |
| `delete(id)` | Deletes the schedule row. Tag links and occurrences cascade. Operations are untouched. |
| `generateDue(id, now, moveOpeningBalanceBack, limit)` | Section 6.3. `internal`, also used by `create`, `update`, `resume`. |
| `requestGenerate()`, `start(scope)` | Triggers (6.3). |
| `issues: StateFlow<Map<Long, GenerationIssue>>` | In-memory result of the last background run, per schedule. |

### 6.3 Generation

`generateDue(id, now, moveOpeningBalanceBack, limit)`, inside `db.withTransaction`:

1. Load the schedule. Return if it is not `ACTIVE` or `next_date` is `NULL`.
2. `today = local date of now in the device zone`. If the schedule's account is `ARCHIVED`, throw `ScheduleBlockedException(ACCOUNT_ARCHIVED)`.
3. Repeat while `next_date != NULL`, `next_date <= today` and fewer than `limit` occurrences were handled in this call:
   1. `occurrenceId = dao.insertOccurrence(GENERATED, operation_id = NULL, handled_at = now)`.
   2. If the result is `-1` the date was already handled: create nothing and go to step 4. Otherwise call `OperationRepository.insertOperation` (5.2) and `dao.setOccurrenceOperation`.
   3. Failures from `insertOperation` propagate and roll back the whole transaction, including the claim row and any occurrences of this call.
   4. `next_date = RecurrenceCalculator.next(start_date, rule, next_date, end_date)`.
4. `dao.updateNextDate(id, next_date)`.

Background run, `generateAllDue(now)` under a `Mutex`:
1. `dueSchedules(today)`. For each schedule, call `generateDue(..., limit = 100)` repeatedly until a call handles fewer than 100 occurrences. Each call is its own transaction, so a long backlog never forms one huge transaction, and a crash between batches loses nothing (the next run continues).
2. A thrown `OpeningBalanceOrderException`, `ScheduleBlockedException` or `MoneyOverflowException` is caught per schedule, the schedule stays as it was (the failed batch rolled back), and the issue is stored in `issues` (`BEFORE_OPENING_BALANCE`, `ACCOUNT_ARCHIVED`, `BALANCE_OVERFLOW`). Other schedules continue. Any other exception is logged and the schedule is retried on the next run.
3. A schedule that generates cleanly is removed from `issues`.

Triggers: `start(scope)` stores the scope and launches a loop: `generateAllDue`, then wait until the next local midnight plus one second (computed from the injected clock and zone), repeat. `requestGenerate()` launches one `generateAllDue` in that scope and returns at once. `FinoraApp.onCreate` calls `start`, `MainActivity.onStart` calls `requestGenerate`. The work runs on `Dispatchers.Default`/IO and never blocks the UI (PRD NFR-20). Because Room emits on every change, lists, balances and the history update by themselves.

A device clock moved backwards generates nothing and is not corrected. A clock moved forward generates the occurrences that are now "due" (Operations 13 has the same exposure for dates). Those transactions can be deleted and are not recreated (R7).

### 6.4 Blocked occurrences

| Issue | Cause | What the user sees and can do |
|---|---|---|
| `ACCOUNT_ARCHIVED` | The schedule's account is archived. | "Account archived. Nothing is created." Actions: change the account in Edit, pause, or delete. Unarchiving the account lets the next run create everything that came due (R4). |
| `BEFORE_OPENING_BALANCE` | The first due occurrence is dated before the account's opening balance (for example after Edit changed the account, or after a resume). | "<date> is before the initial balance of <account> (<date>)." Actions: **"Move initial balance back and create"** (confirmation text from Accounts 4.6, states that the current balance does not change; runs `generateDue(..., moveOpeningBalanceBack = true)`), or **"Skip these occurrences"** (`skipBlocked`). |
| `BALANCE_OVERFLOW` | Adding the amount would overflow the account's `Long` balance (Foundation 5.2). | "The balance of <account> cannot grow any further." Actions: Edit, pause or delete. |

The detail screen and the list row show the issue. Create, edit and resume do not leave a blocked state behind: they run generation inside their own transaction, ask the same questions in the form (5.3, 7.3), and write nothing on a refusal.

## 7. UI

### 7.1 Recurring list (`RecurringScreen`, route `more/recurring`)

- Sections **Active**, **Paused**, **Ended**. Active is sorted by `next_date`, then name.
- Row: kind icon and sign, name, native amount (`Money.format()`), "Santander · Every month", and the status line: "Next: 1 Dec 2026", "Paused", "Ended 31 Dec 2026", or the blocked reason in the error color.
- FAB "Add schedule". Empty state explains what a schedule is and offers the same action.
- Tap opens the detail screen.

### 7.2 Detail (`RecurringDetailScreen`, route `more/recurring/{scheduleId}`)

- Header: name, kind, amount, account, category path, tag chips, frequency text, end date, status.
- "Upcoming": the next 5 dates from `RecurrenceCalculator.between`, honoring `end_date`. Shown also while paused, marked as not active.
- Actions: **Edit**, **Pause** or **Resume**, **Skip next** (names the date and asks for confirmation), **Delete**. Skip and Pause are hidden for an ended schedule.
- Blocked banner with the actions of 6.4.
- Resume with due occurrences opens the choice dialog: "While paused, N occurrences came due (<first> to <last>)." with **Create them** and **Skip them and continue from <date>**.
- "History": the latest 100 occurrences, newest first. `GENERATED` rows show the date and amount and open the transaction. A `GENERATED` row without an operation is shown as "Transaction deleted". `SKIPPED` rows are shown as "Skipped". The list states when more exist.
- Delete dialog: "<M> transactions created by this schedule stay in your history. They will no longer be marked as recurring." Confirm deletes.

### 7.3 Form (`RecurringEditScreen`, routes `more/recurring/new` and `more/recurring/{scheduleId}/edit`)

One screen for create and edit with an immutable `StateFlow` state, rotation-safe like the other forms.

- **Type**: segmented Expense | Income, default Expense, disabled on edit.
- **Name**: required, up to 60 characters.
- **Amount**: same input rules as the transaction form (digits, one `.` or `,`, currency symbol next to it). Errors per `ScheduleDraftValidator`.
- **Account**: `AccountPicker` (`originalAccountId` in edit mode). No account is preselected on create; the user must choose one before saving.
- **Category**: `CategoryTreePicker` in `Assign` mode for the kind, shown as a path. Required on create. An already-uncategorized schedule can be saved without one (Operations 5.3).
- **Tags**: `TagPicker`. Pending tags are created inside the save transaction.
- **Frequency**: chips Daily, Weekly, Monthly, Yearly, Custom. Custom shows "Every [N] [days | weeks | months | years]". A live sentence summarizes it ("Every 2 weeks, starting Fri 6 Nov").
- **Starts on** (create) or **Next occurrence** (edit): `DateField`, any date from 2000 to 2100. Past dates are allowed (5.3).
- **Ends on**: optional, with a clear action. Must not be before the first date.
- **Preview line**: when occurrences are already due, the 5.3 text. Otherwise "Next: <date>".
- **Save gates**, in order, each shown only when it applies, nothing written before all pass:
  1. **Currency change** (edit, the account's currency differs from the current one): "Change currency without converting?" "Future transactions will be <new amount with currency> instead of <old amount with currency>. This is not a currency conversion. Transactions already created are not changed." Confirm repeats with `confirmCurrencyChange = true`.
  2. **Past occurrences** (N > 0): "Create N transactions now?" with the date range.
  3. **Opening order**: on `OpeningBalanceOrderException`, the Accounts 4.6 dialog; confirm repeats with `moveOpeningBalanceBack = true`, cancel changes nothing.
- Overflow (`MoneyOverflowException`) and `TOO_MANY_PAST_OCCURRENCES` show as field errors; nothing is written.

### 7.4 Navigation

Routes added to `Routes` (`more/recurring` exists as `RECURRING`):

| Route | Screen |
|---|---|
| `more/recurring` | List |
| `more/recurring/new` | Form, create |
| `more/recurring/{scheduleId}` | Detail |
| `more/recurring/{scheduleId}/edit` | Form, edit |

`new` is a literal segment and wins over `{scheduleId}`, as in `accounts/new`. The More tab stays highlighted through `isRouteUnder`. From a transaction's "Created by recurring schedule" note, navigation uses the detail route.

### 7.5 Generated marker

`TransactionItem.recurringScheduleId` is non-null when an occurrence row links to the operation. The row shows a repeat icon with the content description "Recurring". After the schedule is deleted the link is gone, so the icon disappears, and the transaction is otherwise unchanged.

## 8. Schema and migration

Three new tables. Timestamps are epoch milliseconds, dates are epoch days (`LocalDate.toEpochDay()`), so SQL can compare them without parsing.

`recurring_schedule`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autogenerate | |
| `name` | TEXT NOT NULL | |
| `kind` | TEXT NOT NULL | `INCOME` or `EXPENSE` |
| `account_id` | INTEGER NOT NULL | FK → `account(id)`, no cascade |
| `amount_minor` | INTEGER NOT NULL | positive, in the account's currency |
| `category_id` | INTEGER | FK → `category(id)`, nullable |
| `unit` | TEXT NOT NULL | `DAY`, `WEEK`, `MONTH`, `YEAR` |
| `interval_count` | INTEGER NOT NULL | 1 to 365 |
| `start_date` | INTEGER NOT NULL | anchor of the current rule |
| `end_date` | INTEGER | nullable |
| `next_date` | INTEGER | nullable, `NULL` = ended |
| `status` | TEXT NOT NULL | `ACTIVE` or `PAUSED` |
| `created_at`, `updated_at` | INTEGER NOT NULL | `updated_at` changes on user edits only |

Indexes: `(account_id)`, `(category_id)`, `(status, next_date)`.

`recurring_schedule_tag`: `(schedule_id, tag_id)` primary key, both foreign keys `ON DELETE CASCADE`, index `(tag_id, schedule_id)`.

`recurring_occurrence`

| Column | Type | Notes |
|---|---|---|
| `id` | INTEGER PK autogenerate | |
| `schedule_id` | INTEGER NOT NULL | FK → `recurring_schedule(id)` `ON DELETE CASCADE` |
| `occurrence_date` | INTEGER NOT NULL | epoch day |
| `status` | TEXT NOT NULL | `GENERATED` or `SKIPPED` |
| `operation_id` | INTEGER | FK → `operation(id)` `ON DELETE SET NULL` |
| `handled_at` | INTEGER NOT NULL | |

Indexes: **unique `(schedule_id, occurrence_date)`**, `(operation_id)`.

- `FinoraDatabase.VERSION` becomes 3 with `@AutoMigration(from = 2, to = 3)`. It only creates the three tables; no existing table or index changes. `fallbackToDestructiveMigration` stays forbidden.
- `operation` ids are never reused (`AUTOINCREMENT`), so `SET NULL` can never attach an occurrence to an unrelated later operation.
- Fresh installs create v3 directly. The new tables start empty.
- Room cannot declare CHECK constraints, so positivity, ranges and consistency are checked by the repository and `InvariantChecker` (section 9).
- Import and export (8): the `.db` carries all three tables. An older file migrates through the chain and arrives with no schedules. A file restored from an earlier backup resumes generation from its stored `next_date`, which is correct: transactions created after the backup are gone with the rest of that data.

## 9. Invariant checker additions

Added to `InvariantChecker.checks`; SQL only.

| Check | Assertion |
|---|---|
| `recurring amount not positive` | `amount_minor <= 0` |
| `recurring bad interval` | `interval_count < 1 OR interval_count > 365` |
| `recurring end before start` | `end_date IS NOT NULL AND end_date < start_date` |
| `recurring next out of range` | `next_date IS NOT NULL AND (next_date < start_date OR (end_date IS NOT NULL AND next_date > end_date))` |
| `recurring open-ended without next` | `next_date IS NULL AND end_date IS NULL` |
| `recurring category type mismatch` | category `type` differs from `kind` (`INCOME` with `INCOME`, `EXPENSE` with `EXPENSE`) |
| `recurring occurrence not before next` | an occurrence with `occurrence_date >= next_date` when `next_date IS NOT NULL` |
| `recurring skipped with operation` | `status = 'SKIPPED' AND operation_id IS NOT NULL` |
| `recurring generated operation mismatch` | `operation_id IS NOT NULL` and the operation's `type` differs from the schedule's `kind` |

The "occurrence not before next" check is the data-level form of rules R2 and R4: handled dates are always strictly before the next pending date.

## 10. Privacy and security

No network, no new permission, no new exported component. Schedules live in the same database and follow the Foundation backup exclusions (Foundation 8).

## 11. Testing

JVM unit tests:
- `RecurrenceCalculatorTest`: each unit with interval 1 and N; `nth`, `firstAfter` (exactly on an occurrence, one day before, one day after, far from the anchor); `next` returns `null` past the end and on the end date itself; **anchoring**: Jan 31 monthly gives Feb 28, Mar 31, Apr 30; Jan 31 every 2 months; Feb 29 yearly gives Feb 28 in non-leap years and Feb 29 in leap years; weekly keeps the weekday; `between` with limit; `startOfDay` in a zone with a missing midnight (for example `America/Sao_Paulo` on 2018-11-04) and in a zone with a DST overlap.
- `ScheduleDatesTest`: unchanged rule keeps the anchor; rule change or next-date change re-anchors; `firstDate` equal to or before `lastHandled` is rejected; end date shortened ends the schedule; end date extended revives an ended one; nothing handled yet keeps `nextDate = startDate`.
- `ScheduleDraftValidatorTest`: every error; amount scales 0, 2, 3 and symmetric `Long` range; name 60 and 61 characters; interval 0, 1, 365, 366; date bounds; `END_BEFORE_START`; 1000 and 1001 due occurrences; `CATEGORY_MISSING` on create versus edit of an uncategorized schedule.
- Static check: `domain/recurring` has no `Float`, `Double`, Android, Room or `System.currentTimeMillis` use.
- `RecurringFormLogicTest` (fake repository): preview text and due count, save gates in order, currency dialog text contains both amounts and the "not a currency conversion" statement, opening-order retry, type disabled on edit, switching account back removes the currency dialog.
- `RecurringListLogicTest`: section grouping and ordering, status lines for paused, ended and each issue.

Instrumented Room tests (`RecurringRepositoryTest`, `RecurringIntegrityTest`, `MigrationV3Test`), with a controllable clock and zone:

Generation
- A daily schedule created with today as start creates one transaction: dated at the start of the local day, correct sign, currency of the account, category, tags, description = name, `created_at = updated_at = now`; balance changes; `InvariantChecker` is clean; the occurrence row links to it; `next_date` is tomorrow.
- Nothing is created for tomorrow's date (R3). Exactly at local midnight the new day's occurrence becomes due.
- **Catch-up**: a daily schedule not run for 5 days creates 5 transactions in date order. 250 missed days create 250 transactions through three batches. A crash simulated after the first batch loses nothing, and the next run continues without duplicates.
- Weekly, monthly (Jan 31), yearly (Feb 29) and custom intervals produce the expected dates through several runs.
- End date: the last occurrence on the end date is created, then `next_date` is `NULL`. Extending the end date revives it. Shortening it below `next_date` ends it.

Idempotency (NFR-08)
- `generateAllDue` run twice in a row creates each occurrence once.
- Twenty concurrent `requestGenerate` calls create each occurrence once.
- A raw-SQL duplicate `(schedule_id, occurrence_date)` insert fails on the unique index.
- A failure inside `insertOperation` (forced) rolls back the occurrence row and `next_date` together.
- Deleting a generated transaction and running again does **not** recreate it (R7). The occurrence row remains with `operation_id = NULL`.
- Editing the schedule so that its sequence passes over a handled date never produces that date again.

Pause, skip, resume
- Paused schedules create nothing across many runs. Resume with `catchUp = true` creates everything due; with `catchUp = false` marks them `SKIPPED` and continues from the first future date.
- `skipNext` creates no transaction, writes a `SKIPPED` row, advances, and the following occurrence is created normally (R9). Skipping a future occurrence works, and repeated skips advance one date each.
- `skipNext` and `pause` on an ended schedule are refused.

Historical integrity (R5, R6)
- After changing amount, category, tags, name, account (same currency) and frequency, every previously generated `operation` and `ledger_entry` row is byte-for-byte unchanged, and the next generated transaction uses the new values.
- Deleting a schedule removes its tag links and occurrences, keeps all operations and balances, and the transactions no longer have a recurring marker.
- Editing or deleting a generated transaction does not alter the schedule.

Blocked states (R12, R13)
- Archived account: background run creates nothing, `issues` reports `ACCOUNT_ARCHIVED`, `next_date` unchanged. After unarchiving, the next run creates the backlog.
- Occurrence dated before the account's opening balance: `create` throws `OpeningBalanceOrderException` and writes nothing (no schedule, no operation); with `moveOpeningBalanceBack = true` it succeeds, the opening balance moves to the first occurrence's day, the current balance is unchanged, the checker is clean. A background run never moves the opening balance and reports `BEFORE_OPENING_BALANCE`. `skipBlocked` and the move-back action both clear the issue.
- Balance overflow: nothing is written and `BALANCE_OVERFLOW` is reported.
- Cross-currency account change without `confirmCurrencyChange` throws `CurrencyChangeNotConfirmedException` and writes nothing. With it, the number is kept and later transactions use the new currency (no other table is written, rates untouched). Moving to a different archived account is refused, keeping an archived original is allowed.
- `create` with 1000 due occurrences succeeds, with 1001 it is refused and nothing is written.

Hooks
- Deleting an account removes its schedules and occurrences. Deleting a category reassigns its schedules to the chosen category or to Uncategorized, and a later generation creates uncategorized transactions that edit normally. Deleting a tag removes the link only. Transfers are not touched.
- `TransactionRepository` returns `recurringScheduleId` for generated rows, and `observePage` updates when the link appears.

Schema and checker
- `MigrationV3Test`: seed accounts, categories, tags, operations, transfers, rate rows and settings in v2; migrate; validate against `3.json`; every existing table is row-for-row equal; the three new tables exist and are empty. Full chain 1 to 3. `SchemaUnchangedTest` passes for v1, v2 and v3. `NoDestructiveMigrationTest` unchanged.
- `InvariantChecker` is clean after every test above and flags a raw-SQL violation of each check in section 9.
- `EXPLAIN QUERY PLAN` for `dueSchedules` uses `index_recurring_schedule_status_next_date`.
- Trigger loop (fake clock and delay): runs at start, waits until the next local midnight plus one second, runs again, and recomputes after a zone change.

Manual checklist (device or emulator; the migration keeps existing data):
1. Create a monthly expense on the 1st of next month, with category and two tags. It appears under Active with "Next: 1 …" and nothing is created.
2. Create a daily income starting 3 days ago. Confirm "Create 4 transactions now?", then check four income transactions with the repeat icon and the balance.
3. Set the device date forward 5 days, return to the app: 5 transactions appear, no duplicates. Return the date and restart: nothing new.
4. Delete one generated transaction, restart the app: it does not come back. The detail history shows "Transaction deleted".
5. Skip next on a weekly schedule: the history shows "Skipped" and the following week is created normally.
6. Pause a schedule, move the date forward, resume: both choices (create / skip) behave as described.
7. Edit amount and category: older transactions keep the old values, the next one uses the new ones.
8. Change the account to another currency: the confirmation says "not a currency conversion", the number stays.
9. Archive the schedule's account: the row shows "Account archived", nothing is created. Unarchive and restart: the backlog appears.
10. Create a schedule starting before the account's initial balance: the move-back dialog appears. Cancel leaves no schedule. Confirm moves the initial balance and the current balance does not change.
11. Delete a schedule: its transactions stay without the repeat icon.
12. Delete a category used by a schedule: the dialog mentions the schedule, and the chosen reassignment applies to it.
13. Airplane mode: everything above works (no network is used).

## 12. Done when

1. All PRD 33 RECURRING criteria hold:
   - Daily, weekly, monthly and yearly schedules can be created (and custom intervals).
   - Ordinary transactions are created automatically.
   - No duplicates occur.
   - Pause, resume and skip work.
   - Editing a schedule does not change history.
2. PRD 9.6 and 31.7 hold: occurrences missed while the app was closed are created when it runs again, and blocked ones are visible and never lost.
3. The Recurring list, detail and form replace the placeholder, and generated transactions are marked in the history.
4. Database is at version 3 with committed `3.json`, untouched `1.json` and `2.json`, and passing migration tests.
5. No change to transfer, rate or settings behaviour, and no new permission.
6. All tests in section 11 pass, the invariant checker is clean, and the build has no new lint warnings.

## 13. Risks

- **Generation happens only while the app runs.** Balances are not updated until the app is opened (PRD 9.6 explicitly allows this). A WorkManager worker could be added later without schema change.
- **Large backlogs.** A daily schedule left for years creates thousands of rows on the next start. Batches of 100 keep each transaction small, and the UI stays responsive, but the history fills at once. The user can delete them; they are not recreated.
- **Archived accounts hold schedules back.** Unarchiving an account after a long time creates its whole backlog. The archive dialog warns, and deleting or pausing the schedule avoids it.
- **Device clock and time zone.** "Due" is judged against the device calendar at run time. Moving the clock forward creates transactions that look odd later; backwards does nothing. Travelling across zones can move a day boundary by a day. No guard is added, consistent with Operations 13.
- **The recurring marker disappears with the schedule.** Generated transactions keep every value, but their origin is not recorded after deletion. Recording it independently would add a column to `operation`.
- **Description is the schedule name.** Renaming a schedule changes future descriptions only.
- **Cross-currency account change reinterprets a number.** The confirmation is the only protection (same trade-off as Operations T12), and it protects future transactions only.
- **Pending dates before an opening balance** need a human decision. The app never picks one for the user.
- **Delete without undo** for schedules, as elsewhere.
- **Restoring an old backup** regenerates the transactions created after that backup. That matches the restored data.

## 14. Assumptions to confirm

These details were not specified and are set to the simplest reasonable choice:
1. **Income and expense only.** PRD 9.1 and 31.1 say so. The Transfers hand-off (15) suggested recurring transfers; this design supersedes it. Adding them later needs a destination account and a received amount on the schedule, which is a schema change.
2. **Occurrences have a date, not a time.** They are created at the start of the local day. No time-of-day setting.
3. **A deleted generated transaction is not recreated.** The alternative (recreate it) would break "at most once" (PRD 31.2).
4. **All missed occurrences are created** (oldest first, no cap), including after unarchiving. After an explicit pause, the user chooses on resume.
5. **Blocked occurrences wait.** Archived account, opening-balance order and overflow never lead to clamping, silent skipping or moving an opening balance (Accounts 12).
6. **No WorkManager.** Generation runs on start, on foreground, and at midnight while the process lives.
7. **The generated description is the schedule name**, and a schedule has no description field.
8. **Deleting a schedule removes the recurring marker** from its transactions.
9. **The schedule's `start_date` column is the anchor of the current rule.** Editing the rule or the next date re-anchors it, so the original first date is not kept after such an edit.
10. **Limits**: interval 1 to 365, dates 2000 to 2100, at most 1000 past occurrences per save, name up to 60 characters, names need not be unique.

## 15. Hand-offs

- **Sub-project 7 (Dashboard and analytics):** "Upcoming recurring transactions" (PRD 11.2 item 8) uses `RecurringRepository.observeSchedules()` and `RecurrenceCalculator.between` for `ACTIVE`, non-blocked schedules. Native amounts only, or `RateConverter` estimates marked `≈` (Currency 15). Generated transactions are ordinary income and expense rows, so every existing statistic already includes them. `recurringScheduleId` is available if analytics want to mark or filter them.
- **Sub-project 8 (Import / Export):** the `.db` carries `recurring_schedule`, `recurring_schedule_tag` and `recurring_occurrence`. Import validation runs the section 9 checks together with the earlier ones. CSV export of transactions is unaffected.
- **Later:** recurring transfers, a "make recurring" shortcut in the transaction form, a history filter by schedule, reminders and WorkManager generation.
