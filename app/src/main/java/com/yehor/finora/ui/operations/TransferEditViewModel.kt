package com.yehor.finora.ui.operations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.FutureDateException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransferRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.EffectiveRate
import com.yehor.finora.domain.operation.TransactionDateTime
import com.yehor.finora.domain.operation.TransferAmountParser
import com.yehor.finora.domain.operation.TransferDraft
import com.yehor.finora.domain.operation.TransferDraftError
import com.yehor.finora.domain.operation.TransferDraftValidator
import com.yehor.finora.domain.operation.TransferItem
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

data class TransferEditUiState(
    val isEdit: Boolean = false,
    val operationId: Long = 0,
    val sourceAccountId: Long? = null,
    val destAccountId: Long? = null,
    val originalSourceId: Long? = null,
    val originalDestId: Long? = null,
    val originalSentMoney: Money? = null,
    val originalReceivedMoney: Money? = null,
    val sentText: String = "",
    val receivedText: String = "",
    val selectedCategoryId: Long? = null,
    val hadCategoryBefore: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val description: String = "",
    val selectedTagIds: Set<Long> = emptySet(),
    val pendingTagNames: List<String> = emptyList(),
    val allAccounts: List<Account> = emptyList(),
    val sourceChoices: List<PickableAccount> = emptyList(),
    val destChoices: List<PickableAccount> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentCategoryIds: List<Long> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val errors: Set<TransferDraftError> = emptySet(),
    val effectiveRate: EffectiveRate? = null,
    val isOverflow: Boolean = false,
    /** Lines such as `100.00 PLN → 100.00 USD`, set when the T12 currency confirmation is needed. */
    val currencyChangeLines: List<String>? = null,
    val showOpeningOrderDialog: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val deleteSentBack: Money? = null,
    val deleteReceivedRemoved: Money? = null,
    val saved: Boolean = false,
) {
    val sourceAccount: Account? get() = allAccounts.find { it.id == sourceAccountId }
    val destAccount: Account? get() = allAccounts.find { it.id == destAccountId }
    val canSave: Boolean get() = errors.isEmpty()
}

