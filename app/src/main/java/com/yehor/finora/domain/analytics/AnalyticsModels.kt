package com.yehor.finora.domain.analytics

import com.yehor.finora.domain.money.Money
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * A native (unconverted) sum for one group. [key] is the category, account or tag id, or the bucket
 * index; null is Uncategorized or "No tag".
 */
data class NativeRow(val key: Long?, val money: Money, val operationCount: Int) {
    fun toAmount(): NativeAmount = NativeAmount(money, operationCount)
}

/** Period figures of one account, in its own currency. [netChange] counts every entry, transfers and opening balance included. */
data class AccountPeriodRow(
    val accountId: Long,
    val income: Money,
    val expense: Money,
    val netChange: Money,
)

/**
 * What the user picked. A preset with [offset] 0 is the current period and follows the calendar, so
 * "Today" moves at midnight. Arrows change [offset]. A custom range keeps explicit dates.
 */
data class PeriodState(
    val kind: PeriodKind = PeriodKind.MONTH,
    val offset: Int = 0,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
) {
    fun toPeriod(today: LocalDate): Period = when (kind) {
        PeriodKind.CUSTOM -> {
            val start = customStart ?: today
            Period(PeriodKind.CUSTOM, start, customEnd ?: start)
        }
        else -> PeriodCalculator.shift(PeriodCalculator.preset(kind, today), offset)
    }

    fun resolve(today: LocalDate, zone: ZoneId, firstDayOfWeek: DayOfWeek): ResolvedPeriod =
        PeriodCalculator.resolve(toPeriod(today), zone, firstDayOfWeek)

    fun shifted(steps: Int, today: LocalDate): PeriodState = when (kind) {
        PeriodKind.CUSTOM -> {
            val moved = PeriodCalculator.shift(toPeriod(today), steps)
            copy(customStart = moved.anchor, customEnd = moved.customEnd)
        }
        else -> copy(offset = offset + steps)
    }

    fun canShiftForward(today: LocalDate, zone: ZoneId, firstDayOfWeek: DayOfWeek): Boolean =
        PeriodCalculator.canShiftForward(toPeriod(today), today, zone, firstDayOfWeek)

    companion object {
        fun preset(kind: PeriodKind) = PeriodState(kind = kind)
        fun custom(start: LocalDate, end: LocalDate) =
            if (end.isBefore(start)) PeriodState(PeriodKind.CUSTOM, customStart = start, customEnd = start)
            else PeriodState(PeriodKind.CUSTOM, customStart = start, customEnd = end)
    }
}
