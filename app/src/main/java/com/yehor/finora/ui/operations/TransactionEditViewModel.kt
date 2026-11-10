package com.yehor.finora.ui.operations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.FutureDateException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.RecurringRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.ui.navigation.CREATED_CATEGORY_ID_KEY
import kotlinx.coroutines.flow.filterNotNull
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.ReassignmentDialogInfo
import com.yehor.finora.domain.operation.TransactionDateTime
import com.yehor.finora.domain.operation.TransactionDraft
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionDraftValidator
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionKind
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
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class TransactionEditUiState(
    val isEdit: Boolean = false,
    val operationId: Long = 0,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountText: String = "",
    val selectedAccountId: Long? = null,
    val originalAccountId: Long? = null,
    val originalCurrency: Currency? = null,
    val originalMoney: Money? = null,
    val selectedCategoryId: Long? = null,
    val hadCategoryBefore: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    /** True once the user picked a date or time, so tab switches know it is worth carrying over. */
    val dateTouched: Boolean = false,
    val description: String = "",
    val selectedTagIds: Set<Long> = emptySet(),
    val pendingTagNames: List<String> = emptyList(),
    val accounts: List<PickableAccount> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentCategoryIds: List<Long> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val errors: Set<TransactionDraftError> = emptySet(),
    val isOverflow: Boolean = false,
    val reassignmentInfo: ReassignmentDialogInfo? = null,
    val showOpeningOrderDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    /** The change a delete makes to the account balance. */
    val deleteBalanceChange: Money? = null,
    val saved: Boolean = false,
    /** The schedule that created this transaction, while it still exists. Only set when editing. */
    val recurringScheduleId: Long? = null,
    val recurringScheduleName: String? = null,
    /** Current balance per account id; absent when the balance overflows. */
    val balances: Map<Long, Money> = emptyMap(),
    /** True while a "save and create another" request is in flight (including its confirmation dialogs). */
    val createAnother: Boolean = false,
    /** Incremented each time "save and create another" succeeds; the screen shows a confirmation. */
    val savedAnotherCount: Int = 0,
) {
    val selectedAccount: Account?
        get() = accounts.find { it.account.id == selectedAccountId }?.account

    val currency: Currency
        get() = selectedAccount?.currency ?: accounts.firstOrNull()?.account?.currency ?: CurrencyRegistry.all.first()

    val canSave: Boolean
        get() = errors.isEmpty() && selectedAccountId != null
}

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val recurringRepository: RecurringRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    // This VM is created for every mode of the unified editor; in Transfer mode it is a fresh Expense draft.
    private val mode = EditorMode.fromArg(savedStateHandle.get<String>("kind"))

    /** The tab the route asked for; Expense/Income of an existing operation is only known once it loads. */
    val initialMode: EditorMode get() = mode
    private val operationId: Long? =
        savedStateHandle.get<Long>("operationId")?.takeIf { it > 0 && mode != EditorMode.TRANSFER }
    private val duplicateOf: Long? =
        savedStateHandle.get<Long>("duplicateOf")?.takeIf { it > 0 && mode != EditorMode.TRANSFER }
    private val initialKind: TransactionKind =
        if (mode == EditorMode.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE

    private val _state = MutableStateFlow(
        TransactionEditUiState(
            isEdit = operationId != null,
            operationId = operationId ?: 0,
            kind = initialKind,
        ),
    )

    val state: StateFlow<TransactionEditUiState> = combine(
        _state,
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        tagRepository.observeAllWithUsage(),
    ) { current, accountsWithBalance, categories, tagsWithUsage ->
        val originalId = current.originalAccountId
        val pickable = accountsWithBalance.map { it.account }
            .filter { it.status == AccountStatus.ACTIVE || it.id == originalId }
            .map { PickableAccount(it, isArchivedOriginal = it.status == AccountStatus.ARCHIVED && it.id == originalId) }
        val withAccounts = current.copy(
            accounts = pickable,
            categories = categories,
            allTags = tagsWithUsage.map { it.tag },
            balances = accountsWithBalance.mapNotNull { row ->
                (row.balance as? Balance.Value)?.let { row.account.id to it.money }
            }.toMap(),
        )
        withAccounts.copy(
            errors = TransactionDraftValidator.validate(
                amountText = current.amountText,
                accountId = current.selectedAccountId,
                categoryId = current.selectedCategoryId,
                occurredAt = instantOf(current.date, current.time),
                description = current.description,
                currency = withAccounts.currency,
                isEdit = current.isEdit,
                hadCategoryBefore = current.hadCategoryBefore,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    init {
        viewModelScope.launch { loadInitialData() }
        // Category created from the picker: select it once it shows up in the categories flow.
        viewModelScope.launch {
            combine(
                savedStateHandle.getStateFlow<Long?>(CREATED_CATEGORY_ID_KEY, null),
                categoryRepository.observeAll(),
            ) { id, categories -> categories.firstOrNull { it.id == id } }
                .filterNotNull()
                .collect { created ->
                    if (created.type == CategoryType.TRANSFER) return@collect // the transfer VM handles it
                    val kind = if (created.type == CategoryType.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE
                    if (_state.value.kind == kind) onCategoryChange(created.id)
                    savedStateHandle.remove<Long>(CREATED_CATEGORY_ID_KEY)
                }
        }
    }

    private suspend fun loadInitialData() {
        val source = (operationId ?: duplicateOf)?.let { transactionRepository.observeTransaction(it).first() }
        if (source != null) {
            applySource(source)
            if (operationId != null) {
                source.recurringScheduleId?.let { scheduleId ->
                    val name = recurringRepository.observeSchedule(scheduleId).first()?.name
                    if (name != null) {
                        _state.value = _state.value.copy(recurringScheduleId = scheduleId, recurringScheduleName = name)
                    }
                }
            }
        } else {
            transactionRepository.defaultAccountId()?.let { accountId ->
                _state.value = _state.value.copy(selectedAccountId = accountId)
            }
        }
        loadRecentCategories(_state.value.kind)
    }

    private fun applySource(item: TransactionItem) {
        val entry = item.entries.first()
        val kind = if (item.type == OperationType.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE
        val magnitude = Money(Math.abs(entry.money.amountMinor), entry.money.currency)
        val common = _state.value.copy(
            kind = kind,
            amountText = magnitude.toPlainString(),
            selectedCategoryId = item.category?.id,
            description = item.description.orEmpty(),
            selectedTagIds = item.tags.map { it.id }.toSet(),
        )
        _state.value = if (operationId != null) {
            val split = TransactionDateTime.split(item.occurredAt, ZoneId.systemDefault())
            common.copy(
                selectedAccountId = entry.account.id,
                originalAccountId = entry.account.id,
                originalCurrency = entry.account.currency,
                originalMoney = magnitude,
                hadCategoryBefore = item.category != null,
                date = split.date,
                time = split.time,
                deleteBalanceChange = -entry.money,
            )
        } else {
            // Duplicate: date and time default to now, and an archived account is not carried over.
            common.copy(selectedAccountId = entry.account.takeIf { it.status == AccountStatus.ACTIVE }?.id)
        }
    }

    private suspend fun loadRecentCategories(kind: TransactionKind) {
        _state.value = _state.value.copy(recentCategoryIds = transactionRepository.recentCategoryIds(kind))
    }

    fun onKindChange(newKind: TransactionKind) {
        if (_state.value.isEdit || _state.value.kind == newKind) return
        _state.value = _state.value.copy(kind = newKind, selectedCategoryId = null)
        viewModelScope.launch { loadRecentCategories(newKind) }
    }

    fun onAmountChange(text: String) {
        _state.value = _state.value.copy(amountText = text, isOverflow = false)
    }

    fun onAccountChange(accountId: Long) {
        _state.value = _state.value.copy(selectedAccountId = accountId, isOverflow = false)
    }

    fun onCategoryChange(categoryId: Long?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
    }

    fun onDateChange(date: LocalDate) {
        _state.value = _state.value.copy(date = date, dateTouched = true)
    }

    fun onTimeChange(time: LocalTime) {
        _state.value = _state.value.copy(time = time, dateTouched = true)
    }

    /**
     * Carries over what was typed on the Transfer tab. The amount and description only fill empty fields; the
     * account replaces the default one, and a date/time is passed only when the user deliberately picked it there.
     */
    fun prefill(amountText: String, accountId: Long?, date: LocalDate?, time: LocalTime?, description: String) {
        val s = _state.value
        _state.value = s.copy(
            amountText = if (s.amountText.isEmpty()) amountText else s.amountText,
            selectedAccountId = accountId ?: s.selectedAccountId,
            date = date ?: s.date,
            time = time ?: s.time,
            description = if (s.description.isEmpty()) description else s.description,
        )
    }

    fun onDescriptionChange(description: String) {
        _state.value = _state.value.copy(description = description)
    }

    fun onToggleTag(tagId: Long) {
        val current = _state.value.selectedTagIds
        _state.value = _state.value.copy(
            selectedTagIds = if (tagId in current) current - tagId else current + tagId,
        )
    }

    fun onAddPendingTag(name: String) {
        val normalized = TagNameRules.normalize(name)
        if (TagNameRules.validate(normalized) != null) return
        val key = TagNameRules.key(normalized)
        if (_state.value.pendingTagNames.none { TagNameRules.key(it) == key }) {
            _state.value = _state.value.copy(pendingTagNames = _state.value.pendingTagNames + normalized)
        }
    }

    fun onRemovePendingTag(name: String) {
        _state.value = _state.value.copy(pendingTagNames = _state.value.pendingTagNames - name)
    }

    /** Saves the form. With [createAnother] (new transactions only) the form is reset for the next entry instead of closing. */
    fun save(createAnother: Boolean = false) {
        _state.value = _state.value.copy(createAnother = createAnother && !_state.value.isEdit)
        submit()
    }

    fun onConfirmCurrencyChange() = submit(confirmCurrencyChange = true)

    fun onConfirmOpeningOrder() {
        onDismissOpeningOrder()
        // The currency check runs before the opening-order check, so a currency
        // change has already been confirmed when this dialog appears.
        submit(confirmCurrencyChange = true, moveOpeningBalanceBack = true)
    }

    private fun submit(confirmCurrencyChange: Boolean = false, moveOpeningBalanceBack: Boolean = false) {
        val current = state.value
        if (!current.canSave) return
        val account = current.selectedAccount ?: return
        val parsed = AmountParser.parse(current.amountText, account.currency) as? AmountParseResult.Value ?: return

        val draft = TransactionDraft(
            kind = current.kind,
            accountId = account.id,
            amountMinor = parsed.minor,
            categoryId = current.selectedCategoryId,
            occurredAt = instantOf(current.date, current.time),
            description = current.description,
            tagIds = current.selectedTagIds,
            newTagNames = current.pendingTagNames,
        )

        viewModelScope.launch {
            try {
                if (current.isEdit) {
                    transactionRepository.update(
                        id = current.operationId,
                        draft = draft,
                        moveOpeningBalanceBack = moveOpeningBalanceBack,
                        confirmCurrencyChange = confirmCurrencyChange,
                    )
                } else {
                    transactionRepository.create(draft, moveOpeningBalanceBack)
                }
                if (current.createAnother) {
                    resetForNextEntry()
                } else {
                    _state.value = _state.value.copy(saved = true, reassignmentInfo = null, showOpeningOrderDialog = false)
                }
            } catch (_: CurrencyChangeNotConfirmedException) {
                val oldMoney = current.originalMoney ?: return@launch
                _state.value = _state.value.copy(
                    reassignmentInfo = ReassignmentDialogInfo(
                        oldFormatted = "${oldMoney.toPlainString()} ${oldMoney.currency.code}",
                        newFormatted = "${Money(parsed.minor, account.currency).toPlainString()} ${account.currency.code}",
                        oldAccountName = current.accounts.find { it.account.id == current.originalAccountId }?.account?.name.orEmpty(),
                        newAccountName = account.name,
                        isExpense = current.kind == TransactionKind.EXPENSE,
                    ),
                )
            } catch (_: OpeningBalanceOrderException) {
                _state.value = _state.value.copy(showOpeningOrderDialog = true, reassignmentInfo = null)
            } catch (_: MoneyOverflowException) {
                _state.value = _state.value.copy(isOverflow = true, reassignmentInfo = null, showOpeningOrderDialog = false)
            } catch (_: FutureDateException) {
                // The form validator rejects future dates; this only happens when the clock passes the chosen minute.
            }
        }
    }

    /** Keeps kind, account and date (batch entry usually shares them) and clears the rest. */
    private suspend fun resetForNextEntry() {
        _state.value = _state.value.copy(
            amountText = "",
            selectedCategoryId = null,
            description = "",
            selectedTagIds = emptySet(),
            pendingTagNames = emptyList(),
            time = LocalTime.now().withSecond(0).withNano(0),
            isOverflow = false,
            reassignmentInfo = null,
            showOpeningOrderDialog = false,
            createAnother = false,
            savedAnotherCount = _state.value.savedAnotherCount + 1,
        )
        loadRecentCategories(_state.value.kind)
    }

    fun onDismissReassignment() {
        _state.value = _state.value.copy(reassignmentInfo = null)
    }

    fun onDismissOpeningOrder() {
        _state.value = _state.value.copy(showOpeningOrderDialog = false)
    }

    fun onDeleteRequest() {
        _state.value = _state.value.copy(showDeleteConfirm = true)
    }

    fun onDismissDelete() {
        _state.value = _state.value.copy(showDeleteConfirm = false)
    }

    fun onConfirmDelete() {
        val id = _state.value.operationId
        viewModelScope.launch {
            transactionRepository.delete(id)
            _state.value = _state.value.copy(saved = true, showDeleteConfirm = false)
        }
    }

    private fun instantOf(date: LocalDate, time: LocalTime): Long =
        TransactionDateTime.combine(
            TransactionDateTime.pickerUtcMidnight(date),
            time.hour,
            time.minute,
            ZoneId.systemDefault(),
        )
}
