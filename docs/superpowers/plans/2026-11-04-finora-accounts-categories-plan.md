# Finora: Accounts and Categories Implementation Plan

Spec: `docs/superpowers/specs/2026-11-04-finora-accounts-categories-design.md` (revision 2). Section numbers below ("S4.6") refer to it.
Package root: `com.yehor.finora` under `app/src/main/java/com/yehor/finora`. Tests: `app/src/test/java/...` (JVM) and `app/src/androidTest/java/...` (instrumented).

## Ground rules

- No schema change. `FinoraDatabase.VERSION` stays 1 and `app/schemas/.../1.json` must stay byte-identical (S2, S9). Every new DAO query is a `@Query` on existing tables.
- Test first for every domain and repository rule. Write the failing test, then the code.
- All writes to `operation` and `ledger_entry` go through `OperationRepository` (S4.4).
- User-visible text goes in `res/values/strings.xml`. Icon-only buttons get content descriptions.
- After each phase: `gradlew :app:testDebugUnitTest` must pass. After phases 2, 3, 4, 5: `gradlew :app:connectedDebugAndroidTest` must pass (needs a device or emulator).
- Commit at the end of each phase. Keep commits small and revertible.

## Current code the plan builds on

| File | What exists | Phase that touches it |
|---|---|---|
| `data/db/Daos.kt` | Minimal DAOs (`AccountDao`, `CategoryDao`, `OperationDao`, `LedgerEntryDao` with `balanceMinor`) | 2 |
| `data/db/Entities.kt` | Entities, unique index on `operation.opening_account_id`, covering index `ledger_entry(account_id, occurred_at, amount_minor)` | none |
| `data/db/FinoraDatabase.kt` | `FinoraDatabaseCallback` object (currency seed, `foreign_keys=ON`) | 4 (new sibling callback) |
| `data/db/InvariantChecker.kt` | `checks` list of name to SQL | 5 |
| `data/repo/OperationRepository.kt` | `insertOperation`, `changeOccurredAt`, `addTag`, `balanceMinor`, `InvariantViolationException` | 2 |
| `di/DatabaseModule.kt` | Builds DB with one callback; provides `AppSettingDao`, `AccountDao` | 4 |
| `ui/navigation/Destinations.kt`, `FinoraNavHost.kt` | `Routes`, placeholders for Accounts and Categories, selected-tab logic | 7, 8, 9 |
| `domain/model/Enums.kt` | `AccountStatus`, `CategoryType`, `OperationType`, `ThemeMode.fromStorage` pattern | 1 |
| `domain/money/*` | `Money.parse/format`, `CurrencyRegistry`, `MoneyOverflowException` | reused |

## Phase 0: Baseline and documentation amendments

1. Run `gradlew :app:testDebugUnitTest` and the androidTest suite once. Record that all Foundation tests are green before any change.
2. Edit `docs/requirements.md`: PRD 6.3 becomes "nesting up to 3 levels (category → subcategory → sub-subcategory)"; Part X hierarchy row becomes "Ограничена: максимум 3 уровня" (S5.7).
3. Edit the Foundation design (`2026-11-03-finora-foundation-design.md`) section 6.4: add the invariants "category depth at most 3" and "opening balance dated on or before every other entry of its account" (S3, S8).
4. Add a unit test `SchemaUnchangedTest` (JVM, reads `app/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json`, compares with a committed copy under `app/src/test/resources/schema-v1.expected.json`). Copy the current file there first. This is the guard for S9.

Done when: docs updated, schema guard test green.

## Phase 1: Pure domain (JVM tests, no Android)

All in `domain/`. Order matters because later phases depend on these types.

1. `domain/account/AccountType.kt`: enum `BANK, CASH, SAVINGS, INVESTMENT, OTHER` with `fromStorage(String?)` (unknown or null → `OTHER`). Test `AccountTypeTest`.
2. `domain/account/Account.kt`: `Account`, `Balance` (`Value`, `Overflow`), `AccountWithBalance`, `OpeningBalance`, `PickableAccount`, `AccountDetail` (account, balance, `OpeningBalance?`, `earliestOtherEntryAt: Long?`). `Account.currency` is a `domain.money.Currency` resolved with `CurrencyRegistry`.
3. `domain/account/AccountDraftValidator.kt`: pure function returning a set of `AccountField Error` values (`NAME_EMPTY`, `NAME_TOO_LONG`, `DESCRIPTION_TOO_LONG`, `AMOUNT_INVALID`, `AMOUNT_TOO_PRECISE`, `AMOUNT_OUT_OF_RANGE`). Name is trimmed, 1..50. Description max 500. Amount text accepts `.` or `,` (normalize to `.`), parsed through `Money.parse`; map `InvalidMoneyException` to INVALID/TOO_PRECISE (check scale first so the two are distinguishable) and `MoneyOverflowException` to OUT_OF_RANGE. Test `AccountDraftValidatorTest` per S10 (50/51 chars, scale 0/2/3, overflow, comma).
4. `domain/account/OpeningDatePolicy.kt`: takes a `ZoneId` and `Clock` (inject both for tests). Functions: `startOfDay(instant)`, `defaultForNewAccount(now)`, `defaultForExistingAccount(now, earliestOtherEntryAt)`, `latestAllowed(now, earliestOtherEntryAt)`, `fromPickerUtcMidnight(utcMillis)` (the Material date picker returns UTC midnight; this is the one mapping helper, S12). Test `OpeningDatePolicyTest` including a DST transition day.
5. `domain/category/Category.kt` and `CategoryTree.kt`: `Category`, `CategoryNode`, `CategoryTree` (`rootsOf(type)`, `node`, `descendantIds`, `ancestorsOf`, `depthOf`, `heightOf`). Build with a visited set so missing parents become roots and cycles or over-deep chains terminate. Test `CategoryTreeTest` including corrupt input.
6. `domain/category/CategoryRules.kt`: `MAX_DEPTH = 3`, `ParentCheck`, `checkParent`, `validParents`. Rule: `depth(newParent) + heightOf(moved) <= 3`, top level = depth 0, creating uses height 1. Check order: SELF, TYPE_MISMATCH, CYCLE, TOO_DEEP. Test `CategoryRulesTest` covering every bullet in S10 (including that every member of `validParents` passes `checkParent`).
7. `domain/category/CategoryRollup.kt`: `rollUp(tree, directTotals)` returning per-category per-currency sums and rolled-up operation counts, `Math.addExact` with `MoneyOverflowException`. `CategoryDirectTotal(categoryId: Long?, currencyCode, totalMinor, operationCount)`. Uncategorized (`null` id) is returned under its own key. Test `CategoryRollupTest` including PRD 6.5 (Food 1,250 = 700 + 450 + 100).
8. `domain/category/DefaultCategories.kt`: plain data for the S5.4 table (name, icon key, color, children). Test `DefaultCategoriesTest` (types, depth ≤ 2, no blank names; icon-key check is added in Phase 6 when `IconCatalog` exists).
9. Exceptions: `OpeningBalanceOrderException(accountId, openingAt, entryAt)`, `InvalidCategoryParentException(reason)`, `AccountHasHistoryException(entryCount)`. The first two extend `InvariantViolationException` (declare them next to it in `data/repo`).

Done when: all new JVM tests pass.

## Phase 2: DAO additions and OperationRepository changes

Instrumented tests live in a new `OperationRepositoryOpeningBalanceTest` and an extension of the existing ledger tests.

1. `Daos.kt`, per S7:
   - `AccountDao`: `observeWithBalance()` written **exactly** as in S7 (LEFT JOIN, COALESCE, no WHERE, `ORDER BY a.id`) returning `AccountWithBalanceRow(@Embedded account, @ColumnInfo(name="balance_minor") balanceMinor)`; `observePickable(originalAccountId)`; `observeById(id)`; `updateDetails(...)`; `setStatus(id, status)`; `deleteById(id)`; `nonOpeningEntryCount(id)`.
   - `OperationDao`: `openingBalanceOf(accountId)`, `deleteById(id)`.
   - `LedgerEntryDao`: `updateAmount(operationId, amountMinor)`, `earliestNonOpeningEntryAt(accountId)`, `openingDateOf(accountId)`.
   - `CategoryDao` additions are done in Phase 3.
