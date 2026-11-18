package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException

/** A native sum of one currency. [money] is null when the sum does not fit in a Long. */
data class NativeTotal(val currency: Currency, val money: Money?, val operationCount: Int)

/** The top rows of a breakdown, the rest folded into [other], and Uncategorized kept apart. */
data class TopSplit(val top: List<DrillRow>, val other: Cell?, val uncategorized: DrillRow?)

/** A row with the cell of the previous period, for the Compare switch. */
data class CompareRow(val row: DrillRow, val previous: Cell?)

data class TrendPoint(val bucket: Bucket, val cell: Cell)

object Reports {

    /** Converts every group once per currency (see [ScopeConverter]). */
    fun cells(rows: List<NativeRow>, converter: ScopeConverter): Map<Long?, Cell> =
        rows.groupBy { it.key }.mapValues { (_, group) -> converter.cell(group.map { it.toAmount() }) }

    /** Sum of the group cells, so a total equals the sum of the rows shown (R7). */
    fun total(direct: Map<Long?, Cell>): Cell = direct.values.sumCells()

    /** Exact sums per currency for the "By currency" sheet, ordered by code. */
    fun nativeTotals(rows: List<NativeRow>): List<NativeTotal> =
        rows.groupBy { it.money.currency }
            .map { (currency, group) ->
                val count = group.sumOf { it.operationCount }
                val sum = try {
                    group.fold(Money.zero(currency)) { acc, row -> acc + row.money }
                } catch (e: MoneyOverflowException) {
                    null
                }
                NativeTotal(currency, sum, count)
            }
            .sortedBy { it.currency.code }

    /** Top [limit] ranked rows, then "Other", with Uncategorized always shown on its own. */
    fun topSplit(rows: List<DrillRow>, limit: Int = 5): TopSplit {
        val uncategorized = rows.firstOrNull { it.category == null && !it.isDirect }
        val ranked = rows.filter { it !== uncategorized }
        if (ranked.size <= limit) return TopSplit(ranked, null, uncategorized)
        val other = ranked.drop(limit).map { it.cell }.sumCells()
        return TopSplit(ranked.take(limit), other, uncategorized)
    }

    /**
     * Rows of the current period plus rows that only exist in the previous one (current amount
     * "—", negative delta). Matching is by category and the direct flag.
     */
    fun compare(current: List<DrillRow>, previous: List<DrillRow>): List<CompareRow> {
        fun key(row: DrillRow) = row.categoryId to row.isDirect
        val previousByKey = previous.associateBy(::key)
        val currentKeys = current.map(::key).toSet()
        val merged = current.map { CompareRow(it, previousByKey[key(it)]?.cell) }
        val onlyPrevious = previous
            .filter { key(it) !in currentKeys }
            .map { CompareRow(it.copy(cell = Cell.NONE), it.cell) }
        return merged + onlyPrevious
    }

    /** One point per bucket, in bucket order, with a quiet bucket as an exact zero. */
    fun trend(buckets: List<Bucket>, rows: List<NativeRow>, converter: ScopeConverter): List<TrendPoint> {
        val byBucket = rows.groupBy { it.key }
        return buckets.map { bucket ->
            val group = byBucket[bucket.index.toLong()].orEmpty()
            TrendPoint(bucket, converter.cell(group.map { it.toAmount() }))
        }
    }
}
