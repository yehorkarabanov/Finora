package com.yehor.finora.data.dataio

import com.yehor.finora.domain.dataio.ImportOutcome
import kotlinx.serialization.json.Json
import java.io.File

object ImportOutcomeStore {

    private val json = Json { ignoreUnknownKeys = true }

    fun consume(databasesDir: File): ImportOutcome? {
        val outcomeFile = File(databasesDir, PendingImportApplier.OUTCOME_FILE_NAME)
        if (!outcomeFile.exists()) return null
        return try {
            val content = outcomeFile.readText()
            outcomeFile.delete()
            json.decodeFromString<ImportOutcome>(content)
        } catch (_: Exception) {
            outcomeFile.delete()
            null
        }
    }
}
