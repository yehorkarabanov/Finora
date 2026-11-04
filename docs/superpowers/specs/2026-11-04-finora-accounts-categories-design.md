# Finora: Accounts and Categories Design

Date: 2026-11-04
Status: Approved with clarifications (revision 2, 2026-11-04). Section 14 maps each review point to the sections that resolve it.
Sub-project: 2 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD). Section numbers like "PRD 26.7" refer to it. "Foundation 6.3" refers to the Foundation design.

## 1. Scope

In scope:
1. **Accounts**: create, edit, view balance, archive and unarchive, delete when permitted, opening balance UI (PRD 4, 26).
2. **Categories**: create, edit, move, delete with reassignment, a tree of at most 3 levels, direct assignment to any node, aggregation, cycle and depth prevention, default category set (PRD 6, 29).
3. Reusable UI pieces that later sub-projects need: account picker, category tree picker, icon picker, color picker.

Out of scope here, with the sub-project that owns it:
- Income and expense entry, transaction history, account transaction history list (3).
- Transfer entry, transfer fees (4).
- Currency conversion, Display Currency totals, "estimate" markers (5). Every amount in this sub-project is shown in its native currency.
- Per-account and per-category charts, drill-down screens, period filters (7). This sub-project delivers the aggregation primitive (section 5.5) and no analytics screens.
- Tags (3). Recurring, budgets, templates (6 and later).

Foundation already ships the tables, DAOs, `OperationRepository`, `InvariantChecker`, `Money`, `CurrencyRegistry`, and a placeholder Accounts tab and Categories screen. This sub-project replaces those placeholders.

## 2. Decisions

| Topic | Decision |
|---|---|
| Schema | **No schema change.** Database stays at version 1, `app/schemas/1.json` is unchanged. Everything below fits the Foundation tables. A test asserts the exported schema is unchanged. |
| Account currency | Chosen at creation, then immutable in the UI and the repository, even for an account with no entries. To "change" it the user creates a new account (PRD 26.7). |
| Account balance | Derived `COALESCE(SUM(ledger_entry.amount_minor), 0)` over a `LEFT JOIN`, so an account with no entries has balance 0 (section 7). Negative balances allowed (PRD 26.9). |
| Opening balance | One `OPENING_BALANCE` operation, written only through `OperationRepository`. Zero or empty means "no opening balance operation". It is the account's **starting point**: dated on or before every other entry of the account (section 4.6). |
| Account delete | Allowed only when the account has no ledger entry other than its own opening balance. Otherwise archive. |
| Archived account | Closed to new operations. Existing operations stay editable and keep their account. Pickers in edit flows keep showing the operation's own archived account (section 4.7). |
| Account and category names | Trimmed, 1 to 50 characters. **Duplicates are allowed** (confirmed), because a uniqueness rule would need a new index and PRD does not ask for one. |
| Account order | Creation order (id). No manual ordering in v1 (confirmed). |
| Category type | Fixed at creation. A subcategory takes its parent's type. |
| Category depth | **Maximum 3 levels**: root → child → grandchild. Enforced in create and move, and the UI offers only valid parents (section 5.2). PRD 6.3 and Part X are amended (section 5.7). |
| Category delete | Operations are reassigned to another category of the same type or to Uncategorized (`category_id = NULL`). Immediate subcategories move up to the deleted category's parent level, keeping their own descendants and operations. No operation is ever deleted. |
| Aggregation | Per-currency sums, never converted. Direct sums come from SQL, the tree roll-up is pure Kotlin with `Math.addExact`. |
| Default categories | Created **inside database creation** (Room `onCreate`, same transaction as the schema), so they exist before any other code can read or write. No startup task and no flag (section 5.4). Never recreated after the user deletes them. |
| Icons and colors | Icon is a stable string key from a fixed catalog. Color is an ARGB `Long` from a fixed palette. Unknown or null values render a default. |
| Account type | Stored in the existing `account.type` text column as `BANK`, `CASH`, `SAVINGS`, `INVESTMENT`, `OTHER`. Unknown stored value maps to `OTHER` (same pattern as `ThemeMode.fromStorage`). |

## 3. Changes to Foundation code

| Change | Reason |
|---|---|
| `OperationRepository.insertOperation` rejects any entry whose account is `ARCHIVED`. | "Closed to new operations" (4.7). Opening balance of a new account is inserted while the account is ACTIVE, so creation is unaffected. |
| `OperationRepository.insertOperation` and `changeOccurredAt` reject a non-opening entry dated before its account's opening balance. Optional parameter `moveOpeningBalanceBack` moves the opening date instead (4.6). | Opening balance is a starting point (O1). |
| New `OperationRepository.setOpeningBalance(accountId, amountMinor: Long?, occurredAt, now)`. | Keeps one write path for operations and entries (Foundation 3). See 4.4. |
| New DAO queries (section 7). | Needed by the repositories below. |
| `InvariantChecker` gains the opening-balance-order, category-depth and category-cycle checks (section 8). | These cannot be Room constraints. |
| `DatabaseModule` registers a second callback, `DefaultCategorySeedCallback`, next to `FinoraDatabaseCallback`. | Section 5.4. `FinoraDatabaseCallback` and the Foundation tests that use it stay unchanged. |
| `FinoraNavHost` selected-tab logic uses `route == dest.route \|\| route.startsWith(dest.route + "/")`. | Today the Accounts tab loses its highlight on nested routes such as `accounts/{id}`. |

`FinoraApp` is **not** changed. There is no startup seeding task.

Refinement of Foundation 6.3: the opening balance's default date is the **start of the account creation day in the device time zone**, not the creation instant. Otherwise an expense entered later for the same morning would sort before the opening balance.

