package com.yehor.finora.data.repo

import com.yehor.finora.domain.category.CategoryRules.ParentCheck
import com.yehor.finora.domain.tag.TagNameError

/**
 * A non-opening entry, or the opening balance itself, would break the rule that an account's
 * opening balance is dated on or before every other entry of that account (design 4.6, O1).
 */
class OpeningBalanceOrderException(
    val accountId: Long,
    val openingAt: Long,
    val entryAt: Long,
) : InvariantViolationException(
    "Opening balance of account $accountId (at $openingAt) must not be after an entry at $entryAt",
)

/** A category create or move was refused by `CategoryRules.checkParent`. */
class InvalidCategoryParentException(val reason: ParentCheck) :
    InvariantViolationException("Invalid category parent: $reason")

/** An operation was dated in the future (T11). */
class FutureDateException(val occurredAt: Long, val now: Long) :
    InvariantViolationException("Operation date ($occurredAt) is in the future relative to $now")

/** A tag name is invalid according to TagNameRules. */
class InvalidTagNameException(val error: TagNameError) :
    IllegalArgumentException("Invalid tag name: $error")

/** A tag with the same case-insensitive name already exists. */
class DuplicateTagNameException(val existingId: Long) :
    IllegalArgumentException("Tag name already exists with id $existingId")

/** Cross-currency reassignment was attempted without explicit confirmation (T12). */
class CurrencyChangeNotConfirmedException(val fromCode: String, val toCode: String) :
    IllegalStateException("Changing currency from $fromCode to $toCode requires explicit confirmation")

/** Generation for a schedule is blocked due to an account/balance issue. */
class ScheduleBlockedException(val issue: com.yehor.finora.domain.recurring.GenerationIssue) :
    IllegalStateException("Recurring schedule generation blocked: $issue")
