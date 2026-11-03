# Finora: Roadmap and Foundation Design

Date: 2026-11-03
Status: Draft for user review
Source of requirements: Personal Finance Tracker Product Specification v1.0 (PRD). Section numbers like "PRD 26.7" refer to it.

## 1. Scope of this document

1. A roadmap that splits the PRD into 8 sub-projects, each with its own spec → plan → implementation cycle.
2. A full design for sub-project 1, **Foundation**. Later sub-projects are described only to the depth needed to keep Foundation's decisions compatible with them.

Out of scope for v1 (unchanged from PRD 3): accounts/auth, cloud, backend, sync, bank integration, investments, shared accounts, AI advice. Also deferred: budgets, templates, attachments, CSV/JSON import and export, SQLCipher, app lock.

PRD priority adjustments adopted: Recurring and currency display/rates are P0. Import/Export stays last, but the data model is designed for it from the start.

## 2. Decisions

| Topic | Decision |
|---|---|
| UI | Jetpack Compose + Material 3. The XML/Fragment template is removed. |
| DI | Hilt (KSP). |
| Persistence | Room over SQLite, WAL mode, `foreign_keys=ON`. |
| Settings storage | `app_setting` table in the same database. No DataStore. |
| Money | `Long` minor units, per-currency scale. No `Float`/`Double`. |
| Balances | Derived: `SUM(ledger_entry.amount_minor)` per account. No balance column. |
| Initial balance | `OPENING_BALANCE` operation with one ledger entry. No `account` column. |
| Data model | Operation header + signed ledger entries. |
| Exchange rates | NBP behind `RateProvider`, PLN pivot, manual overrides. Display/analytics only. |
| Import/Export | Plain unencrypted SQLite `.db`, WAL-safe snapshot, validated import. |
| Encryption | None in v1. DB creation sits behind a `SupportSQLiteOpenHelper.Factory` so SQLCipher can be added later. Adopting it also requires adapting plain SQLite export and import (section 9.1). |
| App lock | None in v1. |
| Cloud backup and device transfer | Database and all app data excluded from both. See section 8. |
| Migrations | Versioned and tested. Destructive migration is forbidden. |

## 3. Architecture

Single Gradle module (`:app`), package `com.yehor.finora`, dependencies pointing inward only.

- `domain/`: pure Kotlin, no Android or Room imports. `Currency`, `CurrencyRegistry`, `Money`, entities, repository interfaces. Unit-testable on the JVM.
- `data/`: Room entities, DAOs, `FinoraDatabase`, `Migrations.kt`, repository implementations, `SettingsRepository`.
- `di/`: Hilt modules.
- `ui/`: `theme/`, `navigation/`, `<feature>/` (Compose screen + ViewModel exposing an immutable `StateFlow` state).
- `work/`: WorkManager workers (empty in Foundation).

Rules:
- Every multi-row write (transfer, category deletion with reassignment, recurring generation, import) runs in one Room transaction.
- Entity-level invariants that Room cannot express as constraints are enforced in one write path (repositories) and verified by tests (section 6.4).

## 4. Roadmap

1. **Foundation** (this document): build setup, schema, `Money`/`Currency`, migrations, settings, theme, navigation shell, backup exclusion.
2. **Accounts and categories**: account CRUD and archiving, category tree with direct assignment, aggregation, cycle prevention, opening balance UI.
3. **Operations and tags**: income and expense entry, history, search and filters, tags with ANY/ALL.
4. **Transfers**: same- and cross-currency transfers, fee as a linked expense operation, transfer categories. Adds the fee-link column through a real migration.
5. **Currency**: NBP provider, rate cache, manual overrides, Display Currency, estimate and outdated markers.
6. **Recurring**: schedules, pause/skip, missed occurrences, idempotent generation.
7. **Dashboard and analytics**: period filters, category drill-down, account and tag analytics.
8. **Data Import / Export**: WAL-safe `.db` export, validated import, confirmation before replace.

Each sub-project below Foundation gets its own spec and plan when it starts.

## 5. Money and Currency (domain)

### 5.1 Currency