Amendment of Foundation 6.4: the invariants table gains "category depth at most 3" and "opening balance dated on or before every other entry of its account" (section 8).

## 4. Accounts

### 4.1 Domain

```kotlin
enum class AccountType { BANK, CASH, SAVINGS, INVESTMENT, OTHER; companion fromStorage(String?) }

data class Account(
    val id: Long, val name: String, val currency: Currency, val type: AccountType,
    val icon: String?, val color: Long?, val description: String?,
    val status: AccountStatus, val createdAt: Long,
)

sealed interface Balance { data class Value(val money: Money) : Balance; data object Overflow : Balance }

data class AccountWithBalance(val account: Account, val balance: Balance)
data class OpeningBalance(val operationId: Long, val money: Money, val occurredAt: Long)

/** An account offered by a picker. [isArchivedOriginal] is true only for the edited operation's own archived account. */
data class PickableAccount(val account: Account, val isArchivedOriginal: Boolean)
```

`AccountDraftValidator` is a pure function in `domain/account`. It returns field errors (`NAME_EMPTY`, `NAME_TOO_LONG`, `DESCRIPTION_TOO_LONG`, `AMOUNT_INVALID`, `AMOUNT_TOO_PRECISE`, `AMOUNT_OUT_OF_RANGE`) so ViewModels can map them to messages without parsing exception text. Description maximum is 500 characters.

`OpeningDatePolicy` (pure, `domain/account`) answers: the default opening date for a new account, the default for adding an opening balance to an account that already has entries, and the latest day the picker may offer (4.6).

### 4.2 Rules

| # | Rule | Source |
|---|---|---|
| A1 | An account has exactly one currency, fixed at creation. `AccountRepository.update` has no currency parameter and its SQL does not touch `currency_code`, `status` or `created_at`. | PRD 26.1, 26.7 |
| A2 | Balance is the sum of the account's ledger entries in its own currency. Display Currency and rates never touch it. An account without entries has balance 0 and is still listed. | PRD 26.2 to 26.4 |
| A3 | Archive and unarchive change only `status`. History and balance are untouched. | PRD 26.5 |
| A4 | An archived account cannot receive new operations (repository check). Editing or deleting its existing operations is still allowed, and edit flows keep offering that account (4.7). | PRD 4.4 |
| A5 | Delete is allowed iff `COUNT(entries on this account whose operation type <> OPENING_BALANCE) = 0`. The check and the delete run in one transaction. | PRD 26.6 |
| A6 | Delete removes the account's opening balance operation (its entry cascades) and then the account. Nothing else can reference the account at that point. | PRD 26.6 |
| A7 | Opening balance may be negative. Zero or empty removes it. | PRD 26.8, 26.9, Foundation 6.3 |
| A8 | Creating the first account changes the automatic Display Currency, because `DisplayCurrencyResolver` already follows the lowest-id active account. No new code, covered by a test. | Foundation 6.6 |
| A9 | The opening balance is dated on or before every other entry of its account (O1, 4.6). | PRD 26.8 |

### 4.3 Order of accounts

Active accounts are listed by id (creation order). Archived accounts are listed below in their own section. Manual reordering is not in v1, because it needs a `sort_order` column and therefore a migration. `DisplayCurrencyResolver` depends on lowest id, so ordering by id also keeps the list and the automatic Display Currency consistent.

### 4.4 Data layer

`AccountRepository` (`data/repo`, `@Singleton`, injects `FinoraDatabase` and `OperationRepository` like the existing repositories):

| Function | Behaviour |
|---|---|
| `observeAll(): Flow<List<AccountWithBalance>>` | Uses `AccountDao.observeWithBalance()` (section 7): a `LEFT JOIN` with `COALESCE(SUM(...), 0)`, so accounts with no entries appear with zero. If SQLite raises "integer overflow", fall back to per-account `OperationRepository.balanceMinor` and mark the failing ones `Balance.Overflow`, so one bad account does not blank the list. |
| `observePickable(originalAccountId: Long?): Flow<List<PickableAccount>>` | All active accounts, plus the account `originalAccountId` when it is archived (4.7). |
| `observe(id): Flow<AccountDetail?>` | Account, balance, `OpeningBalance?`, and `earliestOtherEntryAt: Long?` (for the opening date bound). |
| `create(draft, openingMinor: Long?, openingDate: Long?, now): Long` | One transaction: insert account (ACTIVE, `createdAt = now`), then `setOpeningBalance` if the amount is non-zero. `openingDate` defaults to start of day of `now` in the device zone. |
| `update(id, name, type, icon, color, description)` | `UPDATE account SET name, type, icon, color, description WHERE id`. |
| `setArchived(id, archived)` | Rule A3. |
| `delete(id)` | Rules A5 and A6. Throws `AccountHasHistoryException(entryCount)` when refused. |

`OperationRepository.setOpeningBalance(accountId, amountMinor, occurredAt, now)`, one transaction:
- amount `null` or `0`: delete the account's opening operation if present. No date check.
- `occurredAt` later than the account's earliest non-opening entry: throw `OpeningBalanceOrderException` (4.6).
- no existing operation: `insertOperation(OPENING_BALANCE, ...)` with `openingAccountId = accountId`. Rejected for an archived account (4.7).
- existing operation: update the entry amount, then `changeOccurredAt` so the entry's `occurred_at` follows (Foundation 6.4 denormalized timestamp rule), and set `operation.updated_at`. Allowed for an archived account.

The currency of the entry is read from the account inside `insertOperation`, so the amount is always in the account currency.

### 4.5 UI

Routes (added to `Routes`): `accounts/new`, `accounts/{accountId}`, `accounts/{accountId}/edit`.

