package com.yehor.finora.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.TagMatchMode

object TransactionQuery {

    fun build(
        filter: TransactionFilter,
        sort: TransactionSort,
        categoryClosure: Set<Long>,
        scopeCurrency: String? = filter.amount?.currency?.code,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val whereClauses = buildWhereClauses(filter, categoryClosure, scopeCurrency, args)

        val whereSql = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else ""

        val orderSql = when (sort) {
            TransactionSort.DATE_DESC -> "ORDER BY o.occurred_at DESC, o.id DESC"
            TransactionSort.DATE_ASC -> "ORDER BY o.occurred_at ASC, o.id ASC"
            TransactionSort.AMOUNT_DESC -> {
                requireNotNull(scopeCurrency) { "Scope currency required for amount sort" }
                args.add(scopeCurrency)
                "ORDER BY (SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = ?) DESC, o.occurred_at DESC, o.id DESC"
            }
            TransactionSort.AMOUNT_ASC -> {
                requireNotNull(scopeCurrency) { "Scope currency required for amount sort" }
                args.add(scopeCurrency)
                "ORDER BY (SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = ?) ASC, o.occurred_at ASC, o.id ASC"
            }
        }

        val sql = "SELECT o.* FROM operation o $whereSql $orderSql"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun buildSummary(
        filter: TransactionFilter,
        categoryClosure: Set<Long>,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val baseClauses = buildWhereClauses(filter, categoryClosure, filter.amount?.currency?.code, args)
        val clauses = baseClauses + "o.type IN ('INCOME', 'EXPENSE')"

        val whereSql = "WHERE " + clauses.joinToString(" AND ")
        val sql = """
            SELECT e.currency_code AS currencyCode,
                   COALESCE(SUM(CASE WHEN e.amount_minor > 0 THEN e.amount_minor ELSE 0 END), 0) AS incomeMinor,
                   COALESCE(SUM(CASE WHEN e.amount_minor < 0 THEN ABS(e.amount_minor) ELSE 0 END), 0) AS expenseMinor,
                   COUNT(DISTINCT o.id) AS operationCount
            FROM operation o
            JOIN ledger_entry e ON e.operation_id = o.id
            $whereSql
            GROUP BY e.currency_code
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun buildWhereClauses(
        filter: TransactionFilter,
        categoryClosure: Set<Long>,
        scopeCurrency: String?,
        args: MutableList<Any>,
    ): List<String> {
        val clauses = mutableListOf<String>()

        if (filter.types.isEmpty()) {
            clauses += "o.type != 'OPENING_BALANCE'"
        } else {
            val allowed = filter.types.filter { it != OperationType.OPENING_BALANCE }
            val placeholders = allowed.joinToString(",") { "?" }
            clauses += "o.type IN ($placeholders)"
            args.addAll(allowed.map { it.name })
        }

        if (filter.from != null) {
            clauses += "o.occurred_at >= ?"
            args.add(filter.from)
        }
        if (filter.toExclusive != null) {
            clauses += "o.occurred_at < ?"
            args.add(filter.toExclusive)
        }

        if (filter.categoryIds.isNotEmpty() || filter.includeUncategorized) {
            val catClauses = mutableListOf<String>()
            if (categoryClosure.isNotEmpty()) {
                val placeholders = categoryClosure.joinToString(",") { "?" }
                catClauses += "o.category_id IN ($placeholders)"
                args.addAll(categoryClosure)
            }
            if (filter.includeUncategorized) {
                catClauses += "o.category_id IS NULL"
            }
            if (catClauses.isNotEmpty()) {
                clauses += "(" + catClauses.joinToString(" OR ") + ")"
            }
        }

        if (filter.accountIds.isNotEmpty()) {
            val placeholders = filter.accountIds.joinToString(",") { "?" }
            clauses += "EXISTS (SELECT 1 FROM ledger_entry e WHERE e.operation_id = o.id AND e.account_id IN ($placeholders))"
            args.addAll(filter.accountIds)
        }

        if (filter.tagIds.isNotEmpty()) {
            when (filter.tagMode) {
                TagMatchMode.ANY -> {
                    val placeholders = filter.tagIds.joinToString(",") { "?" }
                    clauses += "EXISTS (SELECT 1 FROM operation_tag t WHERE t.operation_id = o.id AND t.tag_id IN ($placeholders))"
                    args.addAll(filter.tagIds)
                }
                TagMatchMode.ALL -> {
                    val placeholders = filter.tagIds.joinToString(",") { "?" }
                    clauses += "(SELECT COUNT(DISTINCT t.tag_id) FROM operation_tag t WHERE t.operation_id = o.id AND t.tag_id IN ($placeholders)) = ?"
                    args.addAll(filter.tagIds)
                    args.add(filter.tagIds.size)
                }
            }
        }

        if (filter.text.isNotBlank()) {
            clauses += "o.description LIKE ? ESCAPE '\\'"
            val escaped = filter.text.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            args.add("%$escaped%")
        }

        if (filter.amount != null) {
            requireNotNull(scopeCurrency) { "Scope currency required for amount filter" }
            val min = filter.amount.minMinor ?: 0L
            val max = filter.amount.maxMinor ?: Long.MAX_VALUE
            clauses += "EXISTS (SELECT 1 FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = ? AND ABS(e.amount_minor) >= ? AND ABS(e.amount_minor) <= ?)"
            args.add(scopeCurrency)
            args.add(min)
            args.add(max)
        }

        return clauses
    }
}
