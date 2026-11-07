package com.yehor.finora.domain.dataio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class BackupFileNamesTest {

    private val safeCharacters = Regex("^[A-Za-z0-9._-]+$")

    @Test
    fun `backup filename format and character set`() {
        val now = Instant.parse("2026-11-07T14:05:09Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        val name = BackupFileNames.backup(now, zone)
        assertEquals("Finora-backup-2026-11-07-140509.db", name)
        assertTrue(safeCharacters.matches(name))
    }

    @Test
    fun `csv filename format and character set`() {
        val now = Instant.parse("2026-11-07T14:05:09Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        val name = BackupFileNames.csv(now, zone)
        assertEquals("Finora-transactions-2026-11-07.csv", name)
        assertTrue(safeCharacters.matches(name))
    }

    @Test
    fun `zero padding for single digit month day hour minute second`() {
        val now = Instant.parse("2026-01-05T04:03:02Z").toEpochMilli()
        val zone = ZoneId.of("UTC")
        assertEquals("Finora-backup-2026-01-05-040302.db", BackupFileNames.backup(now, zone))
        assertEquals("Finora-transactions-2026-01-05.csv", BackupFileNames.csv(now, zone))
    }
}