**Accounts tab** (`AccountsScreen` + `AccountsViewModel`):
- Active accounts as cards: icon in the account color, name, type label, native balance (`Money.format()`), negative balances in the error color.
- Archived accounts in a collapsible section below, visually muted.
- FAB "Add account". Empty state with the same action.
- Tap opens the detail screen.
- No cross-currency total. A mixed-currency sum would be an estimate and belongs to sub-project 5.

**Account detail** (`AccountDetailScreen`): header with icon, name, type, currency; current balance; "Initial balance as of <date>" or "none"; description; actions Edit, Archive or Unarchive, Delete. A reserved section below holds the transaction history list that sub-project 3 adds. Delete is disabled with the explanation "This account has N transactions. Archive it instead." when rule A5 fails. Otherwise delete asks for confirmation and states that the initial balance goes with the account. Archive asks for confirmation when the balance is non-zero and states that archived accounts are excluded from totals. An archived account shows a banner: "Archived. Existing transactions can be edited, new ones cannot be added."

**Account form** (`AccountEditScreen`, create and edit share one screen):
- Name, type (segmented or dropdown), currency, icon, color, description.
- Currency: searchable list from `CurrencyRegistry`, defaulting to the effective Display Currency. On the edit screen it is read-only with the text "Currency cannot be changed after creation."
- Initial balance (create and edit): amount field plus a "negative balance" switch, and a date field. Empty or zero means none. In edit mode the same fields change or remove the existing opening balance.
- Date field rules (4.6): default per `OpeningDatePolicy`; the picker offers no day after today and no day after the account's earliest other entry. When the account already has entries, helper text reads "Earliest transaction: <date>. The initial balance must be dated on or before it."
- In edit mode, when the account has other entries and the amount changes, a non-blocking warning shows the effect: "Changing the initial balance changes the current balance by <delta>."
- On an archived account the initial balance fields are editable only if an opening balance already exists. If none exists the section is disabled with "Unarchive the account to add an initial balance."
- Amount input accepts digits and one decimal separator (`.` or `,`, normalized to `.`). It is parsed with `Money.parse` in the account currency. More fractional digits than the currency scale is an error shown on the field, never rounded. Overflow is an error on the field.
- Save is disabled while any field is invalid. State is an immutable `StateFlow` (Foundation 3).

### 4.6 Opening balance as a starting point

The opening balance is the balance **before any recorded operation** of the account. It is not an ordinary adjustment that may sit in the middle of the history.

| # | Rule |
|---|---|
| O1 | The opening operation's `occurred_at` is less than or equal to the `occurred_at` of every other ledger entry on the same account. Equal is allowed. |
| O2 | The **current** balance is the sum of all entries and never depends on dates. Moving the opening date never changes the current balance. |
| O3 | Balance at time `t` is the sum of the account's entries with `occurred_at <= t`. Because of O1 the series starts at the opening balance, so no point in the series shows a partial history. Entries with equal timestamps order the opening balance first. An account without an opening balance starts at zero before its first entry. |
| O4 | Defaults: a new account uses the start of its creation day (local zone). Adding an opening balance to an account that already has entries uses the start of the local day of the earliest entry when that is earlier than today, otherwise the start of today. The picker maximum is the earlier of today and the day of the earliest other entry. Any day up to and including that day satisfies O1, because a day start is never after a timestamp within that day. |
| O5 | Applies per account. A transfer's two entries are each checked against their own account. |

Enforcement in `OperationRepository`, the only write path:
- `setOpeningBalance` with a date later than the earliest other entry throws `OpeningBalanceOrderException(accountId, openingAt, entryAt)`, a subclass of `InvariantViolationException`.
- `insertOperation` and `changeOccurredAt` that would place a non-opening entry before its account's opening date throw the same exception.
- `changeOccurredAt` on the opening operation itself is validated like `setOpeningBalance`.

**Back-dated operations.** Users will often create an account today and then enter older transactions. The repository therefore does not silently accept them, and does not make the user fix the date by hand:
1. The entry form (sub-projects 3, 4, 6) catches `OpeningBalanceOrderException` and shows: "This account's initial balance is dated <D>. Move it to <entry date> so this transaction comes after it?"
2. On confirm, the form repeats the write with `moveOpeningBalanceBack = true`. The repository then, in the same transaction, moves the opening operation and its entry to the start of the entry's local day, and writes the new operation. On cancel nothing changes.
3. The dialog states that the current balance does not change. Only the history chart is affected, because the initial amount now counts as the balance before the earlier transaction.
4. Generated recurring operations (sub-project 6) never move an opening date on their own. Their start-date handling is defined there (risks, section 12).

The invariant checker reports any account that violates O1 (section 8), so import validation rejects files that break it.

### 4.7 Archived accounts in edit flows

Archiving closes an account to **new** operations only.

Repository rules (`OperationRepository`):
- `insertOperation` throws `InvariantViolationException` when any draft entry is on an archived account. This covers income, expense, both sides of a transfer, and creating a new opening balance.
- Operations already on an archived account stay fully editable: `changeOccurredAt`, `addTag`, `setOpeningBalance` changing or removing an existing opening balance, and the update and delete functions added in sub-projects 3 and 4.
- Rule for those later update functions: an edit may keep an entry on its current account even if archived, and may move an entry to an account only if that account is active. It may never move an entry onto a different archived account.

Picker contract (`AccountPicker`, delivered here, used by sub-projects 3 and 4):
- `AccountPicker(originalAccountId: Long? = null, selectedAccountId: Long?, onSelect: (Long) -> Unit)`, backed by `AccountRepository.observePickable(originalAccountId)`.
- New operation: `originalAccountId = null`, only active accounts are listed.
- Editing an existing operation: the form passes the account that operation currently uses. If that account is archived, it is listed with an "Archived" badge, is preselected, and stays selectable so the user can switch away and back. No other archived account is ever listed.
- With the archived original selected, the form shows a non-blocking note: "This account is archived. You can edit this transaction, but not add new ones."
- A transfer form uses one picker per side, each with its own original account.

