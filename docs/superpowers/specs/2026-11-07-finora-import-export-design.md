# Finora: Data Import / Export Design

Date: 2026-11-07
Status: Draft for user review
Sub-project: 8 of 8 (see roadmap in `2026-11-03-finora-foundation-design.md`, section 4)
Source of requirements: `docs/requirements.md` (PRD sections 4.2, 5.3, 8.7, 16.1 to 16.3, 17.3, 33 DATA MANAGEMENT, NFR-01 to NFR-04, NFR-06, NFR-10, NFR-11, NFR-21, NFR-22). References like "Foundation 9" refer to `2026-11-03-finora-foundation-design.md`, "Accounts" to `2026-11-04-finora-accounts-categories-design.md`, "Operations" to `2026-11-04-finora-operations-tags-design.md`, "Transfers" to `2026-11-05-finora-transfers-design.md`, "Currency" to `2026-11-06-finora-currency-design.md`, "Recurring" to `2026-11-06-finora-recurring-design.md` and "Analytics" to `2026-11-07-finora-dashboard-analytics-design.md`.

## 1. Scope

In scope:
1. **Create backup** (PRD 16.1): a WAL-safe snapshot of the whole database as one plain SQLite `.db` file, written to a location the user picks (Foundation 9).
2. **Restore backup** (PRD 16.2): validate a chosen file, show what it contains next to the current data, ask for explicit confirmation, then **replace** all current data with it.
3. **Validation** of every incoming file: file type, format version, structural integrity, foreign keys, the invariant checker, currency table, and safety checks for files that were not produced by Finora.
4. **Upgrade on restore**: a file from an older schema version is migrated through the normal migration chain (NFR-22).
5. **Crash-safe replacement**: the swap is driven by a marker file and applied at the next process start, before Room opens (section 6.5, a deviation from Foundation 9).
6. **CSV export** of transaction history with filters for accounts, types, dates, categories and tags (PRD 16.3, 33 DATA MANAGEMENT), reusing `TransactionFilter`.
7. **Data screen** under Settings with data information (PRD 17.3), backup, restore and CSV export.
8. **Schema**: no change. Version stays 3 (section 9).

Not changed by this sub-project:
- Every table, DAO and write rule. Import is the only code that ever replaces the database as a whole.
- `Money`, `Estimate`, rates, recurring generation, analytics.
- The backup exclusion configuration from Foundation 8. All files this sub-project creates sit in already excluded domains (section 11).

Out of scope, now and in v1:
- **Merging** a backup into existing data. Restore always replaces (section 2).
- **CSV or JSON import** (Foundation 1 defers it, and the PRD asks for none). The Foundation line "CSV/JSON import and export" is narrowed here: **CSV export is built**, because PRD 16.3 and 33 require it and the Analytics hand-off assigns it to this sub-project. CSV and JSON import stay out.
- **Encrypted backups, passphrases, SQLCipher** (Foundation 8.3 and 9.1). The backup is plain SQLite, and the UI says so (section 11).
- Automatic or scheduled backups, reminders, cloud upload, share sheets, "last backup" tracking.
- Undo after a restore (section 15, item 3).
- "Delete all data" (section 15, item 5).
- Exporting charts, analytics reports, or opening balances in CSV (section 7.6).
- Any new permission, library, schema table or network call.

## 2. Decisions

| Topic | Decision |
|---|---|
| Backup format | The database itself: a plain, unencrypted SQLite file made with `VACUUM INTO`. No wrapper, archive or manifest. Version information is `PRAGMA user_version` and Room's `room_master_table` identity, which the file already carries (PRD NFR-22). |
| Why not a custom envelope | The Foundation requires a plain SQLite file, one file keeps relationships intact (PRD 16.1), and a separate manifest could disagree with the data. |
| Snapshot | `VACUUM INTO '<cache temp file>'` on the live connection. It reads one consistent snapshot while the app keeps running and includes WAL content (Foundation 9). |
| Destination | Storage Access Framework only: `CreateDocument` for export, `OpenDocument` for import. No storage permission, no `FileProvider`, no share intent, no new exported component. |
| File names | `Finora-backup-yyyy-MM-dd-HHmmss.db` (device zone) and `Finora-transactions-yyyy-MM-dd.csv`. |
| Restore semantics | **Replace only.** The confirmation says "This replaces all data in Finora". There is no merge (PRD 16.2 asks the app to say which it is). |
| Replace mechanism | Validate and stage the file in the live process, write a marker, **restart the process**, and apply the swap at the next start before Hilt or Room exist. Singletons holding DAOs are never reconfigured in place (section 6.5). |
| Where validation runs | On a **staging copy** in the app's `databases/` directory, never on the live file. The live database is untouched until the atomic swap. |
| Upgrade path | The staging copy is opened by a second `FinoraDatabase` instance built with the same migrations, callbacks and journal mode as the live one (shared builder, section 3). Room's own schema validation is part of the check. |
| Reject, never repair | A file that fails any check is rejected with a report. It is not edited or fixed (Foundation 6.4). The only write to the staging copy is the migration itself and the currency reference-data sync (6.3, step 9). |
| Failure leaves no trace | Any failure before the marker is written deletes the staging copy. The live database is byte-identical to before. |
| Size limit | Incoming files over 512 MiB are refused. Free space must cover `2 × file size + 16 MiB` (section 6.2). |
| CSV rows | One row per operation. A transfer is one row with both sides. Amounts are plain decimals in the account's own currency. No conversion, no rates (Currency R1). |
| CSV encoding | UTF-8 with BOM, CRLF line endings, RFC 4180 quoting, English header row, fixed column order (section 7.2). |
| CSV filter | `TransactionFilter`, exactly the semantics of the Transactions list, so an export contains the rows the user sees (Analytics 2, "one definition of what a filter matches"). |
| Restart | Non-exported `RestartActivity` in a separate process, with a documented fallback that does not affect correctness (section 6.6). |
| Schema | **None.** Version stays 3. `1.json`, `2.json`, `3.json` and their pinned copies are untouched. |

