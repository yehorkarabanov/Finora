package com.yehor.finora.ui.analytics

import com.yehor.finora.domain.analytics.Bucket
import com.yehor.finora.domain.analytics.BucketUnit
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.CategoryDrill
import com.yehor.finora.domain.analytics.CategoryEstimateRollup
import com.yehor.finora.domain.analytics.CompareRow
import com.yehor.finora.domain.analytics.CurrencyScope
import com.yehor.finora.domain.analytics.Delta
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.ScopeConverter
import com.yehor.finora.domain.analytics.Share
import com.yehor.finora.domain.analytics.TopSplit
import com.yehor.finora.domain.analytics.TrendPoint
import com.yehor.finora.domain.analytics.Reports
import com.yehor.finora.domain.analytics.delta
import com.yehor.finora.domain.analytics.netCashFlow
import com.yehor.finora.domain.analytics.orZero
import com.yehor.finora.domain.analytics.share
import com.yehor.finora.domain.analytics.sumCells
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.OperationType

/** One row of the category list, with its share of the level and, with Compare on, its change. */
data class CategoryLine(val row: DrillRow, val share: Share?, val previous: Cell?, val delta: Delta?)

data class CategoryReport(
    /** The period total, whatever level is shown. */
    val total: Cell,
    val previousTotal: Cell?,
    val totalDelta: Delta?,
    /** The sum of the rows shown at this level (R7). */
    val levelTotal: Cell,
    val breadcrumb: List<Category>,
    val lines: List<CategoryLine>,
    /** The current rows (without previous-only rows) for the donut. */
    val split: TopSplit,
)

/** A row of the by-account, by-tag or by-category-of-a-tag breakdown. [id] is null for "No tag". */
data class BreakdownLine(val id: Long?, val cell: Cell, val share: Share?)

/** Totals of the tag analytics. A null figure is a type the filter excludes and reads "—". */
data class Figures(val income: Cell?, val expense: Cell?, val net: Cell?, val transferred: Cell?) {
    val operationCount: Int
        get() = listOfNotNull(income, expense, transferred).sumOf { it.operationCount }
}

data class TagsReport(
    val figures: Figures,
    val previous: Figures?,
    val measure: Measure,
    val category: CategoryReport,
    val byAccount: List<BreakdownLine>,
    val byDate: List<TrendPoint>,
    val dateUnit: BucketUnit,
)

data class FlowReport(
    val measure: Measure,
    val category: CategoryReport,
    val byAccount: List<BreakdownLine>,
    val byTag: List<BreakdownLine>,
    val trend: List<TrendPoint>,
    val trendYearly: Boolean,
)

/** Pure assembly of the numbers the analytics screens show. Everything converted goes through [ScopeConverter]. */
object ReportBuilder {

    /** A figure whose sum did not fit in a Long: shown as "Unavailable" (R13). */
    val OVERFLOW = Cell(estimate = null, operationCount = 0, excluded = emptySet(), overflowed = true, oldestFetchedAt = null)

    private class Level(val total: Cell, val rows: List<DrillRow>)

    private fun level(tree: CategoryTree, converter: ScopeConverter, measure: Measure, rows: Rows, path: List<Long>): Level {
        if (rows is Rows.Overflow) return Level(OVERFLOW, emptyList())
        val direct = Reports.cells(rows.list(), converter)
        val rolled = CategoryEstimateRollup.rollUp(tree, direct)
        return Level(Reports.total(direct), CategoryDrill.rows(tree, direct, rolled, path, measure.categoryType))
    }

