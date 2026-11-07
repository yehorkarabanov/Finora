package com.yehor.finora.ui.analytics

import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.AnalyticsClock
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.PeriodSelection
import com.yehor.finora.data.repo.RateRepository
import com.yehor.finora.data.repo.SettingsRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountWithBalance
import com.yehor.finora.domain.analytics.PeriodKind
import com.yehor.finora.domain.analytics.PeriodState
import com.yehor.finora.domain.analytics.ResolvedPeriod
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.rate.RateTable
import com.yehor.finora.domain.tag.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Everything the analytics screens read besides their own aggregates. */
data class AnalyticsEnv(
    val periodState: PeriodState,
    val period: ResolvedPeriod,
    val today: LocalDate,
    val zone: ZoneId,
    val firstDayOfWeek: DayOfWeek,
    val accounts: List<AccountWithBalance>,
    val categories: List<Category>,
    val tags: List<Tag>,
    val table: RateTable,
    val display: Currency,
    val now: Long,
) {
    val allAccounts: List<Account> get() = accounts.map { it.account }

    val canShiftForward: Boolean
        get() = periodState.canShiftForward(today, zone, firstDayOfWeek)
}

/**
 * Combines the shared period, the day ticker, accounts, categories, tags, rates and the Display
 * Currency into one stream. Read-only (R1): the only state it changes is the in-memory period.
 */
class AnalyticsEnvironment @Inject constructor(
    private val clock: AnalyticsClock,
    private val selection: PeriodSelection,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
    private val rateRepository: RateRepository,
    private val settings: SettingsRepository,
) {
    private class Inputs(
        val accounts: List<AccountWithBalance>,
        val categories: List<Category>,
        val tags: List<Tag>,
        val table: RateTable,
        val display: Currency,
    )

    fun observe(): Flow<AnalyticsEnv> {
        val time = combine(clock.days(), selection.state) { today, state -> today to state }
        val data = combine(
            accountRepository.observeAll(),
            categoryRepository.observeAll(),
            tagRepository.observeAll(),
            rateRepository.observeTable(),
            settings.displayCurrency,
        ) { accounts, categories, tags, table, display ->
            Inputs(accounts, categories, tags, table, display.currency)
        }
        return combine(time, data) { (today, state), inputs ->
            val zone = clock.zone()
            val firstDay = clock.firstDayOfWeek()
            AnalyticsEnv(
                periodState = state,
                period = state.resolve(today, zone, firstDay),
                today = today,
                zone = zone,
                firstDayOfWeek = firstDay,
                accounts = inputs.accounts,
                categories = inputs.categories,
                tags = inputs.tags,
                table = inputs.table,
                display = inputs.display,
                now = clock.now(),
            )
        }
    }

    fun zone(): ZoneId = clock.zone()

    fun today(): LocalDate = Instant.ofEpochMilli(clock.now()).atZone(clock.zone()).toLocalDate()

    fun selectPeriod(kind: PeriodKind) = selection.select(kind)

    fun setCustomPeriod(start: LocalDate, end: LocalDate) = selection.setCustom(start, end)

    fun shiftPeriod(steps: Int) = selection.shift(steps, today())
}
