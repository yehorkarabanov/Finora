package com.yehor.finora.data.db

import androidx.room.ColumnInfo

/** One group of [AnalyticsQuery.byCategory], `byAccount`, `byTag` or `byBucket`. [key] is null for Uncategorized or "No tag". */
data class AggregateRow(
    @ColumnInfo(name = "key") val key: Long?,
    @ColumnInfo(name = "currency_code") val currencyCode: String,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
    @ColumnInfo(name = "operation_count") val operationCount: Int,
)

/** Row of [AnalyticsQuery.accountFigures]. */
data class FigureRow(
    @ColumnInfo(name = "op_type") val opType: String,
    @ColumnInfo(name = "positive") val positive: Int,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** Row of [AnalyticsQuery.accountsOverview]. */
data class OverviewRow(
    @ColumnInfo(name = "account_id") val accountId: Long,
    @ColumnInfo(name = "op_type") val opType: String,
    @ColumnInfo(name = "positive") val positive: Int,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** Row of [AnalyticsQuery.balanceDeltas]. */
data class DeltaRow(
    @ColumnInfo(name = "bucket_index") val bucketIndex: Int,
    @ColumnInfo(name = "total_minor") val totalMinor: Long,
)

/** Row of [AnalyticsQuery.balanceBefore]. */
data class LongRow(
    @ColumnInfo(name = "value") val value: Long,
)
