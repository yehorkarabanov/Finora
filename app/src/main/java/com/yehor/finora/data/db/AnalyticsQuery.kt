package com.yehor.finora.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.operation.TransactionFilter

/**
 * SQL for the analytics aggregates. Every filter predicate comes from
 * [TransactionQuery.buildWhereClauses], so "what a filter matches" has one definition. The measure
 * adds constant predicates only, and every user value is a bound argument.
 */
object AnalyticsQuery {

    fun byCategory(filter: TransactionFilter, measure: Measure, categoryClosure: Set<Long>): SimpleSQLiteQuery =
        grouped(filter, measure, categoryClosure, keyExpression = "o.category_id", extraJoin = "")

    fun byAccount(filter: TransactionFilter, measure: Measure, categoryClosure: Set<Long>): SimpleSQLiteQuery =
        grouped(filter, measure, categoryClosure, keyExpression = "e.account_id", extraJoin = "")

    /** A transaction with several tags appears once per tag. NULL key = "No tag". */
    fun byTag(filter: TransactionFilter, measure: Measure, categoryClosure: Set<Long>): SimpleSQLiteQuery =
        grouped(
            filter, measure, categoryClosure,
            keyExpression = "ot.tag_id",
            extraJoin = "LEFT JOIN operation_tag ot ON ot.operation_id = o.id",
        )

    /** The filter's own `from` and `toExclusive` are replaced by the bucket bounds. */
    fun byBucket(
        filter: TransactionFilter,
        measure: Measure,
        categoryClosure: Set<Long>,
        buckets: List<Bucket>,
    ): SimpleSQLiteQuery {
        require(buckets.isNotEmpty() && buckets.size <= MAX_BUCKETS) { "1..$MAX_BUCKETS buckets required" }
        val args = mutableListOf<Any>()
        val cte = bucketCte(buckets, args)
        val clauses = clauses(filter.copy(from = null, toExclusive = null), measure, categoryClosure, args)
        val sql = """
            $cte
            SELECT b.idx AS key, e.currency_code AS currency_code,
                   SUM(ABS(e.amount_minor)) AS total_minor, COUNT(DISTINCT o.id) AS operation_count
            FROM b
            JOIN operation o ON o.occurred_at >= b.lo AND o.occurred_at < b.hi
            JOIN ledger_entry e ON e.operation_id = o.id
            WHERE ${clauses.joinToString(" AND ")}
            GROUP BY b.idx, e.currency_code
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    /** Signed sums per operation type and direction for one account and period. */
    fun accountFigures(accountId: Long, fromInclusive: Long, toExclusive: Long): SimpleSQLiteQuery {
        val sql = """
            SELECT o.type AS op_type,
                   CASE WHEN e.amount_minor > 0 THEN 1 ELSE 0 END AS positive,
                   SUM(e.amount_minor) AS total_minor
            FROM ledger_entry e
            JOIN operation o ON o.id = e.operation_id
            WHERE e.account_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?
            GROUP BY o.type, 2
        """.trimIndent()
        return SimpleSQLiteQuery(sql, arrayOf(accountId, fromInclusive, toExclusive))
    }

    /** [accountFigures] for every account at once. */
    fun accountsOverview(fromInclusive: Long, toExclusive: Long): SimpleSQLiteQuery {
        val sql = """
            SELECT e.account_id AS account_id, o.type AS op_type,
                   CASE WHEN e.amount_minor > 0 THEN 1 ELSE 0 END AS positive,
                   SUM(e.amount_minor) AS total_minor
            FROM ledger_entry e
            JOIN operation o ON o.id = e.operation_id
            WHERE e.occurred_at >= ? AND e.occurred_at < ?
            GROUP BY e.account_id, o.type, 3
        """.trimIndent()
        return SimpleSQLiteQuery(sql, arrayOf(fromInclusive, toExclusive))
    }

    /** Balance of an account from all entries strictly before [beforeExclusive]. */
    fun balanceBefore(accountId: Long, beforeExclusive: Long): SimpleSQLiteQuery =
        SimpleSQLiteQuery(
            "SELECT COALESCE(SUM(amount_minor), 0) AS value FROM ledger_entry WHERE account_id = ? AND occurred_at < ?",
            arrayOf(accountId, beforeExclusive),
        )

    /** Sum of all of an account's entries per bucket, opening balance included. */
    fun balanceDeltas(accountId: Long, buckets: List<Bucket>): SimpleSQLiteQuery {
        require(buckets.isNotEmpty() && buckets.size <= MAX_BUCKETS) { "1..$MAX_BUCKETS buckets required" }
        val args = mutableListOf<Any>()
        val cte = bucketCte(buckets, args)
        args.add(accountId)
        val sql = """
            $cte
            SELECT b.idx AS bucket_index, SUM(e.amount_minor) AS total_minor
            FROM b
            JOIN ledger_entry e ON e.account_id = ? AND e.occurred_at >= b.lo AND e.occurred_at < b.hi
            GROUP BY b.idx
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun grouped(
        filter: TransactionFilter,
        measure: Measure,
        categoryClosure: Set<Long>,
        keyExpression: String,
        extraJoin: String,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val clauses = clauses(filter, measure, categoryClosure, args)
        val sql = """
            SELECT $keyExpression AS key, e.currency_code AS currency_code,
                   SUM(ABS(e.amount_minor)) AS total_minor, COUNT(DISTINCT o.id) AS operation_count
            FROM operation o
            JOIN ledger_entry e ON e.operation_id = o.id
            $extraJoin
            WHERE ${clauses.joinToString(" AND ")}
            GROUP BY $keyExpression, e.currency_code
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    /** Filter predicates, the measure, and the account restriction on the aggregated entry. Appends to [args] in order. */
    private fun clauses(
        filter: TransactionFilter,
        measure: Measure,
        categoryClosure: Set<Long>,
        args: MutableList<Any>,
    ): List<String> {
        val result = TransactionQuery
            .buildWhereClauses(filter, categoryClosure, filter.amount?.currency?.code, args)
            .toMutableList()
        result += when (measure) {
            Measure.EXPENSE -> "o.type = 'EXPENSE'"
            Measure.INCOME -> "o.type = 'INCOME'"
            Measure.TRANSFER -> "o.type = 'TRANSFER' AND e.amount_minor < 0"
        }
        if (filter.accountIds.isNotEmpty()) {
            result += "e.account_id IN (${filter.accountIds.joinToString(",") { "?" }})"
            args.addAll(filter.accountIds)
        }
        return result
    }

    private fun bucketCte(buckets: List<Bucket>, args: MutableList<Any>): String {
        buckets.forEach {
            args.add(it.index.toLong())
            args.add(it.from)
            args.add(it.toExclusive)
        }
        return "WITH b(idx, lo, hi) AS (VALUES ${buckets.joinToString(",") { "(?,?,?)" }})"
    }

    const val MAX_BUCKETS = 100
}