- `Currency` is a value type created only through `CurrencyRegistry`. Unknown codes are rejected.
- Registry entry fields: **ISO 4217 code**, **minor unit scale**, **display symbol**, **display name** (English).
- The committed registry is the **single source of truth** for code, scale, symbol and name. It is a static data file in the repo, never derived from `java.util.Currency` at runtime, because runtime currency data differs across Android and JDK versions. Initial content is the active ISO 4217 list.
- Scale tests use **pinned expected values** written into the test source, not values read from the platform. The pinned set covers at least: USD, EUR, GBP, PLN, UAH = 2; JPY, KRW = 0; BHD, KWD = 3; CLF = 4. Any change to a registry scale must change the pinned test in the same commit, which makes the change visible in review.
- `java.util.Currency.getDefaultFractionDigits()` is an additional, non-authoritative cross-check. It runs in a separate test that prints every code whose scale differs from the platform and does not fail the build, because the platform data may be older or newer than the registry. The pinned test is the one that gates the build.
- The Room `currency` table (`code` PK, `scale`, `symbol`, `name`) is seeded from the registry on database creation. Currencies added later arrive through migrations. A currency's scale never changes after it exists.

### 5.2 Money

- `Money(amountMinor: Long, currency: Currency)`. `Float` and `Double` are not used anywhere in the money path.
- Arithmetic uses `Math.addExact`, `subtractExact`, `negateExact`, `multiplyExact`. Overflow throws `MoneyOverflowException`. Values never wrap.
- Adding or subtracting different currencies throws. There is no implicit conversion.
- Parsing and formatting use `BigDecimal`. Input with more fractional digits than the currency scale is rejected, not rounded.
- A converted value is a different type (`Estimate`), produced only by `RateConverter`. It cannot be assigned where `Money` is required (PRD NFR-14, NFR-15). Rounding is `HALF_UP`, applied once at the end.
- SQL `SUM` overflow raises "integer overflow" in SQLite. Repositories map it to `MoneyOverflowException`.

### 5.3 Ledger currency invariant

The currency of a ledger entry always equals its account's currency, enforced by the schema:
- `account` has a unique index on `(id, currency_code)`.
- `ledger_entry(account_id, currency_code)` has a composite foreign key to `account(id, currency_code)`, default `NO ACTION`.

Consequences: a mismatched entry cannot be inserted, and an account's currency cannot be changed while any entry references it (PRD 26.7).

### 5.4 Rates (design only here, built in sub-project 5)

NBP publishes rates against PLN, so the provider declares PLN as its pivot and every quote means "1 unit of X = r PLN". `RateConverter` converts through the pivot: USD→EUR is USD→PLN→EUR, in `BigDecimal` with one final rounding. A currency with no rate yields "unavailable", never zero. Manual overrides cover gaps. The `RateProvider` interface keeps the pivot as data, so replacing NBP later changes no other code. Rates affect display and analytics only, never stored amounts.

## 6. Database schema (v1)

IDs are `Long` auto-generated primary keys. Timestamps are epoch milliseconds in UTC. Period boundaries are computed in the device time zone at query time.

### 6.1 Tables

| Table | Purpose and key columns |
|---|---|
| `currency` | `code` PK, `scale`, `symbol`, `name` |
| `account` | `id`, `name`, `currency_code` FK, `type`, `icon`, `color`, `description`, `status` (active/archived), `created_at`. Unique `(id, currency_code)`. |
| `category` | `id`, `name`, `icon`, `color`, `type` (income/expense/transfer), `parent_id` FK (nullable), `created_at` |
| `tag` | `id`, `name` (unique), `color` (nullable), `created_at` |
| `operation` | `id`, `type`, `occurred_at`, `category_id` FK (nullable), `description`, `opening_account_id` FK → `account(id)` (nullable, unique), `created_at`, `updated_at` |
| `ledger_entry` | `id`, `operation_id` FK, `account_id`, `currency_code`, `amount_minor` (signed), `occurred_at` (denormalized from operation for index coverage). Composite FK `(account_id, currency_code)` → `account`. |
| `operation_tag` | `operation_id`, `tag_id`, PK `(operation_id, tag_id)` |
| `app_setting` | `key` PK, `value`, `updated_at` |

Recurring schedules, exchange rates and the transfer-fee link are not in v1. They arrive through real migrations in their own sub-projects.