## 3. Changes to existing code

| Change | Reason |
|---|---|
| New `FinoraDatabaseFactory` (`data/db`): `fun build(context, name): FinoraDatabase` holding the builder chain now inside `DatabaseModule.provideDatabase` (open helper factory, WAL, both callbacks, `ALL_MIGRATIONS`). `DatabaseModule` calls it with `FinoraDatabase.FILE_NAME`. No behaviour change. | The staging validator must open a file with exactly the live configuration. One builder prevents drift. |
| `FinoraApp`: override `attachBaseContext` to call `PendingImportApplier.run(...)` **before** the Hilt component is created, and guard `rates.start`, `recurring.start` so they run only in the main process. | Section 6.5 and 6.6. |
| `AndroidManifest.xml`: add `<activity android:name=".data.dataio.RestartActivity" android:exported="false" android:process=":restart" .../>`. No permission is added. | Section 6.6. |
| `InvariantChecker`: add the enum-domain and storage-class checks of section 10. Existing checks are unchanged. | Import must not admit values the app cannot read (section 6.3, step 11). |
| `TransactionRepository`: add `suspend fun count(filter): Int` built on `TransactionQuery.buildWhereClauses` (already `internal`, Analytics 3). | CSV preview count, including transfers (`summarize` excludes them). |
| `TransactionsScreen`: overflow action "Export as CSV" that opens the CSV screen with the current filter encoded by `FilterCodec`. | PRD 16.3 entry point. |
| `SettingsScreen`: a "Data" row below Display Currency that opens `Routes.DATA`. `SettingsContent` gets an `onOpenData` callback. | PRD 17.3. |
| `Routes`, `FinoraNavHost`: `DATA = "more/settings/data"` and `DATA_CSV = "more/settings/data/csv?filter={filter}"`. Both sit under `more`, so the More tab stays highlighted through `isRouteUnder`. | Section 8. |
| `TransactionFilterSheet`: used with `sections = {TYPE, ACCOUNT, CATEGORY, TAG, DATE}`. No code change. | Already parameterized (Analytics 3). |
| `strings.xml`: strings and plurals for all new text. | Existing convention. |
| `FinoraDatabase`, all `@Entity` classes, all DAOs, all repositories except `count`, `SettingsRepository`, `data_extraction_rules.xml`, `BackupConfigurationTest`: **unchanged**. | Sections 1 and 9. |

## 4. Domain (pure Kotlin, JVM-testable)

Package `domain/dataio`. No Android, Room or `System` clock imports. No `Float` or `Double`.

```kotlin
enum class ImportFailure {
    UNREADABLE, TOO_LARGE, NOT_ENOUGH_SPACE,          // reading the chosen file
    NOT_SQLITE, NOT_FINORA, NEWER_VERSION, UNKNOWN_SCHEMA,
    UNSAFE_OBJECTS, CORRUPT, FOREIGN_KEY_VIOLATIONS,
    MIGRATION_FAILED, UNEXPECTED_OBJECTS,
    CURRENCY_MISMATCH, BALANCE_OVERFLOW, INVARIANT_VIOLATIONS,
}

data class ImportProblem(val check: String, val subject: String)   // e.g. "recurring bad interval", "schedule 4"

data class DataSummary(
    val accounts: Int, val activeAccounts: Int,
    val categories: Int, val tags: Int,
    val transactions: Int,            // INCOME + EXPENSE
    val transfers: Int,
    val schedules: Int,
    val firstOperationAt: Long?, val lastOperationAt: Long?,   // epoch ms, opening balances excluded
    val databaseBytes: Long,
    val schemaVersion: Int,
)

sealed interface StagingResult {
    data class Ready(val summary: DataSummary, val upgradedFrom: Int?, val sizeBytes: Long, val sha256: String) : StagingResult
    data class Rejected(val failure: ImportFailure, val problems: List<ImportProblem>, val totalProblems: Int) : StagingResult
}

@Serializable
data class PendingImportMarker(val format: Int = 1, val stagingFile: String, val sizeBytes: Long, val sha256: String, val createdAt: Long)

@Serializable
data class ImportOutcome(val format: Int = 1, val success: Boolean, val reason: String?, val summary: DataSummary?)

object KnownSchemas {            // schema version -> Room identity hash
    val identityHashes: Map<Int, String>
}
```

`KnownSchemas.identityHashes` is a committed constant for versions 1 to 3. A JVM test compares each entry with the `identityHash` in `app/schemas/.../<version>.json`, so it cannot drift and every schema bump must extend it (section 12).

