package com.yehor.finora.data.dataio

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseFactory
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.domain.dataio.DataSummary
import com.yehor.finora.domain.dataio.ImportFailure
import com.yehor.finora.domain.dataio.ImportProblem
import com.yehor.finora.domain.dataio.ImportReportFormatter
import com.yehor.finora.domain.dataio.KnownSchemas
import com.yehor.finora.domain.dataio.PendingImportMarker
import com.yehor.finora.domain.dataio.StagingResult
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.tag.TagNameRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class BackupImporter(
    private val context: Context,
    private val liveDb: FinoraDatabase,
) {

    private companion object {
        const val TAG = "BackupImporter"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val maxFileSizeBytes = 512L * 1024 * 1024

    suspend fun stage(
        source: Uri,
        onProgress: (String) -> Unit = {},
    ): StagingResult = withContext(Dispatchers.IO) {
        val databasesDir = context.getDatabasePath(FinoraDatabase.FILE_NAME).parentFile
            ?: File(context.applicationInfo.dataDir, "databases")
        databasesDir.mkdirs()

        val stagingFile = File(databasesDir, PendingImportApplier.STAGING_DB_NAME)
        val stagingWal = File(databasesDir, "${PendingImportApplier.STAGING_DB_NAME}-wal")
        val stagingShm = File(databasesDir, "${PendingImportApplier.STAGING_DB_NAME}-shm")

        fun cleanupStaging() {
            stagingFile.delete()
            stagingWal.delete()
            stagingShm.delete()
        }

        cleanupStaging()

        // 1. Size check
        val reportedSize = getReportedSize(source)
        if (reportedSize != null && reportedSize > maxFileSizeBytes) {
            return@withContext StagingResult.Rejected(ImportFailure.TOO_LARGE, emptyList(), 0)
        }
        val requiredSpace = if (reportedSize != null) (2 * reportedSize + (16 * 1024 * 1024)) else (32 * 1024 * 1024)
        if (databasesDir.usableSpace < requiredSpace) {
            return@withContext StagingResult.Rejected(ImportFailure.NOT_ENOUGH_SPACE, emptyList(), 0)
        }

        // 2. Copy stream to staging
        onProgress("Copying")
        var bytesCopied = 0L
        try {
            val inStream = context.contentResolver.openInputStream(source)
                ?: return@withContext StagingResult.Rejected(ImportFailure.UNREADABLE, emptyList(), 0)
            inStream.use { input ->
                FileOutputStream(stagingFile).use { output ->
                    val buffer = ByteArray(65536)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        bytesCopied += read
                        if (bytesCopied > maxFileSizeBytes) {
                            cleanupStaging()
                            return@withContext StagingResult.Rejected(ImportFailure.TOO_LARGE, emptyList(), 0)
                        }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
        } catch (_: Exception) {
            cleanupStaging()
            return@withContext StagingResult.Rejected(ImportFailure.UNREADABLE, emptyList(), 0)
        }

        // 3. Validation pipeline
        onProgress("Checking structure")

        // Step 1: SQLite header check
        if (stagingFile.length() < 16) {
            cleanupStaging()
            return@withContext StagingResult.Rejected(ImportFailure.NOT_SQLITE, emptyList(), 0)
        }
        val header = ByteArray(16)
        FileInputStream(stagingFile).use { it.read(header) }
        val headerStr = String(header, StandardCharsets.US_ASCII)
        if (headerStr != "SQLite format 3\u0000") {
            cleanupStaging()
            return@withContext StagingResult.Rejected(ImportFailure.NOT_SQLITE, emptyList(), 0)
        }

        // Step 2-7: Framework SQLiteDatabase checks
        var initialUserVersion = -1
        var rawDb: SQLiteDatabase? = null
        try {
            rawDb = SQLiteDatabase.openDatabase(stagingFile.path, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                rawDb.execSQL("PRAGMA trusted_schema = OFF")
            } catch (_: Exception) {
                // Ignore if not supported on older sqlite
            }

            // Step 2: room_master_table and core tables
            val masterTableCount = rawDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'",
                null,
            ).use { it.count }
            if (masterTableCount != 1) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.NOT_FINORA, emptyList(), 0)
            }

            val masterRowCount = rawDb.rawQuery("SELECT identity_hash FROM room_master_table", null).use { it.count }
            if (masterRowCount != 1) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.NOT_FINORA, emptyList(), 0)
            }

            initialUserVersion = rawDb.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
            if (initialUserVersion < 1) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.NOT_FINORA, emptyList(), 0)
            }

            val coreTables = setOf("account", "operation", "ledger_entry")
            val existingTables = mutableSetOf<String>()
            rawDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c ->
                while (c.moveToNext()) existingTables.add(c.getString(0))
            }
            if (!existingTables.containsAll(coreTables)) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.NOT_FINORA, emptyList(), 0)
            }

            // Step 3: user_version <= VERSION
            if (initialUserVersion > FinoraDatabase.VERSION) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.NEWER_VERSION, emptyList(), 0)
            }

            // Step 4: Identity hash matches KnownSchemas
            val identityHash = rawDb.rawQuery("SELECT identity_hash FROM room_master_table", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            val expectedHash = KnownSchemas.identityHashes[initialUserVersion]
            if (identityHash == null || identityHash != expectedHash) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.UNKNOWN_SCHEMA, emptyList(), 0)
            }

            // Step 5: Unsafe objects (triggers, views, virtual tables)
            val unsafeCount = rawDb.rawQuery(
                "SELECT name FROM sqlite_master WHERE type IN ('trigger', 'view') OR (sql IS NOT NULL AND upper(sql) LIKE '%CREATE VIRTUAL TABLE%')",
                null,
            ).use { it.count }
            if (unsafeCount > 0) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.UNSAFE_OBJECTS, emptyList(), 0)
            }

            // Step 6: PRAGMA integrity_check
            val integrity = rawDb.rawQuery("PRAGMA integrity_check", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else "error"
            }
            if (integrity != "ok") {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.CORRUPT, emptyList(), 0)
            }

            // Step 7: PRAGMA foreign_key_check
            val fkCount = rawDb.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
            if (fkCount > 0) {
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.FOREIGN_KEY_VIOLATIONS, emptyList(), 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Raw open/check failed: ${e.javaClass.name}: ${e.message}")
            cleanupStaging()
            return@withContext StagingResult.Rejected(ImportFailure.CORRUPT, emptyList(), 0)
        } finally {
            rawDb?.close()
        }

        // Step 8: Room migration with FinoraDatabaseFactory
        if (initialUserVersion < FinoraDatabase.VERSION) {
            onProgress("Upgrading")
        }
        val stagingRoomDb = try {
            FinoraDatabaseFactory.build(context, PendingImportApplier.STAGING_DB_NAME)
        } catch (e: Exception) {
            Log.w(TAG, "Room build failed: ${e.javaClass.name}: ${e.message}")
            cleanupStaging()
            return@withContext StagingResult.Rejected(ImportFailure.MIGRATION_FAILED, emptyList(), 0)
        }

        var stagingSupportDb: SupportSQLiteDatabase? = null
        try {
            stagingSupportDb = stagingRoomDb.openHelper.writableDatabase
        } catch (e: Exception) {
            Log.w(TAG, "Room open/migration failed: ${e.javaClass.name}: ${e.message}")
            stagingRoomDb.close()
            cleanupStaging()
            return@withContext StagingResult.Rejected(ImportFailure.MIGRATION_FAILED, emptyList(), 0)
        }

        onProgress("Checking data")

        try {
            // Step 9: Currency table verification and sync
            val currencyProblems = verifyAndSyncCurrencies(stagingSupportDb)
            if (!currencyProblems) {
                stagingRoomDb.close()
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.CURRENCY_MISMATCH, emptyList(), 0)
            }

            // Step 10: Object set match with live database
            val objectSetMatch = verifyObjectSet(stagingSupportDb, liveDb.openHelper.readableDatabase)
            if (!objectSetMatch) {
                stagingRoomDb.close()
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.UNEXPECTED_OBJECTS, emptyList(), 0)
            }

            // Step 11: InvariantChecker
            val rawProblems = InvariantChecker.checkDetailed(stagingSupportDb)
            if (rawProblems.isNotEmpty()) {
                val enriched = enrichProblems(stagingSupportDb, rawProblems)
                stagingRoomDb.close()
                cleanupStaging()
                return@withContext StagingResult.Rejected(
                    ImportFailure.INVARIANT_VIOLATIONS,
                    enriched,
                    rawProblems.size,
                )
            }

            // Step 12: Balance overflow
            try {
                stagingSupportDb.query("SELECT account_id, SUM(amount_minor) FROM ledger_entry GROUP BY account_id").use { c ->
                    while (c.moveToNext()) {
                        c.getLong(1)
                    }
                }
            } catch (_: Exception) {
                stagingRoomDb.close()
                cleanupStaging()
                return@withContext StagingResult.Rejected(ImportFailure.BALANCE_OVERFLOW, emptyList(), 0)
            }

            // Step 13: Tag names case rule
            val tagDuplicates = checkTagCaseCollisions(stagingSupportDb)
            if (tagDuplicates.isNotEmpty()) {
                stagingRoomDb.close()
                cleanupStaging()
                return@withContext StagingResult.Rejected(
                    ImportFailure.INVARIANT_VIOLATIONS,
                    tagDuplicates,
                    tagDuplicates.size,
                )
            }

            // Step 14: Checkpoint, compute DataSummary and SHA-256
            // The pragma returns a row, so it must go through query, not execSQL.
            stagingSupportDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            val summary = DataSummaryReader.read(stagingSupportDb, stagingFile.length(), FinoraDatabase.VERSION)
            stagingRoomDb.close()

            val sha256 = PendingImportApplier.computeSha256(stagingFile)
            val upgradedFrom = if (initialUserVersion < FinoraDatabase.VERSION) initialUserVersion else null

            StagingResult.Ready(
                summary = summary,
                upgradedFrom = upgradedFrom,
                sizeBytes = stagingFile.length(),
                sha256 = sha256,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Staging validation failed: ${e.javaClass.name}: ${e.message}")
            try { stagingRoomDb.close() } catch (_: Exception) {}
            cleanupStaging()
            StagingResult.Rejected(ImportFailure.CORRUPT, emptyList(), 0)
        }
    }

    suspend fun commit(ready: StagingResult.Ready) = withContext(Dispatchers.IO) {
        val databasesDir = context.getDatabasePath(FinoraDatabase.FILE_NAME).parentFile
            ?: File(context.applicationInfo.dataDir, "databases")
        val stagingFile = File(databasesDir, PendingImportApplier.STAGING_DB_NAME)

        if (!stagingFile.exists() || stagingFile.length() != ready.sizeBytes) {
            throw BackupException(ImportFailure.UNREADABLE, "STAGING_LOST")
        }

        val marker = PendingImportMarker(
            format = 1,
            stagingFile = stagingFile.name,
            sizeBytes = ready.sizeBytes,
            sha256 = ready.sha256,
            createdAt = System.currentTimeMillis(),
            summary = ready.summary,
        )

        val tmpMarker = File(databasesDir, PendingImportApplier.MARKER_TMP_FILE_NAME)
        val finalMarker = File(databasesDir, PendingImportApplier.MARKER_FILE_NAME)

        tmpMarker.writeText(json.encodeToString(marker))
        try {
            Files.move(
                tmpMarker.toPath(),
                finalMarker.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tmpMarker.toPath(),
                finalMarker.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }

        ProcessRestarter.restart(context)
    }

    private fun getReportedSize(uri: Uri): Long? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun verifyAndSyncCurrencies(db: SupportSQLiteDatabase): Boolean {
        // 1. Verify existing rows
        db.query("SELECT code, scale FROM currency").use { c ->
            while (c.moveToNext()) {
                val code = c.getString(0)
                val scale = c.getInt(1)
                val reg = CurrencyRegistry.findOrNull(code) ?: return false
                if (reg.scale != scale) return false
            }
        }

        // 2. Insert missing currencies
        CurrencyRegistry.all.forEach { cur ->
            db.execSQL(
                "INSERT OR IGNORE INTO currency (code, scale, symbol, name) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(cur.code, cur.scale, cur.symbol, cur.name),
            )
        }
        return true
    }

    private fun verifyObjectSet(stagingDb: SupportSQLiteDatabase, liveDb: SupportSQLiteDatabase): Boolean {
        fun getObjects(db: SupportSQLiteDatabase): Set<Pair<String, String>> {
            val set = mutableSetOf<Pair<String, String>>()
            db.query("SELECT type, name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name != 'room_master_table'").use { c ->
                while (c.moveToNext()) {
                    set.add(c.getString(0) to c.getString(1))
                }
            }
            return set
        }

        val stagingObjects = getObjects(stagingDb)
        val liveObjects = getObjects(liveDb)

        // Must not contain any triggers or views
        if (stagingObjects.any { it.first in setOf("trigger", "view") }) return false

        return stagingObjects == liveObjects
    }

    private fun enrichProblems(db: SupportSQLiteDatabase, rawProblems: List<ImportProblem>): List<ImportProblem> {
        val accounts = mutableMapOf<Long, String>()
        val categories = mutableMapOf<Long, String>()
        val tags = mutableMapOf<Long, String>()
        val schedules = mutableMapOf<Long, String>()

        try {
            db.query("SELECT id, name FROM account").use { c ->
                while (c.moveToNext()) accounts[c.getLong(0)] = c.getString(1)
            }
            db.query("SELECT id, name FROM category").use { c ->
                while (c.moveToNext()) categories[c.getLong(0)] = c.getString(1)
            }
            db.query("SELECT id, name FROM tag").use { c ->
                while (c.moveToNext()) tags[c.getLong(0)] = c.getString(1)
            }
            db.query("SELECT id, name FROM recurring_schedule").use { c ->
                while (c.moveToNext()) schedules[c.getLong(0)] = c.getString(1)
            }
        } catch (_: Exception) {
            // Best effort
        }

        return rawProblems.map { p ->
            val enrichedSubject = ImportReportFormatter.enrichSubject(
                p.subject,
                accounts = accounts,
                categories = categories,
                tags = tags,
                schedules = schedules,
            )
            ImportProblem(p.check, enrichedSubject)
        }
    }

    private fun checkTagCaseCollisions(db: SupportSQLiteDatabase): List<ImportProblem> {
        val seen = mutableMapOf<String, String>()
        val problems = mutableListOf<ImportProblem>()
        db.query("SELECT name FROM tag").use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0)
                val key = TagNameRules.key(name)
                val existing = seen[key]
                if (existing != null && existing != name) {
                    problems.add(ImportProblem("tag case collision", "$existing / $name"))
                } else {
                    seen[key] = name
                }
            }
        }
        return problems
    }
}