### 6.2 Operation types

| Type | Ledger entries | Sign | Category |
|---|---|---|---|
| INCOME | 1 | + | income category |
| EXPENSE | 1 | − | expense category |
| TRANSFER | 2, different accounts, written in one transaction | − and + | transfer category |
| OPENING_BALANCE | 1 | non-zero, either sign | none |

### 6.3 Opening balance rules

- Created from the optional "initial balance" field of the account form. It is an operation, never an `account` column.
- At most one per account, enforced by the unique `operation.opening_account_id` (NULL for all other types; SQLite allows multiple NULLs in a unique index).
- `opening_account_id` is set if and only if the type is OPENING_BALANCE. The operation has exactly one ledger entry, and that entry's `account_id` must equal `opening_account_id`. The operation has no category.
- **Tags cannot be assigned to an OPENING_BALANCE operation.** The tag UI is not offered for it, the repository rejects the write, and the invariant checker reports any `operation_tag` row that points at one.
- Editable and deletable. Default date is the account creation date. A negative value is allowed.
- Counts toward balance and balance over time.
- Excluded from income, expense, net cash flow, category statistics and tag statistics. Account analytics shows it as "Initial balance".

### 6.4 Invariants and how they are enforced

| Invariant | Enforced by |
|---|---|
| Entry currency = account currency | Composite FK (5.3) |
| Account currency immutable while entries exist | Same FK |
| One opening balance per account | Unique index |
| Transfer has exactly 2 entries on different accounts | Single repository write path + test |
| Income entry > 0, expense entry < 0 | Repository + test |
| OPENING_BALANCE: `opening_account_id` is non-NULL, the type has exactly one entry, `ledger_entry.account_id` = `operation.opening_account_id`, entry is non-zero, `category_id` is NULL. For every other type `opening_account_id` is NULL. | Repository (single write path) + invariant checker |
| No `operation_tag` row references an OPENING_BALANCE operation | Repository + invariant checker |
| `ledger_entry.occurred_at` = its parent `operation.occurred_at` for every entry | Repository + invariant checker (below) |
| Category type matches operation type; child has parent's type; no cycles | Repository + test |
| No orphan rows | FKs with `foreign_keys=ON` |

A test-side **invariant checker** runs SQL over the whole database for each row above that is not a constraint. It is used by Foundation tests now and is reused by import validation in sub-project 8.

**Denormalized timestamp rule.** `ledger_entry.occurred_at` is a copy of the parent operation's `occurred_at`, kept so the covering index in 6.5 can answer balance-over-time queries without a join. It must always match.
- Every write that changes `operation.occurred_at` (editing a transaction's date, editing an opening balance's date, editing either side of a transfer) updates all of that operation's entries in the same Room transaction. There is no other code path that writes either column.
- Import validation runs the invariant checker over the imported file. A file with any mismatch is rejected with a report. It is not silently repaired, because a mismatch means the file was produced or edited outside the app's write path.
- Tests: changing an operation's date moves every entry, including both sides of a transfer; the checker detects a deliberately mismatched row inserted with raw SQL.

### 6.5 Indexes

- `ledger_entry(account_id, occurred_at, amount_minor)`: covering index for balance, balance over time and per-account totals.
- `ledger_entry(operation_id)`.
- `operation(type, occurred_at)` and `operation(category_id, occurred_at)`.
- `operation_tag(tag_id, operation_id)`.
- `category(parent_id)`.
- A test runs `EXPLAIN QUERY PLAN` on the balance query and asserts it uses the covering index with no table scan.

### 6.6 Settings

`app_setting` holds every user-owned persistent setting so one `.db` export carries everything. Keys in Foundation: `theme_mode` (system/light/dark) and `display_currency`. `SettingsRepository` wraps typed access and exposes `Flow`s. DataStore is not used.

**Defaults.** A missing `theme_mode` means system. The effective Display Currency is resolved in this order, taking the first that applies:
1. The user's explicit `display_currency` setting.
2. The currency of the first account, meaning the active account with the lowest id.
3. The device locale's currency, if it exists in the registry.
4. USD.

