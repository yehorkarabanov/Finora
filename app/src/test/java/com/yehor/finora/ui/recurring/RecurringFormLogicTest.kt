package com.yehor.finora.ui.recurring

import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.ScheduleBlockedException
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.ScheduleDates
import com.yehor.finora.domain.recurring.ScheduleDraft
import com.yehor.finora.domain.recurring.ScheduleDraftError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val USD = CurrencyRegistry.get("USD")
private val EUR = CurrencyRegistry.get("EUR")
private val ZONE = ZoneOffset.UTC
private val TODAY: LocalDate = LocalDate.of(2026, 11, 6)
private val NOW: Long = TODAY.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()

private sealed interface Call {
    data class Create(val draft: ScheduleDraft, val moveBack: Boolean) : Call
    data class Update(val id: Long, val draft: ScheduleDraft, val moveBack: Boolean, val confirmCurrency: Boolean) : Call
}

/** Records writes; [failures] are thrown by successive calls, oldest first. */
private class FakeWriter(vararg failures: Throwable) : ScheduleWriter {
    val calls = mutableListOf<Call>()
    private val queue = ArrayDeque(failures.toList())

    override suspend fun create(draft: ScheduleDraft, moveOpeningBalanceBack: Boolean) {
        queue.removeFirstOrNull()?.let { throw it }
        calls += Call.Create(draft, moveOpeningBalanceBack)
    }

    override suspend fun update(
        id: Long,
        draft: ScheduleDraft,
        moveOpeningBalanceBack: Boolean,
        confirmCurrencyChange: Boolean,
    ) {
        queue.removeFirstOrNull()?.let { throw it }
        calls += Call.Update(id, draft, moveOpeningBalanceBack, confirmCurrencyChange)
    }
}

private fun createState(
    first: LocalDate = LocalDate.of(2026, 12, 1),
    end: LocalDate? = null,
    frequency: FrequencyInput = FrequencyInput.of(RecurrenceRule(RecurrenceUnit.MONTH, 1)),
) = RecurringFormState(
    kind = TransactionKind.EXPENSE,
    name = "Rent",
    amountText = "12.50",
    accountId = 1,
    categoryId = 10,
    frequency = frequency,
    firstDate = first,
    endDate = end,
)

/** A monthly schedule from 2026-09-01 whose 2026-09-01 and 2026-10-01 were handled; it is on a USD account. */
private fun editState(
    first: LocalDate = LocalDate.of(2026, 11, 1),
    next: LocalDate? = LocalDate.of(2026, 11, 1),
    accountId: Long = 1,
    active: Boolean = true,
    accountArchived: Boolean = false,
) = createState(first = first).copy(
    accountId = accountId,
    scheduleId = 5,
    accountArchived = accountArchived,
    original = OriginalSchedule(
        accountId = 1,
        currency = USD,
        money = Money(12_50, USD),
        anchor = ScheduleDates.Anchor(
            startDate = LocalDate.of(2026, 9, 1),
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            endDate = null,
            nextDate = next,
        ),
        lastHandled = LocalDate.of(2026, 10, 1),
        active = active,
    ),
)

class RecurringFormLogicTest {

    // ---- Frequency ----

    @Test
    fun presetsMapToIntervalOneRules() {
        assertEquals(RecurrenceRule(RecurrenceUnit.DAY, 1), FrequencyInput(FrequencyPreset.DAILY).rule())
        assertEquals(RecurrenceRule(RecurrenceUnit.WEEK, 1), FrequencyInput(FrequencyPreset.WEEKLY).rule())
        assertEquals(RecurrenceRule(RecurrenceUnit.MONTH, 1), FrequencyInput(FrequencyPreset.MONTHLY).rule())
        assertEquals(RecurrenceRule(RecurrenceUnit.YEAR, 1), FrequencyInput(FrequencyPreset.YEARLY).rule())
    }

    @Test
    fun aStoredRuleComesBackAsItsPresetOrCustom() {
        assertEquals(FrequencyPreset.WEEKLY, FrequencyInput.of(RecurrenceRule(RecurrenceUnit.WEEK, 1)).preset)
        val custom = FrequencyInput.of(RecurrenceRule(RecurrenceUnit.WEEK, 2))
        assertEquals(FrequencyPreset.CUSTOM, custom.preset)
        assertEquals(RecurrenceRule(RecurrenceUnit.WEEK, 2), custom.rule())
        // An interval of 1 is never shown as Custom.
        assertEquals(FrequencyPreset.YEARLY, FrequencyInput.of(RecurrenceRule(RecurrenceUnit.YEAR, 1)).preset)
    }