Sub-project 3 and 4 hand-off: their edit forms must pass `originalAccountId`, and their tests must cover (a) editing an operation on an archived account keeps it, (b) another archived account is not offered, (c) creating a new operation cannot choose an archived account.

## 5. Categories

### 5.1 Domain

```kotlin
data class Category(
    val id: Long, val name: String, val icon: String?, val color: Long?,
    val type: CategoryType, val parentId: Long?, val createdAt: Long,
)

class CategoryTree(categories: List<Category>) {
    val roots: List<CategoryNode>            // per type via rootsOf(type)
    fun node(id: Long): CategoryNode?
    fun descendantIds(id: Long): Set<Long>   // excludes id
    fun ancestorsOf(id: Long): List<Category>
    fun depthOf(id: Long): Int               // root = 1
    fun heightOf(id: Long): Int              // leaf = 1, counts the node's own level
}
data class CategoryNode(val category: Category, val children: List<CategoryNode>)

object CategoryRules {
    const val MAX_DEPTH = 3
    enum class ParentCheck { OK, SELF, CYCLE, TYPE_MISMATCH, TOO_DEEP }
    /** [id] is null when creating. A null [newParentId] means top level, which is always valid. */
    fun checkParent(tree: CategoryTree, id: Long?, type: CategoryType, newParentId: Long?): ParentCheck
    /** Every category that may be offered as the parent of [id], or of a new category of [type] when [id] is null. */
    fun validParents(tree: CategoryTree, id: Long?, type: CategoryType): Set<Long>
}
```

`CategoryRules.checkParent` is the single place where parent validity is decided. The repository, the form and the picker all call it, so what the UI offers always matches what the repository accepts.

`CategoryTree` is pure Kotlin. It tolerates corrupt input (a missing parent makes the category a root, a cycle or over-deep chain is cut with a visited set) so a bad imported file cannot hang the UI. The invariant checker is what reports such data.

### 5.2 Rules

| # | Rule | Source |
|---|---|---|
| C1 | Income, expense and transfer categories are three independent trees. | PRD 29.1 |
| C2 | A subcategory has its parent's type. Type cannot change after creation. | PRD 29.2 |
| C3 | A category holds direct operations and subcategories at the same time. | PRD 6.4, 29.3 |
| C4 | A category cannot become its own parent or a child of one of its descendants. | PRD 29.7 |
| C5 | Moving a category is allowed only under a parent of the same type, or to the top level. Moving, renaming and recoloring never change an operation or any amount. | PRD 29.6, 27.6 |
| C6 | Deleting a category never deletes operations. The user chooses where its direct operations go: another category of the same type, or Uncategorized. | PRD 6.6, 29.5 |
| C7 | The reassignment target cannot be the category being deleted. Its subcategories survive, so they are valid targets. | |
| C8 | On delete, its immediate subcategories move to the deleted category's parent (or become top level). Their own descendants and all operations stay as they are. The subtree gets one level shallower, so this can never break C11. | |
| C9 | Uncategorized is `operation.category_id IS NULL`. Foundation does not require a category on income, expense or transfer, so this needs no schema or invariant change. Sub-project 3 decides whether its entry form requires one. | |
| C10 | Delete reassigns operations, moves subcategories and deletes the row in one Room transaction. A failure rolls back all three. | Foundation 3 |
| C11 | **Maximum depth is 3** (`CategoryRules.MAX_DEPTH`): root = level 1, child = level 2, grandchild = level 3. A category at level 3 cannot have children. | Product decision, 5.7 |
| C12 | Depth is checked for the whole moved subtree: a move is valid only if `depth(newParent) + heightOf(moved) <= 3`, where a top-level target has depth 0. | |

Examples for C12 (L = level after the move):
- Leaf moved under a level-2 category → level 3, valid.
- Leaf moved under a level-3 category → level 4, rejected.
- Root with one child (height 2) moved under a root → child ends at level 3, valid.
- Root with one child moved under a level-2 category → child ends at level 4, rejected.
- Root with child and grandchild (height 3) moved under any category → rejected. It can only stay or move to the top level.

### 5.3 Data layer

`CategoryRepository` (`data/repo`, `@Singleton`):

| Function | Behaviour |
|---|---|
| `observeAll(): Flow<List<Category>>` | UI builds a `CategoryTree` from it. |
| `create(name, type, parentId?, icon, color, now): Long` | Loads all categories inside the transaction and calls `CategoryRules.checkParent`. A non-`OK` result throws `InvalidCategoryParentException(reason)`, a subclass of `InvariantViolationException`. When `parentId` is set the stored type is the parent's and a mismatching `type` argument is `TYPE_MISMATCH`. |
| `update(id, name, icon, color)` | Never touches type or parent. |
| `move(id, newParentId?)` | Same check inside the transaction (`SELF`, `CYCLE`, `TYPE_MISMATCH`, `TOO_DEEP`), then updates `parent_id`. |
| `deletionImpact(id): DeletionImpact` | `directOperationCount`, `childCount`, `suggestedTarget` (the parent, or Uncategorized). Used by the dialog. |
| `delete(id, target: ReassignTarget)` | `ReassignTarget.To(categoryId)` or `ReassignTarget.Uncategorized`. Rules C6 to C10. |
| `directTotals(type, from, to): List<CategoryDirectTotal>` | Section 5.5. |

Repositories enforce the rules above. The database enforces only the FKs (`foreign_keys=ON`), so a hand-edited file can still violate C4 or C11. That is what the invariant checker covers (section 8).