The resolution is a pure function in `domain` (`DisplayCurrencyResolver`) so it can be unit-tested. Only an explicit user choice is written to `app_setting`; the resolved fallback is never stored, so it follows the user's accounts until they choose a currency. Settings shows the effective currency and marks it as automatic when it did not come from an explicit setting.

## 7. Migrations

- `exportSchema = true`. Schema JSON files are committed under `app/schemas`.
- `fallbackToDestructiveMigration` is never used. A test and a review rule enforce this.
- Every version bump ships a versioned migration (`@AutoMigration` only for trivial changes) and a `MigrationTestHelper` test that seeds data in the old version, migrates, validates the schema, and checks the data.
- A full-chain test migrates from v1 to the latest version.
- Foundation ships the v1 schema and the migration test infrastructure. Foundation also includes a **test-only** scratch v1→v2 migration to prove the infrastructure works. Room exports schema JSON only for the production database version, so the scratch test uses its own artifacts, all in the `androidTest` source set:
- `ScratchDatabase` (version 2), a copy of the production entities plus one extra column, processed by `kspAndroidTest` with its own schema directory `app/schemas-test`, also added as `androidTest` assets.
- `ScratchMigration_1_2`, applied only by the scratch test through `MigrationTestHelper`. Room looks up schema JSON in a folder named after the database class, so `ScratchDatabase` cannot read the production folder directly. A Gradle copy task in the `androidTest` setup copies production `1.json` into the `ScratchDatabase` folder of `app/schemas-test`, and a test asserts the copy is byte-identical to the production file, so the scratch migration always starts from the real v1 schema.

None of these is referenced from `main`, `Migrations.kt`, `app/schemas`, or the production migration chain. A test asserts that the production chain contains no migration whose target version exceeds the production database version, and the production `FinoraDatabase` stays at version 1.

## 8. Backup, device transfer and data exposure

### 8.1 Policy

Financial data leaves the device only through the app's explicit Export function (sub-project 8). It is excluded from Android cloud backup and from automatic device-to-device transfer. Users moving to a new phone use SQLite Export and Import.

### 8.2 Android configuration

`app/src/main/AndroidManifest.xml`, `<application>`:

```xml
android:allowBackup="${allowBackup}"
android:dataExtractionRules="@xml/data_extraction_rules"
```

- `android:fullBackupContent` and `res/xml/backup_rules.xml` are removed. `minSdk` is 33, and `fullBackupContent` is only read on Android 11 and lower.
- `app/build.gradle.kts`, `defaultConfig`: `manifestPlaceholders["allowBackup"] = if (project.hasProperty("backupVerification")) "true" else "false"`. Every normal build, including release, has `allowBackup="false"`. Only a build started with `-PbackupVerification=true` allows backup, so the exclusion rules can be exercised by the end-to-end test (8.4). That flag is never used for a distributed build.
- Why both layers: on API 31+ some OEMs apply device-to-device transfer even when `allowBackup="false"` (Android "Auto Backup for Apps" documentation). The rules guarantee exclusion in that case, and `allowBackup="false"` covers everything else.

`app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
        <exclude domain="device_root" path="." />
        <exclude domain="device_file" path="." />
        <exclude domain="device_database" path="." />
        <exclude domain="device_sharedpref" path="." />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" path="." />
        <exclude domain="file" path="." />
        <exclude domain="database" path="." />
        <exclude domain="sharedpref" path="." />
        <exclude domain="external" path="." />
        <exclude domain="device_root" path="." />
        <exclude domain="device_file" path="." />
        <exclude domain="device_database" path="." />
        <exclude domain="device_sharedpref" path="." />
    </device-transfer>
</data-extraction-rules>
```

Both sections are present and contain only `<exclude>` elements. Excluding every domain, not only `database`, covers the `-wal`, `-shm` and journal files and any future file the app writes. The `database` domain also covers the WAL companions. Export temp files are written to `cacheDir`, which Android always excludes.

### 8.3 Local data

The database lives in app-private storage (`Room.databaseBuilder` default location), in WAL mode, with `foreign_keys=ON` set in the open callback. The database is created through a `SupportSQLiteOpenHelper.Factory`, so SQLCipher with an Android Keystore-protected key can be adopted later without schema or DAO changes.