    @Test
    fun customStartsFromTheSelectedUnit() {
        val custom = FrequencyInput.of(RecurrenceRule(RecurrenceUnit.WEEK, 1)).select(FrequencyPreset.CUSTOM)
        assertEquals(RecurrenceRule(RecurrenceUnit.WEEK, 2), custom.rule())
        assertEquals(custom, custom.select(FrequencyPreset.CUSTOM))
    }

    @Test
    fun anUnreadableCustomNumberIsAnIntervalError() {
        val frequency = FrequencyInput(FrequencyPreset.CUSTOM, RecurrenceUnit.DAY, "")
        assertEquals(0, frequency.rule().interval)
        val errors = RecurringFormLogic.errors(createState(frequency = frequency), USD, NOW, ZONE)
        assertTrue(ScheduleDraftError.INTERVAL_OUT_OF_RANGE in errors)
        assertFalse(RecurringFormLogic.canSave(createState(frequency = frequency), USD, NOW, ZONE))
        val big = frequency.copy(customIntervalText = "366")
        assertTrue(ScheduleDraftError.INTERVAL_OUT_OF_RANGE in RecurringFormLogic.errors(createState(frequency = big), USD, NOW, ZONE))
    }

    // ---- Preview ----

    @Test
    fun aFutureStartShowsTheNextDate() {
        assertEquals(
            FormPreview.Next(LocalDate.of(2026, 12, 1)),
            RecurringFormLogic.preview(createState(), TODAY),
        )
    }

    @Test
    fun aStartTodayIsDueNow() {
        assertEquals(
            FormPreview.Due(1, TODAY, TODAY),
            RecurringFormLogic.preview(createState(first = TODAY), TODAY),
        )
    }

    @Test
    fun aPastStartCountsEveryDueOccurrence() {
        val state = createState(first = LocalDate.of(2026, 11, 3), frequency = FrequencyInput(FrequencyPreset.DAILY))
        assertEquals(
            FormPreview.Due(4, LocalDate.of(2026, 11, 3), TODAY),
            RecurringFormLogic.preview(state, TODAY),
        )
    }

