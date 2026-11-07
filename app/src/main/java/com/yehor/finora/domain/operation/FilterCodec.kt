package com.yehor.finora.domain.operation

import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.tag.TagMatchMode

/**
 * Compact text form of the analytics subset of [TransactionFilter], for a navigation argument:
 * `t=EXPENSE;a=1,2;c=7;x=1;u=1;g=4,5;m=ALL;f=1730419200000;e=1733011200000`.
 *
 * Only enum names and integers are written, so no value can contain a separator. [decode] ignores
 * unknown keys and bad values and never throws.
 */
object FilterCodec {

    private val ENCODABLE_TYPES = setOf(OperationType.INCOME, OperationType.EXPENSE, OperationType.TRANSFER)

    fun encode(filter: TransactionFilter): String {
        val parts = ArrayList<String>()
        val types = filter.types.filter { it in ENCODABLE_TYPES }.sortedBy { it.ordinal }
        if (types.isNotEmpty()) parts += "t=" + types.joinToString(",") { it.name }
        if (filter.accountIds.isNotEmpty()) parts += "a=" + filter.accountIds.sorted().joinToString(",")
        if (filter.categoryIds.isNotEmpty()) parts += "c=" + filter.categoryIds.sorted().joinToString(",")
        if (filter.exactCategoryIds.isNotEmpty()) parts += "xe=" + filter.exactCategoryIds.sorted().joinToString(",")
        if (filter.categoryExact) parts += "x=1"
        if (filter.includeUncategorized) parts += "u=1"
        if (filter.tagIds.isNotEmpty()) parts += "g=" + filter.tagIds.sorted().joinToString(",")
        if (filter.tagMode == TagMatchMode.ALL) parts += "m=ALL"
        filter.from?.let { parts += "f=$it" }
        filter.toExclusive?.let { parts += "e=$it" }
        return parts.joinToString(";")
    }

    fun decode(text: String?): TransactionFilter {
        if (text.isNullOrBlank()) return TransactionFilter()
        var filter = TransactionFilter()
        for (part in text.split(';')) {
            val eq = part.indexOf('=')
            if (eq <= 0) continue
            val key = part.substring(0, eq)
            val value = part.substring(eq + 1)
            filter = when (key) {
                "t" -> filter.copy(types = value.split(',').mapNotNull(::parseType).toSet())
                "a" -> filter.copy(accountIds = parseIds(value))
                "c" -> filter.copy(categoryIds = parseIds(value))
                "xe" -> filter.copy(exactCategoryIds = parseIds(value))
                "x" -> filter.copy(categoryExact = value == "1")
                "u" -> filter.copy(includeUncategorized = value == "1")
                "g" -> filter.copy(tagIds = parseIds(value))
                "m" -> filter.copy(tagMode = if (value == "ALL") TagMatchMode.ALL else TagMatchMode.ANY)
                "f" -> filter.copy(from = value.toLongOrNull())
                "e" -> filter.copy(toExclusive = value.toLongOrNull())
                else -> filter
            }
        }
        return filter
    }

    private fun parseType(name: String): OperationType? =
        OperationType.entries.firstOrNull { it.name == name && it in ENCODABLE_TYPES }

    private fun parseIds(value: String): Set<Long> =
        value.split(',').mapNotNull { it.toLongOrNull() }.toSet()
}
