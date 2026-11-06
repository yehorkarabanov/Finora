package com.yehor.finora.ui.recurring

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.RecurringRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.RecurrenceCalculator
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.ScheduleDates
import com.yehor.finora.domain.recurring.ScheduleDraft
import com.yehor.finora.domain.recurring.ScheduleDraftError
import com.yehor.finora.domain.recurring.ScheduleStatus
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class RecurringEditUiState(
    val form: RecurringFormState = RecurringFormState(),
    val loading: Boolean = true,
    val accounts: List<PickableAccount> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentCategoryIds: List<Long> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val errors: Set<ScheduleDraftError> = emptySet(),
    /** Set when an edit would start at or before an occurrence that was already handled. */
    val handledConflict: LocalDate? = null,
    val preview: FormPreview = FormPreview.None,
    val isOverflow: Boolean = false,
    val gate: SaveGate? = null,
    val showOpeningOrderDialog: Boolean = false,
    val showArchivedError: Boolean = false,
    val saved: Boolean = false,
) {
    val isEdit: Boolean get() = form.isEdit

    val selectedAccount: Account?
        get() = accounts.find { it.account.id == form.accountId }?.account

    val currency: Currency
        get() = selectedAccount?.currency ?: accounts.firstOrNull()?.account?.currency ?: CurrencyRegistry.all.first()

    val canSave: Boolean
        get() = !loading && form.accountId != null && errors.isEmpty() && handledConflict == null
}