    @Test
    fun theEndDateLimitsWhatIsDueNow() {
        val state = createState(
            first = LocalDate.of(2026, 11, 3),
            end = LocalDate.of(2026, 11, 4),
            frequency = FrequencyInput(FrequencyPreset.DAILY),
        )
        assertEquals(
            FormPreview.Due(2, LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4)),
            RecurringFormLogic.preview(state, TODAY),
        )
    }

    @Test
    fun anInvalidIntervalHasNoPreview() {
        val state = createState(frequency = FrequencyInput(FrequencyPreset.CUSTOM, RecurrenceUnit.DAY, "0"))
        assertEquals(FormPreview.None, RecurringFormLogic.preview(state, TODAY))
    }

    @Test
    fun editingAPausedScheduleOrOneOnAnArchivedAccountCreatesNothingNow() {
        assertEquals(
            FormPreview.Next(LocalDate.of(2026, 11, 1)),
            RecurringFormLogic.preview(editState(active = false), TODAY),
        )
        assertEquals(
            FormPreview.Next(LocalDate.of(2026, 11, 1)),
            RecurringFormLogic.preview(editState(accountArchived = true), TODAY),
        )
        assertEquals(
            FormPreview.Due(1, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1)),
            RecurringFormLogic.preview(editState(), TODAY),
        )
    }

    // ---- Edit guards ----

    @Test
    fun typeIsLockedOnEditOnly() {
        assertFalse(createState().typeLocked)
        assertTrue(editState().typeLocked)
        assertFalse(createState().isEdit)
        assertTrue(editState().isEdit)
    }

    @Test
    fun anEditCannotStartAtOrBeforeAHandledOccurrence() {
        val rejected = editState(first = LocalDate.of(2026, 10, 1))
        assertEquals(LocalDate.of(2026, 10, 1), RecurringFormLogic.handledConflict(rejected))
        assertFalse(RecurringFormLogic.canSave(rejected, USD, NOW, ZONE))
        assertNull(RecurringFormLogic.handledConflict(editState()))
        assertNull(RecurringFormLogic.handledConflict(createState(first = LocalDate.of(2020, 1, 1))))
    }

    // ---- Currency dialog ----

    @Test
    fun currencyDialogNamesBothAmountsWithTheirCurrency() {
        val info = RecurringFormLogic.currencyChange(editState(accountId = 2), EUR, 12_50)!!
        assertEquals("12.50 EUR", info.newFormatted)
        assertEquals("12.50 USD", info.oldFormatted)
    }

    @Test
    fun currencyDialogTextStatesThatItIsNotAConversion() {
        val strings = File("src/main/res/values/strings.xml").readText()
        val body = Regex("""<string name="recurring_currency_body">(.*?)</string>""").find(strings)!!.groupValues[1]
        assertTrue(body.contains("%1\$s"))
        assertTrue(body.contains("%2\$s"))
        assertTrue(body.contains("not a currency conversion"))
    }

    @Test
    fun switchingTheAccountBackRemovesTheCurrencyDialog() {
        assertNull(RecurringFormLogic.currencyChange(editState(accountId = 1), USD, 12_50))
        // A create never asks.
        assertNull(RecurringFormLogic.currencyChange(createState(), EUR, 12_50))
        // Another account with the same currency does not ask either.
        assertNull(RecurringFormLogic.currencyChange(editState(accountId = 3), USD, 12_50))
    }

    // ---- Save gates ----

    @Test
    fun createWithAFutureStartWritesAtOnce() = runTest {
        val writer = FakeWriter()
        val result = RecurringFormLogic.save(createState(), USD, NOW, ZONE, SaveConfirmations(), writer)
        assertEquals(SaveResult.Saved, result)
        val call = writer.calls.single() as Call.Create
        assertEquals(12_50L, call.draft.amountMinor)
        assertFalse(call.moveBack)
    }

    @Test
    fun anInvalidFormWritesNothing() = runTest {
        val writer = FakeWriter()
        val result = RecurringFormLogic.save(createState().copy(name = " "), USD, NOW, ZONE, SaveConfirmations(), writer)
        assertEquals(SaveResult.Invalid, result)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun pastOccurrencesAskBeforeAnythingIsWritten() = runTest {
        val writer = FakeWriter()
        val state = createState(first = LocalDate.of(2026, 11, 3), frequency = FrequencyInput(FrequencyPreset.DAILY))

        val asked = RecurringFormLogic.save(state, USD, NOW, ZONE, SaveConfirmations(), writer)
        assertEquals(SaveResult.Gate(SaveGate.PastOccurrences(4, LocalDate.of(2026, 11, 3), TODAY)), asked)
        assertTrue(writer.calls.isEmpty())

        val saved = RecurringFormLogic.save(state, USD, NOW, ZONE, SaveConfirmations(pastOccurrences = true), writer)
        assertEquals(SaveResult.Saved, saved)
        assertEquals(1, writer.calls.size)
    }

    @Test
    fun theCurrencyGateComesBeforeThePastOccurrencesGate() = runTest {
        val writer = FakeWriter()
        val state = editState(accountId = 2)

        val first = RecurringFormLogic.save(state, EUR, NOW, ZONE, SaveConfirmations(), writer)
        assertTrue((first as SaveResult.Gate).gate is SaveGate.CurrencyChange)

        val second = RecurringFormLogic.save(state, EUR, NOW, ZONE, SaveConfirmations(currencyChange = true), writer)
        assertEquals(
            SaveResult.Gate(SaveGate.PastOccurrences(1, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1))),
            second,
        )
        assertTrue(writer.calls.isEmpty())

        val third = RecurringFormLogic.save(
            state,
            EUR,
            NOW,
            ZONE,
            SaveConfirmations(currencyChange = true, pastOccurrences = true),
            writer,
        )
        assertEquals(SaveResult.Saved, third)
        val call = writer.calls.single() as Call.Update
        assertEquals(5L, call.id)
        assertTrue(call.confirmCurrency)
        assertFalse(call.moveBack)
    }

    @Test
    fun anEditWithoutCurrencyChangeNeverAsksAboutCurrency() = runTest {
        val writer = FakeWriter()
        // The next occurrence (1 Nov) is moved to 1 Dec, so nothing is due now.
        val state = editState(first = LocalDate.of(2026, 12, 1))
        val result = RecurringFormLogic.save(state, USD, NOW, ZONE, SaveConfirmations(), writer)
        assertEquals(SaveResult.Saved, result)
        assertFalse((writer.calls.single() as Call.Update).confirmCurrency)
    }

    @Test
    fun openingOrderIsAskedByTheWriteAndRetriedWithMoveBack() = runTest {
        val writer = FakeWriter(OpeningBalanceOrderException(1, 100, 50))
        val state = createState()

        val asked = RecurringFormLogic.save(state, USD, NOW, ZONE, SaveConfirmations(), writer)
        assertEquals(SaveResult.OpeningOrder, asked)
        assertTrue(writer.calls.isEmpty())

        val retried = RecurringFormLogic.save(state, USD, NOW, ZONE, SaveConfirmations(moveOpeningBalanceBack = true), writer)
        assertEquals(SaveResult.Saved, retried)
        assertTrue((writer.calls.single() as Call.Create).moveBack)
    }

    @Test
    fun cancellingTheOpeningOrderDialogLeavesNothingWritten() = runTest {
        val writer = FakeWriter(OpeningBalanceOrderException(1, 100, 50))
        val result = RecurringFormLogic.save(createState(), USD, NOW, ZONE, SaveConfirmations(), writer)
        assertEquals(SaveResult.OpeningOrder, result)
        assertTrue(writer.calls.isEmpty())
    }

    @Test
    fun writeFailuresBecomeResultsNotCrashes() = runTest {
        assertEquals(
            SaveResult.Overflow,
            RecurringFormLogic.save(createState(), USD, NOW, ZONE, SaveConfirmations(), FakeWriter(MoneyOverflowException("x"))),
        )
        assertEquals(
            SaveResult.AccountArchived,
            RecurringFormLogic.save(
                createState(),
                USD,
                NOW,
                ZONE,
                SaveConfirmations(),
                FakeWriter(ScheduleBlockedException(GenerationIssue.ACCOUNT_ARCHIVED)),
            ),
        )
        assertEquals(
            SaveResult.Invalid,
            RecurringFormLogic.save(
                editState(first = LocalDate.of(2026, 12, 1)),
                USD,
                NOW,
                ZONE,
                SaveConfirmations(),
                FakeWriter(CurrencyChangeNotConfirmedException("USD", "EUR")),
            ),
        )
    }

    // ---- Validation and draft ----

    @Test
    fun tooManyPastOccurrencesBlockSaving() {
        val state = createState(first = LocalDate.of(2020, 1, 1), frequency = FrequencyInput(FrequencyPreset.DAILY))
        assertTrue(ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES in RecurringFormLogic.errors(state, USD, NOW, ZONE))
        assertFalse(RecurringFormLogic.canSave(state, USD, NOW, ZONE))
    }

    @Test
    fun theAmountIsParsedInTheSelectedAccountsCurrency() {
        val state = createState().copy(amountText = "12,5")
        assertEquals(12_50L, RecurringFormLogic.draft(state, USD)!!.amountMinor)
        assertNull(RecurringFormLogic.draft(createState().copy(amountText = "abc"), USD))
        assertNull(RecurringFormLogic.draft(createState().copy(accountId = null), USD))
    }

    @Test
    fun theDraftCarriesTheRuleDatesAndTags() {
        val state = createState(end = LocalDate.of(2027, 1, 1)).copy(
            selectedTagIds = setOf(1, 2),
            pendingTagNames = listOf("new"),
            frequency = FrequencyInput(FrequencyPreset.CUSTOM, RecurrenceUnit.WEEK, "3"),
        )
        val draft = RecurringFormLogic.draft(state, USD)!!
        assertEquals(RecurrenceRule(RecurrenceUnit.WEEK, 3), draft.rule)
        assertEquals(LocalDate.of(2026, 12, 1), draft.firstDate)
        assertEquals(LocalDate.of(2027, 1, 1), draft.endDate)
        assertEquals(setOf(1L, 2L), draft.tagIds)
        assertEquals(listOf("new"), draft.newTagNames)
        assertEquals(TransactionKind.EXPENSE, draft.kind)
    }
}