### 4.1 `CsvFormat`

Pure functions, parameterized by `ZoneId` so they are testable:

```kotlin
object CsvFormat {
    val HEADER: List<String>                                  // section 7.2, the column contract
    fun writeRow(out: Appendable, fields: List<String>)       // RFC 4180 quoting, CRLF
    fun textField(raw: String?): String                       // spreadsheet-formula guard
    fun tagList(names: List<String>): String                  // ';' separated, '\' and ';' escaped
    fun amount(money: Money): String                          // Money.toPlainString()
    fun dateParts(epochMs: Long, zone: ZoneId): Triple<String, String, String>   // date, time, UTC instant
}
```

### 4.2 `BackupFileNames`

`backup(now, zone)` and `csv(now, zone)` return the file names of section 2. No characters outside `[A-Za-z0-9._-]`.

## 5. Create backup

### 5.1 Steps

`BackupExporter.export(destination: Uri)`, on `Dispatchers.IO`, under the data-operation lock (section 8.3):

1. Check free space in `cacheDir` is at least the live database size plus 10 percent. Otherwise fail with `NOT_ENOUGH_SPACE`.
2. Delete everything in `cacheDir/export/`, then run `VACUUM INTO ?` on `db.openHelper.writableDatabase`, with the path `cacheDir/export/<name>.db` as a bound argument. The target must not exist.
3. **Verify the snapshot before touching the destination:**
   - The first 16 bytes are `SQLite format 3\0`.
   - It opens with the stock framework `SQLiteDatabase` and no key.
   - `PRAGMA user_version` equals `FinoraDatabase.VERSION`.
   - `room_master_table` has one row whose identity equals the live identity.
   - `PRAGMA quick_check` returns `ok`.
   A failed check deletes the temp file and fails with `SNAPSHOT_INVALID`. Nothing is written to the destination.
4. Stream the temp file to `contentResolver.openOutputStream(uri, "wt")` in 64 KiB chunks. After the last chunk, flush and close. Compare the bytes written with the temp file size.
5. Delete the temp file in a `finally` block.
6. On any failure after the destination was created, delete the destination best-effort (`DocumentsContract.deleteDocument`) so no half-written backup remains.

The app stays fully usable during export. `VACUUM INTO` holds a read snapshot and does not block writers in WAL mode.

### 5.2 What the file contains

Everything in the database (PRD 16.1): accounts with icons and colors, opening-balance operations (the initial balances), transactions, transfers, categories and hierarchy, tags, recurring schedules and occurrences, `app_setting` (theme, Display Currency) and the `exchange_rate` cache. Relationships are preserved because the file is the database.

### 5.3 UI wording

The success state shows the file name and size, and always this notice: "This file is not encrypted. Anyone who has it can read all your financial data. Keep it somewhere private." (PRD NFR-04, Foundation 8.3).

## 6. Restore backup

### 6.1 Flow

```
Pick file -> Stage + validate (background, cancellable) -> Preview + confirm -> Marker -> Restart -> Apply -> Result
```

Nothing before "Marker" changes the live database. Cancel, process death or any failure up to that point leaves it untouched; the staging copy is removed by `PendingImportApplier` at the next start (6.5).

### 6.2 Staging

`BackupImporter.stage(source: Uri)` under the data-operation lock:

1. Read the size from `OpenableColumns.SIZE` when available. A size over 512 MiB gives `TOO_LARGE`. Free space on the `databases/` partition must be at least `2 × size + 16 MiB`, otherwise `NOT_ENOUGH_SPACE`. The unknown-size case is enforced while copying.
2. Copy the stream to `databases/finora-import-staging.db` (64 KiB chunks, hard stop at the size limit). Any I/O error gives `UNREADABLE`.
3. The staging file lives in `databases/` rather than `cacheDir`, because the system may evict cache files during a long operation, and a same-directory rename is atomic (6.5).

### 6.3 Validation pipeline

Runs on the staging copy, in this order. The first failing step stops the pipeline.

