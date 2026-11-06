package com.yehor.finora.domain.recurring

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object RecurrenceCalculator {
    fun nth(anchor: LocalDate, rule: RecurrenceRule, k: Long): LocalDate {
        require(k >= 0) { "k must be non-negative: $k" }
        val step = Math.multiplyExact(k, rule.interval.toLong())
        return when (rule.unit) {
            RecurrenceUnit.DAY -> anchor.plusDays(step)
            RecurrenceUnit.WEEK -> anchor.plusWeeks(step)
            RecurrenceUnit.MONTH -> anchor.plusMonths(step)
            RecurrenceUnit.YEAR -> anchor.plusYears(step)
        }
    }

    fun firstAfter(anchor: LocalDate, rule: RecurrenceRule, after: LocalDate): LocalDate {
        if (anchor > after) {
            return anchor
        }
        val estimatedK: Long = when (rule.unit) {
            RecurrenceUnit.DAY -> ChronoUnit.DAYS.between(anchor, after) / rule.interval
            RecurrenceUnit.WEEK -> ChronoUnit.WEEKS.between(anchor, after) / rule.interval
            RecurrenceUnit.MONTH -> ChronoUnit.MONTHS.between(anchor, after) / rule.interval
            RecurrenceUnit.YEAR -> ChronoUnit.YEARS.between(anchor, after) / rule.interval
        }
        var k = maxOf(0L, estimatedK)
        while (nth(anchor, rule, k) <= after) {
            k = Math.incrementExact(k)
        }
        while (k > 0 && nth(anchor, rule, k - 1) > after) {
            k = Math.decrementExact(k)
        }
        return nth(anchor, rule, k)
    }

    fun next(anchor: LocalDate, rule: RecurrenceRule, current: LocalDate, end: LocalDate?): LocalDate? {
        val candidate = firstAfter(anchor, rule, current)
        return if (end != null && candidate > end) null else candidate
    }

    fun between(anchor: LocalDate, rule: RecurrenceRule, from: LocalDate, toInclusive: LocalDate, limit: Int): List<LocalDate> {
        if (from > toInclusive || limit <= 0) return emptyList()
        val result = mutableListOf<LocalDate>()
        var current = if (anchor >= from) anchor else firstAfter(anchor, rule, from.minusDays(1))
        while (current <= toInclusive && result.size < limit) {
            result.add(current)
            current = firstAfter(anchor, rule, current)
        }
        return result
    }

    fun startOfDay(date: LocalDate, zone: ZoneId): Long {
        return date.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
