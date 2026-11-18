package com.yehor.finora.data.dataio

import com.yehor.finora.domain.dataio.ImportOutcome
import com.yehor.finora.domain.dataio.PendingImportMarker
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

object PendingImportApplier {

    const val LIVE_DB_NAME = "finora.db"
    const val STAGING_DB_NAME = "finora-import-staging.db"
    const val MARKER_FILE_NAME = "finora-pending-import.json"
    const val MARKER_TMP_FILE_NAME = "finora-pending-import.json.tmp"
    const val OUTCOME_FILE_NAME = "finora-import-outcome.json"

    private val json = Json { ignoreUnknownKeys = true }

    fun run(databasesDir: File, cacheDir: File? = null) {
        if (cacheDir != null) {
            val exportCache = File(cacheDir, "export")
            if (exportCache.exists()) {
                exportCache.listFiles()?.forEach { it.delete() }
            }
        }

        if (!databasesDir.exists()) return

        val liveFile = File(databasesDir, LIVE_DB_NAME)
        val liveWal = File(databasesDir, "$LIVE_DB_NAME-wal")
        val liveShm = File(databasesDir, "$LIVE_DB_NAME-shm")

        val stagingFile = File(databasesDir, STAGING_DB_NAME)
        val stagingWal = File(databasesDir, "$STAGING_DB_NAME-wal")
        val stagingShm = File(databasesDir, "$STAGING_DB_NAME-shm")

        val markerFile = File(databasesDir, MARKER_FILE_NAME)
        val markerTmpFile = File(databasesDir, MARKER_TMP_FILE_NAME)
        val outcomeFile = File(databasesDir, OUTCOME_FILE_NAME)

        // Delete any leftover temp marker file
        if (markerTmpFile.exists()) {
            markerTmpFile.delete()
        }

        if (!markerFile.exists()) {
            // Row 1 & 2: Marker absent
            if (stagingFile.exists()) {
                stagingFile.delete()
                stagingWal.delete()
                stagingShm.delete()
            }
            return
        }

        // Marker present
        val marker = try {
            json.decodeFromString<PendingImportMarker>(markerFile.readText())
        } catch (_: Exception) {
            stagingFile.delete()
            stagingWal.delete()
            stagingShm.delete()
            markerFile.delete()
            writeOutcome(outcomeFile, ImportOutcome(format = 1, success = false, reason = "STAGING_INVALID"))
            return
        }

        if (stagingFile.exists()) {
            val sizeMatches = stagingFile.length() == marker.sizeBytes
            val hashMatches = sizeMatches && computeSha256(stagingFile) == marker.sha256
            if (sizeMatches && hashMatches) {
                // Row 3: Swap not started -> Step A + Step B
                try {
                    try {
                        Files.move(
                            stagingFile.toPath(),
                            liveFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            stagingFile.toPath(),
                            liveFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    // Step B: Cleanup and record success
                    stagingWal.delete()
                    stagingShm.delete()
                    liveWal.delete()
                    liveShm.delete()
                    markerFile.delete()
                    writeOutcome(outcomeFile, ImportOutcome(format = 1, success = true, summary = marker.summary))
                } catch (_: Exception) {
                    stagingFile.delete()
                    stagingWal.delete()
                    stagingShm.delete()
                    markerFile.delete()
                    writeOutcome(outcomeFile, ImportOutcome(format = 1, success = false, reason = "SWAP_FAILED"))
                }
            } else {
                // Row 4: Staging file damaged
                stagingFile.delete()
                stagingWal.delete()
                stagingShm.delete()
                markerFile.delete()
                writeOutcome(outcomeFile, ImportOutcome(format = 1, success = false, reason = "STAGING_INVALID"))
            }
        } else {
            // Staging file absent
            if (liveFile.exists() && computeSha256(liveFile) == marker.sha256) {
                // Row 5: Swap done, cleanup pending -> Step B
                liveWal.delete()
                liveShm.delete()
                markerFile.delete()
                writeOutcome(outcomeFile, ImportOutcome(format = 1, success = true, summary = marker.summary))
            } else {
                // Row 6: Staging lost before swap
                markerFile.delete()
                writeOutcome(outcomeFile, ImportOutcome(format = 1, success = false, reason = "STAGING_LOST"))
            }
        }
    }

    private fun writeOutcome(outcomeFile: File, outcome: ImportOutcome) {
        try {
            val tmp = File(outcomeFile.parentFile, "${outcomeFile.name}.tmp")
            tmp.writeText(json.encodeToString(outcome))
            try {
                Files.move(
                    tmp.toPath(),
                    outcomeFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    tmp.toPath(),
                    outcomeFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } catch (_: Exception) {
            // Best-effort outcome write
        }
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(65536)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val hashBytes = digest.digest()
        val sb = StringBuilder(hashBytes.size * 2)
        for (b in hashBytes) {
            sb.append(String.format("%02x", b.toInt() and 0xFF))
        }
        return sb.toString()
    }
}
