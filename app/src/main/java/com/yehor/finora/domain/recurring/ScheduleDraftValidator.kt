package com.yehor.finora.domain.recurring

import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.tag.TagNameRules
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ScheduleDraftError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    AMOUNT_EMPTY,
    AMOUNT_INVALID,
    AMOUNT_TOO_PRECISE,
    AMOUNT_NOT_POSITIVE,
    AMOUNT_OUT_OF_RANGE,
    ACCOUNT_MISSING,
    CATEGORY_MISSING,
    INTERVAL_OUT_OF_RANGE,
    DATE_OUT_OF_RANGE,
    END_BEFORE_START,
    TOO_MANY_PAST_OCCURRENCES,
}

object ScheduleDraftValidator {
    const val NAME_MAX_LENGTH = 60
    val MIN_DATE: LocalDate = LocalDate.of(2000, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)
    const val MAX_PAST_OCCURRENCES = 1000

    fun validate(
        name: String,
        amountText: String,
        currency: Currency,
        accountId: Long?,
        categoryId: Long?,
        rule: RecurrenceRule,
        firstDate: LocalDate,
        endDate: LocalDate?,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        isEdit: Boolean = false,
        hadCategoryBefore: Boolean = true,
    ): Set<ScheduleDraftError> {
        val errors = mutableSetOf<ScheduleDraftError>()

        val normalizedName = TagNameRules.normalize(name)
        if (normalizedName.isEmpty()) {
            errors += ScheduleDraftError.NAME_EMPTY
        } else if (normalizedName.codePointCount(0, normalizedName.length) > NAME_MAX_LENGTH) {
            errors += ScheduleDraftError.NAME_TOO_LONG
        }

        when (val parse = AmountParser.parse(amountText, currency)) {
            is AmountParseResult.Empty -> errors += ScheduleDraftError.AMOUNT_EMPTY
            is AmountParseResult.InvalidFormat -> errors += ScheduleDraftError.AMOUNT_INVALID
            is AmountParseResult.TooPrecise -> errors += ScheduleDraftError.AMOUNT_TOO_PRECISE
            is AmountParseResult.OutOfRange -> errors += ScheduleDraftError.AMOUNT_OUT_OF_RANGE
            is AmountParseResult.Value -> {
                if (parse.minor <= 0) {
                    errors += ScheduleDraftError.AMOUNT_NOT_POSITIVE
                }
            }
        }

        if (accountId == null) {
            errors += ScheduleDraftError.ACCOUNT_MISSING
        }

        if (categoryId == null) {
            if (!isEdit || hadCategoryBefore) {
                errors += ScheduleDraftError.CATEGORY_MISSING
            }
        }

        if (rule.interval !in 1..365) {
            errors += ScheduleDraftError.INTERVAL_OUT_OF_RANGE
        }

        val firstDateOutOfRange = firstDate < MIN_DATE || firstDate > MAX_DATE
        val endDateOutOfRange = endDate != null && (endDate < MIN_DATE || endDate > MAX_DATE)
        if (firstDateOutOfRange || endDateOutOfRange) {
            errors += ScheduleDraftError.DATE_OUT_OF_RANGE
        }

        if (endDate != null && endDate < firstDate) {
            errors += ScheduleDraftError.END_BEFORE_START
        }

        if (rule.interval in 1..365 && !firstDateOutOfRange && (endDate == null || endDate >= firstDate)) {
            val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            if (firstDate <= today) {
                val toDate = if (endDate != null && endDate < today) endDate else today
                val dueOccurrences = RecurrenceCalculator.between(
                    anchor = firstDate,
                    rule = rule,
                    from = firstDate,
                    toInclusive = toDate,
                    limit = MAX_PAST_OCCURRENCES + 2,
                )
                if (dueOccurrences.size > MAX_PAST_OCCURRENCES) {
                    errors += ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES
                }
            }
        }

        return errors
    }
}
