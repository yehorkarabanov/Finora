package com.yehor.finora.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.OperationWithDetails
import com.yehor.finora.data.db.TransactionQuery
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.TransactionDraft
import com.yehor.finora.domain.operation.TransactionEntryView
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionRepository @Inject constructor(
    private val db: FinoraDatabase,
    private val operationRepository: OperationRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {

    /** Account definitions without balances, so a balance change does not rebuild the lists. */
    private fun observeAccounts(): Flow<List<Account>> =
        accountRepository.observeAll()
            .map { list -> list.map { it.account } }
            .distinctUntilChanged()

    /**
     * A new [Pager] is created only when accounts or categories change. Callers should apply
     * `cachedIn` on the result.
     */
    fun observePage(
        filter: TransactionFilter,
        sort: TransactionSort,
    ): Flow<PagingData<TransactionItem>> =
        combine(categoryRepository.observeAll(), observeAccounts()) { cats, accounts -> cats to accounts }
            .flatMapLatest { (cats, accounts) ->
                val tree = CategoryTree(cats)
                val accountMap = accounts.associateBy { it.id }
                val catMap = cats.associateBy { it.id }
                val query = TransactionQuery.build(filter, sort, categoryClosure(filter, tree))
                Pager(
                    config = PagingConfig(pageSize = 50, enablePlaceholders = false),
                    pagingSourceFactory = { db.operationDao().page(query) },
                ).flow.map { pagingData ->
                    pagingData.map { mapToItem(it, accountMap, catMap, tree) }
                }
            }

    fun observeRecent(filter: TransactionFilter, limit: Int): Flow<List<TransactionItem>> =
        combine(categoryRepository.observeAll(), observeAccounts()) { cats, accounts -> cats to accounts }
            .flatMapLatest { (cats, accounts) ->
                val tree = CategoryTree(cats)
                val accountMap = accounts.associateBy { it.id }
                val catMap = cats.associateBy { it.id }
                val query = TransactionQuery.build(filter, TransactionSort.DATE_DESC, categoryClosure(filter, tree))
                db.operationDao().listWithDetails(query).map { list ->
                    list.take(limit).map { mapToItem(it, accountMap, catMap, tree) }
                }
            }

    fun observeTransaction(id: Long): Flow<TransactionItem?> =
        combine(
            db.operationDao().observeWithDetails(id),
            observeAccounts(),
            categoryRepository.observeAll(),
        ) { details, accounts, cats ->
            if (details == null) return@combine null
            mapToItem(details, accounts.associateBy { it.id }, cats.associateBy { it.id }, CategoryTree(cats))
        }

    suspend fun summarize(filter: TransactionFilter): List<CurrencySummary> {
        val tree = CategoryTree(categoryRepository.observeAll().first())
        val query = TransactionQuery.buildSummary(filter, categoryClosure(filter, tree))
        return db.operationDao().summarize(query).map { row ->
            val currency = CurrencyRegistry.findOrNull(row.currencyCode)
                ?: throw InvariantViolationException("Unknown currency ${row.currencyCode}")
            CurrencySummary(
                currency = currency,
                income = Money(row.incomeMinor, currency),
                expense = Money(row.expenseMinor, currency),
                operationCount = row.operationCount,
            )
        }
    }

    suspend fun recentCategoryIds(kind: TransactionKind, limit: Int = 5): List<Long> {
        val type = if (kind == TransactionKind.INCOME) OperationType.INCOME else OperationType.EXPENSE
        return db.operationDao().recentCategoryIds(type, limit)
    }

    suspend fun defaultAccountId(): Long? = db.operationDao().latestAccountId()

    suspend fun create(draft: TransactionDraft, moveOpeningBalanceBack: Boolean = false): Long {
        if (draft.categoryId == null) {
            throw InvariantViolationException("Category is required when creating a transaction")
        }
        val type = if (draft.kind == TransactionKind.INCOME) OperationType.INCOME else OperationType.EXPENSE
        val signedAmount = if (draft.kind == TransactionKind.INCOME) draft.amountMinor else -draft.amountMinor
        return operationRepository.insertOperation(
            type = type,
            occurredAt = draft.occurredAt,
            entries = listOf(EntryDraft(draft.accountId, signedAmount)),
            categoryId = draft.categoryId,
            description = draft.description,
            tagIds = draft.tagIds,
            newTagNames = draft.newTagNames,
            moveOpeningBalanceBack = moveOpeningBalanceBack,
        )
    }

    suspend fun update(
        id: Long,
        draft: TransactionDraft,
        moveOpeningBalanceBack: Boolean = false,
        confirmCurrencyChange: Boolean = false,
    ) {
        operationRepository.updateOperation(
            id = id,
            accountId = draft.accountId,
            amountMinor = draft.amountMinor,
            categoryId = draft.categoryId,
            occurredAt = draft.occurredAt,
            description = draft.description,
            tagIds = draft.tagIds,
            newTagNames = draft.newTagNames,
            moveOpeningBalanceBack = moveOpeningBalanceBack,
            confirmCurrencyChange = confirmCurrencyChange,
        )
    }

    suspend fun delete(id: Long) = operationRepository.deleteOperation(id)

    /** Selected categories plus all their descendants. */
    private fun categoryClosure(filter: TransactionFilter, tree: CategoryTree): Set<Long> =
        filter.categoryIds.flatMap { tree.descendantIds(it) + it }.toSet()

    private fun mapToItem(
        details: OperationWithDetails,
        accountMap: Map<Long, Account>,
        catMap: Map<Long, Category>,
        tree: CategoryTree,
    ): TransactionItem {
        val op = details.operation
        val category = op.categoryId?.let { catMap[it] }
        val categoryPath = if (category != null) tree.ancestorsOf(category.id) + category else emptyList()
        val tags = details.tags
            .map { Tag(it.id, it.name, it.color, it.createdAt) }
            .sortedBy { it.name.lowercase() }
        val entries = details.entries.sortedBy { it.id }.map { e ->
            val account = accountMap[e.accountId] ?: Account(
                id = e.accountId,
                name = "?",
                currency = CurrencyRegistry.get(e.currencyCode),
                type = AccountType.OTHER,
                icon = null,
                color = null,
                description = null,
                status = AccountStatus.ACTIVE,
                createdAt = 0L,
            )
            TransactionEntryView(account = account, money = Money(e.amountMinor, account.currency))
        }
        return TransactionItem(
            id = op.id,
            type = op.type,
            occurredAt = op.occurredAt,
            description = op.description,
            category = category,
            categoryPath = categoryPath,
            tags = tags,
            entries = entries,
            createdAt = op.createdAt,
            updatedAt = op.updatedAt,
        )
    }
}
