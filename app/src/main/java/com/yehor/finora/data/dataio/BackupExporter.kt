package com.yehor.finora.data.dataio

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.DocumentsContract
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.domain.dataio.ImportFailure
import com.yehor.finora.domain.dataio.KnownSchemas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

data class BackupExportResult(val fileName: String, val sizeBytes: Long)

class BackupException(val failure: ImportFailure, message: String) : Exception(message)

class BackupExporter(
    private val context: Context,
    private val db: FinoraDatabase,
) {

    suspend fun export(destination: Uri, fileName: String = "Finora-backup.db"): BackupExportResult =
        withContext(Dispatchers.IO) {
            val liveDbFile = context.getDatabasePath(FinoraDatabase.FILE_NAME)
            val liveSize = if (liveDbFile.exists()) liveDbFile.length() else 0L
            val requiredCache = (liveSize * 1.1).toLong() + (1024 * 1024)

            if (context.cacheDir.usableSpace < requiredCache) {
                throw BackupException(ImportFailure.NOT_ENOUGH_SPACE, "Not enough space in cache for backup snapshot")
            }

            val exportDir = File(context.cacheDir, "export").apply { mkdirs() }
            exportDir.listFiles()?.forEach { it.delete() }

            val tempFile = File(exportDir, "backup-${UUID.randomUUID()}.db")
            if (tempFile.exists()) tempFile.delete()

            var destinationTouched = false

            try {
                // Step 2: VACUUM INTO
                db.openHelper.writableDatabase.execSQL(
                    "VACUUM INTO ?",
                    arrayOf<Any>(tempFile.absolutePath),
                )

                // Step 3: Verify snapshot
                verifySnapshot(tempFile)

                // Step 4: Stream to destination
                destinationTouched = true
                val outStream = context.contentResolver.openOutputStream(destination, "wt")
                    ?: throw BackupException(ImportFailure.UNREADABLE, "Could not open output stream for destination")

                var bytesWritten = 0L
                outStream.use { out ->
                    FileInputStream(tempFile).use { input ->
                        val buffer = ByteArray(65536)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            bytesWritten += read
                        }
                    }
                    out.flush()
                }

                if (bytesWritten != tempFile.length()) {
                    throw BackupException(ImportFailure.UNREADABLE, "Incomplete write to destination")
                }

                BackupExportResult(fileName, bytesWritten)
            } catch (e: Exception) {
                if (destinationTouched) {
                    try {
                        DocumentsContract.deleteDocument(context.contentResolver, destination)
                    } catch (_: Exception) {
                        // Best effort cleanup
                    }
                }
                throw e
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }

    private fun verifySnapshot(file: File) {
        if (!file.exists() || file.length() < 16) {
            file.delete()
            throw BackupException(ImportFailure.CORRUPT, "Snapshot is missing or empty")
        }

        // Header check
        val header = ByteArray(16)
        FileInputStream(file).use { it.read(header) }
        val headerStr = String(header, StandardCharsets.US_ASCII)
        if (headerStr != "SQLite format 3\u0000") {
            file.delete()
            throw BackupException(ImportFailure.NOT_SQLITE, "Snapshot header is not SQLite")
        }

        // Open framework database
        var sqliteDb: SQLiteDatabase? = null
        try {
            sqliteDb = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

            // PRAGMA user_version
            val version = sqliteDb.rawQuery("PRAGMA user_version", null).use { c ->
                if (c.moveToFirst()) c.getInt(0) else -1
            }
            if (version != FinoraDatabase.VERSION) {
                throw BackupException(ImportFailure.UNKNOWN_SCHEMA, "Snapshot user_version ($version) != ${FinoraDatabase.VERSION}")
            }

            // room_master_table check
            val identity = sqliteDb.rawQuery("SELECT identity_hash FROM room_master_table", null).use { c ->
                if (c.moveToFirst() && c.count == 1) c.getString(0) else null
            }
            if (identity != KnownSchemas.identityHashes[FinoraDatabase.VERSION]) {
                throw BackupException(ImportFailure.UNKNOWN_SCHEMA, "Snapshot identity hash mismatch")
            }

            // PRAGMA quick_check
            val quickCheck = sqliteDb.rawQuery("PRAGMA quick_check", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else "error"
            }
            if (quickCheck != "ok") {
                throw BackupException(ImportFailure.CORRUPT, "Snapshot quick_check failed: $quickCheck")
            }
        } finally {
            sqliteDb?.close()
        }
    }
}