| # | Check | Failure |
|---|---|---|
| 1 | First 16 bytes are `SQLite format 3\0`. Covers encrypted files, ZIPs, CSVs, random data. | `NOT_SQLITE` |
| 2 | Open with the framework `SQLiteDatabase` (read-write on the copy). Set `PRAGMA trusted_schema = OFF` where supported. `room_master_table` exists with exactly one row and `user_version >= 1`. Tables `account`, `operation`, `ledger_entry` exist. | `NOT_FINORA` |
| 3 | `user_version <= FinoraDatabase.VERSION`. | `NEWER_VERSION` |
| 4 | The file's identity hash equals `KnownSchemas.identityHashes[user_version]`. | `UNKNOWN_SCHEMA` |
| 5 | `sqlite_master` has no `trigger`, no `view`, and no `CREATE VIRTUAL TABLE`. | `UNSAFE_OBJECTS` |
| 6 | `PRAGMA integrity_check` returns exactly `ok`. | `CORRUPT` |
| 7 | `PRAGMA foreign_key_check` returns no rows. | `FOREIGN_KEY_VIOLATIONS` |
| 8 | Close the framework handle. Open the copy with `FinoraDatabaseFactory.build` (same migrations, WAL, `foreign_keys=ON`). If `user_version < VERSION`, Room runs the migration chain. Any exception, including Room's identity or schema validation failure, becomes `MIGRATION_FAILED`. After this step the file is at the current version. | `MIGRATION_FAILED` |
| 9 | **Currency table.** Every row's `scale` must equal the registry scale for that code, and the code must be in `CurrencyRegistry` (a scale never changes, Foundation 5.1). Registry currencies missing from the file's `currency` table are inserted with `INSERT OR IGNORE`. This is reference data, not user data, and is the one write besides the migration. | `CURRENCY_MISMATCH` |
| 10 | **Object set.** The set of `(type, name)` in `sqlite_master` equals the set in the live database. Room does not flag extra tables, so this does. Re-checks steps 5's object kinds after migration. | `UNEXPECTED_OBJECTS` |
| 11 | `InvariantChecker.check` over the migrated copy, covering every Foundation 6.4 rule plus the Accounts, Operations, Transfers, Currency, Recurring and section 10 additions. | `INVARIANT_VIOLATIONS` |
| 12 | **Balance overflow.** `SELECT account_id, SUM(amount_minor) FROM ledger_entry GROUP BY account_id` must not raise "integer overflow" (Foundation 5.2). | `BALANCE_OVERFLOW` |
| 13 | **Tag names.** Two tags whose `TagNameRules.key(name)` are equal are rejected (Operations: the case rule lives in the repository, so the SQL checker cannot see it). | `INVARIANT_VIOLATIONS` |
| 14 | `PRAGMA wal_checkpoint(TRUNCATE)`, close the Room instance, compute `DataSummary` (a single read, also used for the live database) and the SHA-256 of the closed file. | |

Steps 5 and 10 apply to a file that may be hostile. Opening it executes only SQLite's parser and Room's migration SQL. Triggers, views and virtual tables are refused before any migration runs, and the final object set must equal the live one.

### 6.4 Rejection report

`Rejected` carries up to 50 `ImportProblem`s and the total count. The UI shows a headline per `ImportFailure`, then the lines "`<check>`: `<subject>`", then "and N more". Subjects of the form `account N`, `category N`, `tag N` and `schedule N` are enriched with the name read from the file, for example `account 7 "Santander"`, so the user can understand the rejection (Accounts requires this for the opening-balance rule). The report text is selectable. No data from the file is written anywhere else.

### 6.5 Atomic replace

#### Commit

After the preview is confirmed (section 8.2), `BackupImporter.commit(ready)`:
1. Re-checks that `databases/finora-import-staging.db` still has `ready.sizeBytes`. Otherwise it fails with `STAGING_LOST` and restarts staging.
2. Writes `databases/finora-pending-import.json` (a `PendingImportMarker`) to a `.tmp` name, flushes, and renames it into place.
3. Calls `ProcessRestarter.restart()` (6.6).

From the moment the marker exists, the import is committed and will be completed at the next start.

#### Apply at start

`PendingImportApplier.run(databasesDir: File)` is plain `java.io` logic with no Android types except the directory, so it is JVM-testable. `FinoraApp.attachBaseContext` calls it in the main process only, before `Hilt_FinoraApp.onCreate`. At that point Room does not exist in the process, so the files can be replaced without closing anything.

| Marker | Staging file | Live `finora.db` | Meaning | Action |
|---|---|---|---|---|
| absent | present | any | An abandoned validation | Delete staging file and its `-wal`, `-shm`. |
| absent | absent | any | Normal start | Nothing. Two `File.exists` calls. |
| present | present, SHA-256 and size match | present or absent | Swap not started | Step A. |
| present | present, mismatch | any | Staging file damaged | Abort: delete staging and marker, write outcome `STAGING_INVALID`. Live database untouched. |
| present | absent | SHA-256 equals marker | Swap done, cleanup pending | Step B. |
| present | absent | SHA-256 differs | Staging lost before the swap | Abort: delete marker, write outcome `STAGING_LOST`. Live database untouched. |

- **Step A:** `Files.move(staging, live, REPLACE_EXISTING, ATOMIC_MOVE)`. Same directory, same filesystem, so `rename` replaces the live file in one atomic operation. A failure here aborts with `SWAP_FAILED` and leaves the old file intact. Then Step B.
- **Step B:** delete `finora.db-wal` and `finora.db-shm` (they belong to the replaced file), delete the marker, write `finora-import-outcome.json` (`ImportOutcome`), and delete any `.tmp` file.

Every step is idempotent. A crash at any point resumes from the table on the next start. The old WAL is deleted only after the new main file is in place, so a crash can never pair the new file with a stale log, and Room never opens in between because the applier finishes before it can.

#### Outcome

`ImportOutcomeStore.consume()` reads and deletes `finora-import-outcome.json`. The shell shows it once: "Backup restored: 5 accounts, 2,310 transactions." or "The backup could not be restored. Your data was not changed."

### 6.6 Restart