### 5.4 Default categories

On first run the user should be able to record a first expense and a first transfer without building a tree.

**Mechanism.** `DefaultCategorySeedCallback : RoomDatabase.Callback` inserts the defaults in `onCreate`, exactly like `FinoraDatabaseCallback` seeds currencies. `onCreate` runs once, when the database file is first created, inside the creation transaction and before the first query or write issued through Room can complete. Therefore:
- No ViewModel, repository or screen can create a category or an operation before the defaults exist. There is no window for a user-created category to make the table "non-empty" first, so the race described in review point 1 cannot occur. Nothing runs asynchronously.
- The schema, the currencies and the defaults are created atomically. If seeding throws, database creation fails and is retried on the next open instead of leaving a half-initialised file.
- The defaults are never recreated: `onCreate` does not run again for an existing file. Deleting every default stays deleted.
- An imported database file (sub-project 8) replaces the file as a whole, so its categories are exactly what the file contains and nothing is seeded on top. No flag or "table is empty" guard is needed.
- Hilt's `DatabaseModule` adds the callback next to `FinoraDatabaseCallback`. Tests that want a seeded database add it explicitly, so Foundation tests behave as before.

**Parents get ids first.** The callback inserts each parent with `SupportSQLiteDatabase.insert` and uses the returned row id as `parent_id` for its children. `created_at` is `System.currentTimeMillis()` for all rows.

**Existing installs.** A Foundation build already installed on a development device holds a version 1 database created without defaults, and `onCreate` will not run for it. Those devices need one uninstall or "Clear storage". No released version exists, so no migration is added (section 9). Choosing this over a startup task is deliberate: it removes the race entirely instead of racing against it.

Default set (names are English data, editable and deletable like any user category; there is no app-language setting yet, see risks):

| Type | Tree |
|---|---|
| Expense | Food (Groceries, Restaurants & Cafes), Housing, Transport, Shopping, Entertainment, Health, Subscriptions, Fees & Charges, Other |
| Income | Salary, Freelance, Investment Income, Gifts, Refunds, Other |
| Transfer | Own Account Transfer, Currency Exchange, Savings Transfer, Investment Deposit, Other |

The set lives in `domain/category/DefaultCategories.kt` as plain data (name, icon key, color, children) and is two levels deep at most. "Fees & Charges" gives transfer fees (PRD 8.5) an expense category from day one.

### 5.5 Aggregation

PRD 6.5: a parent shows its own operations plus all descendants. Because accounts have different currencies, aggregation produces **per-currency totals** and never mixes currencies. Converting them is sub-project 5's job.

SQL returns direct sums only:

```sql
SELECT o.category_id      AS category_id,       -- NULL means Uncategorized
       e.currency_code    AS currency_code,
       SUM(e.amount_minor) AS total_minor,
       COUNT(DISTINCT o.id) AS operation_count
FROM operation o
JOIN ledger_entry e ON e.operation_id = o.id
WHERE o.type = :type AND o.occurred_at >= :from AND o.occurred_at < :to
GROUP BY o.category_id, e.currency_code
```

- `type` is `INCOME` or `EXPENSE` only. Transfers have two entries in possibly two currencies, so their amount aggregation is designed in sub-project 7. Opening balances are excluded by the type filter (Foundation 6.3).
- Sums keep the stored sign (expenses are negative). Analytics negates for display.
- `from` and `to` default to `Long.MIN_VALUE` and `Long.MAX_VALUE` (all time). Period boundaries in the device zone are the caller's responsibility (Foundation 6).
- Rows with a NULL `category_id` are returned so Uncategorized is not silently dropped from totals.

`CategoryRollup.rollUp(tree, directTotals): Map<Long, Map<String, Long>>` in `domain/category` adds each category's direct totals to those of all its descendants using `Math.addExact`. Overflow throws `MoneyOverflowException`. It also returns the matching rolled-up `operation_count`. The PRD example must hold: Food 1,250 PLN equals Groceries 700, Restaurants 450 and direct 100.

This sub-project's UI uses only the counts (delete dialog). Amount screens are sub-project 7.

### 5.6 UI

Routes: `more/categories` (exists as placeholder), `more/categories/new?type={type}&parentId={parentId}`, `more/categories/{categoryId}/edit`. The More-tab highlight already covers `more/` prefixes.

**Categories screen** (`CategoriesScreen` + `CategoriesViewModel`):
- Tabs: Expense, Income, Transfer, one tree each.
- The tree is flattened for a `LazyColumn`. Expanded node ids are kept in the ViewModel. Rows show the icon in its color, the name, and an expand arrow when the node has children. With at most 3 levels, indentation is a fixed step per level.
- Row menu: Add subcategory, Edit, Delete. **Add subcategory is not shown on level 3 rows.**
- FAB "Add category" creates a top-level category in the current tab's type.

**Category form** (`CategoryEditScreen`): name, icon, color, parent. Type is shown read-only. The parent is chosen with `CategoryTreePicker` in parent mode, which lists only `CategoryRules.validParents(tree, id, type)`: same type, not the category or its descendants, and shallow enough for the whole moved subtree (C11, C12). Categories that are not valid parents are hidden, not shown disabled. "No parent (top level)" is always offered. Changing the parent calls `move`. In create mode a `parentId` argument preselects the parent and fixes the type. If the repository still rejects the write (for example another edit changed the tree meanwhile), the form shows the `ParentCheck` reason as an error and refreshes the picker.

**Delete dialog**: loads `deletionImpact`. States how many operations will be reassigned and how many subcategories move up one level, and that their own subcategories and operations are kept. A target selector offers the suggested target first (the parent, or Uncategorized), then Uncategorized, then "Choose another category…" via `CategoryTreePicker` in assign mode (every category of the same type except the one being deleted, no depth restriction, because only operations move). Confirm is enabled once a target is set. When there are zero operations and zero subcategories no target is needed.

