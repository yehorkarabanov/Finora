package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.RecurringOccurrenceEntity
import com.yehor.finora.data.db.RecurringScheduleEntity
import com.yehor.finora.data.db.RecurringScheduleTagEntity
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.OccurrenceItem
import com.yehor.finora.domain.recurring.OccurrenceStatus
import com.yehor.finora.domain.recurring.RecurrenceCalculator
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDates
import com.yehor.finora.domain.recurring.ScheduleDraft
import com.yehor.finora.domain.recurring.ScheduleDraftValidator
import com.yehor.finora.domain.recurring.ScheduleStatus
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecurringRepository(
    private val db: FinoraDatabase,
    private val operationRepo: OperationRepository,
    private val accountRepo: AccountRepository,
    private val categoryRepo: CategoryRepository,
    private val tagRepo: TagRepository,
    private val clock: () -> Long,
    private val zoneProvider: () -> ZoneId,
) {
    @Inject
    constructor(
        db: FinoraDatabase,
        operationRepo: OperationRepository,
        accountRepo: AccountRepository,
        categoryRepo: CategoryRepository,
        tagRepo: TagRepository,
    ) : this(
        db,
        operationRepo,
        accountRepo,
        categoryRepo,
        tagRepo,
        System::currentTimeMillis,
        ZoneId::systemDefault,
    )

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    private val _issues = MutableStateFlow<Map<Long, GenerationIssue>>(emptyMap())
    val issues: StateFlow<Map<Long, GenerationIssue>> = _issues.asStateFlow()

    fun observeSchedules(): Flow<List<RecurringSchedule>> =
        combine(
            db.recurringDao().observeSchedules(),
            accountRepo.observeAll(),
            categoryRepo.observeAll(),
            tagRepo.observeAll(),
        ) { entities, accounts, categories, tags ->
            val accountMap = accounts.associate { it.account.id to it.account }
            val catMap = categories.associateBy { it.id }
            val tree = CategoryTree(categories)
            val tagMap = tags.associateBy { it.id }

            entities.map { entity ->
                mapToSchedule(entity, accountMap, catMap, tree, tagMap)
            }
        }

    fun observeSchedule(id: Long): Flow<RecurringSchedule?> =
        combine(
            db.recurringDao().observeSchedule(id),
            accountRepo.observeAll(),
            categoryRepo.observeAll(),
            tagRepo.observeAll(),
        ) { entity, accounts, categories, tags ->
            if (entity == null) return@combine null
            val accountMap = accounts.associate { it.account.id to it.account }
            val catMap = categories.associateBy { it.id }
            val tree = CategoryTree(categories)
            val tagMap = tags.associateBy { it.id }
            mapToSchedule(entity, accountMap, catMap, tree, tagMap)
        }

    fun observeOccurrences(id: Long, limit: Int = 100): Flow<List<OccurrenceItem>> =
        combine(
            db.recurringDao().observeOccurrences(id, limit),
            // An edit of a generated transaction changes its amount, not the occurrence row.
            db.invalidationTracker.createFlow("ledger_entry"),
        ) { list, _ ->
            val operationIds = list.mapNotNull { it.operationId }
            val amounts = if (operationIds.isEmpty()) {
                emptyMap()
            } else {
                db.recurringDao().amountsOf(operationIds).associateBy { it.operationId }
            }
            list.map { entity ->
                OccurrenceItem(
                    date = LocalDate.ofEpochDay(entity.occurrenceDate),
                    status = entity.status,
                    operationId = entity.operationId,
                    handledAt = entity.handledAt,
                    money = entity.operationId?.let { amounts[it] }?.let { row ->
                        CurrencyRegistry.findOrNull(row.currencyCode)?.let { Money(row.amountMinor, it) }
                    },
                )
            }
        }

    /** Transactions that still exist and were created by this schedule. */
    suspend fun generatedCount(id: Long): Int = db.recurringDao().generatedCount(id)

    /** The latest date of any handled occurrence (generated or skipped), or null when none was handled. */
    suspend fun lastHandledDate(id: Long): LocalDate? =
        db.recurringDao().lastHandledDate(id)?.let { LocalDate.ofEpochDay(it) }

    suspend fun create(
        draft: ScheduleDraft,
        moveOpeningBalanceBack: Boolean = false,
        now: Long = clock(),
    ): Long = db.withTransaction {
        val account = db.accountDao().getById(draft.accountId)
            ?: throw InvariantViolationException("Unknown account ${draft.accountId}")
        val currency = CurrencyRegistry.get(account.currencyCode)

        val errors = ScheduleDraftValidator.validate(
            name = draft.name,
            amountText = Money(draft.amountMinor, currency).toPlainString(),
            currency = currency,
            accountId = draft.accountId,
            categoryId = draft.categoryId,
            rule = draft.rule,
            firstDate = draft.firstDate,
            endDate = draft.endDate,
            now = now,
            zone = zoneProvider(),
            isEdit = false,
            hadCategoryBefore = true,
        )
        if (errors.isNotEmpty()) {
            throw InvariantViolationException("Invalid recurring draft: $errors")
        }

        if (draft.categoryId != null) {
            val cat = db.categoryDao().getById(draft.categoryId)
                ?: throw InvariantViolationException("Unknown category ${draft.categoryId}")
            if (cat.type.name != draft.kind.name) {
                throw InvariantViolationException("Category type (${cat.type}) does not match schedule kind (${draft.kind})")
            }
        }

        val resolvedTagIds = operationRepo.resolveTagIds(draft.tagIds, draft.newTagNames, now)

        val scheduleEntity = RecurringScheduleEntity(
            name = TagNameRules.normalize(draft.name),
            kind = draft.kind,
            accountId = draft.accountId,
            amountMinor = draft.amountMinor,
            categoryId = draft.categoryId,
            unit = draft.rule.unit,
            intervalCount = draft.rule.interval,
            startDate = draft.firstDate.toEpochDay(),
            endDate = draft.endDate?.toEpochDay(),
            nextDate = draft.firstDate.toEpochDay(),
            status = ScheduleStatus.ACTIVE,
            createdAt = now,
            updatedAt = now,
        )

        val scheduleId = db.recurringDao().insertSchedule(scheduleEntity)
        if (resolvedTagIds.isNotEmpty()) {
            db.recurringDao().insertScheduleTags(
                resolvedTagIds.map { RecurringScheduleTagEntity(scheduleId, it) },
            )
        }

        generateDue(scheduleId, now, moveOpeningBalanceBack, limit = Int.MAX_VALUE)
        scheduleId
    }

    suspend fun update(
        id: Long,
        draft: ScheduleDraft,
        moveOpeningBalanceBack: Boolean = false,
        confirmCurrencyChange: Boolean = false,
        now: Long = clock(),
    ) = db.withTransaction {
        val current = db.recurringDao().getSchedule(id)
            ?: throw InvariantViolationException("Unknown schedule $id")

        if (draft.kind != current.kind) {
            throw InvariantViolationException("Schedule kind cannot be changed")
        }

        val currentAccount = db.accountDao().getById(current.accountId)
            ?: throw InvariantViolationException("Unknown account ${current.accountId}")
        val newAccount = db.accountDao().getById(draft.accountId)
            ?: throw InvariantViolationException("Unknown account ${draft.accountId}")

        if (newAccount.id != current.accountId && newAccount.status == AccountStatus.ARCHIVED) {
            throw InvariantViolationException("Cannot reassign schedule to an archived account")
        }

        if (newAccount.currencyCode != currentAccount.currencyCode && !confirmCurrencyChange) {
            throw CurrencyChangeNotConfirmedException(currentAccount.currencyCode, newAccount.currencyCode)
        }

        val currency = CurrencyRegistry.get(newAccount.currencyCode)
        val errors = ScheduleDraftValidator.validate(
            name = draft.name,
            amountText = Money(draft.amountMinor, currency).toPlainString(),
            currency = currency,
            accountId = draft.accountId,
            categoryId = draft.categoryId,
            rule = draft.rule,
            firstDate = draft.firstDate,
            endDate = draft.endDate,
            now = now,
            zone = zoneProvider(),
            isEdit = true,
            hadCategoryBefore = current.categoryId != null,
        )
        if (errors.isNotEmpty()) {
            throw InvariantViolationException("Invalid recurring draft: $errors")
        }

        if (draft.categoryId != null) {
            val cat = db.categoryDao().getById(draft.categoryId)
                ?: throw InvariantViolationException("Unknown category ${draft.categoryId}")
            if (cat.type.name != draft.kind.name) {
                throw InvariantViolationException("Category type (${cat.type}) does not match schedule kind (${draft.kind})")
            }
        }

        val lastHandled = db.recurringDao().lastHandledDate(id)?.let { LocalDate.ofEpochDay(it) }
        val currentAnchor = ScheduleDates.Anchor(
            startDate = LocalDate.ofEpochDay(current.startDate),
            rule = RecurrenceRule(current.unit, current.intervalCount),
            endDate = current.endDate?.let { LocalDate.ofEpochDay(it) },
            nextDate = current.nextDate?.let { LocalDate.ofEpochDay(it) },
        )
        val edited = ScheduleDates.Edited(
            firstDate = draft.firstDate,
            rule = draft.rule,
            endDate = draft.endDate,
        )

        val rescheduleResult = ScheduleDates.reschedule(currentAnchor, edited, lastHandled)
        val (newStartDate, newNextDate) = when (rescheduleResult) {
            is ScheduleDates.Result.Accepted -> rescheduleResult.startDate to rescheduleResult.nextDate
            is ScheduleDates.Result.Rejected -> throw InvariantViolationException("Reschedule rejected: ${rescheduleResult.reason}")
        }

        val resolvedTagIds = operationRepo.resolveTagIds(draft.tagIds, draft.newTagNames, now)
        db.recurringDao().deleteTagsOf(id)
        if (resolvedTagIds.isNotEmpty()) {
            db.recurringDao().insertScheduleTags(
                resolvedTagIds.map { RecurringScheduleTagEntity(id, it) },
            )
        }

        val updatedEntity = current.copy(
            name = TagNameRules.normalize(draft.name),
            accountId = draft.accountId,
            amountMinor = draft.amountMinor,
            categoryId = draft.categoryId,
            unit = draft.rule.unit,
            intervalCount = draft.rule.interval,
            startDate = newStartDate.toEpochDay(),
            endDate = draft.endDate?.toEpochDay(),
            nextDate = newNextDate?.toEpochDay(),
            updatedAt = now,
        )
        db.recurringDao().updateSchedule(updatedEntity)

        // A schedule that keeps its archived account stays pending (R12); the edit itself must still work.
        if (current.status == ScheduleStatus.ACTIVE && newNextDate != null && newAccount.status != AccountStatus.ARCHIVED) {
            generateDue(id, now, moveOpeningBalanceBack, limit = Int.MAX_VALUE)
        }
    }

    suspend fun pause(id: Long, now: Long = clock()) = db.withTransaction {
        val schedule = db.recurringDao().getSchedule(id)
            ?: throw InvariantViolationException("Unknown schedule $id")
        if (schedule.nextDate == null) {
            throw InvariantViolationException("Cannot pause ended schedule")
        }
        db.recurringDao().setStatus(id, ScheduleStatus.PAUSED, now)
    }

    suspend fun resume(
        id: Long,
        catchUp: Boolean,
        moveOpeningBalanceBack: Boolean = false,
        now: Long = clock(),
    ) = db.withTransaction {
        val schedule = db.recurringDao().getSchedule(id)
            ?: throw InvariantViolationException("Unknown schedule $id")
        db.recurringDao().setStatus(id, ScheduleStatus.ACTIVE, now)

        if (schedule.nextDate == null) return@withTransaction

        val zone = zoneProvider()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        if (catchUp) {
            generateDue(id, now, moveOpeningBalanceBack, limit = Int.MAX_VALUE)
        } else {
            var curDate = LocalDate.ofEpochDay(schedule.nextDate)
            val rule = RecurrenceRule(schedule.unit, schedule.intervalCount)
            val anchor = LocalDate.ofEpochDay(schedule.startDate)
            val end = schedule.endDate?.let { LocalDate.ofEpochDay(it) }

            while (curDate <= today) {
                db.recurringDao().insertOccurrence(
                    RecurringOccurrenceEntity(
                        scheduleId = id,
                        occurrenceDate = curDate.toEpochDay(),
                        status = OccurrenceStatus.SKIPPED,
                        operationId = null,
                        handledAt = now,
                    ),
                )
                val n = RecurrenceCalculator.next(anchor, rule, curDate, end)
                if (n == null) {
                    db.recurringDao().updateNextDate(id, null)
                    break
                }
                curDate = n
            }
            if (curDate > today) {
                db.recurringDao().updateNextDate(id, curDate.toEpochDay())
            }
        }
    }

    suspend fun skipNext(id: Long, now: Long = clock()) = db.withTransaction {
        val schedule = db.recurringDao().getSchedule(id)
            ?: throw InvariantViolationException("Unknown schedule $id")
        if (schedule.nextDate == null) {
            throw InvariantViolationException("Cannot skip ended schedule")
        }
        val curDate = LocalDate.ofEpochDay(schedule.nextDate)
        db.recurringDao().insertOccurrence(
            RecurringOccurrenceEntity(
                scheduleId = id,
                occurrenceDate = curDate.toEpochDay(),
                status = OccurrenceStatus.SKIPPED,
                operationId = null,
                handledAt = now,
            ),
        )
        val rule = RecurrenceRule(schedule.unit, schedule.intervalCount)
        val anchor = LocalDate.ofEpochDay(schedule.startDate)
        val end = schedule.endDate?.let { LocalDate.ofEpochDay(it) }
        val next = RecurrenceCalculator.next(anchor, rule, curDate, end)
        db.recurringDao().updateNextDate(id, next?.toEpochDay())
    }

    suspend fun skipBlocked(id: Long, now: Long = clock()) = db.withTransaction {
        val schedule = db.recurringDao().getSchedule(id)
            ?: throw InvariantViolationException("Unknown schedule $id")
        if (schedule.nextDate == null) return@withTransaction

        val zone = zoneProvider()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        var curDate = LocalDate.ofEpochDay(schedule.nextDate)
        val rule = RecurrenceRule(schedule.unit, schedule.intervalCount)
        val anchor = LocalDate.ofEpochDay(schedule.startDate)
        val end = schedule.endDate?.let { LocalDate.ofEpochDay(it) }

        while (curDate <= today) {
            db.recurringDao().insertOccurrence(
                RecurringOccurrenceEntity(
                    scheduleId = id,
                    occurrenceDate = curDate.toEpochDay(),
                    status = OccurrenceStatus.SKIPPED,
                    operationId = null,
                    handledAt = now,
                ),
            )
            val n = RecurrenceCalculator.next(anchor, rule, curDate, end)
            if (n == null) {
                db.recurringDao().updateNextDate(id, null)
                break
            }
            curDate = n
        }
        if (curDate > today) {
            db.recurringDao().updateNextDate(id, curDate.toEpochDay())
        }
        _issues.update { it - id }
    }

    /**
     * The user-confirmed action for [GenerationIssue.BEFORE_OPENING_BALANCE]: creates everything
     * that is due and moves the account's opening balance back where needed (design 6.4).
     */
    suspend fun generateBlocked(id: Long, now: Long = clock()) = mutex.withLock {
        var handled: Int
        do {
            handled = generateDue(id, now, moveOpeningBalanceBack = true, limit = 100)
        } while (handled == 100)
        _issues.update { it - id }
    }

    suspend fun delete(id: Long) = db.withTransaction {
        db.recurringDao().deleteSchedule(id)
        _issues.update { it - id }
    }

    internal suspend fun generateDue(
        id: Long,
        now: Long,
        moveOpeningBalanceBack: Boolean,
        limit: Int = 100,
    ): Int = db.withTransaction {
        val schedule = db.recurringDao().getSchedule(id) ?: return@withTransaction 0
        if (schedule.status != ScheduleStatus.ACTIVE || schedule.nextDate == null) {
            return@withTransaction 0
        }

        val zone = zoneProvider()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

        val account = db.accountDao().getById(schedule.accountId)
            ?: throw InvariantViolationException("Unknown account ${schedule.accountId}")
        if (account.status == AccountStatus.ARCHIVED) {
            throw ScheduleBlockedException(GenerationIssue.ACCOUNT_ARCHIVED)
        }

        var handledCount = 0
        var curNextEpoch: Long? = schedule.nextDate
        val rule = RecurrenceRule(schedule.unit, schedule.intervalCount)
        val anchor = LocalDate.ofEpochDay(schedule.startDate)
        val end = schedule.endDate?.let { LocalDate.ofEpochDay(it) }
        val tagIds = db.recurringDao().tagIdsOf(id).toSet()

        val type = if (schedule.kind == TransactionKind.INCOME) OperationType.INCOME else OperationType.EXPENSE
        val signedAmount = if (schedule.kind == TransactionKind.INCOME) schedule.amountMinor else -schedule.amountMinor

        while (curNextEpoch != null && curNextEpoch <= today.toEpochDay() && handledCount < limit) {
            val curDate = LocalDate.ofEpochDay(curNextEpoch)
            val occurredAt = RecurrenceCalculator.startOfDay(curDate, zone)

            val occurrenceId = db.recurringDao().insertOccurrence(
                RecurringOccurrenceEntity(
                    scheduleId = id,
                    occurrenceDate = curNextEpoch,
                    status = OccurrenceStatus.GENERATED,
                    operationId = null,
                    handledAt = now,
                ),
            )

            if (occurrenceId != -1L) {
                val opId = operationRepo.insertOperation(
                    type = type,
                    occurredAt = occurredAt,
                    entries = listOf(EntryDraft(schedule.accountId, signedAmount)),
                    categoryId = schedule.categoryId,
                    description = schedule.name,
                    tagIds = tagIds,
                    moveOpeningBalanceBack = moveOpeningBalanceBack,
                    now = now,
                )
                db.recurringDao().setOccurrenceOperation(occurrenceId, opId)
            }

            handledCount++
            val nextDate = RecurrenceCalculator.next(anchor, rule, curDate, end)
            curNextEpoch = nextDate?.toEpochDay()
        }

        db.recurringDao().updateNextDate(id, curNextEpoch)
        handledCount
    }

    suspend fun generateAllDue(now: Long = clock()) = mutex.withLock {
        val zone = zoneProvider()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val due = db.recurringDao().dueSchedules(today.toEpochDay())

        for (s in due) {
            try {
                var handled: Int
                do {
                    handled = generateDue(s.id, now, moveOpeningBalanceBack = false, limit = 100)
                } while (handled == 100)
                _issues.update { it - s.id }
            } catch (e: OpeningBalanceOrderException) {
                _issues.update { it + (s.id to GenerationIssue.BEFORE_OPENING_BALANCE) }
            } catch (e: ScheduleBlockedException) {
                _issues.update { it + (s.id to e.issue) }
            } catch (e: MoneyOverflowException) {
                _issues.update { it + (s.id to GenerationIssue.BALANCE_OVERFLOW) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Log and retry on next run
            }
        }
    }

    fun start(scope: CoroutineScope) {
        this.scope = scope
        scope.launch {
            while (true) {
                generateAllDue()
                val now = clock()
                val zone = zoneProvider()
                val currentLocal = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                val nextMidnight = currentLocal.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + 1000L
                val delayMs = maxOf(1000L, nextMidnight - now)
                delay(delayMs)
            }
        }
    }

    fun requestGenerate() {
        scope?.launch {
            generateAllDue()
        }
    }

    private suspend fun mapToSchedule(
        entity: RecurringScheduleEntity,
        accountMap: Map<Long, Account>,
        catMap: Map<Long, Category>,
        tree: CategoryTree,
        tagMap: Map<Long, Tag>,
    ): RecurringSchedule {
        val account = accountMap[entity.accountId] ?: Account(
            id = entity.accountId,
            name = "?",
            currency = CurrencyRegistry.all.first(),
            type = AccountType.OTHER,
            icon = null,
            color = null,
            description = null,
            status = AccountStatus.ACTIVE,
            createdAt = 0L,
        )
        val category = entity.categoryId?.let { catMap[it] }
        val categoryPath = if (category != null) tree.ancestorsOf(category.id) + category else emptyList()
        val tagIds = db.recurringDao().tagIdsOf(entity.id)
        val tags = tagIds.mapNotNull { tagMap[it] }.sortedBy { it.name.lowercase() }

        return RecurringSchedule(
            id = entity.id,
            name = entity.name,
            kind = entity.kind,
            account = account,
            money = Money(entity.amountMinor, account.currency),
            category = category,
            categoryPath = categoryPath,
            tags = tags,
            rule = RecurrenceRule(entity.unit, entity.intervalCount),
            startDate = LocalDate.ofEpochDay(entity.startDate),
            endDate = entity.endDate?.let { LocalDate.ofEpochDay(it) },
            nextDate = entity.nextDate?.let { LocalDate.ofEpochDay(it) },
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}
