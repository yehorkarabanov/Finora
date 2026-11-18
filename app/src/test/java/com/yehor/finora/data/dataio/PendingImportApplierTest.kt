package com.yehor.finora.data.dataio

import com.yehor.finora.domain.dataio.DataSummary
import com.yehor.finora.domain.dataio.PendingImportMarker
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingImportApplierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private val sampleSummary = DataSummary(
        accounts = 3,
        activeAccounts = 3,
        categories = 10,
        tags = 5,
        transactions = 100,
        transfers = 10,
        schedules = 2,
        firstOperationAt = 1000L,
        lastOperationAt = 2000L,
        databaseBytes = 40960L,
        schemaVersion = 3,
    )

    @Test
    fun `row 1 - marker absent with leftover staging deletes staging and wal shm`() {
        val dbDir = tempFolder.newFolder("databases")
        val staging = File(dbDir, PendingImportApplier.STAGING_DB_NAME).apply { writeText("staging content") }
        val stagingWal = File(dbDir, "${PendingImportApplier.STAGING_DB_NAME}-wal").apply { writeText("wal") }
        val stagingShm = File(dbDir, "${PendingImportApplier.STAGING_DB_NAME}-shm").apply { writeText("shm") }
        val live = File(dbDir, PendingImportApplier.LIVE_DB_NAME).apply { writeText("live content") }

        PendingImportApplier.run(dbDir)

        assertFalse(staging.exists())
        assertFalse(stagingWal.exists())
        assertFalse(stagingShm.exists())
        assertTrue(live.exists())
        assertEquals("live content", live.readText())
        assertNull(ImportOutcomeStore.consume(dbDir))
    }

    @Test
    fun `row 2 - normal start with no marker and no staging does nothing`() {
        val dbDir = tempFolder.newFolder("databases")
        val live = File(dbDir, PendingImportApplier.LIVE_DB_NAME).apply { writeText("live content") }

        PendingImportApplier.run(dbDir)

        assertTrue(live.exists())
        assertEquals("live content", live.readText())
        assertNull(ImportOutcomeStore.consume(dbDir))
    }

    @Test
    fun `row 3 - marker present and staging matches performs atomic swap and writes success outcome`() {
        val dbDir = tempFolder.newFolder("databases")
        val live = File(dbDir, PendingImportApplier.LIVE_DB_NAME).apply { writeText("old live") }
        val liveWal = File(dbDir, "${PendingImportApplier.LIVE_DB_NAME}-wal").apply { writeText("old wal") }
        val liveShm = File(dbDir, "${PendingImportApplier.LIVE_DB_NAME}-shm").apply { writeText("old shm") }

        val stagingContent = "new staged database content that will replace live"
        val staging = File(dbDir, PendingImportApplier.STAGING_DB_NAME).apply { writeText(stagingContent) }
        val stagingWal = File(dbDir, "${PendingImportApplier.STAGING_DB_NAME}-wal").apply { writeText("new wal") }
        val stagingShm = File(dbDir, "${PendingImportApplier.STAGING_DB_NAME}-shm").apply { writeText("new shm") }

        val hash = PendingImportApplier.computeSha256(staging)
        val marker = PendingImportMarker(
            format = 1,
            stagingFile = staging.name,
            sizeBytes = staging.length(),
            sha256 = hash,
            createdAt = 123456789L,
            summary = sampleSummary,
        )
        val markerFile = File(dbDir, PendingImportApplier.MARKER_FILE_NAME).apply {
            writeText(json.encodeToString(marker))
        }

        PendingImportApplier.run(dbDir)

        assertFalse(markerFile.exists())
        assertFalse(staging.exists())
        assertFalse(stagingWal.exists())
        assertFalse(stagingShm.exists())
        assertFalse(liveWal.exists())
        assertFalse(liveShm.exists())

        assertTrue(live.exists())
        assertEquals(stagingContent, live.readText())

        val outcome = ImportOutcomeStore.consume(dbDir)
        assertNotNull(outcome)
        assertTrue(outcome!!.success)
        assertEquals(sampleSummary, outcome.summary)
        assertNull(outcome.reason)
    }

    @Test
    fun `row 4 - marker present but staging damaged aborts with STAGING_INVALID leaving live untouched`() {
        val dbDir = tempFolder.newFolder("databases")
        val live = File(dbDir, PendingImportApplier.LIVE_DB_NAME).apply { writeText("original live") }
        val liveWal = File(dbDir, "${PendingImportApplier.LIVE_DB_NAME}-wal").apply { writeText("live wal") }

        val staging = File(dbDir, PendingImportApplier.STAGING_DB_NAME).apply { writeText("corrupted staging") }
        val marker = PendingImportMarker(
            format = 1,
            stagingFile = staging.name,
            sizeBytes = 999999L, // wrong size and hash
            sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            createdAt = 123456789L,
        )
        val markerFile = File(dbDir, PendingImportApplier.MARKER_FILE_NAME).apply {
            writeText(json.encodeToString(marker))
        }

        PendingImportApplier.run(dbDir)

        assertFalse(markerFile.exists())
        assertFalse(staging.exists())
        assertTrue(live.exists())
        assertEquals("original live", live.readText())
        assertTrue(liveWal.exists())

        val outcome = ImportOutcomeStore.consume(dbDir)
        assertNotNull(outcome)
        assertFalse(outcome!!.success)
        assertEquals("STAGING_INVALID", outcome.reason)
    }

    @Test
    fun `row 5 - crash simulated between Step A and Step B resumes Step B`() {
        val dbDir = tempFolder.newFolder("databases")
        val liveContent = "already swapped live content"
        val live = File(dbDir, PendingImportApplier.LIVE_DB_NAME).apply { writeText(liveContent) }
        val liveWal = File(dbDir, "${PendingImportApplier.LIVE_DB_NAME}-wal").apply { writeText("old wal from previous db") }
        val liveShm = File(dbDir, "${PendingImportApplier.LIVE_DB_NAME}-shm").apply { writeText("old shm from previous db") }

        val hash = PendingImportApplier.computeSha256(live)
        val marker = PendingImportMarker(
            format = 1,
            stagingFile = PendingImportApplier.STAGING_DB_NAME,
            sizeBytes = live.length(),
            sha256 = hash,
            createdAt = 123456789L,
            summary = sampleSummary,
        )
        val markerFile = File(dbDir, PendingImportApplier.MARKER_FILE_NAME).apply {
            writeText(json.encodeToString(marker))
        }

        PendingImportApplier.run(dbDir)

        assertFalse(markerFile.exists())
        assertFalse(liveWal.exists())
        assertFalse(liveShm.exists())
        assertTrue(live.exists())
        assertEquals(liveContent, live.readText())

        val outcome = ImportOutcomeStore.consume(dbDir)
        assertNotNull(outcome)
        assertTrue(outcome!!.success)
        assertEquals(sampleSummary, outcome.summary)
    }

    @Test
    fun `row 6 - staging lost before swap aborts with STAGING_LOST leaving live untouched`() {
        val dbDir = tempFolder.newFolder("databases")
        val live = File(dbDir, PendingImportApplier.LIVE_DB_NAME).apply { writeText("original live") }

        val marker = PendingImportMarker(
            format = 1,
            stagingFile = PendingImportApplier.STAGING_DB_NAME,
            sizeBytes = 1234L,
            sha256 = "differenthash",
            createdAt = 123456789L,
        )
        val markerFile = File(dbDir, PendingImportApplier.MARKER_FILE_NAME).apply {
            writeText(json.encodeToString(marker))
        }

        PendingImportApplier.run(dbDir)

        assertFalse(markerFile.exists())
        assertTrue(live.exists())
        assertEquals("original live", live.readText())

        val outcome = ImportOutcomeStore.consume(dbDir)
        assertNotNull(outcome)
        assertFalse(outcome!!.success)
        assertEquals("STAGING_LOST", outcome.reason)
    }

    @Test
    fun `repeated runs are idempotent`() {
        val dbDir = tempFolder.newFolder("databases")
        val staging = File(dbDir, PendingImportApplier.STAGING_DB_NAME).apply { writeText("staged data") }
        val marker = PendingImportMarker(
            format = 1,
            stagingFile = staging.name,
            sizeBytes = staging.length(),
            sha256 = PendingImportApplier.computeSha256(staging),
            createdAt = 123456789L,
        )
        File(dbDir, PendingImportApplier.MARKER_FILE_NAME).writeText(json.encodeToString(marker))

        PendingImportApplier.run(dbDir)
        val outcome = ImportOutcomeStore.consume(dbDir)
        assertNotNull(outcome)
        assertTrue(outcome!!.success)

        // Run again
        PendingImportApplier.run(dbDir)
        assertNull(ImportOutcomeStore.consume(dbDir))
    }
}