@HiltViewModel
class RecurringEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RecurringRepository,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    private val scheduleId: Long? = savedStateHandle.get<Long>("scheduleId")?.takeIf { it > 0 }

    private val form = MutableStateFlow(RecurringFormState(scheduleId = scheduleId ?: 0))
    private val loading = MutableStateFlow(true)
    private val recentCategoryIds = MutableStateFlow<List<Long>>(emptyList())
    private val flags = MutableStateFlow(Flags())

    private data class Flags(
        val overflow: Boolean = false,
        val gate: SaveGate? = null,
        val openingOrder: Boolean = false,
        val archivedError: Boolean = false,
        val saved: Boolean = false,
    )

    /** Confirmations the user has given since the last edit of the form. Nothing is written before all pass. */
    private var confirmed = SaveConfirmations()

    private val writer = object : ScheduleWriter {
        override suspend fun create(draft: ScheduleDraft, moveOpeningBalanceBack: Boolean) {
            repository.create(draft, moveOpeningBalanceBack)
        }

        override suspend fun update(
            id: Long,
            draft: ScheduleDraft,
            moveOpeningBalanceBack: Boolean,
            confirmCurrencyChange: Boolean,
        ) {
            repository.update(id, draft, moveOpeningBalanceBack, confirmCurrencyChange)
        }
    }

    val state: StateFlow<RecurringEditUiState> = combine(
        combine(form, loading, recentCategoryIds, flags) { f, l, r, fl -> Quad(f, l, r, fl) },
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        tagRepository.observeAllWithUsage(),
    ) { (current, isLoading, recents, fl), accountsWithBalance, categories, tagsWithUsage ->
        val originalId = current.original?.accountId
        val pickable = accountsWithBalance.map { it.account }
            .filter { it.status == AccountStatus.ACTIVE || it.id == originalId }
            .map { PickableAccount(it, isArchivedOriginal = it.status == AccountStatus.ARCHIVED && it.id == originalId) }
        val selected = pickable.find { it.account.id == current.accountId }?.account
        val effective = current.copy(accountArchived = selected?.status == AccountStatus.ARCHIVED)
        val currency = selected?.currency ?: pickable.firstOrNull()?.account?.currency ?: CurrencyRegistry.all.first()
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        RecurringEditUiState(
            form = effective,
            loading = isLoading,
            accounts = pickable,
            categories = categories,
            recentCategoryIds = recents,
            allTags = tagsWithUsage.map { it.tag },
            errors = RecurringFormLogic.errors(effective, currency, now, zone),
            handledConflict = RecurringFormLogic.handledConflict(effective),
            preview = RecurringFormLogic.preview(effective, RecurringFormLogic.today(now, zone)),
            isOverflow = fl.overflow,
            gate = fl.gate,
            showOpeningOrderDialog = fl.openingOrder,
            showArchivedError = fl.archivedError,
            saved = fl.saved,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecurringEditUiState())

    private data class Quad(val form: RecurringFormState, val loading: Boolean, val recents: List<Long>, val flags: Flags)

    init {
        viewModelScope.launch { loadInitialData() }
    }

    private suspend fun loadInitialData() {
        if (scheduleId != null) {
            val schedule = repository.observeSchedule(scheduleId).first()
            if (schedule != null) {
                val lastHandled = repository.lastHandledDate(scheduleId)
                val anchor = ScheduleDates.Anchor(schedule.startDate, schedule.rule, schedule.endDate, schedule.nextDate)
                // An ended schedule has no next date; show the date its sequence would continue with.
                val shownDate = schedule.nextDate
                    ?: lastHandled?.let { RecurrenceCalculator.firstAfter(schedule.startDate, schedule.rule, it) }
                    ?: schedule.startDate
                form.value = form.value.copy(
                    kind = schedule.kind,
                    name = schedule.name,
                    amountText = schedule.money.toPlainString(),
                    accountId = schedule.account.id,
                    categoryId = schedule.category?.id,
                    hadCategoryBefore = schedule.category != null,
                    selectedTagIds = schedule.tags.map { it.id }.toSet(),
                    frequency = FrequencyInput.of(schedule.rule),
                    firstDate = shownDate,
                    endDate = schedule.endDate,
                    original = OriginalSchedule(
                        accountId = schedule.account.id,
                        currency = schedule.account.currency,
                        money = schedule.money,
                        anchor = anchor,
                        lastHandled = lastHandled,
                        active = schedule.status == ScheduleStatus.ACTIVE,
                    ),
                )
            }
        }
        loadRecentCategories(form.value.kind)
        loading.value = false
    }

    private suspend fun loadRecentCategories(kind: TransactionKind) {
        recentCategoryIds.value = transactionRepository.recentCategoryIds(kind)
    }

    /** Any change of the form invalidates earlier answers to the save dialogs. */
    private fun edit(transform: (RecurringFormState) -> RecurringFormState) {
        confirmed = SaveConfirmations()
        form.value = transform(form.value)
        flags.value = flags.value.copy(overflow = false)
    }

    fun onKindChange(kind: TransactionKind) {
        val current = form.value
        if (current.typeLocked || current.kind == kind) return
        edit { it.copy(kind = kind, categoryId = null) }
        viewModelScope.launch { loadRecentCategories(kind) }
    }

    fun onNameChange(text: String) = edit { it.copy(name = text) }

    fun onAmountChange(text: String) = edit { it.copy(amountText = text) }

    fun onAccountChange(accountId: Long) = edit { it.copy(accountId = accountId) }

    fun onCategoryChange(categoryId: Long?) = edit { it.copy(categoryId = categoryId) }

    fun onToggleTag(tagId: Long) = edit {
        it.copy(selectedTagIds = if (tagId in it.selectedTagIds) it.selectedTagIds - tagId else it.selectedTagIds + tagId)
    }

    fun onAddPendingTag(name: String) {
        val normalized = TagNameRules.normalize(name)
        if (TagNameRules.validate(normalized) != null) return
        val key = TagNameRules.key(normalized)
        if (form.value.pendingTagNames.none { TagNameRules.key(it) == key }) {
            edit { it.copy(pendingTagNames = it.pendingTagNames + normalized) }
        }
    }

    fun onRemovePendingTag(name: String) = edit { it.copy(pendingTagNames = it.pendingTagNames - name) }

    fun onPresetChange(preset: FrequencyPreset) = edit { it.copy(frequency = it.frequency.select(preset)) }

    fun onCustomUnitChange(unit: RecurrenceUnit) = edit { it.copy(frequency = it.frequency.copy(customUnit = unit)) }

    fun onCustomIntervalChange(text: String) = edit {
        it.copy(frequency = it.frequency.copy(customIntervalText = text.filter(Char::isDigit).take(3)))
    }

    fun onFirstDateChange(date: LocalDate) = edit { it.copy(firstDate = date) }

    fun onEndDateChange(date: LocalDate?) = edit { it.copy(endDate = date) }

    fun save() = runSave()

    private fun runSave() {
        val current = state.value
        if (!current.canSave) return
        viewModelScope.launch {
            val result = RecurringFormLogic.save(
                state = current.form,
                currency = current.currency,
                now = System.currentTimeMillis(),
                zone = ZoneId.systemDefault(),
                confirmed = confirmed,
                writer = writer,
            )
            flags.value = when (result) {
                SaveResult.Saved -> Flags(saved = true)
                is SaveResult.Gate -> Flags(gate = result.gate)
                SaveResult.OpeningOrder -> Flags(openingOrder = true)
                SaveResult.Overflow -> Flags(overflow = true)
                SaveResult.AccountArchived -> Flags(archivedError = true)
                SaveResult.Invalid -> Flags()
            }
        }
    }

    fun onConfirmGate() {
        val gate = flags.value.gate ?: return
        confirmed = when (gate) {
            is SaveGate.CurrencyChange -> confirmed.copy(currencyChange = true)
            is SaveGate.PastOccurrences -> confirmed.copy(pastOccurrences = true)
        }
        flags.value = Flags()
        runSave()
    }

    fun onDismissGate() {
        flags.value = Flags()
    }

    fun onConfirmOpeningOrder() {
        // The currency and past-occurrence gates come first, so both have been answered already.
        confirmed = confirmed.copy(moveOpeningBalanceBack = true)
        flags.value = Flags()
        runSave()
    }

    fun onDismissOpeningOrder() {
        flags.value = Flags()
    }

    fun onDismissArchivedError() {
        flags.value = Flags()
    }
}
