package com.yehor.finora.domain.dataio

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BackupFileNames {
    private val BACKUP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    private val CSV_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun backup(now: Long, zone: ZoneId): String {
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        return "Finora-backup-${zdt.format(BACKUP_FORMATTER)}.db"
    }

    fun csv(now: Long, zone: ZoneId): String {
        val zdt = Instant.ofEpochMilli(now).atZone(zone)
        return "Finora-transactions-${zdt.format(CSV_FORMATTER)}.csv"
    }
}
