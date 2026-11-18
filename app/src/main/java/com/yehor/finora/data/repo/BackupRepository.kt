package com.yehor.finora.data.repo

import android.content.Context
import android.net.Uri
import com.yehor.finora.data.dataio.BackupExportResult
import com.yehor.finora.data.dataio.BackupExporter
import com.yehor.finora.data.dataio.BackupImporter
import com.yehor.finora.data.dataio.CsvExportResult
import com.yehor.finora.data.dataio.CsvExporter
import com.yehor.finora.data.dataio.DataSummaryReader
import com.yehor.finora.data.dataio.ImportOutcomeStore
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.domain.dataio.DataSummary
import com.yehor.finora.domain.dataio.ImportOutcome
import com.yehor.finora.domain.dataio.StagingResult
import com.yehor.finora.domain.operation.TransactionFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: FinoraDatabase,
    private val categoryRepository: CategoryRepository,
) {

    val operationLock = Mutex()

    suspend fun getLiveSummary(): DataSummary = withContext(Dispatchers.IO) {
        val liveFile = context.getDatabasePath(FinoraDatabase.FILE_NAME)
        val size = if (liveFile.exists()) liveFile.length() else 0L
        DataSummaryReader.read(db.openHelper.readableDatabase, size, FinoraDatabase.VERSION)
    }

    suspend fun exportBackup(destination: Uri, fileName: String): Result<BackupExportResult> =
        operationLock.withLock {
            runCatching {
                BackupExporter(context, db).export(destination, fileName)
            }
        }

    suspend fun stageBackup(source: Uri, onProgress: (String) -> Unit = {}): StagingResult =
        operationLock.withLock {
            BackupImporter(context, db).stage(source, onProgress)
        }

    suspend fun commitBackup(ready: StagingResult.Ready): Result<Unit> =
        operationLock.withLock {
            runCatching {
                BackupImporter(context, db).commit(ready)
            }
        }

    suspend fun exportCsv(
        filter: TransactionFilter,
        destination: Uri,
        fileName: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result<CsvExportResult> =
        operationLock.withLock {
            runCatching {
                CsvExporter(context, db, categoryRepository).export(filter, destination, fileName, zone)
            }
        }

    fun consumeOutcome(): ImportOutcome? {
        val dbDir = context.getDatabasePath(FinoraDatabase.FILE_NAME).parentFile
            ?: File(context.applicationInfo.dataDir, "databases")
        return ImportOutcomeStore.consume(dbDir)
    }
}
