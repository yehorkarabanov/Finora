package com.yehor.finora.domain.operation

import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.tag.TagMatchMode

data class AmountRange(
    val currency: Currency,
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
)

data class TransactionFilter(
    val types: Set<OperationType> = emptySet(), // subset of INCOME, EXPENSE, TRANSFER. Empty = all.
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(), // includes descendants unless in [exactCategoryIds] or [categoryExact]
    val exactCategoryIds: Set<Long> = emptySet(), // subset of [categoryIds] that match only direct operations
    val categoryExact: Boolean = false, // true: all [categoryIds] match only their own operations
    val includeUncategorized: Boolean = false,
    val tagIds: Set<Long> = emptySet(),
    val tagMode: TagMatchMode = TagMatchMode.ANY,
    val from: Long? = null,
    val toExclusive: Long? = null,
    val amount: AmountRange? = null,
    val text: String = "",
) {
    val isActive: Boolean
        get() = types.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            categoryIds.isNotEmpty() ||
            exactCategoryIds.isNotEmpty() ||
            categoryExact ||
            includeUncategorized ||
            tagIds.isNotEmpty() ||
            from != null ||
            toExclusive != null ||
            amount != null ||
            text.isNotBlank()
}

enum class TransactionSort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }
