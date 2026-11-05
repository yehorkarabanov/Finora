package com.yehor.finora.data.repo

import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.OperationWithDetails
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransferDraft
import com.yehor.finora.domain.operation.TransferItem
import com.yehor.finora.domain.tag.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransferRepository @Inject constructor(
    private val db: FinoraDatabase,
    private val operationRepository: OperationRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {

    /** Emits null when the operation does not exist or is not a well-formed transfer. */
    fun observeTransfer(id: Long): Flow<TransferItem?> =
        combine(
            db.operationDao().observeWithDetails(id),
            accountRepository.observeAll().map { list -> list.map { it.account } }.distinctUntilChanged(),
            categoryRepository.observeAll(),
        ) { details, accounts, cats ->
            if (details == null) return@combine null
            toItem(details, accounts.associateBy { it.id }, cats.associateBy { it.id }, CategoryTree(cats))
        }

    suspend fun recentCategoryIds(limit: Int = 5): List<Long> =
        db.operationDao().recentCategoryIds(OperationType.TRANSFER, limit)

    suspend fun create(draft: TransferDraft, moveOpeningBalanceBack: Boolean = false): Long {
        if (draft.categoryId == null) {
            throw InvariantViolationException("Category is required when creating a transfer")
        }
        return operationRepository.insertTransfer(draft, moveOpeningBalanceBack)
    }

    suspend fun update(
        id: Long,
        draft: TransferDraft,
        moveOpeningBalanceBack: Boolean = false,
        confirmCurrencyChange: Boolean = false,
    ) = operationRepository.updateTransfer(id, draft, moveOpeningBalanceBack, confirmCurrencyChange)

    suspend fun delete(id: Long) = operationRepository.deleteTransfer(id)

    private fun toItem(
        details: OperationWithDetails,
        accountMap: Map<Long, Account>,
        catMap: Map<Long, Category>,
        tree: CategoryTree,
    ): TransferItem? {
        val op = details.operation
        if (op.type != OperationType.TRANSFER || details.entries.size != 2) return null
        val sourceEntry = details.entries.singleOrNull { it.amountMinor < 0 } ?: return null
        val destEntry = details.entries.singleOrNull { it.amountMinor > 0 } ?: return null

        fun accountOf(accountId: Long, currencyCode: String): Account = accountMap[accountId] ?: Account(
            id = accountId,
            name = "?",
            currency = CurrencyRegistry.get(currencyCode),
            type = AccountType.OTHER,
            icon = null,
            color = null,
            description = null,
            status = AccountStatus.ACTIVE,
            createdAt = 0L,
        )

        val source = accountOf(sourceEntry.accountId, sourceEntry.currencyCode)
        val dest = accountOf(destEntry.accountId, destEntry.currencyCode)
        val category = op.categoryId?.let { catMap[it] }
        val categoryPath = if (category != null) tree.ancestorsOf(category.id) + category else emptyList()
        return TransferItem(
            id = op.id,
            occurredAt = op.occurredAt,
            description = op.description,
            category = category,
            categoryPath = categoryPath,
            tags = details.tags
                .map { Tag(it.id, it.name, it.color, it.createdAt) }
                .sortedBy { it.name.lowercase() },
            sourceAccount = source,
            destAccount = dest,
            sentMoney = Money(-sourceEntry.amountMinor, source.currency),
            receivedMoney = Money(destEntry.amountMinor, dest.currency),
            createdAt = op.createdAt,
            updatedAt = op.updatedAt,
        )
    }
}
