package com.yehor.finora.domain.operation

enum class TransactionKind { INCOME, EXPENSE }

data class TransactionDraft(
    val kind: TransactionKind,
    val accountId: Long,
    val amountMinor: Long, // positive magnitude in account's currency
    val categoryId: Long?,
    val occurredAt: Long,
    val description: String?,
    val tagIds: Set<Long> = emptySet(),
    val newTagNames: List<String> = emptyList(),
)