2. `OperationRepository`:
   - `insertOperation`: reject any entry on an `ARCHIVED` account (S4.7); reject a non-opening entry dated before its account's opening date (S4.6); add `moveOpeningBalanceBack: Boolean = false` which, in the same transaction, moves the opening operation and entry to the start of the entry's local day. The "start of day" computation is passed in or taken from `OpeningDatePolicy`, not duplicated.
   - `changeOccurredAt`: same O1 checks per entry/account; for the opening operation itself, validate like `setOpeningBalance`.
   - New `setOpeningBalance(accountId, amountMinor: Long?, occurredAt, now)` following the four cases in S4.4 (null/0 deletes; late date throws `OpeningBalanceOrderException`; no existing → `insertOperation(OPENING_BALANCE, openingAccountId=accountId)`, rejected when archived; existing → `updateAmount` + `changeOccurredAt` + `updated_at`, allowed when archived).
3. Tests (S10 "Accounts" bullets, O1 and Archived groups): equal-timestamp allowed, +1 ms throws, transfer checked per side, `moveOpeningBalanceBack` keeps balance and passes `InvariantChecker`, archived insert throws for income, expense, either transfer side and new opening balance; archived `changeOccurredAt`/`addTag`/edit-existing-opening succeed.
4. Check all existing callers/tests still compile and pass (`LedgerSchemaTest` inserts operations directly, verify it does not trip the new checks; it should use DAOs or accounts that are ACTIVE).

Done when: new and old instrumented tests pass.

## Phase 3: AccountRepository and CategoryRepository

1. `CategoryDao` additions: `getAll`, `updateDetails`, `setParent`, `reparentChildren`, `reassignOperations(fromId, toId, now)` (sets `category_id` and `updated_at`), `operationCount`, `childCount`, `deleteById`, `directTotals(type, from, to)` with the S5.5 SQL.
2. `data/repo/AccountRepository.kt` (`@Singleton`, injects `FinoraDatabase` and `OperationRepository`): `observeAll` (with the overflow fallback to per-account `OperationRepository.balanceMinor`, marking only failing accounts `Balance.Overflow`), `observePickable`, `observe`, `create`, `update`, `setArchived`, `delete`. `update` must not accept or write `currency_code`, `status`, `created_at` (A1).
   - `create`: one transaction; `createdAt = now`; opening date defaults to `OpeningDatePolicy.startOfDay(now)`; opening balance only when amount is non-zero.
   - `delete`: in one transaction, `nonOpeningEntryCount == 0` else `AccountHasHistoryException`; delete opening operation (entry cascades), then the account.
3. `data/repo/CategoryRepository.kt` (`@Singleton`): `observeAll`, `create`, `update`, `move`, `deletionImpact`, `delete(id, ReassignTarget)`, `directTotals`. `create` and `move` load all categories inside the transaction and call `CategoryRules.checkParent`. `delete`: validate target (not self, same type, exists) → `reassignOperations` → `reparentChildren(id, parentId)` → `deleteById`, all in one `withTransaction`; any failure rolls back (C6 to C10). Types `DeletionImpact`, `ReassignTarget`, `CategoryDirectTotal`.
4. Provide DAOs in `DatabaseModule` only if a class needs them directly; repositories inject `FinoraDatabase`.
5. Instrumented tests `AccountRepositoryTest`, `CategoryRepositoryTest` exactly per S10 (Balance query group including `EXPLAIN QUERY PLAN` on the balance query asserting a covering-index search on `ledger_entry`; first-account switches automatic Display Currency via the existing `DisplayCurrencyResolver`).

Done when: instrumented repository tests pass.

## Phase 4: Default category seeding

