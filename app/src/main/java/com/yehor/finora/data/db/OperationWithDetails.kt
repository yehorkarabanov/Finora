package com.yehor.finora.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class OperationWithDetails(
    @Embedded val operation: OperationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "operation_id",
    )
    val entries: List<LedgerEntryEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = OperationTagEntity::class,
            parentColumn = "operation_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

data class TagWithUsageRow(
    @Embedded val tag: TagEntity,
    val usageCount: Int,
)

data class SummaryRow(
    val currencyCode: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val operationCount: Int,
)
