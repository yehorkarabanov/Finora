package com.yehor.finora.data.dataio

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.sqlite.db.SimpleSQLiteQuery
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.TransactionQuery
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.dataio.CsvFormat
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CsvExportResult(val fileName: String, val count: Int)

class CsvExporter(
    private val context: Context,
    private val db: FinoraDatabase,
    private val categoryRepository: CategoryRepository,
) {

    suspend fun export(
        filter: TransactionFilter,
        destination: Uri,
        fileName: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): CsvExportResult = withContext(Dispatchers.IO) {
        val categories = categoryRepository.observeAll().first()
        val categoryTree = CategoryTree(categories)
        val categoryClosure: Set<Long> = if (filter.categoryIds.isNotEmpty()) {
            filter.categoryIds.flatMap { setOf(it) + categoryTree.descendantIds(it) }.toSet()
        } else {
            emptySet()
        }

        // Load account map
        val accountNames = mutableMapOf<Long, String>()
        val accountCurrencies = mutableMapOf<Long, String>()
        db.openHelper.readableDatabase.query("SELECT id, name, currency_code FROM account").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                accountNames[id] = c.getString(1)
                accountCurrencies[id] = c.getString(2)
            }
        }

        val outStream = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IllegalStateException("Could not open destination output stream")

        var destinationTouched = true
        var exportedCount = 0

        val readableDb = db.openHelper.readableDatabase

        try {
            BufferedWriter(OutputStreamWriter(outStream, StandardCharsets.UTF_8)).use { writer ->
                // Write UTF-8 BOM
                writer.write("\uFEFF")
                // Write Header
                CsvFormat.writeRow(writer, CsvFormat.HEADER)

                try {
                    readableDb.beginTransactionNonExclusive()
                } catch (_: Exception) {
                    readableDb.beginTransaction()
                }

                try {
                    var lastOccurredAt: Long? = null
                    var lastId: Long? = null
                    var hasMore = true
                    val batchSize = 500

                    while (hasMore) {
                        val batchOps = loadOperationBatch(
                            filter = filter,
                            categoryClosure = categoryClosure,
                            lastOccurredAt = lastOccurredAt,
                            lastId = lastId,
                            limit = batchSize,
                        )

                        if (batchOps.isEmpty()) {
                            hasMore = false
                            break
                        }

                        val opIds = batchOps.map { it.id }
                        val entriesByOp = loadEntries(opIds)
                        val tagsByOp = loadTags(opIds)

                        for (op in batchOps) {
                            val entries = entriesByOp[op.id] ?: emptyList()
                            val tags = tagsByOp[op.id] ?: emptyList()

                            val row = buildCsvRow(
                                op = op,
                                entries = entries,
                                tags = tags,
                                accountNames = accountNames,
                                categoryTree = categoryTree,
                                zone = zone,
                            )
                            CsvFormat.writeRow(writer, row)
                            exportedCount++

                            lastOccurredAt = op.occurredAt
                            lastId = op.id
                        }

                        if (batchOps.size < batchSize) {
                            hasMore = false
                        }
                    }
                } finally {
                    try {
                        readableDb.endTransaction()
                    } catch (_: Exception) {
                        // Ignore
                    }
                }

                writer.flush()
            }

            CsvExportResult(fileName, exportedCount)
        } catch (e: Exception) {
            if (destinationTouched) {
                try {
                    DocumentsContract.deleteDocument(context.contentResolver, destination)
                } catch (_: Exception) {
                    // Best effort cleanup
                }
            }
            throw e
        }
    }

    private data class OperationRow(
        val id: Long,
        val type: OperationType,
        val occurredAt: Long,
        val categoryId: Long?,
        val description: String?,
        val createdAt: Long,
        val updatedAt: Long,
    )

    private data class EntryRow(
        val id: Long,
        val operationId: Long,
        val accountId: Long,
        val currencyCode: String,
        val amountMinor: Long,
    )

    private fun loadOperationBatch(
        filter: TransactionFilter,
        categoryClosure: Set<Long>,
        lastOccurredAt: Long?,
        lastId: Long?,
        limit: Int,
    ): List<OperationRow> {
        val args = mutableListOf<Any>()
        val baseClauses = TransactionQuery.buildWhereClauses(filter, categoryClosure, null, args)
        val clauses = baseClauses.toMutableList()

        if (lastOccurredAt != null && lastId != null) {
            clauses += "(o.occurred_at > ? OR (o.occurred_at = ? AND o.id > ?))"
            args.add(lastOccurredAt)
            args.add(lastOccurredAt)
            args.add(lastId)
        }

        val whereSql = if (clauses.isNotEmpty()) "WHERE " + clauses.joinToString(" AND ") else ""
        args.add(limit)
        val sql = "SELECT o.id, o.type, o.occurred_at, o.category_id, o.description, o.created_at, o.updated_at FROM operation o $whereSql ORDER BY o.occurred_at ASC, o.id ASC LIMIT ?"

        val ops = mutableListOf<OperationRow>()
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, args.toTypedArray())).use { c ->
            while (c.moveToNext()) {
                ops.add(
                    OperationRow(
                        id = c.getLong(0),
                        type = OperationType.valueOf(c.getString(1)),
                        occurredAt = c.getLong(2),
                        categoryId = if (c.isNull(3)) null else c.getLong(3),
                        description = if (c.isNull(4)) null else c.getString(4),
                        createdAt = c.getLong(5),
                        updatedAt = c.getLong(6),
                    ),
                )
            }
        }
        return ops
    }

    private fun loadEntries(opIds: List<Long>): Map<Long, List<EntryRow>> {
        if (opIds.isEmpty()) return emptyMap()
        val placeholders = opIds.joinToString(",") { "?" }
        val sql = "SELECT id, operation_id, account_id, currency_code, amount_minor FROM ledger_entry WHERE operation_id IN ($placeholders) ORDER BY id ASC"
        val map = mutableMapOf<Long, MutableList<EntryRow>>()
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, opIds.toTypedArray())).use { c ->
            while (c.moveToNext()) {
                val entry = EntryRow(
                    id = c.getLong(0),
                    operationId = c.getLong(1),
                    accountId = c.getLong(2),
                    currencyCode = c.getString(3),
                    amountMinor = c.getLong(4),
                )
                map.getOrPut(entry.operationId) { mutableListOf() }.add(entry)
            }
        }
        return map
    }

    private fun loadTags(opIds: List<Long>): Map<Long, List<String>> {
        if (opIds.isEmpty()) return emptyMap()
        val placeholders = opIds.joinToString(",") { "?" }
        val sql = "SELECT ot.operation_id, t.name FROM operation_tag ot JOIN tag t ON t.id = ot.tag_id WHERE ot.operation_id IN ($placeholders) ORDER BY t.name ASC"
        val map = mutableMapOf<Long, MutableList<String>>()
        db.openHelper.readableDatabase.query(SimpleSQLiteQuery(sql, opIds.toTypedArray())).use { c ->
            while (c.moveToNext()) {
                val opId = c.getLong(0)
                val tagName = c.getString(1)
                map.getOrPut(opId) { mutableListOf() }.add(tagName)
            }
        }
        return map
    }

    private fun buildCsvRow(
        op: OperationRow,
        entries: List<EntryRow>,
        tags: List<String>,
        accountNames: Map<Long, String>,
        categoryTree: CategoryTree,
        zone: ZoneId,
    ): List<String> {
        val dateParts = CsvFormat.dateParts(op.occurredAt, zone)

        val accountName: String
        val amountStr: String
        val currencyStr: String
        var toAccountName = ""
        var toAmountStr = ""
        var toCurrencyStr = ""

        if (op.type == OperationType.TRANSFER) {
            val sourceEntry = entries.firstOrNull { it.amountMinor < 0 } ?: entries.firstOrNull()
            val destEntry = entries.firstOrNull { it.amountMinor > 0 } ?: entries.lastOrNull()

            val srcCurrency = sourceEntry?.let { CurrencyRegistry.get(it.currencyCode) }
            val dstCurrency = destEntry?.let { CurrencyRegistry.get(it.currencyCode) }

            accountName = sourceEntry?.let { accountNames[it.accountId] }.orEmpty()
            amountStr = if (sourceEntry != null && srcCurrency != null) {
                CsvFormat.amount(Money(sourceEntry.amountMinor, srcCurrency))
            } else {
                ""
            }
            currencyStr = sourceEntry?.currencyCode.orEmpty()

            toAccountName = destEntry?.let { accountNames[it.accountId] }.orEmpty()
            toAmountStr = if (destEntry != null && dstCurrency != null) {
                CsvFormat.amount(Money(destEntry.amountMinor, dstCurrency))
            } else {
                ""
            }
            toCurrencyStr = destEntry?.currencyCode.orEmpty()
        } else {
            val entry = entries.firstOrNull()
            val currency = entry?.let { CurrencyRegistry.get(it.currencyCode) }

            accountName = entry?.let { accountNames[it.accountId] }.orEmpty()
            amountStr = if (entry != null && currency != null) {
                CsvFormat.amount(Money(entry.amountMinor, currency))
            } else {
                ""
            }
            currencyStr = entry?.currencyCode.orEmpty()
        }

        val categoryName: String
        val categoryPath: String
        if (op.categoryId != null) {
            val cat = categoryTree.category(op.categoryId)
            categoryName = cat?.name.orEmpty()
            categoryPath = if (cat != null) {
                (categoryTree.ancestorsOf(cat.id) + cat).joinToString(" > ") { it.name }
            } else {
                ""
            }
        } else {
            categoryName = ""
            categoryPath = ""
        }

        val tagsStr = CsvFormat.tagList(tags)
        val createdAtUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(op.createdAt))
        val updatedAtUtc = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(op.updatedAt))

        return listOf(
            op.id.toString(),
            op.type.name,
            dateParts.first,
            dateParts.second,
            dateParts.third,
            CsvFormat.textField(accountName),
            amountStr,
            currencyStr,
            CsvFormat.textField(toAccountName),
            toAmountStr,
            toCurrencyStr,
            CsvFormat.textField(categoryName),
            CsvFormat.textField(categoryPath),
            CsvFormat.textField(tagsStr),
            CsvFormat.textField(op.description),
            createdAtUtc,
            updatedAtUtc,
        )
    }
}