### 5.7 Amendment to the PRD

The PRD originally required unlimited nesting. The product decision is a maximum of 3 levels. `docs/requirements.md` is updated in this change:
- PRD 6.3: "Categories must support unlimited nesting." becomes "Categories must support nesting up to 3 levels (category → subcategory → sub-subcategory)." The Food example already fits.
- Part X: "Иерархия категорий: Неограниченная" becomes "Ограничена: максимум 3 уровня".
- Foundation 6.4 gets the depth invariant.

PRD 6.5 aggregation, 12.4 drill-down and 29.3 to 29.4 are unaffected, because they describe behaviour at any depth.

## 6. Shared UI components (`ui/common`)

| Component | Notes |
|---|---|
| `IconCatalog` | Map of stable string key → `ImageVector` from `material-icons-extended`, about 40 icons grouped for accounts (wallet, bank, card, savings, trending-up, …) and categories (food, home, car, cart, health, work, gift, …). Missing or unknown key → a default icon. Keys are never renamed, because they are stored in user data and exported files. |
| `ColorPalette` | 16 ARGB values. Null color → a per-type default. A stored color outside the palette still renders. |
| `IconPicker`, `ColorPicker` | Grid pickers used by both forms. |
| `AccountPicker(originalAccountId, selectedAccountId, onSelect)` | Contract in 4.7. Reused by sub-projects 3 and 4. |
| `CategoryTreePicker(type, mode, onSelect)` | Searchable, expandable tree. `mode` is `Assign` (any node of the type is selectable, including a parent with children, PRD 6.4; optional excluded id for the delete dialog) or `Parent(movingId?)` (only `validParents`, plus "top level"). Reused by sub-project 3 in `Assign` mode. |

All user-visible text goes in `strings.xml`. Icon-only buttons have content descriptions.

## 7. DAO additions

| DAO | Additions |
|---|---|
| `AccountDao` | `observeWithBalance()`, `observePickable(originalAccountId)`, `observeById(id)`, `updateDetails(id, name, type, icon, color, description)`, `setStatus(id, status)`, `deleteById(id)`, `nonOpeningEntryCount(id)` |
| `OperationDao` | `openingBalanceOf(accountId)`, `deleteById(id)` |
| `LedgerEntryDao` | `updateAmount(operationId, amountMinor)`, `earliestNonOpeningEntryAt(accountId): Long?`, `openingDateOf(accountId): Long?` |
| `CategoryDao` | `getAll()`, `updateDetails(id, name, icon, color)`, `setParent(id, parentId)`, `reparentChildren(fromParentId, toParentId)`, `reassignOperations(fromId, toId, now)`, `operationCount(id)`, `childCount(id)`, `deleteById(id)`, `directTotals(type, from, to)` |

**`AccountDao.observeWithBalance()` is required to be written exactly in this shape:**

```sql
SELECT a.*, COALESCE(SUM(e.amount_minor), 0) AS balance_minor
FROM account a
LEFT JOIN ledger_entry e ON e.account_id = a.id
GROUP BY a.id
ORDER BY a.id
```

- `LEFT JOIN`, not `JOIN`: an account with no entries must still be returned.
- `COALESCE(SUM(...), 0)`: `SUM` of no rows is NULL, which must become 0.
- No `WHERE` on `status`: archived accounts are returned too and the UI splits them into sections.
- Result type is an `@Embedded` account plus `balance_minor: Long`.
- The ledger side uses the covering index `ledger_entry(account_id, occurred_at, amount_minor)` (a test checks the query plan, section 10).

`AccountDao.observePickable(originalAccountId)` selects accounts where `status = 'ACTIVE' OR id = :originalAccountId`, ordered by id. It returns the original account only if it exists, whatever its status.

`reassignOperations` sets `category_id` and `updated_at` on every affected operation. Existing indexes cover the other queries: `ledger_entry(account_id, …)`, `operation(category_id, occurred_at)`, `category(parent_id)`.

## 8. Invariant checker additions

Added to `InvariantChecker.checks` and therefore also run by import validation in sub-project 8:

| Check | Detects |
|---|---|
| `opening balance dated after an entry` | An account whose opening operation has `occurred_at` greater than the `occurred_at` of any other entry on that account (O1). |
| `category deeper than 3 levels` | A category with more than two ancestors. Uses a recursive CTE whose recursion stops at depth 5, so it terminates on any data, including cycles. |
| `category cycle` | A category that is its own ancestor, using the same bounded CTE. A cycle that is too long to repeat within the bound is still reported by the depth check, because its ancestor chain never ends. |
| `category type mismatch` and `child category type differs from parent` | Already present. Kept, now covered by the new tests. |

## 9. Migrations

None. `FinoraDatabase.VERSION` stays 1. All existing Foundation tests keep passing unchanged, including the production-chain check that no migration targets a version above 1. A schema test compares the exported `app/schemas/.../1.json` with its committed content. Seeding in `onCreate` writes rows, not schema, so it does not affect the schema file.

## 10. Testing