**SQLCipher is not a drop-in change for export and import.** Swapping the factory keeps the schema and DAOs intact, but plain-SQLite export and import (section 9) must be adapted in the same change. See 9.1.

### 8.4 Tests for the exclusions

1. **JVM unit test `BackupConfigurationTest`** parses the source XML files. It asserts that `data_extraction_rules.xml` has both `cloud-backup` and `device-transfer`, that neither contains `<include>`, and that each excludes all nine domains with `path="."`. It asserts that the manifest sets `android:dataExtractionRules="@xml/data_extraction_rules"`, has no `android:fullBackupContent`, and that `allowBackup` is the `${allowBackup}` placeholder. It also asserts the Gradle default for the placeholder is `"false"`.
2. **Instrumented test `AllowBackupFlagTest`** asserts `ApplicationInfo.FLAG_ALLOW_BACKUP` is not set, and is skipped when the instrumentation argument `backupVerification=true` is passed.
3. **End-to-end script `scripts/verify-backup-exclusion.ps1`**, run against an emulator with Google APIs on Android 12 or higher, using a **debug** build (needed for `run-as`) installed with `-PbackupVerification=true`:
   - Open the app and change the theme to Dark, so a row exists in `app_setting`, and confirm the database file exists via `adb shell run-as com.yehor.finora ls databases`.
   - **Cloud backup:** select the local transport (`adb shell bmgr transport com.android.localtransport/.LocalTransport`), run `adb shell bmgr backupnow com.yehor.finora`, uninstall, reinstall the same APKs, launch.
   - **Device transfer:** enable `backup_enable_d2d_test_mode`, select the `D2dTransport` (commands from Android's "Test Cloud Backups and Device-to-Device Transfer" guide), run `bmgr backupnow`, uninstall, reinstall, launch.
   - **Pass criteria in both runs:** after reinstall, the theme is back to System, `databases/` contains only a freshly created database (no restored data), and logcat shows no restore of `databases` or `files` for the package.
   - **Positive control (run once when the script is written):** repeat with the exclusion rules temporarily removed and confirm the theme survives reinstall. This proves the script can detect a leak.
4. **Release check:** `processReleaseManifest` output is grepped for `allowBackup="false"`.

## 9. Export design constraints (built in sub-project 8)

- The database runs in WAL mode, so copying the `.db` file alone would miss recent writes in the `-wal` file. With the v1 plain database, export uses `VACUUM INTO '<cache temp file>'`, producing one consistent, WAL-free, unencrypted SQLite file while the app keeps running. The file is then streamed to the user's chosen location. `user_version` and Room's identity table are preserved.
- An export test (sub-project 8) must verify the produced file is plain SQLite: its first 16 bytes are `SQLite format 3\0`, it opens with the stock Android SQLite API without a key, and it contains the expected rows. This test is what guards the requirement through any later storage change.
- Import copies the chosen file to a temp location and validates it: `PRAGMA integrity_check`, `PRAGMA foreign_key_check`, Room identity and `user_version` compatibility, and the invariant checker. An older file is migrated through the normal migration chain. Import never replaces existing data without explicit confirmation. On confirmation it closes Room, swaps the file, deletes stale `-wal` and `-shm` files, and reopens.
- Because settings live in `app_setting`, an exported file restores the whole app state.
- Import validation also runs the invariant checker from 6.4, including the opening-balance, tag-on-opening-balance and `occurred_at` rules. A file that violates any of them is rejected with a report.

### 9.1 Adopting SQLCipher later

Switching to SQLCipher changes how export and import must work. `VACUUM INTO` on its own must not be assumed to produce an unencrypted file from an encrypted database. The destination of `VACUUM INTO` is encrypted with the source key by default. A future SQLCipher change must therefore:
- Replace the export step with an explicit plain copy, for example attaching a new database with an empty key and using SQLCipher's `sqlcipher_export`, while still producing a consistent snapshot that includes WAL content.
- Replace the import step so a plain SQLite file is validated as plain, then loaded into the encrypted database. The file swap in the current import design no longer applies, because the incoming file is not in the working database's format.
- Keep the export test above as a gate: if the file does not start with `SQLite format 3\0`, the change fails.
- Move the open-time key handling (Android Keystore) into the `SupportSQLiteOpenHelper.Factory` and keep `foreign_keys=ON` and WAL setup in the open callback.

This work belongs to the change that adopts SQLCipher, and that change needs its own spec.

## 10. Build setup

- Dependencies are added to `gradle/libs.versions.toml`: Compose BOM, Material 3, activity-compose, lifecycle-viewmodel-compose, Room (runtime, ktx, compiler, testing), Hilt (+ navigation integration if used), KSP, WorkManager, coroutines-test, Turbine. Exact versions are looked up at plan time.
- The project uses AGP 9.5.0, which includes built-in Kotlin. Compatibility of the KSP and Hilt plugins with AGP 9.5 is verified first at plan time; this is the main build risk.
- The Compose compiler plugin is enabled and `viewBinding` is removed.
- Removed: `MainActivity` XML usage, `FirstFragment`, `SecondFragment`, all layouts, `nav_graph.xml`, `menu_main.xml`, the Material XML themes, and the `appcompat`, `constraintlayout`, `navigation-fragment`, `navigation-ui` dependencies.
- Room KSP argument `room.schemaLocation=$projectDir/schemas`. The `androidTest` source set includes `schemas` as assets for `MigrationTestHelper`.
- Navigation library (Navigation 3 or Navigation Compose with type-safe routes) is chosen at plan time.

## 11. App shell

- Single `MainActivity` with `enableEdgeToEdge()`, Compose content, Material 3 theme with light and dark. Theme follows `theme_mode` from `app_setting`.
- Bottom bar: Dashboard, Transactions, Accounts, Statistics, each an empty placeholder screen. A "More" screen links to Categories, Tags, Recurring, Transfers and Settings. Only Settings is functional in Foundation (theme mode and display currency).
- Foundation ships tables and DAOs for all entities but no CRUD screens or use cases. Those belong to sub-projects 2 and 3.

## 12. Testing summary

JVM unit tests:
- `Currency`/`CurrencyRegistry`: pinned scale values (5.1), symbol and name present for every entry, unknown code rejected. Separate non-gating cross-check against `java.util.Currency` that only reports differences.
- `DisplayCurrencyResolver`: explicit setting wins; otherwise the first active account's currency; otherwise the locale currency when it is in the registry; otherwise USD. Also covers a locale currency missing from the registry, and an archived-only account list.
- `Money`: parsing and formatting per scale, overflow on add, subtract, negate and multiply, cross-currency rejection.
- Static check that the money package contains no `Float` or `Double`.
- `BackupConfigurationTest` (8.4).

Instrumented Room tests:
- Currency-mismatch insert fails.
- Currency change fails while entries exist.
- Balance sums are correct, including negative balances.
- Second opening balance for one account fails.
- Opening balance with an entry on a different account than `opening_account_id` is rejected by the repository and flagged by the invariant checker.
- Assigning a tag to an OPENING_BALANCE operation is rejected by the repository and flagged by the invariant checker.
- Changing an operation's date updates every ledger entry's `occurred_at`; the checker flags a raw-SQL mismatch.
- Foreign keys are enforced.
- `EXPLAIN QUERY PLAN` uses the covering index.
- Settings round trip through `app_setting`.
- Migration infrastructure test (test-only v1→v2) and the check that the production chain has no scratch migration.
- `AllowBackupFlagTest`.

Manual and scripted: `verify-backup-exclusion.ps1` (8.4).

## 13. Foundation is done when

1. The app builds and launches with the Compose shell, bottom bar and working light/dark theme.
2. All tests in section 12 pass.
3. The v1 schema JSON is committed and the migration test infrastructure works.
4. The backup exclusion configuration from 8.2 is in place, and `verify-backup-exclusion.ps1` passes for both cloud backup and device transfer, including the positive control.
5. The build is clean, with no new lint warnings.

## 14. Risks

- KSP/Hilt/Room compatibility with AGP 9.5 built-in Kotlin (section 10).
- The end-to-end backup test needs a Google APIs emulator or device with the GMS transports; if the D2D transport is unavailable on the available image, the D2D leg is marked as not run rather than passed.
- Room cannot declare CHECK constraints or partial indexes in entity annotations, so some invariants rely on the single write path and tests (6.4).