@HiltViewModel
class TransferEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transferRepository: TransferRepository,
    private val accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    tagRepository: TagRepository,
) : ViewModel() {

    private val operationId: Long? = savedStateHandle.get<Long>("operationId")?.takeIf { it > 0 }
    private val duplicateOf: Long? = savedStateHandle.get<Long>("duplicateOf")?.takeIf { it > 0 }

    private val _state = MutableStateFlow(
        TransferEditUiState(isEdit = operationId != null, operationId = operationId ?: 0),
    )

    val state: StateFlow<TransferEditUiState> = combine(
        _state,
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        tagRepository.observeAllWithUsage(),
    ) { current, accountsWithBalance, categories, tagsWithUsage ->
        val accounts = accountsWithBalance.map { it.account }
        fun choices(originalId: Long?) = accounts
            .filter { it.status == AccountStatus.ACTIVE || it.id == originalId }
            .map { PickableAccount(it, isArchivedOriginal = it.status == AccountStatus.ARCHIVED && it.id == originalId) }

        val sourceCurrency = accounts.find { it.id == current.sourceAccountId }?.currency
        val destCurrency = accounts.find { it.id == current.destAccountId }?.currency
        current.copy(
            allAccounts = accounts,
            sourceChoices = choices(current.originalSourceId),
            destChoices = choices(current.originalDestId),
            categories = categories,
            allTags = tagsWithUsage.map { it.tag },
            effectiveRate = TransferFormLogic.effectiveRate(current.sentText, current.receivedText, sourceCurrency, destCurrency),
            errors = TransferDraftValidator.validate(
                sentText = current.sentText,
                receivedText = current.receivedText,
                sourceAccountId = current.sourceAccountId,
                destAccountId = current.destAccountId,
                sourceCurrency = sourceCurrency,
                destCurrency = destCurrency,
                categoryId = current.selectedCategoryId,
                occurredAt = instantOf(current.date, current.time),
                description = current.description,
                isEdit = current.isEdit,
                hadCategoryBefore = current.hadCategoryBefore,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    init {
        viewModelScope.launch { loadInitialData() }
    }

    private suspend fun loadInitialData() {
        val source = (operationId ?: duplicateOf)?.let { transferRepository.observeTransfer(it).first() }
        val recent = transferRepository.recentCategoryIds()
        if (source != null) {
            applySource(source)
        } else {
            val active = accountRepository.observeAll().first().map { it.account }
                .filter { it.status == AccountStatus.ACTIVE }
            _state.value = _state.value.copy(
                sourceAccountId = active.getOrNull(0)?.id,
                destAccountId = active.getOrNull(1)?.id,
                selectedCategoryId = recent.firstOrNull(),
            )
        }
        _state.value = _state.value.copy(recentCategoryIds = recent)
    }

    private fun applySource(item: TransferItem) {
        val common = _state.value.copy(
            sentText = item.sentMoney.toPlainString(),
            receivedText = item.receivedMoney.toPlainString(),
            selectedCategoryId = item.category?.id,
            description = item.description.orEmpty(),
            selectedTagIds = item.tags.map { it.id }.toSet(),
        )
        _state.value = if (operationId != null) {
            val split = TransactionDateTime.split(item.occurredAt, ZoneId.systemDefault())
            common.copy(
                sourceAccountId = item.sourceAccount.id,
                destAccountId = item.destAccount.id,
                originalSourceId = item.sourceAccount.id,
                originalDestId = item.destAccount.id,
                originalSentMoney = item.sentMoney,
                originalReceivedMoney = item.receivedMoney,
                hadCategoryBefore = item.category != null,
                date = split.date,
                time = split.time,
                deleteSentBack = item.sentMoney,
                deleteReceivedRemoved = item.receivedMoney,
            )
        } else {
            // Duplicate: date and time default to now, and an archived account is not carried over.
            common.copy(
                sourceAccountId = item.sourceAccount.takeIf { it.status == AccountStatus.ACTIVE }?.id,
                destAccountId = item.destAccount.takeIf { it.status == AccountStatus.ACTIVE }?.id,
            )
        }
    }

    fun onSentChange(text: String) {
        _state.value = _state.value.copy(sentText = text, isOverflow = false)
    }

    fun onReceivedChange(text: String) {
        _state.value = _state.value.copy(receivedText = text, isOverflow = false)
    }

    fun onSourceChange(accountId: Long) {
        _state.value = _state.value.copy(sourceAccountId = accountId, isOverflow = false)
    }

    fun onDestChange(accountId: Long) {
        _state.value = _state.value.copy(destAccountId = accountId, isOverflow = false)
    }

    fun onSwap() {
        val current = state.value
        val swapped = TransferFormLogic.swap(
            TransferSides(current.sourceAccountId, current.destAccountId, current.sentText, current.receivedText),
            current.sourceAccount?.currency,
            current.destAccount?.currency,
        )
        _state.value = _state.value.copy(
            sourceAccountId = swapped.sourceAccountId,
            destAccountId = swapped.destAccountId,
            sentText = swapped.sentText,
            receivedText = swapped.receivedText,
            isOverflow = false,
        )
    }

    fun onCategoryChange(categoryId: Long?) {
        _state.value = _state.value.copy(selectedCategoryId = categoryId)
    }

    fun onDateChange(date: LocalDate) {
        _state.value = _state.value.copy(date = date)
    }

    fun onTimeChange(time: LocalTime) {
        _state.value = _state.value.copy(time = time)
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

    fun save(confirmCurrencyChange: Boolean = false, moveOpeningBalanceBack: Boolean = false) {
        val current = state.value
        if (!current.canSave) return
        val source = current.sourceAccount ?: return
        val dest = current.destAccount ?: return
        val sent = (TransferAmountParser.parse(current.sentText, source.currency) as? TransferAmountParser.Result.Value)?.minor ?: return
        val received = (TransferAmountParser.parse(current.receivedText, dest.currency) as? TransferAmountParser.Result.Value)?.minor ?: return

        val draft = TransferDraft(
            sourceAccountId = source.id,
            destAccountId = dest.id,
            sentAmountMinor = sent,
            receivedAmountMinor = received,
            categoryId = current.selectedCategoryId,
            occurredAt = instantOf(current.date, current.time),
            description = current.description,
            tagIds = current.selectedTagIds,
            newTagNames = current.pendingTagNames,
        )

        viewModelScope.launch {
            try {
                if (current.isEdit) {
                    transferRepository.update(
                        id = current.operationId,
                        draft = draft,
                        moveOpeningBalanceBack = moveOpeningBalanceBack,
                        confirmCurrencyChange = confirmCurrencyChange,
                    )
                } else {
                    transferRepository.create(draft, moveOpeningBalanceBack)
                }
                _state.value = _state.value.copy(saved = true, currencyChangeLines = null, showOpeningOrderDialog = false)
            } catch (_: CurrencyChangeNotConfirmedException) {
                _state.value = _state.value.copy(
                    currencyChangeLines = currencyChangeLines(current, source.currency, sent, dest.currency, received),
                )
            } catch (_: OpeningBalanceOrderException) {
                _state.value = _state.value.copy(showOpeningOrderDialog = true, currencyChangeLines = null)
            } catch (_: MoneyOverflowException) {
                _state.value = _state.value.copy(isOverflow = true, currencyChangeLines = null, showOpeningOrderDialog = false)
            } catch (_: FutureDateException) {
                // The form validator rejects future dates; this only happens when the clock passes the chosen minute.
            }
        }
    }

    private fun currencyChangeLines(
        current: TransferEditUiState,
        sourceCurrency: Currency,
        sentMinor: Long,
        destCurrency: Currency,
        receivedMinor: Long,
    ): List<String> = listOfNotNull(
        changeLine(current.originalSentMoney, sourceCurrency, sentMinor),
        changeLine(current.originalReceivedMoney, destCurrency, receivedMinor),
    )

    private fun changeLine(old: Money?, newCurrency: Currency, newMinor: Long): String? {
        if (old == null || old.currency.code == newCurrency.code) return null
        val new = Money(newMinor, newCurrency)
        return "${old.toPlainString()} ${old.currency.code} → ${new.toPlainString()} ${newCurrency.code}"
    }

    fun onDismissCurrencyChange() {
        _state.value = _state.value.copy(currencyChangeLines = null)
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
            transferRepository.delete(id)
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
