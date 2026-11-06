package com.yehor.finora.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Runs SQL over the whole database for each invariant that is not a schema constraint
 * (design section 6.4). Returns human-readable violations; an empty list means the data is valid.
 * Reused by import validation in a later sub-project.
 */
object InvariantChecker {

    fun check(db: SupportSQLiteDatabase): List<String> {
        val violations = mutableListOf<String>()
        checks.forEach { (name, sql) ->
            db.query(sql).use { c ->
                while (c.moveToNext()) violations += "$name: ${c.getString(0)}"
            }
        }
        return violations
    }

    private val checks = listOf(
        "opening balance fields" to """
            SELECT 'operation ' || o.id FROM operation o
            LEFT JOIN ledger_entry e ON e.operation_id = o.id
            WHERE o.type = 'OPENING_BALANCE'
            GROUP BY o.id
            HAVING o.opening_account_id IS NULL
                OR o.category_id IS NOT NULL
                OR COUNT(e.id) <> 1
                OR MIN(e.account_id) <> o.opening_account_id
                OR MIN(e.amount_minor) = 0
        """,
        "opening_account_id on non-opening operation" to """
            SELECT 'operation ' || id FROM operation
            WHERE type <> 'OPENING_BALANCE' AND opening_account_id IS NOT NULL
        """,
        "tag on opening balance" to """
            SELECT 'operation ' || ot.operation_id FROM operation_tag ot
            JOIN operation o ON o.id = ot.operation_id
            WHERE o.type = 'OPENING_BALANCE'
        """,
        "ledger occurred_at mismatch" to """
            SELECT 'entry ' || e.id FROM ledger_entry e
            JOIN operation o ON o.id = e.operation_id
            WHERE e.occurred_at <> o.occurred_at
        """,
        "income entry not positive" to """
            SELECT 'operation ' || o.id FROM operation o
            JOIN ledger_entry e ON e.operation_id = o.id
            GROUP BY o.id HAVING o.type = 'INCOME' AND (COUNT(e.id) <> 1 OR MIN(e.amount_minor) <= 0)
        """,
        "expense entry not negative" to """
            SELECT 'operation ' || o.id FROM operation o
            JOIN ledger_entry e ON e.operation_id = o.id
            GROUP BY o.id HAVING o.type = 'EXPENSE' AND (COUNT(e.id) <> 1 OR MAX(e.amount_minor) >= 0)
        """,
        "transfer shape" to """
            SELECT 'operation ' || o.id FROM operation o
            LEFT JOIN ledger_entry e ON e.operation_id = o.id
            GROUP BY o.id HAVING o.type = 'TRANSFER'
                AND (COUNT(e.id) <> 2 OR COUNT(DISTINCT e.account_id) <> 2)
        """,
        "transfer entries have invalid signs" to """
            SELECT 'operation ' || o.id FROM operation o
            JOIN ledger_entry e ON e.operation_id = o.id
            GROUP BY o.id HAVING o.type = 'TRANSFER'
                AND (MIN(e.amount_minor) >= 0 OR MAX(e.amount_minor) <= 0)
        """,
        "category type mismatch" to """
            SELECT 'operation ' || o.id FROM operation o
            JOIN category c ON c.id = o.category_id
            WHERE c.type <> o.type
        """,
        "child category type differs from parent" to """
            SELECT 'category ' || c.id FROM category c
            JOIN category p ON p.id = c.parent_id
            WHERE p.type <> c.type
        """,
        "opening balance dated after an entry" to """
            SELECT 'account ' || o.opening_account_id FROM operation o
            WHERE o.type = 'OPENING_BALANCE' AND EXISTS (
                SELECT 1 FROM ledger_entry e
                WHERE e.account_id = o.opening_account_id
                  AND e.operation_id <> o.id
                  AND e.occurred_at < o.occurred_at
            )
        """,
        // chain(start_id, cur_id, depth): cur_id is the depth-th ancestor of start_id. Bounded at 5,
        // so it terminates on any data, including cycles.
        "category deeper than 3 levels" to """
            WITH RECURSIVE chain(start_id, cur_id, depth) AS (
                SELECT id, parent_id, 1 FROM category
                UNION ALL
                SELECT chain.start_id, c.parent_id, chain.depth + 1
                FROM chain JOIN category c ON c.id = chain.cur_id
                WHERE chain.depth < 5
            )
            SELECT DISTINCT 'category ' || start_id FROM chain
            WHERE depth = 3 AND cur_id IS NOT NULL
        """,
        "category cycle" to """
            WITH RECURSIVE chain(start_id, cur_id, depth) AS (
                SELECT id, parent_id, 1 FROM category
                UNION ALL
                SELECT chain.start_id, c.parent_id, chain.depth + 1
                FROM chain JOIN category c ON c.id = chain.cur_id
                WHERE chain.depth < 5
            )
            SELECT DISTINCT 'category ' || start_id FROM chain
            WHERE cur_id = start_id
        """,
        "exchange_rate same currency" to """
            SELECT base_currency || '/' || quote_currency FROM exchange_rate
            WHERE base_currency = quote_currency
        """,
        "exchange_rate invalid rate" to """
            SELECT base_currency || '/' || quote_currency FROM exchange_rate
            WHERE rate GLOB '*[^0-9.]*' OR rate GLOB '*.*.*' OR rate NOT GLOB '*[1-9]*'
        """,
        "exchange_rate invalid quote date" to """
            SELECT base_currency || '/' || quote_currency FROM exchange_rate
            WHERE quote_date NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
        """,
        "exchange_rate blank source" to """
            SELECT base_currency || '/' || quote_currency FROM exchange_rate
            WHERE trim(source) = ''
        """,
    )
}