    /** [previous] is null when Compare is off. */
    fun category(
        env: AnalyticsEnv,
        scope: CurrencyScope,
        measure: Measure,
        current: Rows,
        previous: Rows?,
        path: List<Long>,
    ): CategoryReport {
        val tree = CategoryTree(env.categories)
        val converter = ScopeConverter(scope, env.table, env.now)
        val valid = path.filter { tree.node(it) != null }
        val cur = level(tree, converter, measure, current, valid)
        val prev = previous?.let { level(tree, converter, measure, it, valid) }
        val currency = scope.currency()

        val levelTotal = cur.rows.map { it.cell }.sumCells()
        val compareRows: List<CompareRow> = if (prev != null) {
            Reports.compare(cur.rows, prev.rows)
        } else {
            cur.rows.map { CompareRow(it, null) }
        }
        val lines = compareRows.map { compareRow ->
            CategoryLine(
                row = compareRow.row,
                share = shareOf(compareRow.row.cell, levelTotal),
                previous = compareRow.previous,
                delta = if (prev != null) delta(compareRow.row.cell.estimate, compareRow.previous?.estimate) else null,
            )
        }
        val total = cur.total.orZero(currency)
        val previousTotal = prev?.total?.orZero(currency)
        return CategoryReport(
            total = total,
            previousTotal = previousTotal,
            totalDelta = previousTotal?.let { delta(total.estimate, it.estimate) },
            levelTotal = levelTotal,
            breadcrumb = valid.mapNotNull { tree.category(it) },
            lines = lines,
            split = Reports.topSplit(cur.rows),
        )
    }

    /** Rows grouped by account or tag, largest first. [total] is the base of the shares. */
    fun breakdown(rows: Rows, converter: ScopeConverter, total: Cell): List<BreakdownLine> =
        Reports.cells(rows.list(), converter)
            .map { (key, cell) -> BreakdownLine(key, cell, shareOf(cell, total)) }
            .filter { it.cell.hasAmount || it.cell.partial || it.cell.overflowed }
            .sortedByDescending { it.cell.estimate?.amountMinor ?: Long.MIN_VALUE }

    fun flow(
        env: AnalyticsEnv,
        scope: CurrencyScope,
        measure: Measure,
        categoryRows: Rows,
        previousRows: Rows?,
        accountRows: Rows,
        tagRows: Rows,
        trendRows: Rows,
        trendBuckets: List<Bucket>,
        trendYearly: Boolean,
        path: List<Long>,
    ): FlowReport {
        val converter = ScopeConverter(scope, env.table, env.now)
        val category = category(env, scope, measure, categoryRows, previousRows, path)
        // Account and tag rows are shown against the period total, not against a drill-down level.
        val periodTotal = if (categoryRows is Rows.Ok) Reports.total(Reports.cells(categoryRows.rows, converter)) else OVERFLOW
        return FlowReport(
            measure = measure,
            category = category,
            byAccount = breakdown(accountRows, converter, periodTotal),
            byTag = breakdown(tagRows, converter, periodTotal),
            trend = Reports.trend(trendBuckets, trendRows.list(), converter),
            trendYearly = trendYearly,
        )
    }

    /** [types] is the filter's type set. A type the filter leaves out has no figure. */
    fun figures(
        env: AnalyticsEnv,
        scope: CurrencyScope,
        types: Set<OperationType>,
        income: Rows,
        expense: Rows,
        transfer: Rows,
    ): Figures {
        val converter = ScopeConverter(scope, env.table, env.now)
        val currency = scope.currency()
        fun total(rows: Rows, type: OperationType): Cell? {
            if (types.isNotEmpty() && type !in types) return null
            if (rows is Rows.Overflow) return OVERFLOW
            return Reports.total(Reports.cells(rows.list(), converter)).orZero(currency)
        }
        val inc = total(income, OperationType.INCOME)
        val exp = total(expense, OperationType.EXPENSE)
        return Figures(
            income = inc,
            expense = exp,
            net = if (inc != null && exp != null) netCashFlow(inc, exp) else null,
            transferred = total(transfer, OperationType.TRANSFER),
        )
    }

    private fun shareOf(cell: Cell, total: Cell): Share? {
        val part = cell.estimate ?: return null
        val whole = total.estimate ?: return null
        return share(part, whole)
    }
}