`ProcessRestarter.restart()` starts `RestartActivity` in the `:restart` process with the main PID as an extra. That activity starts the launcher intent (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`) and kills the main process. The same technique is used by process-restart helpers, and it avoids the race of killing a process right after `startActivity`.

Consequences, all handled in this design:
- `FinoraApp` is also instantiated in `:restart`. It skips the applier and the `start` calls there (section 3).
- Correctness does not depend on the restart working. If the restart mechanism fails on some device, the fallback is `finishAffinity()` plus `Process.killProcess`, with the message "Finora closed to finish restoring. Open it again." The next start applies the marker exactly as above.

### 6.7 Failure and edge cases

| Situation | Behaviour |
|---|---|
| The user cancels during staging or validation | The coroutine is cancelled, the Room instance is closed, the staging file is deleted. |
| The process dies during staging | The next start deletes the leftover staging file (table row 1). |
| The process dies after the marker is written | The next start completes the import. |
| The process dies during the swap | The table above resumes or aborts without data loss. |
| Storage is full while copying | `NOT_ENOUGH_SPACE` or `UNREADABLE`. Live data untouched. |
| The chosen file is a valid backup of an **empty** database | Accepted. The preview shows zero counts, and the confirmation still warns that all current data is replaced. |
| The same backup is restored twice | Allowed. Each restore replaces the data again. |

## 7. CSV export

### 7.1 Behaviour

`CsvExporter.export(filter, destination: Uri)` under the data-operation lock:
1. Load accounts and categories once (small) for names and category paths through `CategoryTree`.
2. Open one read transaction so that all batches see the same snapshot and writers are not blocked (a DEFERRED transaction on a read connection; section 14, item 4 on the API check).
3. Select matching operations with `TransactionQuery.buildWhereClauses` ordered by `occurred_at ASC, id ASC`, in keyset batches of 500: `(occurred_at, id) > (?, ?)`. For each batch load its entries and tags with `IN (...)` queries.
4. Write each row straight to `openOutputStream(uri, "wt")` through a `BufferedWriter` (no temp file, constant memory).
5. A cancellation or failure deletes the destination best-effort.

`OPENING_BALANCE` is never exported, matching the Transactions list (section 7.6).

### 7.2 Columns (a stable contract)

Fixed order. New columns may only be appended in a later version.

| # | Column | Content |
|---|---|---|
| 1 | `id` | Operation id |
| 2 | `type` | `INCOME`, `EXPENSE` or `TRANSFER` |
| 3 | `date` | Local date, `yyyy-MM-dd`, device zone at export time |
| 4 | `time` | Local time, `HH:mm:ss` |
| 5 | `timestamp_utc` | ISO-8601 instant, for example `2026-11-07T12:49:28Z` |
| 6 | `account` | Account name. For a transfer, the **source** account |
| 7 | `amount` | Signed decimal in the account's currency, scale of that currency, `.` separator, no grouping, no symbol (`-12.50`, `1000` for JPY, `0.005` for scale 3). For a transfer, the amount sent (negative) |
| 8 | `currency` | ISO code of that account |
| 9 | `to_account` | Transfer destination account, empty otherwise |
| 10 | `to_amount` | Amount received (positive), empty otherwise |
| 11 | `to_currency` | Destination currency, empty otherwise |
| 12 | `category` | Category name, empty when uncategorized |
| 13 | `category_path` | Full path with ` > ` between levels |
| 14 | `tags` | Tag names joined by `;`, with `\` and `;` inside a name escaped by a backslash |
| 15 | `description` | Text |
| 16 | `created_at_utc` | ISO-8601 instant (PRD 5.3) |
| 17 | `updated_at_utc` | ISO-8601 instant (PRD 5.3) |

### 7.3 Rules

- Amounts are exactly the stored minor units rendered with `Money.toPlainString()`. Nothing is rounded, converted or locale-formatted (PRD 16.3, NFR-13, Currency R1). A cross-currency transfer keeps both amounts, and the effective rate is not written because it is derivable (Transfers R5).
- Both sides of a transfer are one row, so a filter on either account includes it and both sides are always exported together (PRD 8.7, 16.3).
- Quoting follows RFC 4180: a field containing `,`, `"`, CR or LF is wrapped in quotes and inner quotes are doubled. Lines end with CRLF. The file starts with a UTF-8 BOM so spreadsheets detect the encoding.
- **Formula guard.** In the text columns (`account`, `to_account`, `category`, `category_path`, `tags`, `description`), a value starting with `=`, `+`, `-`, `@`, tab or CR gets a leading `'`. Numeric and date columns are never altered, so negative amounts stay numbers. There is no CSV import, so nothing needs to read the prefix back.
- Rows are ordered by time ascending, then id.

### 7.4 Filters

`TransactionFilter` with the sections TYPE, ACCOUNT, CATEGORY, TAG and DATE (PRD 16.3 lists accounts, types, dates and categories; tags come free from the same model). `AMOUNT` and `TEXT` are not offered. `OPENING_BALANCE` is excluded by the filter model itself. Category filtering includes descendants, as in the Transactions list.

### 7.5 Preview

The CSV screen shows the active filter as chips, "N transactions will be exported" from `TransactionRepository.count(filter)`, and a disabled Export button when N is 0. After export it shows "Exported N transactions" and the file name.

### 7.6 Limits

Opening balances are not exported, so a spreadsheet sum of `amount` does not equal an account balance. This is stated on the CSV screen: "Initial balances are not included." Including them is a section 15 item.

## 8. UI

### 8.1 Data screen (`DataScreen`, route `more/settings/data`)

Immutable `StateFlow` state in `DataViewModel`, rotation-safe like the other screens.

- **Your data** card from `DataSummary`: accounts, transactions, transfers, categories, tags, schedules, first and last transaction date, database size, schema version, app version (PRD 17.3 "application data information").
- **Create backup**: button, then the SAF create dialog with the default file name. States: idle, exporting (indeterminate progress), done (name, size, encryption notice), failed (reason).
- **Restore from backup**: button, then the SAF open dialog (`application/vnd.sqlite3`, `application/x-sqlite3`, `application/octet-stream`). States below.
- **Export transactions (CSV)**: opens the CSV screen.
- All three actions are disabled while any one runs (8.3).

### 8.2 Restore states

| State | UI |
|---|---|
| `Validating(step)` | Progress with the step label (Copying, Checking structure, Upgrading, Checking data) and a Cancel button. |
| `Rejected(report)` | Headline, report (6.4), buttons "Choose another file" and "Close". |
| `Preview(ready)` | A dialog comparing the current data and the file side by side (below), the sentence "This replaces all data in Finora with the contents of this file. It cannot be undone.", a note when the file will be upgraded ("The file is from an older version of Finora and will be upgraded."), and the buttons **Replace my data** (destructive style), **Cancel** and **Back up current data first**. |
| `Restarting` | A blocking full-screen progress with "Restoring...". No input is accepted. |

Preview table:

| | Current | Backup file |
|---|---|---|
| Accounts | 3 | 5 |
| Transactions | 1,204 | 2,310 |
| Transfers | 12 | 40 |
| Categories | 38 | 41 |
| Tags | 7 | 12 |
| Recurring schedules | 2 | 4 |
| Period | 3 Jan 2025 to 5 Nov 2026 | 1 Mar 2024 to 2 Nov 2026 |
| Format version | 3 | 2 (will be upgraded) |

The "current" column is read again when the dialog opens. **Back up current data first** runs the Create backup flow and returns to the same dialog with "Backed up" shown.

### 8.3 One operation at a time

`DataOperationLock` (a `Mutex` in `BackupRepository`) serializes backup, staging, commit and CSV export. A second request while one runs is refused and the buttons stay disabled. The rest of the app is not blocked.

### 8.4 Navigation

| Route | Screen |
|---|---|
| `more/settings/data` | Data |
| `more/settings/data/csv?filter={filter}` | CSV export (filter decoded by `FilterCodec`, empty means no filter) |

### 8.5 Accessibility

Progress indicators and result messages have content descriptions and are announced. The destructive button has a distinct color and a text label, not color alone.

## 9. Schema and migration

None. `FinoraDatabase.VERSION` stays 3, `ALL_MIGRATIONS` stays empty, `1.json`, `2.json`, `3.json` and the pinned copies are untouched, and `fallbackToDestructiveMigration` stays forbidden.

**Backup compatibility policy (PRD NFR-22).** A backup made by version N of the schema can be restored by any later version through the migration chain. It cannot be restored by an older app (`NEWER_VERSION`). Every future schema change must:
1. add its identity hash to `KnownSchemas`,
2. add a restore test for the previous version's backup (section 12).

## 10. Invariant checker additions

Added to `InvariantChecker.checks`; SQL only. SQLite is dynamically typed, so a hand-edited file can hold a value Room cannot read, or a floating-point amount.

| Check | Assertion |
|---|---|
| `account status domain` | `status NOT IN ('ACTIVE','ARCHIVED')` |
| `category type domain` | `type NOT IN ('INCOME','EXPENSE','TRANSFER')` |
| `operation type domain` | `type NOT IN ('INCOME','EXPENSE','TRANSFER','OPENING_BALANCE')` |
| `recurring kind domain` | `kind NOT IN ('INCOME','EXPENSE')` |
| `recurring unit domain` | `unit NOT IN ('DAY','WEEK','MONTH','YEAR')` |
| `recurring status domain` | `status NOT IN ('ACTIVE','PAUSED')` |
| `recurring occurrence status domain` | `status NOT IN ('GENERATED','SKIPPED')` |
| `ledger amount storage class` | `typeof(amount_minor) <> 'integer'` |
| `timestamp storage class` | `typeof(occurred_at) <> 'integer'` on `operation` and `ledger_entry`, and the same for `created_at`, `updated_at` on `operation` |
| `recurring storage class` | `typeof` of `amount_minor`, `start_date`, `interval_count` is not `integer`, or of `end_date`, `next_date` is neither `integer` nor `null` |

The `account.type` column is not checked: `AccountType.fromStorage` already maps an unknown value to `OTHER`, and icon keys fall back to a default icon (Accounts).

## 11. Privacy and security

- **No network, no new permission.** Files move only through the Storage Access Framework, with the user choosing every location (NFR-01 to NFR-03, Foundation 8.1).
- **Backups are plain.** Anyone with the file reads everything. The UI says so at export. There is no passphrase option in v1 (NFR-04, section 15, item 2). Adopting SQLCipher later is governed by Foundation 9.1, and the export test below remains its gate.
- **Hostile files.** A restore file may not come from Finora. Defences, in order: header check, no triggers, views or virtual tables before migration, `trusted_schema` off where supported, `integrity_check`, Room's schema validation, an exact object-set match with the live database, and the invariant checker. The file is never opened as the live database and never attached to it.
- **Exclusion from device backup.** The staging file, marker, outcome file and the `.tmp` marker live in `databases/`, which `data_extraction_rules.xml` already excludes in both sections. Export temp files live in `cacheDir`. No new rule is needed, and `BackupConfigurationTest` and `verify-backup-exclusion.ps1` stay valid.
- **Cleanup.** `cacheDir/export` is emptied at the start of every export and by the applier at every start. A staging file never outlives the process unless a marker owns it.
- **No logging of content.** Logs carry file sizes, step names and failure codes. They never carry account names, amounts or descriptions.

## 12. Testing

JVM unit tests:
- `CsvFormatTest`: quoting of `,`, `"`, CR, LF; BOM and CRLF; formula guard for each leading character, only on text columns, never on amounts; tag escaping of `;` and `\`; amount rendering at scales 0, 2, 3 and 4, negative values, `Long` extremes; `dateParts` in a zone with a missing midnight, a DST overlap, a half-hour offset, and for a timestamp near midnight UTC; column header matches the section 7.2 list exactly.
- `BackupFileNamesTest`: format, zero padding, restricted character set.
- `PendingImportApplierTest` on a temp directory: every row of the 6.5 table; a crash simulated between Step A and Step B (staging gone, live equals marker hash); leftover staging with no marker; damaged staging; staging lost; repeated runs are idempotent; the old `-wal` and `-shm` are removed only after the swap.
- `KnownSchemasTest`: each entry equals the `identityHash` in `app/schemas/.../<n>.json`, and the keys are exactly `1..FinoraDatabase.VERSION`.
- `ImportReportFormatterTest`: 50-line limit with "and N more", name enrichment for `account`, `category`, `tag`, `schedule` subjects, unknown subject kept as is.
- Static check: `domain/dataio` contains no `Float`, `Double`, Android, Room or `System.currentTimeMillis` use.
- `DataViewModel` tests with fakes: backup states, restore state machine (validate, reject, preview, cancel, confirm), buttons disabled while busy, "Back up current data first" returns to the same preview, failure messages per `ImportFailure`.

Instrumented tests (`BackupExporterTest`, `ImportValidatorTest`, `RestoreRoundTripTest`, `CsvExporterTest`):

Export
- **Plain SQLite gate (Foundation 9):** the exported file starts with `SQLite format 3\0`, opens with the stock Android API without a key, has `user_version == VERSION` and the live identity row, and contains the expected rows.
- **WAL-safe:** write rows with auto-checkpoint disabled so they exist only in the `-wal`, export, and find them in the file. Copying `finora.db` alone must be shown to miss them (this proves the test can detect the problem).
- **Concurrent writes:** inserts running during export never produce a file that fails `InvariantChecker`.
- A failed snapshot check, a failing destination stream and a cancellation each leave no destination file and no cache leftover.

Validation, one crafted file per failure code
- Not SQLite (text file, zero bytes, random bytes, a file with a valid header but a damaged page), another application's SQLite file, `user_version = 99`, a known version with a changed identity hash, a trigger, a view, a virtual table, an extra table (also after migration), a flipped byte in a data page (`CORRUPT`), a foreign key violation created with `foreign_keys=OFF`, a currency with a different scale, an unknown currency, an overflowing balance sum, two tags differing only in case.
- One raw-SQL violation for **every** `InvariantChecker` check, existing and new, each giving `INVARIANT_VIOLATIONS` with the right check name.
- The live database is byte-identical after every rejection, and no staging file remains.

Restore
- **Round trip:** export from a populated database, restore into an empty one through `commit` plus `PendingImportApplier`, and compare every table row for row, including `app_setting`, `exchange_rate` and the recurring tables. `InvariantChecker` is clean afterwards.
- **Upgrade:** files created at version 1 and 2 through `MigrationTestHelper` with seeded data restore into version 3 with all data preserved and `upgradedFrom` set. Every future schema bump adds one for the previous version (section 9).
- **Currency sync:** a file whose `currency` table lacks a registry row is accepted and the row is added.
- Restoring an empty-database backup works. Restoring while recurring generation and rate refresh are running does not corrupt either file.
- After the applier runs, Room opens the new file without error and the first screen reads the restored data.

CSV
- Rows for each filter dimension match the ids returned by `TransactionRepository.observePage` for the same filter (one definition of "what a filter matches").
- A transfer appears once with both sides, in both accounts' filters. A cross-currency transfer keeps both original amounts.
- Opening balances are absent. Uncategorized rows have empty category columns.
- Descriptions with commas, quotes, line breaks, emoji and right-to-left text round-trip through a standard CSV parser.
- 50,000 operations export with constant memory (batch size 500) and finish. Timing is recorded, not gated.
- A concurrent insert during export does not appear half-way (one snapshot).
- Cancellation deletes the partial file. `count(filter)` equals the number of exported rows.

Manual and scripted (device or emulator, Google APIs image):
1. Create a backup to Downloads. Open it with `sqlite3` or DB Browser and see the data.
2. Uninstall, reinstall, restore the backup: accounts, balances, icons, colors, categories, tags, schedules, theme and Display Currency are back.
3. Restore a v1 and a v2 file (kept from earlier builds): the "will be upgraded" note shows and the data is intact.
4. Restore a renamed JPEG, a CSV, a truncated backup and a backup with a flipped byte: each is rejected with a clear message and the data does not change.
5. Kill the app (`adb shell am force-stop`) right after the marker is written: the next launch finishes the restore. Kill it during validation: the next launch deletes the staging file and keeps the old data.
6. Fill the device storage and try a backup and a restore: both fail cleanly.
7. Export CSV for all data and for one account and one month; open it in a spreadsheet; check amounts, transfers and Cyrillic text.
8. Airplane mode: everything above works.
9. Re-run `scripts/verify-backup-exclusion.ps1`: it still passes.

## 13. Done when

1. All PRD 33 DATA MANAGEMENT criteria hold:
   - All data stays local and the app works without internet.
   - A full backup can be created.
   - A backup can be restored.
   - Transactions can be exported to CSV.
2. PRD 16.2 holds: integrity is validated, incompatible formats are detected, existing data is never silently corrupted, and the app says that restore replaces data.
3. The exported file is plain SQLite (Foundation 9 test) and includes WAL content.
4. Restore of a version 1 or 2 file produces a clean version 3 database with all data.
5. A crash or kill at any point leaves either the old data or the fully restored data, never a mix.
6. No schema change, no new permission, and `1.json`, `2.json`, `3.json` are untouched.
7. All tests in section 12 pass, the invariant checker is clean, and the build has no new lint warnings.

## 14. Risks

1. **Restart mechanism.** A separate-process restart activity is a known pattern but depends on OEM behaviour. Correctness does not depend on it (6.6 fallback). A plan-time spike checks it on the emulator and on at least one physical device.
2. **Apply at startup costs time.** Verifying the staging file's SHA-256 runs in `attachBaseContext` once per import. For a 100 MiB file this is on the order of a second. In the normal case (no marker) it is two `File.exists` calls.
3. **`VACUUM INTO` behaviour on API 33.** Binding the path as an argument, preservation of `user_version`, and the journal mode flag of the output file are checked at plan time with the export tests. Import does not depend on the output's journal mode.
4. **CSV snapshot API.** A read-only snapshot across batches needs a DEFERRED read transaction. Room 2.8 and the Android SQLite wrapper may differ on API 33 from newer APIs. The plan-time spike confirms it. The fallback is a non-exclusive transaction, which briefly delays writes during an export.
5. **SAF providers.** Some providers (cloud drives) are slow, reject `"wt"`, or rename an unknown MIME type. The export uses `application/vnd.sqlite3` and falls back to `application/octet-stream` if the provider renames the file. A short destination write is detected by the size comparison.
6. **Plain backups.** A copied file exposes everything. This is a deliberate v1 trade-off (Foundation 8.3), made visible in the UI.
7. **Large files.** Validation of a file near 512 MiB takes noticeable time (copy, `integrity_check`, migration). It is cancellable and shows steps, but is not instant.
8. **Silent surprises after restore.** Recurring schedules continue from their stored `next_date` and may create transactions on the first launch (Recurring 8). Cached rates older than 72 hours show as outdated until refreshed (Currency 8). Both match the restored data.
9. **Restore cannot be undone.** See assumption 3.
10. **No opening balances in CSV.** Documented on the CSV screen.
11. **A tampered but internally consistent file is accepted.** The checks verify structure and rules, not authorship. A user can restore a file they edited themselves, as long as it satisfies every invariant.

## 15. Assumptions to confirm

These details were not specified and are set to the simplest reasonable choice:
1. **CSV export is in this sub-project; CSV and JSON import are not.** This narrows the Foundation deferral to match PRD 16.3 and 33. Tags are offered as an extra CSV filter.
2. **No encryption of backups.** The Foundation keeps v1 plain. A passphrase option would conflict with the plain-SQLite gate and belongs to the SQLCipher change.
3. **No undo after a restore.** The confirmation and the "Back up current data first" button are the protection. Keeping the replaced database for N days would be a small addition on top of the marker mechanism.
4. **Restore replaces, never merges** (PRD 16.2 allows either as long as the app says which).
5. **"Manage application data" (PRD 17.3) is covered by the Data screen's data information, backup and restore.** A "Delete all data" action is not built. It would reuse the marker mechanism with a reset marker.
6. **Opening balances are not in the CSV**, matching the Transactions list. A checkbox can add them later by extending the query.
7. **Timestamps in CSV use the device zone at export time**, with the UTC instant in a separate column so nothing is lost.
8. **A 512 MiB import limit and `2 × size + 16 MiB` free-space rule.** Both are single constants.
9. **The restore result is shown once on the first screen after restart**, through the existing shell (a snackbar or a dialog, chosen at plan time).
10. **No "last backup" date or reminder.** The PRD asks for none.

## 16. Hand-offs

- **Roadmap complete.** This is the last sub-project. After it, only the P2 items (budgets, templates, attachments, advanced reports) remain.
- **Budgets, templates and attachments (later):** they add tables through normal migrations. The backup, the invariant checker and the `KnownSchemas` rule above already cover them. Attachments would need files beside the database, so a backup would no longer be one file. That change needs its own spec.
- **SQLCipher (later):** follows Foundation 9.1. The plain-SQLite export test is its gate. `VACUUM INTO`, the staging validator and the apply step must be adapted in that change, and the marker mechanism can stay.
- **Recurring transfers, "make recurring" shortcut, reminders:** unaffected. They add schema through migrations and are restorable by the same rules.
