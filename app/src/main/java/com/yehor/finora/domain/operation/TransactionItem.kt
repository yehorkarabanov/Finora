package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.tag.Tag

data class TransactionEntryView(
    val account: Account,
    val money: Money, // signed
)

data class TransactionItem(
    val id: Long,
    val type: OperationType,
    val occurredAt: Long,
    val description: String?,
    val category: Category?,
    val categoryPath: List<Category>,
    val tags: List<Tag>,
    val entries: List<TransactionEntryView>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CurrencySummary(
    val currency: Currency,
    val income: Money,
    val expense: Money,
    val operationCount: Int,
)