1. `data/db/DefaultCategorySeedCallback.kt`: `RoomDatabase.Callback` object. In `onCreate(db)`, walk `DefaultCategories` and insert with `db.insert("category", OnConflictStrategy.ABORT, ContentValues)`; use the returned row id as `parent_id` for children. `created_at = System.currentTimeMillis()`. Do not touch `onOpen` (the foreign-key pragma stays in `FinoraDatabaseCallback`).
2. `DatabaseModule.provideDatabase`: add `.addCallback(DefaultCategorySeedCallback)` after `FinoraDatabaseCallback`.
3. Tests `DefaultCategorySeedTest` (S10): race test (first DAO call inserts a user category; afterwards count = defaults + 1), seeded rows pass `InvariantChecker` with depth ≤ 2 and all three types, file-based DB with all categories deleted then reopened stays empty, DB without the callback has none.
4. Note for the manual run: devices with a Foundation-era install need uninstall or Clear storage (S5.4).

Done when: seed tests pass and a fresh install shows defaults.

## Phase 5: InvariantChecker additions

Add to the `checks` list in `InvariantChecker.kt` (S8):
- `opening balance dated after an entry` (opening operation `occurred_at` > any other entry on its account).
- `category deeper than 3 levels` and `category cycle`, both with a recursive CTE bounded at depth 5.

Tests in `CategoryRepositoryTest`/new `InvariantCheckerCategoryTest` using raw SQL: self-parent, two-cycle, 4-level chain flagged; valid 3-level chain not flagged; raw-SQL late opening balance flagged. Confirm the existing two category checks are exercised.

Done when: new checker tests pass, Foundation checker tests unchanged.

## Phase 6: Shared UI components (`ui/common`)

1. `IconCatalog.kt` (~40 `ImageVector` keys from `material-icons-extended`, default icon, `AccountIcons`/`CategoryIcons` groups) and `ColorPalette.kt` (16 ARGB values, per-type default, out-of-palette color still renders). JVM tests for fallbacks. Extend `DefaultCategoriesTest` to assert every default icon key exists in `IconCatalog` (a plain-Kotlin map of keys to a lambda/`ImageVector` is fine in a JVM test; if `ImageVector` loading is awkward, keep the key set in a separate `IconKeys` object that the test reads).
2. `IconPicker`, `ColorPicker` (grid composables).
3. `AccountPicker(originalAccountId, selectedAccountId, onSelect)` backed by `AccountRepository.observePickable` through a small `AccountPickerViewModel`; "Archived" badge on the original archived account (S4.7).
4. `CategoryTreePicker(type, mode, onSelect)` with `PickerMode.Assign(excludedId)` and `PickerMode.Parent(movingId)`; flatten via `CategoryTree`, searchable, expandable; Parent mode lists only `validParents` plus "No parent (top level)". Keep the flatten/search/filter logic in a pure function (`CategoryPickerModel`) with JVM tests.
5. Compose previews for each component (render with `studio_render_compose_preview` while building).

Done when: components render in previews and pure logic has JVM tests.

## Phase 7: Accounts UI

1. `Routes`: add `ACCOUNT_NEW = "accounts/new"`, `ACCOUNT_DETAIL = "accounts/{accountId}"`, `ACCOUNT_EDIT = "accounts/{accountId}/edit"` plus builder helpers.
2. `ui/accounts/AccountsViewModel` + `AccountsScreen`: active cards (icon in account color, name, type label, native `Money.format()`, negative in error color), collapsible Archived section, FAB, empty state, overflow balance shown as an error text. No cross-currency total (S4.5).
3. `AccountDetailViewModel` + `AccountDetailScreen`: header, balance, "Initial balance as of <date>" or none, description, Edit/Archive/Unarchive/Delete, archived banner, reserved empty section for sub-project 3, delete disabled with "This account has N transactions. Archive it instead." when A5 fails, confirmations for delete and for archiving a non-zero balance.
4. `AccountEditViewModel` + `AccountEditScreen` (create and edit): all fields from S4.5, currency searchable list defaulting to the effective Display Currency and read-only on edit, opening-balance amount + negative switch + date field using `OpeningDatePolicy` bounds and helper text, delta warning on edit, archived-account rules for the opening-balance section, save disabled while `AccountDraftValidator` reports errors, immutable `StateFlow` UI state. Map `OpeningBalanceOrderException` to a field error.
5. ViewModel JVM tests with fake repositories + Turbine (state mapping, validation gating, delete-disabled logic) where the logic is non-trivial; keep logic out of composables.

Done when: all flows 2 to 6 in the manual checklist work.

## Phase 8: Categories UI

1. `Routes`: add `CATEGORY_NEW = "more/categories/new?type={type}&parentId={parentId}"` and `CATEGORY_EDIT = "more/categories/{categoryId}/edit"` with nav arguments (nullable `parentId`).
2. `CategoriesViewModel` + `CategoriesScreen`: three tabs (Expense, Income, Transfer), flattened tree in a `LazyColumn`, expanded ids kept in the ViewModel, row menu (Add subcategory hidden at level 3, Edit, Delete), FAB.
3. `CategoryEditViewModel` + `CategoryEditScreen`: name, icon, color, read-only type, parent via `CategoryTreePicker(Parent)`; create mode preselects `parentId` and fixes type; repository rejection shown as a `ParentCheck` error and picker refreshed.
4. Delete dialog: loads `deletionImpact`, shows counts, target selector (suggested first, Uncategorized, "Choose another category…" via `CategoryTreePicker(Assign, excluded = id)`), confirm enabled when a target is set or nothing to move.
5. JVM tests for the flatten/expansion logic and the delete-dialog state machine.

Done when: manual checklist items 1, 7, 8 pass.

## Phase 9: Navigation wiring

1. `FinoraNavHost.kt`: replace `Routes.ACCOUNTS` and `Routes.CATEGORIES` placeholders with the real screens; register the new routes with `navArgument`s.
2. Selected-tab logic: `currentDestination?.hierarchy?.any { it.route == dest.route || it.route?.startsWith(dest.route + "/") == true }` (S3). Keep the existing More-tab `more/` special case or fold it into the same rule; verify the Accounts tab stays highlighted on `accounts/{id}`.
3. Remove unused placeholder strings only if nothing else references them.

Done when: tab highlight works on nested routes and back navigation behaves.

## Phase 10: Verification and sign-off

1. `gradlew :app:testDebugUnitTest` all green.
2. `gradlew :app:connectedDebugAndroidTest` all green (including Foundation tests).
3. `gradlew :app:lintDebug` reports no new warnings.
4. `SchemaUnchangedTest` green; `git diff app/schemas` empty.
5. Run the manual checklist (S10) on a device after uninstalling the old build; check light and dark theme, large font scale, rotation during a form edit.
6. Walk through S11 "Done when" items 1 to 6 and tick each against evidence.

## Hand-off notes for later sub-projects

- Sub-projects 3 and 4: forms must pass `originalAccountId` to `AccountPicker`, catch `OpeningBalanceOrderException` and offer `moveOpeningBalanceBack`, and test the three archived-account cases (S4.7).
- Sub-project 6: define behavior for recurring starts before an opening date (S12).
- Sub-projects 6+ must extend `AccountRepository.delete` and `CategoryRepository.delete` with their own reassignment steps (S12).

## Risks to watch while implementing

- Room's `@Embedded` plus an extra column in `observeWithBalance` needs a prefix-free row class; if column names collide, alias them explicitly rather than changing entities.
- `changeOccurredAt` is used by `moveOpeningBalanceBack`; avoid recursive validation (the opening move must not fail its own O1 check).
- `DefaultCategorySeedCallback` runs inside Room's creation transaction; use only the `SupportSQLiteDatabase` passed to `onCreate`, never the Room DAOs.
- `LedgerSchemaTest` and other Foundation tests may insert on archived or out-of-order data; adjust test setup, not production rules, if they trip the new checks.