JVM unit tests:
- `AccountDraftValidator`: empty and whitespace names, 50 and 51 characters, description length, amount parsing with `.` and `,`, too many fractional digits for scale 0, 2 and 3 currencies, overflow.
- `AccountType.fromStorage`: known values, null, unknown → `OTHER`.
- `OpeningDatePolicy`: start of the local day (including a DST transition day); default for a new account; default and picker maximum for an account whose earliest entry is earlier than today, equal to today, and absent.
- `CategoryTree`: build, children, `descendantIds`, `ancestorsOf`, `depthOf`, `heightOf`, tolerance of a missing parent and of a cyclic or over-deep input.
- `CategoryRules.checkParent` and `validParents`:
  - top level always `OK`;
  - `SELF`, `CYCLE` (own child, own grandchild), `TYPE_MISMATCH`;
  - **depth**: create root (level 1), child (level 2) and grandchild (level 3) are `OK`; create under a level-3 category is `TOO_DEEP`;
  - move a leaf under level 2 → `OK`; under level 3 → `TOO_DEEP`;
  - move a height-2 subtree under a root → `OK`; under a level-2 category → `TOO_DEEP`;
  - move a height-3 subtree under any category → `TOO_DEEP`, and to the top level → `OK`;
  - `validParents` never contains a level-3 category, the moved category, its descendants, or another type, and every member passes `checkParent`.
- `CategoryRollup`: the PRD 6.5 example (1,250 = 700 + 450 + 100), a 3-level tree, several currencies kept apart, uncategorized bucket, `MoneyOverflowException` on overflow, counts roll up.
- `DefaultCategories`: every child has its parent's type, depth is at most 3, all icon keys exist in `IconCatalog`, no blank names.
- `IconCatalog` and `ColorPalette`: unknown key and null color fall back without throwing.

Instrumented Room tests (`AccountRepositoryTest`, `CategoryRepositoryTest`, `DefaultCategorySeedTest`, in-memory database with `FinoraDatabaseCallback` unless noted):

Accounts
- Create without opening balance creates no operation. Create with positive and with negative opening balance gives the right balance and passes `InvariantChecker`.
- Create stores `createdAt = now` and the opening entry at the start of that day.
- `setOpeningBalance`: insert, change amount, change date (entry `occurred_at` follows), set to zero removes it, second call never produces two opening operations.
- **Opening balance order (O1)**:
  - with an expense dated D, `setOpeningBalance` at D succeeds and at D + 1 ms throws `OpeningBalanceOrderException`;
  - `insertOperation` for an expense dated before the opening date throws, dated equal succeeds;
  - `changeOccurredAt` moving an ordinary operation before the opening date throws and changes nothing;
  - `changeOccurredAt` moving the opening operation after the earliest entry throws;
  - `moveOpeningBalanceBack = true` moves the opening operation and its entry to the start of the entry's day and writes the operation in one transaction, the current balance is unchanged, and `InvariantChecker` is clean;
  - a transfer is checked per side: one side before its account's opening date throws, the other account's opening date is irrelevant;
  - removing the opening balance (amount 0) never throws;
  - the checker flags an opening balance dated after an entry inserted with raw SQL.
- `update` leaves `currency_code`, `status`, `created_at` unchanged; raw SQL currency change fails once an entry exists (already covered by Foundation, repeated for an account created through the repository).
- Archive and unarchive keep balance and entries.
- **Archived accounts**:
  - `insertOperation` (income, expense, either side of a transfer) on an archived account throws; after unarchive it succeeds;
  - on an archived account `changeOccurredAt`, `addTag` and `setOpeningBalance` that edits or removes an existing opening balance succeed; `setOpeningBalance` that would create a new one throws;
  - `observePickable(null)` returns only active accounts; `observePickable(archivedId)` returns the active accounts plus that archived one flagged `isArchivedOriginal`, and never a second archived account; `observePickable(activeId)` equals the active list;
  - the list updates when the original account is archived while the form is open.
- **Balance query**:
  - `observeWithBalance` returns an account with no entries with balance 0 (not missing, not null);
  - returns archived accounts as well;
  - matches per-account sums, shows negatives, keeps accounts in id order;
  - handles an overflow in one account by marking only that account `Overflow`;
  - `EXPLAIN QUERY PLAN` shows a covering-index search on `ledger_entry` and no scan of `ledger_entry`.
- Delete: succeeds with no history and removes the opening operation and its entry; succeeds with only an opening balance; refused with an expense; refused with either side of a transfer; refusal leaves everything unchanged.
- Creating the first account switches the automatic Display Currency to its currency.

Categories
- Create root, child and grandchild; child takes the parent's type; mismatching type throws `TYPE_MISMATCH`; **creating under a grandchild throws `TOO_DEEP`** and inserts nothing.
- Move: to top level, to another parent of the same type; rejected for self, own child, own grandchild, a parent of another type; **rejected with `TOO_DEEP` for each depth case in section 10 (JVM `checkParent`) when run against real rows, including a height-2 subtree under a level-2 category**; accepted at exactly level 3. Operations and amounts are unchanged after a move, and a rejected move leaves `parent_id` unchanged.
- Delete with reassignment to a sibling, to the parent, to Uncategorized (NULL): operation count before and after is equal, `updated_at` changed, immediate subcategories now point at the deleted category's parent (or are top level), their own children and operations are untouched, the row is gone, `InvariantChecker` is clean.
- Delete of a level-2 category with children keeps every remaining category at depth at most 3.
- Delete rejects a target of another type, the category itself, and a nonexistent target; each rejection changes nothing (transaction rolled back).
- Delete of an unused leaf needs no target.
- `directTotals` plus `CategoryRollup` on real rows: parent total includes descendants and direct operations; period bounds are half-open; opening balances are excluded; two currencies stay separate; uncategorized appears with a NULL id.
- `InvariantChecker` flags, from raw SQL: a self-parent, a two-category cycle, and a 4-level chain; it does not flag a valid 3-level chain.

Default category seeding (`DefaultCategorySeedTest`, databases built with `DefaultCategorySeedCallback`)
- **Race test**: on a freshly built database, the very first DAO call is inserting a user category. Afterwards the table contains the user category **and** all defaults (count = defaults + 1). This is the regression test for review point 1.
- The seeded rows pass `InvariantChecker`, parents are linked correctly, depth is at most 2, and all three types are present.
- A file-based database is created, all categories are deleted, the database is closed and reopened: the table stays empty.
- A database built without the seed callback (all Foundation tests) has no categories.

