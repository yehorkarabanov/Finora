package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.tag.Tag

data class TransferDraft(
    val sourceAccountId: Long,
    val destAccountId: Long,
    val sentAmountMinor: Long, // positive magnitude in source currency
    val receivedAmountMinor: Long, // positive magnitude in dest currency
    val categoryId: Long?, // must be CategoryType.TRANSFER
    val occurredAt: Long,
    val description: String?,
    val tagIds: Set<Long> = emptySet(),
    val newTagNames: List<String> = emptyList(),
)

data class TransferItem(
    val id: Long,
    val occurredAt: Long,
    val description: String?,
    val category: Category?,
    val categoryPath: List<Category>,
    val tags: List<Tag>,
    val sourceAccount: Account,
    val destAccount: Account,
    val sentMoney: Money,
    val receivedMoney: Money,
    val createdAt: Long,
    val updatedAt: Long,
)

enum class TransferDraftError {
    SAME_ACCOUNTS,
    SOURCE_ACCOUNT_MISSING,
    DEST_ACCOUNT_MISSING,
    SENT_AMOUNT_EMPTY,
    SENT_AMOUNT_INVALID,
    SENT_AMOUNT_TOO_PRECISE,
    SENT_AMOUNT_NOT_POSITIVE,
    SENT_AMOUNT_OUT_OF_RANGE,
    RECEIVED_AMOUNT_EMPTY,
    RECEIVED_AMOUNT_INVALID,
    RECEIVED_AMOUNT_TOO_PRECISE,
    RECEIVED_AMOUNT_NOT_POSITIVE,
    RECEIVED_AMOUNT_OUT_OF_RANGE,
    CATEGORY_MISSING,
    DESCRIPTION_TOO_LONG,
    DATE_IN_FUTURE,
}
