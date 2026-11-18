package com.yehor.finora.domain.dataio

import kotlinx.serialization.Serializable

enum class ImportFailure {
    UNREADABLE,
    TOO_LARGE,
    NOT_ENOUGH_SPACE, // reading the chosen file
    NOT_SQLITE,
    NOT_FINORA,
    NEWER_VERSION,
    UNKNOWN_SCHEMA,
    UNSAFE_OBJECTS,
    CORRUPT,
    FOREIGN_KEY_VIOLATIONS,
    MIGRATION_FAILED,
    UNEXPECTED_OBJECTS,
    CURRENCY_MISMATCH,
    BALANCE_OVERFLOW,
    INVARIANT_VIOLATIONS,
}

@Serializable
data class ImportProblem(
    val check: String,
    val subject: String,
)

@Serializable
data class DataSummary(
    val accounts: Int,
    val activeAccounts: Int,
    val categories: Int,
    val tags: Int,
    val transactions: Int, // INCOME + EXPENSE
    val transfers: Int,
    val schedules: Int,
    val firstOperationAt: Long?,
    val lastOperationAt: Long?, // epoch ms, opening balances excluded
    val databaseBytes: Long,
    val schemaVersion: Int,
)

sealed interface StagingResult {
    data class Ready(
        val summary: DataSummary,
        val upgradedFrom: Int?,
        val sizeBytes: Long,
        val sha256: String,
    ) : StagingResult

    data class Rejected(
        val failure: ImportFailure,
        val problems: List<ImportProblem>,
        val totalProblems: Int,
    ) : StagingResult
}

@Serializable
data class PendingImportMarker(
    val format: Int = 1,
    val stagingFile: String,
    val sizeBytes: Long,
    val sha256: String,
    val createdAt: Long,
    val summary: DataSummary? = null,
)

@Serializable
data class ImportOutcome(
    val format: Int = 1,
    val success: Boolean,
    val reason: String? = null,
    val summary: DataSummary? = null,
)

object KnownSchemas {
    val identityHashes: Map<Int, String> = mapOf(
        1 to "47c8a7411cf5ec364d7f1653929306bc",
        2 to "bc27b988dc1b760155ba97c9020680b8",
        3 to "04921f168acbcd459670725d7c26a468",
    )
}