Schema: committed `1.json` is unchanged.

Manual checklist (run on a device or emulator before sign-off; first uninstall or clear data on devices with a Foundation build):
1. First launch: Categories shows the defaults in all three tabs before any interaction.
2. Create PLN, USD and JPY accounts; each shows its own scale. Create one with a negative opening balance. Create an account with no opening balance: it is listed with zero.
3. Edit name, icon, color. Currency is read-only.
4. Archive a non-zero account; it moves to the Archived section, history intact. Unarchive it.
5. Delete an empty account (works) and, after sub-project 3 exists, one with transactions (refused). Until then, create a test expense through a debug-only path or the instrumented tests.
6. Edit the date of an account's initial balance: the picker does not offer days after today or after its earliest transaction.
7. Build Food → Restaurants & Cafes → Coffee shops (exactly 3 levels). Coffee shops has no "Add subcategory" action. Move Coffee shops between parents. Try to move Food under Coffee shops (not offered), and try to move a category with a child under Restaurants & Cafes (not offered).
8. Delete Food with the dialog's default target; verify subcategories moved up one level with their children.
9. Light and dark theme, large font scale, screen rotation during a form edit.

## 11. Done when

1. Accounts tab and Categories screen replace their placeholders and all flows in sections 4.5 and 5.6 work.
2. All tests in section 10 pass, together with every Foundation test.
3. Database version and `1.json` are unchanged.
4. PRD acceptance items for ACCOUNTS (all five) and for CATEGORIES (all five, aggregation verified at the data layer, nesting within the 3-level limit) are met. TRANSACTIONS items stay open until sub-project 3.
5. `docs/requirements.md` and Foundation 6.4 carry the 3-level amendment.
6. The build is clean, with no new lint warnings.

## 12. Risks

- **Default category names are stored English text.** There is no app-language setting yet (PRD 17.1), so the seeded names will not follow a later language change. Acceptable because the names are user data, but a settings sub-project may need a one-time rename helper.
- **Seeding only reaches new database files.** Choosing `onCreate` removes the startup race but means an existing Foundation-era database gets no defaults. Acceptable before the first release (section 5.4). If a release ever ships without defaults, seeding for those users needs its own migration spec.
- **Delete hooks for later references.** Recurring schedules (6), templates and budgets will reference accounts and categories. `AccountRepository.delete` and `CategoryRepository.delete` are the two places that must learn about them. Each later sub-project adds its reassignment step and test there.
- **Recurring and back-dated starts.** A recurring schedule whose start date is before an account's opening date would hit `OpeningBalanceOrderException`. Sub-project 6 must define whether it clamps to the opening date, skips, or asks the user. It must not silently move the opening date (4.6, item 4).
- **Opening balance rule is stricter than the schema.** Files edited outside the app that break O1 are rejected on import, as intended, but the rejection report must name the account so the user can understand it. Sub-project 8 uses the checker messages for that.
- **Depth limit versus imported data.** A file with categories deeper than 3 levels is rejected by import validation. The in-app tree builder still renders such data safely (5.1), so a database that somehow contains it does not crash the UI.
- **Balance list query cost.** The left-join-and-`SUM` query runs on every ledger change. The covering index keeps it cheap for realistic histories (NFR-18). If measurements disagree in sub-project 7, a per-account cache can be added without changing the schema.
- **Date picker and time zone.** The Material date picker returns UTC midnight. The mapping to a local-day start is isolated in one helper (`OpeningDatePolicy`) with a DST test.

## 13. Confirmed decisions

1. Duplicate account and category names are allowed.
2. Deleting a category moves its immediate subcategories up to the deleted category's parent level; their descendants and operations are preserved.
3. Archived accounts allow editing existing operations but cannot be used for new ones.
4. Default categories are seeded on first launch and never recreated after the user deletes them.
5. Manual account ordering is not needed in v1; creation order is used.

## 14. Review resolutions (revision 2)

| Review point | Resolution | Where |
|---|---|---|
| 1. Default category seeding race | Seeding moved from an asynchronous `FinoraApp` task into `RoomDatabase.Callback.onCreate`, in the database creation transaction. No code can touch the database before it completes. The flag, the "table is empty" guard and the `FinoraApp` change are removed. A race regression test is added. | 2, 3, 5.4, 10 |
| 2. Opening balance date semantics | Opening balance is the starting point: dated on or before every other entry (O1 to O5). Enforced in `OperationRepository` for creating, editing and moving operations, with a confirm-and-move flow for back-dated entries. UI bounds and warnings, checker rule, tests. | 4.2 A9, 4.4, 4.5, 4.6, 8, 10 |
| 3. Archived accounts in edit flows | New operations rejected, existing ones editable. `AccountPicker` takes `originalAccountId` and `observePickable` keeps the operation's own archived account visible and selectable, and no other archived account. Hand-off requirements for sub-projects 3 and 4. | 4.4, 4.7, 6, 7, 10 |
| 4. Balance query | `observeWithBalance()` specified verbatim with `LEFT JOIN` and `COALESCE(SUM(...), 0)`. Tests for zero-entry and archived accounts, and for the query plan. | 7, 10 |
| 5. Category depth limit | Maximum 3 levels (C11, C12). One rule function, `CategoryRules`, used by repository, form and picker. Enforced in create and move, invalid parents hidden in the UI, "Add subcategory" hidden at level 3. Checker rules and depth tests. PRD 6.3, Part X and Foundation 6.4 amended. | 2, 5.1 to 5.3, 5.6, 5.7, 8, 10 |
