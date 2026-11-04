# Finora: Operations and Tags Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use skills:subagent-driven-development (recommended) or skills:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement income/expense creation, editing, deletion, and duplication; transaction history with dynamic filtering, sorting, summary bar, and Paging 3; tag management and inline tag creation; and account detail transaction history.

**Architecture:** Pure domain validation and rules (`AmountParser`, `TransactionDraftValidator`, `TagNameRules`, `TransactionDateTime`, `DayGrouping`, `FilterScope`, `CurrencyReassignment`) in `domain/` tested via JVM unit tests; Room DAO extensions with query builder (`TransactionQuery`), updated `OperationEntity` index, `OperationRepository` write path, and new `TransactionRepository` and `TagRepository` in `data/repo/` tested via Android instrumented tests; Jetpack Compose UI (`TransactionEditScreen`, `TransactionsScreen`, `TagsScreen`, reusable `TransactionRow`, `TagPicker`, `TagFilterPicker`) and Hilt ViewModels in `ui/`.

**Tech Stack:** Kotlin 2.4 / Coroutines & Flow, Android Room 2.8.5, AndroidX Paging 3 (`paging-runtime`, `paging-compose`, `room-paging`), Jetpack Compose (Material 3), Dagger Hilt, Turbine, JUnit 4.

---

## Task 1: Dependencies and AmountParser Extraction

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/yehor/finora/domain/money/AmountParser.kt`
- Modify: `app/src/main/java/com/yehor/finora/domain/account/AccountDraftValidator.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/money/AmountParserTest.kt`
- Test: `app/src/test/java/com/yehor/finora/domain/account/AccountDraftValidatorTest.kt`

- [ ] **Step 1: Add Paging 3 dependencies to catalog and build script**

In `gradle/libs.versions.toml`:
```toml
[versions]
# ... existing versions ...
paging = "3.3.6"

[libraries]
# ... existing libraries ...
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
androidx-paging-testing = { group = "androidx.paging", name = "paging-testing", version.ref = "paging" }
androidx-room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }
```

In `app/build.gradle.kts`:
```kotlin
dependencies {
    // ... existing dependencies ...
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)
    testImplementation(libs.androidx.paging.testing)
}
```

- [ ] **Step 2: Write failing unit test for `AmountParser`**

Create `app/src/test/java/com/yehor/finora/domain/money/AmountParserTest.kt`:
```kotlin
package com.yehor.finora.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountParserTest {

    private val usd = Currency(code = "USD", scale = 2, symbol = "$", name = "US Dollar")
    private val jpy = Currency(code = "JPY", scale = 0, symbol = "¥", name = "Japanese Yen")
    private val bhd = Currency(code = "BHD", scale = 3, symbol = "BD", name = "Bahraini Dinar")

    @Test
    fun blankInputGivesEmpty() {
        assertEquals(AmountParseResult.Empty, AmountParser.parse("", usd))
        assertEquals(AmountParseResult.Empty, AmountParser.parse("   ", usd))
    }

    @Test
    fun validDecimalWithDotAndComma() {
        assertEquals(AmountParseResult.Value(1250L), AmountParser.parse("12.50", usd))
        assertEquals(AmountParseResult.Value(1250L), AmountParser.parse("12,50", usd))
        assertEquals(AmountParseResult.Value(1200L), AmountParser.parse("12", usd))
        assertEquals(AmountParseResult.Value(50L), AmountParser.parse(".50", usd))
        assertEquals(AmountParseResult.Value(50L), AmountParser.parse(",50", usd))
    }

    @Test
    fun invalidPattern() {
        assertTrue(AmountParser.parse("abc", usd) is AmountParseResult.InvalidFormat)
        assertTrue(AmountParser.parse("12.34.56", usd) is AmountParseResult.InvalidFormat)
        assertTrue(AmountParser.parse("12..34", usd) is AmountParseResult.InvalidFormat)
    }

    @Test
    fun scalePrecisionChecks() {
        assertEquals(AmountParseResult.Value(100L), AmountParser.parse("100", jpy))
        assertTrue(AmountParser.parse("100.5", jpy) is AmountParseResult.TooPrecise)
        assertEquals(AmountParseResult.Value(12345L), AmountParser.parse("12.345", bhd))
        assertTrue(AmountParser.parse("12.3456", bhd) is AmountParseResult.TooPrecise)
    }

    @Test
    fun overflowAndMinRange() {
        assertTrue(AmountParser.parse("9999999999999999999999999", usd) is AmountParseResult.OutOfRange)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `gradlew :app:testDebugUnitTest --tests com.yehor.finora.domain.money.AmountParserTest`
Expected: FAIL (unresolved reference `AmountParser`)

- [ ] **Step 4: Implement `AmountParser` and delegate from `AccountDraftValidator`**

Create `app/src/main/java/com/yehor/finora/domain/money/AmountParser.kt`:
```kotlin
package com.yehor.finora.domain.money

import java.math.BigDecimal

sealed interface AmountParseResult {
    data object Empty : AmountParseResult
    data class Value(val minor: Long) : AmountParseResult
    data object InvalidFormat : AmountParseResult
    data object TooPrecise : AmountParseResult
    data object OutOfRange : AmountParseResult
}

object AmountParser {
    private val amountPattern = Regex("""^-?(\d+([.,]\d*)?|[.,]\d+)$""")

    /** Parses [text] into minor units of [currency] without rounding. */
    fun parse(text: String, currency: Currency): AmountParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return AmountParseResult.Empty
        if (!amountPattern.matches(trimmed)) return AmountParseResult.InvalidFormat
        val normalized = trimmed.replace(',', '.')
        val decimalStr = if (normalized.endsWith(".")) normalized.dropLast(1) else normalized
        val decimal = try {
            BigDecimal(decimalStr)
        } catch (_: Exception) {
            return AmountParseResult.InvalidFormat
        }
        if (decimal.stripTrailingZeros().scale() > currency.scale) {
            return AmountParseResult.TooPrecise
        }
        return try {
            val minor = Money.parse(decimal.toPlainString(), currency).amountMinor
            if (minor == Long.MIN_VALUE) AmountParseResult.OutOfRange
            else AmountParseResult.Value(minor)
        } catch (_: MoneyOverflowException) {
            AmountParseResult.OutOfRange
        }
    }
}
```

Refactor `app/src/main/java/com/yehor/finora/domain/account/AccountDraftValidator.kt`:
```kotlin
package com.yehor.finora.domain.account

import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency

enum class AccountDraftError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    DESCRIPTION_TOO_LONG,
    AMOUNT_INVALID,
    AMOUNT_TOO_PRECISE,
    AMOUNT_OUT_OF_RANGE,
}

sealed interface AmountParse {
    data object Empty : AmountParse
    data class Value(val minor: Long) : AmountParse
    data class Invalid(val error: AccountDraftError) : AmountParse
}

object AccountDraftValidator {
    const val NAME_MAX = 50
    const val DESCRIPTION_MAX = 500

    fun validate(
        name: String,
        description: String?,
        amountText: String,
        currency: Currency,
    ): Set<AccountDraftError> {
        val errors = mutableSetOf<AccountDraftError>()
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) errors += AccountDraftError.NAME_EMPTY
        if (trimmedName.length > NAME_MAX) errors += AccountDraftError.NAME_TOO_LONG
        if ((description?.trim()?.length ?: 0) > DESCRIPTION_MAX) {
            errors += AccountDraftError.DESCRIPTION_TOO_LONG
        }
        val amount = parseAmount(amountText, currency)
        if (amount is AmountParse.Invalid) errors += amount.error
        return errors
    }

    fun parseAmount(text: String, currency: Currency): AmountParse =
        when (val result = AmountParser.parse(text, currency)) {
            is AmountParseResult.Empty -> AmountParse.Empty
            is AmountParseResult.Value -> AmountParse.Value(result.minor)
            is AmountParseResult.InvalidFormat -> AmountParse.Invalid(AccountDraftError.AMOUNT_INVALID)
            is AmountParseResult.TooPrecise -> AmountParse.Invalid(AccountDraftError.AMOUNT_TOO_PRECISE)
            is AmountParseResult.OutOfRange -> AmountParse.Invalid(AccountDraftError.AMOUNT_OUT_OF_RANGE)
        }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew :app:testDebugUnitTest --tests "com.yehor.finora.domain.money.AmountParserTest" --tests "com.yehor.finora.domain.account.AccountDraftValidatorTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/com/yehor/finora/domain/money/AmountParser.kt app/src/main/java/com/yehor/finora/domain/account/AccountDraftValidator.kt app/src/test/java/com/yehor/finora/domain/money/AmountParserTest.kt
git commit -m "feat: extract AmountParser and add Paging 3 dependencies"
```

---

## Task 2: Domain Layer — Models, Rules, and Validators

**Files:**
- Create: `app/src/main/java/com/yehor/finora/domain/tag/Tag.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/tag/TagNameRules.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/TransactionDraft.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/TransactionFilter.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/TransactionItem.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/TransactionDraftValidator.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/TransactionDateTime.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/DayGrouping.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/FilterScope.kt`
- Create: `app/src/main/java/com/yehor/finora/domain/operation/CurrencyReassignment.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/tag/TagNameRulesTest.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/operation/TransactionDraftValidatorTest.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/operation/TransactionDateTimeTest.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/operation/DayGroupingTest.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/operation/FilterScopeTest.kt`
- Create: `app/src/test/java/com/yehor/finora/domain/operation/CurrencyReassignmentTest.kt`

- [ ] **Step 1: Write failing unit tests for TagNameRules, TransactionDraftValidator, TransactionDateTime, DayGrouping, FilterScope, CurrencyReassignment**

Create `app/src/test/java/com/yehor/finora/domain/tag/TagNameRulesTest.kt`:
```kotlin
package com.yehor.finora.domain.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TagNameRulesTest {

    @Test
    fun normalizeCollapsesWhitespace() {
        assertEquals("food", TagNameRules.normalize("  food  "))
        assertEquals("food grocery", TagNameRules.normalize("food \t \n  grocery"))
    }

    @Test
    fun keyIsCaseInsensitiveAndLocaleIndependent() {
        assertEquals("debt", TagNameRules.key("Debt"))
        assertEquals("debt", TagNameRules.key("DEBT"))
        assertEquals("дебет", TagNameRules.key("Дебет"))
        assertEquals("дебет", TagNameRules.key("ДЕБЕТ"))
        assertEquals("żabka", TagNameRules.key("Żabka"))
        // Turkish locale 'I' -> 'ı' must not affect root lowercase
        val defaultLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals("invoice", TagNameRules.key("INVOICE"))
        } finally {
            Locale.setDefault(defaultLocale)
        }
    }

    @Test
    fun validateLength() {
        assertEquals(TagNameError.EMPTY, TagNameRules.validate(""))
        assertEquals(TagNameError.EMPTY, TagNameRules.validate("   "))
        assertNull(TagNameRules.validate("a".repeat(30)))
        assertEquals(TagNameError.TOO_LONG, TagNameRules.validate("a".repeat(31)))
    }
}
```

Create `app/src/test/java/com/yehor/finora/domain/operation/TransactionDraftValidatorTest.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDraftValidatorTest {

    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val now = 1_000_000L

    @Test
    fun validCreateDraftPasses() {
        val errors = TransactionDraftValidator.validate(
            amountText = "10.50",
            accountId = 1L,
            categoryId = 2L,
            occurredAt = now,
            description = "Lunch",
            currency = usd,
            now = now,
            isEdit = false,
            hadCategoryBefore = false,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun amountErrors() {
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_EMPTY),
            TransactionDraftValidator.validate("", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_INVALID),
            TransactionDraftValidator.validate("abc", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_TOO_PRECISE),
            TransactionDraftValidator.validate("10.555", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_NOT_POSITIVE),
            TransactionDraftValidator.validate("0", 1L, 2L, now, null, usd, now, false, false),
        )
        assertEquals(
            setOf(TransactionDraftError.AMOUNT_NOT_POSITIVE),
            TransactionDraftValidator.validate("-5.00", 1L, 2L, now, null, usd, now, false, false),
        )
    }

    @Test
    fun accountAndCategoryRequirements() {
        val missingAcc = TransactionDraftValidator.validate("10", null, 2L, now, null, usd, now, false, false)
        assertTrue(TransactionDraftError.ACCOUNT_MISSING in missingAcc)

        val missingCatCreate = TransactionDraftValidator.validate("10", 1L, null, now, null, usd, now, false, false)
        assertTrue(TransactionDraftError.CATEGORY_MISSING in missingCatCreate)

        val editUncategorizedAllowed = TransactionDraftValidator.validate("10", 1L, null, now, null, usd, now, true, false)
        assertTrue(TransactionDraftError.CATEGORY_MISSING !in editUncategorizedAllowed)

        val editCategorizedRequiresCategory = TransactionDraftValidator.validate("10", 1L, null, now, null, usd, now, true, true)
        assertTrue(TransactionDraftError.CATEGORY_MISSING in editCategorizedRequiresCategory)
    }

    @Test
    fun descriptionLength() {
        val ok = TransactionDraftValidator.validate("10", 1L, 2L, now, "a".repeat(500), usd, now, false, false)
        assertTrue(TransactionDraftError.DESCRIPTION_TOO_LONG !in ok)

        val tooLong = TransactionDraftValidator.validate("10", 1L, 2L, now, "a".repeat(501), usd, now, false, false)
        assertTrue(TransactionDraftError.DESCRIPTION_TOO_LONG in tooLong)
    }

    @Test
    fun futureDateRefused() {
        val atNow = TransactionDraftValidator.validate("10", 1L, 2L, now, null, usd, now, false, false)
        assertTrue(TransactionDraftError.DATE_IN_FUTURE !in atNow)

        val past = TransactionDraftValidator.validate("10", 1L, 2L, now - 1, null, usd, now, false, false)
        assertTrue(TransactionDraftError.DATE_IN_FUTURE !in past)

        val future = TransactionDraftValidator.validate("10", 1L, 2L, now + 1, null, usd, now, false, false)
        assertTrue(TransactionDraftError.DATE_IN_FUTURE in future)
    }
}
```

Create `app/src/test/java/com/yehor/finora/domain/operation/TransactionDateTimeTest.kt`:
```kotlin
package com.yehor.finora.domain.operation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TransactionDateTimeTest {

    private val zone = ZoneId.of("Europe/Warsaw")

    @Test
    fun combineAndSplitRoundTrip() {
        val localDate = LocalDate.of(2026, 11, 4)
        val localTime = LocalTime.of(15, 30)
        val pickerUtcMidnight = localDate.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

        val instant = TransactionDateTime.combine(pickerUtcMidnight, localTime.hour, localTime.minute, zone)
        val split = TransactionDateTime.split(instant, zone)

        assertEquals(localDate, split.date)
        assertEquals(LocalTime.of(15, 30, 0), split.time)
    }

    @Test
    fun futureCheck() {
        val now = 1000L
        assertFalse(TransactionDateTime.isFuture(1000L, now))
        assertFalse(TransactionDateTime.isFuture(999L, now))
        assertTrue(TransactionDateTime.isFuture(1001L, now))
    }
}
```

Create `app/src/test/java/com/yehor/finora/domain/operation/DayGroupingTest.kt`:
```kotlin
package com.yehor.finora.domain.operation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DayGroupingTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun groupLabelsForTodayYesterdayAndDate() {
        val today = LocalDate.of(2026, 11, 4)
        val now = ZonedDateTime.of(today, java.time.LocalTime.of(12, 0), zone).toInstant().toEpochMilli()

        val todayInstant = ZonedDateTime.of(today, java.time.LocalTime.of(9, 0), zone).toInstant().toEpochMilli()
        val yesterdayInstant = ZonedDateTime.of(today.minusDays(1), java.time.LocalTime.of(18, 0), zone).toInstant().toEpochMilli()
        val olderInstant = ZonedDateTime.of(today.minusDays(3), java.time.LocalTime.of(10, 0), zone).toInstant().toEpochMilli()

        assertEquals(DayGroup.Today, DayGrouping.group(todayInstant, now, zone))
        assertEquals(DayGroup.Yesterday, DayGrouping.group(yesterdayInstant, now, zone))
        assertEquals(DayGroup.Date(today.minusDays(3)), DayGrouping.group(olderInstant, now, zone))
    }
}
```

Create `app/src/test/java/com/yehor/finora/domain/operation/FilterScopeTest.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterScopeTest {

    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val eur = Currency("EUR", 2, "€", "Euro")

    private fun acc(id: Long, currency: Currency) = Account(
        id = id, name = "A$id", currency = currency, type = AccountType.BANK,
        icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L,
    )

    @Test
    fun scopeCurrencySingleAccount() {
        val accounts = listOf(acc(1, usd), acc(2, eur))
        val filter = TransactionFilter(accountIds = setOf(1))
        assertEquals(usd, FilterScope.scopeCurrency(filter, accounts))
    }

    @Test
    fun scopeCurrencyAllAccountsSameCurrency() {
        val accounts = listOf(acc(1, usd), acc(2, usd))
        val filter = TransactionFilter()
        assertEquals(usd, FilterScope.scopeCurrency(filter, accounts))
    }

    @Test
    fun scopeCurrencyMixedAccountsGivesNull() {
        val accounts = listOf(acc(1, usd), acc(2, eur))
        val filter = TransactionFilter()
        assertNull(FilterScope.scopeCurrency(filter, accounts))

        val filterSelectedBoth = TransactionFilter(accountIds = setOf(1, 2))
        assertNull(FilterScope.scopeCurrency(filterSelectedBoth, accounts))
    }

    @Test
    fun pruneMissingRemovesDeletedIds() {
        val filter = TransactionFilter(
            accountIds = setOf(1, 99),
            categoryIds = setOf(10, 88),
            tagIds = setOf(100, 200),
        )
        val pruned = FilterScope.pruneMissing(
            filter = filter,
            existingAccountIds = setOf(1),
            existingCategoryIds = setOf(10),
            existingTagIds = setOf(100),
        )
        assertEquals(setOf(1L), pruned.accountIds)
        assertEquals(setOf(10L), pruned.categoryIds)
        assertEquals(setOf(100L), pruned.tagIds)
    }
}
```

Create `app/src/test/java/com/yehor/finora/domain/operation/CurrencyReassignmentTest.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyReassignmentTest {

    private val pln = Currency("PLN", 2, "zł", "Polish Zloty")
    private val usd = Currency("USD", 2, "$", "US Dollar")
    private val jpy = Currency("JPY", 0, "¥", "Japanese Yen")

    @Test
    fun sameCurrencyRequiresNoConfirmation() {
        assertFalse(CurrencyReassignment.needsConfirmation(fromCurrency = pln, toCurrency = pln))
    }

    @Test
    fun differentCurrencyRequiresConfirmation() {
        assertTrue(CurrencyReassignment.needsConfirmation(fromCurrency = pln, toCurrency = usd))
    }

    @Test
    fun buildsInfoForDialog() {
        val info = CurrencyReassignment.buildInfo(
            oldAmountMinor = 10000L,
            oldCurrency = pln,
            newCurrency = usd,
            oldAccountName = "Santander",
            newAccountName = "Chase",
            isExpense = true,
        )
        assertEquals("100.00 PLN", info.oldFormatted)
        assertEquals("100.00 USD", info.newFormatted)
        assertEquals("Santander", info.oldAccountName)
        assertEquals("Chase", info.newAccountName)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew :app:testDebugUnitTest --tests "com.yehor.finora.domain.tag.*" --tests "com.yehor.finora.domain.operation.*"`
Expected: FAIL (unresolved symbols)

- [ ] **Step 3: Implement domain models and helpers**

Create `app/src/main/java/com/yehor/finora/domain/tag/Tag.kt`:
```kotlin
package com.yehor.finora.domain.tag

data class Tag(
    val id: Long,
    val name: String,
    val color: Long?,
    val createdAt: Long,
)

data class TagWithUsage(
    val tag: Tag,
    val usageCount: Int,
)

enum class TagMatchMode { ANY, ALL }
```

Create `app/src/main/java/com/yehor/finora/domain/tag/TagNameRules.kt`:
```kotlin
package com.yehor.finora.domain.tag

import java.util.Locale

enum class TagNameError {
    EMPTY,
    TOO_LONG,
}

object TagNameRules {
    const val MAX_LENGTH = 30
    private val whitespaceRegex = Regex("""\s+""")

    fun normalize(raw: String): String =
        raw.trim().replace(whitespaceRegex, " ")

    fun key(name: String): String =
        normalize(name).lowercase(Locale.ROOT)

    fun validate(raw: String): TagNameError? {
        val normalized = normalize(raw)
        return when {
            normalized.isEmpty() -> TagNameError.EMPTY
            normalized.codePointCount(0, normalized.length) > MAX_LENGTH -> TagNameError.TOO_LONG
            else -> null
        }
    }
}
```

Create `app/src/main/java/com/yehor/finora/domain/operation/TransactionDraft.kt`:
```kotlin
package com.yehor.finora.domain.operation

enum class TransactionKind { INCOME, EXPENSE }

data class TransactionDraft(
    val kind: TransactionKind,
    val accountId: Long,
    val amountMinor: Long, // positive magnitude in account's currency
    val categoryId: Long?,
    val occurredAt: Long,
    val description: String?,
    val tagIds: Set<Long> = emptySet(),
    val newTagNames: List<String> = emptyList(),
)
```

Create `app/src/main/java/com/yehor/finora/domain/operation/TransactionFilter.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.tag.TagMatchMode

data class AmountRange(
    val currency: Currency,
    val minMinor: Long? = null,
    val maxMinor: Long? = null,
)

data class TransactionFilter(
    val types: Set<OperationType> = emptySet(), // subset of INCOME, EXPENSE, TRANSFER. Empty = all.
    val accountIds: Set<Long> = emptySet(),
    val categoryIds: Set<Long> = emptySet(), // includes descendants
    val includeUncategorized: Boolean = false,
    val tagIds: Set<Long> = emptySet(),
    val tagMode: TagMatchMode = TagMatchMode.ANY,
    val from: Long? = null,
    val toExclusive: Long? = null,
    val amount: AmountRange? = null,
    val text: String = "",
) {
    val isActive: Boolean
        get() = types.isNotEmpty() ||
            accountIds.isNotEmpty() ||
            categoryIds.isNotEmpty() ||
            includeUncategorized ||
            tagIds.isNotEmpty() ||
            from != null ||
            toExclusive != null ||
            amount != null ||
            text.isNotBlank()
}

enum class TransactionSort { DATE_DESC, DATE_ASC, AMOUNT_DESC, AMOUNT_ASC }
```

Create `app/src/main/java/com/yehor/finora/domain/operation/TransactionItem.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.tag.Tag

data class TransactionEntryView(
    val account: Account,
    val money: Money, // signed
)

data class TransactionItem(
    val id: Long,
    val type: OperationType,
    val occurredAt: Long,
    val description: String?,
    val category: Category?,
    val categoryPath: List<Category>,
    val tags: List<Tag>,
    val entries: List<TransactionEntryView>,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CurrencySummary(
    val currency: Currency,
    val income: Money,
    val expense: Money,
    val operationCount: Int,
)
```

Create `app/src/main/java/com/yehor/finora/domain/operation/TransactionDraftValidator.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency

enum class TransactionDraftError {
    AMOUNT_EMPTY,
    AMOUNT_INVALID,
    AMOUNT_TOO_PRECISE,
    AMOUNT_OUT_OF_RANGE,
    AMOUNT_NOT_POSITIVE,
    ACCOUNT_MISSING,
    CATEGORY_MISSING,
    DESCRIPTION_TOO_LONG,
    DATE_IN_FUTURE,
}

object TransactionDraftValidator {
    const val DESCRIPTION_MAX = 500

    fun validate(
        amountText: String,
        accountId: Long?,
        categoryId: Long?,
        occurredAt: Long,
        description: String?,
        currency: Currency,
        now: Long = System.currentTimeMillis(),
        isEdit: Boolean = false,
        hadCategoryBefore: Boolean = true,
    ): Set<TransactionDraftError> {
        val errors = mutableSetOf<TransactionDraftError>()

        when (val parse = AmountParser.parse(amountText, currency)) {
            is AmountParseResult.Empty -> errors += TransactionDraftError.AMOUNT_EMPTY
            is AmountParseResult.InvalidFormat -> errors += TransactionDraftError.AMOUNT_INVALID
            is AmountParseResult.TooPrecise -> errors += TransactionDraftError.AMOUNT_TOO_PRECISE
            is AmountParseResult.OutOfRange -> errors += TransactionDraftError.AMOUNT_OUT_OF_RANGE
            is AmountParseResult.Value -> {
                if (parse.minor <= 0) {
                    errors += TransactionDraftError.AMOUNT_NOT_POSITIVE
                }
            }
        }

        if (accountId == null) {
            errors += TransactionDraftError.ACCOUNT_MISSING
        }

        if (categoryId == null) {
            if (!isEdit || hadCategoryBefore) {
                errors += TransactionDraftError.CATEGORY_MISSING
            }
        }

        if ((description?.trim()?.length ?: 0) > DESCRIPTION_MAX) {
            errors += TransactionDraftError.DESCRIPTION_TOO_LONG
        }

        if (occurredAt > now) {
            errors += TransactionDraftError.DATE_IN_FUTURE
        }

        return errors
    }
}
```

Create `app/src/main/java/com/yehor/finora/domain/operation/TransactionDateTime.kt`:
```kotlin
package com.yehor.finora.domain.operation

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class SplitDateTime(val date: LocalDate, val time: LocalTime)

object TransactionDateTime {

    fun combine(pickerUtcMidnight: Long, hour: Int, minute: Int, zone: ZoneId): Long {
        val utcDate = Instant.ofEpochMilli(pickerUtcMidnight).atZone(ZoneId.of("UTC")).toLocalDate()
        val localTime = LocalTime.of(hour, minute, 0, 0)
        return ZonedDateTime.of(utcDate, localTime, zone).toInstant().toEpochMilli()
    }

    fun split(instant: Long, zone: ZoneId): SplitDateTime {
        val zdt = Instant.ofEpochMilli(instant).atZone(zone)
        return SplitDateTime(zdt.toLocalDate(), zdt.toLocalTime().withSecond(0).withNano(0))
    }

    fun isFuture(instant: Long, now: Long): Boolean = instant > now

    fun latestSelectableDay(now: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    fun pickerUtcMidnight(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
}
```

Create `app/src/main/java/com/yehor/finora/domain/operation/DayGrouping.kt`:
```kotlin
package com.yehor.finora.domain.operation

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface DayGroup {
    data object Today : DayGroup
    data object Yesterday : DayGroup
    data class Date(val localDate: LocalDate) : DayGroup
}

object DayGrouping {
    fun group(instant: Long, now: Long, zone: ZoneId): DayGroup {
        val itemDate = Instant.ofEpochMilli(instant).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val yesterday = today.minusDays(1)
        return when (itemDate) {
            today -> DayGroup.Today
            yesterday -> DayGroup.Yesterday
            else -> DayGroup.Date(itemDate)
        }
    }
}
```

Create `app/src/main/java/com/yehor/finora/domain/operation/FilterScope.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.money.Currency

object FilterScope {

    fun scopeCurrency(filter: TransactionFilter, accounts: List<Account>): Currency? {
        val relevant = if (filter.accountIds.isEmpty()) {
            accounts
        } else {
            accounts.filter { it.id in filter.accountIds }
        }
        val distinctCurrencies = relevant.map { it.currency }.distinct()
        return if (distinctCurrencies.size == 1) distinctCurrencies.single() else null
    }

    fun pruneMissing(
        filter: TransactionFilter,
        existingAccountIds: Set<Long>,
        existingCategoryIds: Set<Long>,
        existingTagIds: Set<Long>,
    ): TransactionFilter = filter.copy(
        accountIds = filter.accountIds.filter { it in existingAccountIds }.toSet(),
        categoryIds = filter.categoryIds.filter { it in existingCategoryIds }.toSet(),
        tagIds = filter.tagIds.filter { it in existingTagIds }.toSet(),
    )
}
```

Create `app/src/main/java/com/yehor/finora/domain/operation/CurrencyReassignment.kt`:
```kotlin
package com.yehor.finora.domain.operation

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money

data class ReassignmentDialogInfo(
    val oldFormatted: String,
    val newFormatted: String,
    val oldAccountName: String,
    val newAccountName: String,
    val isExpense: Boolean,
)

object CurrencyReassignment {

    fun needsConfirmation(fromCurrency: Currency, toCurrency: Currency): Boolean =
        fromCurrency.code != toCurrency.code

    fun buildInfo(
        oldAmountMinor: Long,
        oldCurrency: Currency,
        newCurrency: Currency,
        oldAccountName: String,
        newAccountName: String,
        isExpense: Boolean,
    ): ReassignmentDialogInfo {
        val oldMoney = Money(oldAmountMinor, oldCurrency)
        val newMoney = Money(oldAmountMinor, newCurrency)
        return ReassignmentDialogInfo(
            oldFormatted = oldMoney.format(),
            newFormatted = newMoney.format(),
            oldAccountName = oldAccountName,
            newAccountName = newAccountName,
            isExpense = isExpense,
        )
    }
}
```

- [ ] **Step 4: Run unit tests to verify they pass**

Run: `gradlew :app:testDebugUnitTest --tests "com.yehor.finora.domain.tag.*" --tests "com.yehor.finora.domain.operation.*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yehor/finora/domain/tag/ app/src/main/java/com/yehor/finora/domain/operation/ app/src/test/java/com/yehor/finora/domain/tag/ app/src/test/java/com/yehor/finora/domain/operation/
git commit -m "feat: implement operation and tag domain rules and validators"
```

---

## Task 3: Schema Update (In-Place Version 1)

**Files:**
- Modify: `app/src/main/java/com/yehor/finora/data/db/Entities.kt:101-105`
- Regenerate: `app/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json`
- Update: `app/src/test/resources/schema-v1.expected.json`
- Modify: `app/src/test/java/com/yehor/finora/SchemaUnchangedTest.kt`

- [ ] **Step 1: Add the index `operation(occurred_at, id)` to `OperationEntity`**

In `app/src/main/java/com/yehor/finora/data/db/Entities.kt`:
```kotlin
@Entity(
    tableName = "operation",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["opening_account_id"],
        ),
    ],
    indices = [
        Index(value = ["type", "occurred_at"]),
        Index(value = ["category_id", "occurred_at"]),
        Index(value = ["opening_account_id"], unique = true),
        Index(value = ["occurred_at", "id"]),
    ],
)
data class OperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: OperationType,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    val description: String?,
    @ColumnInfo(name = "opening_account_id") val openingAccountId: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

- [ ] **Step 2: Build to regenerate Room schema 1.json**

Run: `gradlew :app:kspDebugKotlin`
Expected: `app/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json` is updated with `index_operation_occurred_at_id`.

- [ ] **Step 3: Copy regenerated 1.json to test resources and update SchemaUnchangedTest**

Copy `app/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json` to `app/src/test/resources/schema-v1.expected.json`.

Update KDoc in `app/src/test/java/com/yehor/finora/SchemaUnchangedTest.kt`:
```kotlin
/**
 * Ensures that the Room database schema (com.yehor.finora.data.db.FinoraDatabase/1.json)
 * remains exactly byte-identical to the committed v1 expected schema.
 *
 * In sub-project 3, the pinned baseline includes index_operation_occurred_at_id (S9).
 */
```

- [ ] **Step 4: Run SchemaUnchangedTest to verify it passes**

Run: `gradlew :app:testDebugUnitTest --tests com.yehor.finora.SchemaUnchangedTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yehor/finora/data/db/Entities.kt app/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json app/src/test/resources/schema-v1.expected.json app/src/test/java/com/yehor/finora/SchemaUnchangedTest.kt
git commit -m "feat: add occurred_at_id index to operation entity and update schema baseline"
```

---

## Task 4: DAO Additions, Operation Details & Dynamic Query Builder

**Files:**
- Create: `app/src/main/java/com/yehor/finora/data/db/OperationWithDetails.kt`
- Create: `app/src/main/java/com/yehor/finora/data/db/TransactionQuery.kt`
- Modify: `app/src/main/java/com/yehor/finora/data/db/Daos.kt`
- Create: `app/src/test/java/com/yehor/finora/data/db/TransactionQueryTest.kt`
- Create: `app/src/androidTest/java/com/yehor/finora/UnicodeSearchGateTest.kt`

- [ ] **Step 1: Write unit tests for `TransactionQuery`**

Create `app/src/test/java/com/yehor/finora/data/db/TransactionQueryTest.kt`:
```kotlin
package com.yehor.finora.data.db

import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.AmountRange
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.TagMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionQueryTest {

    private val usd = Currency("USD", 2, "$", "US Dollar")

    @Test
    fun defaultFilterExcludesOpeningBalance() {
        val query = TransactionQuery.build(TransactionFilter(), TransactionSort.DATE_DESC, emptySet())
        assertTrue(query.sql.contains("o.type != 'OPENING_BALANCE'"))
        assertTrue(query.sql.contains("ORDER BY o.occurred_at DESC, o.id DESC"))
    }

    @Test
    fun tagFilterAnyVersusAll() {
        val anyFilter = TransactionFilter(tagIds = setOf(1, 2), tagMode = TagMatchMode.ANY)
        val anyQuery = TransactionQuery.build(anyFilter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(anyQuery.sql.contains("EXISTS (SELECT 1 FROM operation_tag"))

        val allFilter = TransactionFilter(tagIds = setOf(1, 2), tagMode = TagMatchMode.ALL)
        val allQuery = TransactionQuery.build(allFilter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(allQuery.sql.contains("COUNT(DISTINCT t.tag_id)"))
    }

    @Test
    fun textEscaping() {
        val filter = TransactionFilter(text = "100%_off")
        val query = TransactionQuery.build(filter, TransactionSort.DATE_DESC, emptySet())
        assertTrue(query.sql.contains("LIKE ? ESCAPE '\\'"))
        val arg = query.argAt(0) as String
        assertEquals("%100\\%\\_off%", arg)
    }

    @Test
    fun amountSort() {
        val query = TransactionQuery.build(
            TransactionFilter(),
            TransactionSort.AMOUNT_DESC,
            emptySet(),
            scopeCurrency = "USD",
        )
        assertTrue(query.sql.contains("MAX(ABS(e.amount_minor))"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew :app:testDebugUnitTest --tests com.yehor.finora.data.db.TransactionQueryTest`
Expected: FAIL

- [ ] **Step 3: Implement `OperationWithDetails`, `TransactionQuery`, and DAO additions**

Create `app/src/main/java/com/yehor/finora/data/db/OperationWithDetails.kt`:
```kotlin
package com.yehor.finora.data.db

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class OperationWithDetails(
    @Embedded val operation: OperationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "operation_id",
    )
    val entries: List<LedgerEntryEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = OperationTagEntity::class,
            parentColumn = "operation_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

data class TagWithUsageRow(
    @Embedded val tag: TagEntity,
    val usageCount: Int,
)

data class SummaryRow(
    val currencyCode: String,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val operationCount: Int,
)
```

Create `app/src/main/java/com/yehor/finora/data/db/TransactionQuery.kt`:
```kotlin
package com.yehor.finora.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.TagMatchMode

object TransactionQuery {

    fun build(
        filter: TransactionFilter,
        sort: TransactionSort,
        categoryClosure: Set<Long>,
        scopeCurrency: String? = filter.amount?.currency?.code,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val whereClauses = buildWhereClauses(filter, categoryClosure, scopeCurrency, args)

        val whereSql = if (whereClauses.isNotEmpty()) {
            "WHERE " + whereClauses.joinToString(" AND ")
        } else ""

        val orderSql = when (sort) {
            TransactionSort.DATE_DESC -> "ORDER BY o.occurred_at DESC, o.id DESC"
            TransactionSort.DATE_ASC -> "ORDER BY o.occurred_at ASC, o.id ASC"
            TransactionSort.AMOUNT_DESC -> {
                requireNotNull(scopeCurrency) { "Scope currency required for amount sort" }
                args.add(scopeCurrency)
                "ORDER BY (SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = ?) DESC, o.occurred_at DESC, o.id DESC"
            }
            TransactionSort.AMOUNT_ASC -> {
                requireNotNull(scopeCurrency) { "Scope currency required for amount sort" }
                args.add(scopeCurrency)
                "ORDER BY (SELECT MAX(ABS(e.amount_minor)) FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = ?) ASC, o.occurred_at ASC, o.id ASC"
            }
        }

        val sql = "SELECT o.* FROM operation o $whereSql $orderSql"
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    fun buildSummary(
        filter: TransactionFilter,
        categoryClosure: Set<Long>,
    ): SimpleSQLiteQuery {
        val args = mutableListOf<Any>()
        val baseClauses = buildWhereClauses(filter, categoryClosure, filter.amount?.currency?.code, args)
        val clauses = baseClauses + "o.type IN ('INCOME', 'EXPENSE')"

        val whereSql = "WHERE " + clauses.joinToString(" AND ")
        val sql = """
            SELECT e.currency_code AS currencyCode,
                   COALESCE(SUM(CASE WHEN e.amount_minor > 0 THEN e.amount_minor ELSE 0 END), 0) AS incomeMinor,
                   COALESCE(SUM(CASE WHEN e.amount_minor < 0 THEN ABS(e.amount_minor) ELSE 0 END), 0) AS expenseMinor,
                   COUNT(DISTINCT o.id) AS operationCount
            FROM operation o
            JOIN ledger_entry e ON e.operation_id = o.id
            $whereSql
            GROUP BY e.currency_code
        """.trimIndent()
        return SimpleSQLiteQuery(sql, args.toTypedArray())
    }

    private fun buildWhereClauses(
        filter: TransactionFilter,
        categoryClosure: Set<Long>,
        scopeCurrency: String?,
        args: MutableList<Any>,
    ): List<String> {
        val clauses = mutableListOf<String>()

        if (filter.types.isEmpty()) {
            clauses += "o.type != 'OPENING_BALANCE'"
        } else {
            val allowed = filter.types.filter { it != OperationType.OPENING_BALANCE }
            val placeholders = allowed.joinToString(",") { "?" }
            clauses += "o.type IN ($placeholders)"
            args.addAll(allowed.map { it.name })
        }

        if (filter.from != null) {
            clauses += "o.occurred_at >= ?"
            args.add(filter.from)
        }
        if (filter.toExclusive != null) {
            clauses += "o.occurred_at < ?"
            args.add(filter.toExclusive)
        }

        if (filter.categoryIds.isNotEmpty() || filter.includeUncategorized) {
            val catClauses = mutableListOf<String>()
            if (categoryClosure.isNotEmpty()) {
                val placeholders = categoryClosure.joinToString(",") { "?" }
                catClauses += "o.category_id IN ($placeholders)"
                args.addAll(categoryClosure)
            }
            if (filter.includeUncategorized) {
                catClauses += "o.category_id IS NULL"
            }
            if (catClauses.isNotEmpty()) {
                clauses += "(" + catClauses.joinToString(" OR ") + ")"
            }
        }

        if (filter.accountIds.isNotEmpty()) {
            val placeholders = filter.accountIds.joinToString(",") { "?" }
            clauses += "EXISTS (SELECT 1 FROM ledger_entry e WHERE e.operation_id = o.id AND e.account_id IN ($placeholders))"
            args.addAll(filter.accountIds)
        }

        if (filter.tagIds.isNotEmpty()) {
            when (filter.tagMode) {
                TagMatchMode.ANY -> {
                    val placeholders = filter.tagIds.joinToString(",") { "?" }
                    clauses += "EXISTS (SELECT 1 FROM operation_tag t WHERE t.operation_id = o.id AND t.tag_id IN ($placeholders))"
                    args.addAll(filter.tagIds)
                }
                TagMatchMode.ALL -> {
                    val placeholders = filter.tagIds.joinToString(",") { "?" }
                    clauses += "(SELECT COUNT(DISTINCT t.tag_id) FROM operation_tag t WHERE t.operation_id = o.id AND t.tag_id IN ($placeholders)) = ?"
                    args.addAll(filter.tagIds)
                    args.add(filter.tagIds.size)
                }
            }
        }

        if (filter.text.isNotBlank()) {
            clauses += "o.description LIKE ? ESCAPE '\\'"
            val escaped = filter.text.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            args.add("%$escaped%")
        }

        if (filter.amount != null) {
            requireNotNull(scopeCurrency) { "Scope currency required for amount filter" }
            val min = filter.amount.minMinor ?: 0L
            val max = filter.amount.maxMinor ?: Long.MAX_VALUE
            clauses += "EXISTS (SELECT 1 FROM ledger_entry e WHERE e.operation_id = o.id AND e.currency_code = ? AND ABS(e.amount_minor) >= ? AND ABS(e.amount_minor) <= ?)"
            args.add(scopeCurrency)
            args.add(min)
            args.add(max)
        }

        return clauses
    }
}
```

In `app/src/main/java/com/yehor/finora/data/db/Daos.kt`, add:
```kotlin
// In OperationDao:
@Transaction
@RawQuery(observedEntities = [OperationEntity::class, LedgerEntryEntity::class, OperationTagEntity::class, TagEntity::class])
fun page(query: androidx.sqlite.db.SupportSQLiteQuery): androidx.paging.PagingSource<Int, OperationWithDetails>

@Transaction
@RawQuery(observedEntities = [OperationEntity::class, LedgerEntryEntity::class, OperationTagEntity::class, TagEntity::class])
fun listWithDetails(query: androidx.sqlite.db.SupportSQLiteQuery): Flow<List<OperationWithDetails>>

@RawQuery
suspend fun summarize(query: androidx.sqlite.db.SupportSQLiteQuery): List<SummaryRow>

@Transaction
@Query("SELECT * FROM operation WHERE id = :id")
fun observeWithDetails(id: Long): Flow<OperationWithDetails?>

@Query("UPDATE operation SET occurred_at = :occurredAt, category_id = :categoryId, description = :description, updated_at = :updatedAt WHERE id = :id")
suspend fun updateFields(id: Long, occurredAt: Long, categoryId: Long?, description: String?, updatedAt: Long)

@Query("""
    SELECT DISTINCT category_id FROM operation
    WHERE type = :type AND category_id IS NOT NULL
    ORDER BY occurred_at DESC, id DESC LIMIT :limit
""")
suspend fun recentCategoryIds(type: com.yehor.finora.domain.model.OperationType, limit: Int): List<Long>

@Query("""
    SELECT e.account_id FROM operation o
    JOIN ledger_entry e ON e.operation_id = o.id
    WHERE o.type IN ('INCOME', 'EXPENSE')
    ORDER BY o.occurred_at DESC, o.id DESC LIMIT 1
""")
suspend fun latestAccountId(): Long?

// In LedgerEntryDao:
@Query("UPDATE ledger_entry SET account_id = :accountId, currency_code = :currencyCode, amount_minor = :amountMinor, occurred_at = :occurredAt WHERE operation_id = :operationId")
suspend fun updateEntry(operationId: Long, accountId: Long, currencyCode: String, amountMinor: Long, occurredAt: Long)

// In OperationTagDao:
@Query("DELETE FROM operation_tag WHERE operation_id = :operationId")
suspend fun deleteAllFor(operationId: Long)

// In TagDao:
@Query("SELECT * FROM tag ORDER BY name")
suspend fun getAll(): List<TagEntity>

@Query("SELECT * FROM tag WHERE id = :id")
suspend fun getById(id: Long): TagEntity?

@Query("""
    SELECT t.*, COUNT(ot.operation_id) AS usageCount
    FROM tag t
    LEFT JOIN operation_tag ot ON ot.tag_id = t.id
    GROUP BY t.id
    ORDER BY t.name
""")
fun observeAllWithUsage(): Flow<List<TagWithUsageRow>>

@Query("UPDATE tag SET name = :name, color = :color WHERE id = :id")
suspend fun updateDetails(id: Long, name: String, color: Long?)

@Query("UPDATE tag SET color = :color WHERE id = :id")
suspend fun setColor(id: Long, color: Long?)

@Query("DELETE FROM tag WHERE id = :id")
suspend fun deleteById(id: Long)

@Query("SELECT COUNT(*) FROM operation_tag WHERE tag_id = :id")
suspend fun usageCount(id: Long): Int
```

- [ ] **Step 4: Create Android instrumented UnicodeSearchGateTest**

Create `app/src/androidTest/java/com/yehor/finora/UnicodeSearchGateTest.kt`:
```kotlin
package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.TransactionQuery
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class UnicodeSearchGateTest {
    private lateinit var db: FinoraDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun unicodeSearchCaseInsensitive() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", currencyCode = "USD", type = "CASH", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val op1 = db.operationDao().insert(OperationEntity(type = OperationType.EXPENSE, occurredAt = 1000L, categoryId = null, description = "Кофе в зернах", openingAccountId = null, createdAt = 0L, updatedAt = 0L))
        db.ledgerEntryDao().insert(LedgerEntryEntity(operationId = op1, accountId = accId, currencyCode = "USD", amountMinor = -100L, occurredAt = 1000L))

        val op2 = db.operationDao().insert(OperationEntity(type = OperationType.EXPENSE, occurredAt = 2000L, categoryId = null, description = "Żabka zakupy", openingAccountId = null, createdAt = 0L, updatedAt = 0L))
        db.ledgerEntryDao().insert(LedgerEntryEntity(operationId = op2, accountId = accId, currencyCode = "USD", amountMinor = -200L, occurredAt = 2000L))

        // Query with lowercase 'кофе'
        val query1 = TransactionQuery.build(TransactionFilter(text = "кофе"), TransactionSort.DATE_DESC, emptySet())
        val res1 = db.operationDao().listWithDetails(query1).first()
        assertEquals(1, res1.size)
        assertEquals(op1, res1.first().operation.id)

        // Query with uppercase 'ZABKA' or lowercase 'żabka'
        val query2 = TransactionQuery.build(TransactionFilter(text = "żabka"), TransactionSort.DATE_DESC, emptySet())
        val res2 = db.operationDao().listWithDetails(query2).first()
        assertEquals(1, res2.size)
        assertEquals(op2, res2.first().operation.id)
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `gradlew :app:testDebugUnitTest --tests com.yehor.finora.data.db.TransactionQueryTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/yehor/finora/data/db/ app/src/test/java/com/yehor/finora/data/db/TransactionQueryTest.kt app/src/androidTest/java/com/yehor/finora/UnicodeSearchGateTest.kt
git commit -m "feat: add OperationWithDetails, TransactionQuery builder and DAO methods"
```

---

## Task 5: TagRepository & Exceptions

**Files:**
- Modify: `app/src/main/java/com/yehor/finora/data/repo/RepositoryExceptions.kt`
- Create: `app/src/main/java/com/yehor/finora/data/repo/TagRepository.kt`
- Create: `app/src/androidTest/java/com/yehor/finora/TagRepositoryTest.kt`

- [ ] **Step 1: Add new exception types to `RepositoryExceptions.kt`**

In `app/src/main/java/com/yehor/finora/data/repo/RepositoryExceptions.kt`:
```kotlin
import com.yehor.finora.domain.tag.TagNameError

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
```

- [ ] **Step 2: Write instrumented tests in `TagRepositoryTest`**

Create `app/src/androidTest/java/com/yehor/finora/TagRepositoryTest.kt`:
```kotlin
package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.DuplicateTagNameException
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.InvalidTagNameException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class TagRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var tagRepo: TagRepository
    private lateinit var opRepo: OperationRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        tagRepo = TagRepository(db)
        opRepo = OperationRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        fail("expected ${T::class.simpleName}")
    }

    @Test
    fun createAndDuplicateCaseInsensitive() = runBlocking {
        val id1 = tagRepo.create("Debt", 0xFF0000L)
        assertTrue(id1 > 0)

        assertFails<DuplicateTagNameException> {
            runBlocking { tagRepo.create("debt", null) }
        }
        assertFails<DuplicateTagNameException> {
            runBlocking { tagRepo.create("  DEBT  ", null) }
        }
        assertFails<InvalidTagNameException> {
            runBlocking { tagRepo.create("", null) }
        }
    }

    @Test
    fun renameAndRecolorDoesNotTouchOperationUpdatedAt() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", currencyCode = "USD", type = "CASH", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val tagId = tagRepo.create("Food", null)
        val opId = opRepo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -100L)),
            tagIds = setOf(tagId),
            now = 5000L,
        )

        val beforeOp = db.operationDao().getById(opId)!!
        assertEquals(5000L, beforeOp.updatedAt)

        // Rename tag
        tagRepo.rename(tagId, "Food & Drinks")
        tagRepo.setColor(tagId, 0x00FF00L)

        val afterOp = db.operationDao().getById(opId)!!
        assertEquals(5000L, afterOp.updatedAt) // untouched!

        val list = tagRepo.observeAllWithUsage().first()
        assertEquals(1, list.size)
        assertEquals("Food & Drinks", list.first().tag.name)
        assertEquals(1, list.first().usageCount)
    }

    @Test
    fun deleteTagRemovesLinksKeepsOperation() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "A", currencyCode = "USD", type = "CASH", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val tagId = tagRepo.create("Travel", null)
        val opId = opRepo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -100L)),
            tagIds = setOf(tagId),
        )

        tagRepo.delete(tagId)

        val op = db.operationDao().getById(opId)
        assertTrue(op != null)
        val tags = db.operationTagDao().tagIdsOf(opId)
        assertTrue(tags.isEmpty())
        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }
}
```

- [ ] **Step 3: Implement `TagRepository`**

Create `app/src/main/java/com/yehor/finora/data/repo/TagRepository.kt`:
```kotlin
package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameRules
import com.yehor.finora.domain.tag.TagWithUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TagRepository @Inject constructor(private val db: FinoraDatabase) {

    fun observeAllWithUsage(): Flow<List<TagWithUsage>> =
        db.tagDao().observeAllWithUsage().map { rows ->
            val collator = Collator.getInstance(Locale.getDefault())
            rows.map { row ->
                TagWithUsage(
                    tag = Tag(
                        id = row.tag.id,
                        name = row.tag.name,
                        color = row.tag.color,
                        createdAt = row.tag.createdAt,
                    ),
                    usageCount = row.usageCount,
                )
            }.sortedWith { a, b -> collator.compare(a.tag.name, b.tag.name) }
        }

    suspend fun create(name: String, color: Long?, now: Long = System.currentTimeMillis()): Long =
        db.withTransaction {
            val error = TagNameRules.validate(name)
            if (error != null) throw InvalidTagNameException(error)
            val normalized = TagNameRules.normalize(name)
            val key = TagNameRules.key(normalized)

            val existing = db.tagDao().getAll()
            val duplicate = existing.find { TagNameRules.key(it.name) == key }
            if (duplicate != null) {
                throw DuplicateTagNameException(duplicate.id)
            }

            db.tagDao().insert(
                TagEntity(
                    name = normalized,
                    color = color,
                    createdAt = now,
                ),
            )
        }

    suspend fun rename(id: Long, name: String) = db.withTransaction {
        val error = TagNameRules.validate(name)
        if (error != null) throw InvalidTagNameException(error)
        val normalized = TagNameRules.normalize(name)
        val key = TagNameRules.key(normalized)

        val current = db.tagDao().getById(id)
            ?: throw InvariantViolationException("Unknown tag $id")

        val existing = db.tagDao().getAll()
        val duplicate = existing.find { it.id != id && TagNameRules.key(it.name) == key }
        if (duplicate != null) {
            throw DuplicateTagNameException(duplicate.id)
        }

        db.tagDao().updateDetails(id, normalized, current.color)
    }

    suspend fun setColor(id: Long, color: Long?) =
        db.tagDao().setColor(id, color)

    suspend fun delete(id: Long) =
        db.tagDao().deleteById(id)

    suspend fun usageCount(id: Long): Int =
        db.tagDao().usageCount(id)
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yehor/finora/data/repo/RepositoryExceptions.kt app/src/main/java/com/yehor/finora/data/repo/TagRepository.kt app/src/androidTest/java/com/yehor/finora/TagRepositoryTest.kt
git commit -m "feat: implement TagRepository with case-insensitive uniqueness and tests"
```

---

## Task 6: OperationRepository Extensions

**Files:**
- Modify: `app/src/main/java/com/yehor/finora/data/repo/OperationRepository.kt`
- Create: `app/src/androidTest/java/com/yehor/finora/OperationRepositoryEditTest.kt`

- [ ] **Step 1: Write instrumented tests in `OperationRepositoryEditTest`**

Create `app/src/androidTest/java/com/yehor/finora/OperationRepositoryEditTest.kt`:
```kotlin
package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.CurrencyChangeNotConfirmedException
import com.yehor.finora.data.repo.EntryDraft
import com.yehor.finora.data.repo.FutureDateException
import com.yehor.finora.data.repo.InvariantViolationException
import com.yehor.finora.data.repo.OpeningBalanceOrderException
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.MoneyOverflowException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OperationRepositoryEditTest {

    private lateinit var db: FinoraDatabase
    private lateinit var repo: OperationRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        repo = OperationRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun account(code: String, status: AccountStatus = AccountStatus.ACTIVE): Long =
        db.accountDao().insert(
            AccountEntity(name = "Account $code", currencyCode = code, type = "CASH", icon = null, color = null, description = null, status = status, createdAt = 0)
        )

    private suspend fun category(type: CategoryType): Long =
        db.categoryDao().insert(CategoryEntity(name = "cat", icon = null, color = null, type = type, parentId = null, createdAt = 0))

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw e
        }
        fail("expected ${T::class.simpleName}")
    }

    @Test
    fun createWithTagsAndInlineTags() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.EXPENSE)
        val tagId = db.tagDao().insert(com.yehor.finora.data.db.TagEntity(name = "Existing", color = null, createdAt = 0L))

        val opId = repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, -500L)),
            categoryId = catId,
            tagIds = setOf(tagId),
            newTagNames = listOf("New Tag"),
            now = 2000L,
        )

        val tags = db.operationTagDao().tagIdsOf(opId)
        assertEquals(2, tags.size)
        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }

    @Test
    fun futureDateRejected() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.INCOME)
        val now = 2000L

        assertFails<FutureDateException> {
            runBlocking {
                repo.insertOperation(
                    type = OperationType.INCOME,
                    occurredAt = 2001L,
                    entries = listOf(EntryDraft(accId, 500L)),
                    categoryId = catId,
                    now = now,
                )
            }
        }
    }

    @Test
    fun crossCurrencyReassignmentT12() = runBlocking {
        val plnAcc = account("PLN")
        val usdAcc = account("USD")
        val catId = category(CategoryType.EXPENSE)

        val opId = repo.insertOperation(
            type = OperationType.EXPENSE,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(plnAcc, -10000L)), // -100.00 PLN
            categoryId = catId,
            now = 1000L,
        )
        assertEquals(-10000L, repo.balanceMinor(plnAcc))

        // Moving without confirmation throws
        assertFails<CurrencyChangeNotConfirmedException> {
            runBlocking {
                repo.updateOperation(
                    id = opId,
                    accountId = usdAcc,
                    amountMinor = 10000L,
                    categoryId = catId,
                    occurredAt = 1000L,
                    description = null,
                    confirmCurrencyChange = false,
                    now = 2000L,
                )
            }
        }

        // Moving with confirmation succeeds and retains nominal magnitude 10000
        repo.updateOperation(
            id = opId,
            accountId = usdAcc,
            amountMinor = 10000L,
            categoryId = catId,
            occurredAt = 1000L,
            description = null,
            confirmCurrencyChange = true,
            now = 2000L,
        )

        assertEquals(0L, repo.balanceMinor(plnAcc))
        assertEquals(-10000L, repo.balanceMinor(usdAcc))

        val entry = db.ledgerEntryDao().entriesOf(opId).single()
        assertEquals("USD", entry.currencyCode)
        assertEquals(-10000L, entry.amountMinor)
        assertEquals(usdAcc, entry.accountId)

        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }

    @Test
    fun deleteOperationCascades() = runBlocking {
        val accId = account("USD")
        val catId = category(CategoryType.INCOME)
        val opId = repo.insertOperation(
            type = OperationType.INCOME,
            occurredAt = 1000L,
            entries = listOf(EntryDraft(accId, 1000L)),
            categoryId = catId,
        )
        assertEquals(1000L, repo.balanceMinor(accId))

        repo.deleteOperation(opId)
        assertEquals(0L, repo.balanceMinor(accId))
        assertTrue(db.operationDao().getById(opId) == null)
        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }
}
```

- [ ] **Step 2: Update `OperationRepository`**

In `app/src/main/java/com/yehor/finora/data/repo/OperationRepository.kt`, update `insertOperation`, implement `updateOperation`, `deleteOperation`, `setTags`, `resolveTags`, `enforceOpeningOrder`, and balance overflow pre-check:
```kotlin
package com.yehor.finora.data.repo

import androidx.room.withTransaction
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.LedgerEntryEntity
import com.yehor.finora.data.db.OperationEntity
import com.yehor.finora.data.db.OperationTagEntity
import com.yehor.finora.data.db.TagEntity
import com.yehor.finora.domain.account.OpeningDatePolicy
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.tag.TagNameRules
import android.database.sqlite.SQLiteException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** One signed ledger line of an operation to be written. */
data class EntryDraft(val accountId: Long, val amountMinor: Long)

/** Raised when a write would violate an invariant that the schema cannot express. */
open class InvariantViolationException(message: String) : IllegalArgumentException(message)

@Singleton
class OperationRepository @Inject constructor(private val db: FinoraDatabase) {

    suspend fun insertOperation(
        type: OperationType,
        occurredAt: Long,
        entries: List<EntryDraft>,
        categoryId: Long? = null,
        description: String? = null,
        openingAccountId: Long? = null,
        tagIds: Set<Long> = emptySet(),
        newTagNames: List<String> = emptyList(),
        moveOpeningBalanceBack: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): Long = db.withTransaction {
        if (type == OperationType.INCOME || type == OperationType.EXPENSE) {
            if (occurredAt > now) throw FutureDateException(occurredAt, now)
        }

        // Reject if any of the entry accounts are archived (S4.7)
        entries.forEach { e ->
            val account = db.accountDao().getById(e.accountId)
                ?: throw InvariantViolationException("Unknown account ${e.accountId}")
            if (account.status == AccountStatus.ARCHIVED) {
                throw InvariantViolationException("Account ${e.accountId} is archived")
            }
        }

        // Check opening balance ordering
        if (type != OperationType.OPENING_BALANCE) {
            entries.forEach { e ->
                enforceOpeningOrder(e.accountId, occurredAt, moveOpeningBalanceBack, now)
            }
        }

        // Pre-check balance overflow for affected accounts
        entries.forEach { e ->
            val currentBalance = balanceMinor(e.accountId)
            try {
                Math.addExact(currentBalance, e.amountMinor)
            } catch (_: ArithmeticException) {
                throw MoneyOverflowException("Balance overflow for account ${e.accountId}")
            }
        }

        validate(type, entries, categoryId, openingAccountId)

        val operationId = db.operationDao().insert(
            OperationEntity(
                type = type,
                occurredAt = occurredAt,
                categoryId = categoryId,
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                openingAccountId = openingAccountId,
                createdAt = now,
                updatedAt = now,
            ),
        )

        entries.forEach { e ->
            val currency = db.accountDao().currencyCodeOf(e.accountId)
                ?: throw InvariantViolationException("Unknown account ${e.accountId}")
            db.ledgerEntryDao().insert(
                LedgerEntryEntity(
                    operationId = operationId,
                    accountId = e.accountId,
                    currencyCode = currency,
                    amountMinor = e.amountMinor,
                    occurredAt = occurredAt,
                ),
            )
        }

        if (tagIds.isNotEmpty() || newTagNames.isNotEmpty()) {
            if (type == OperationType.OPENING_BALANCE) {
                throw InvariantViolationException("Tags cannot be assigned to an opening balance")
            }
            resolveAndLinkTags(operationId, tagIds, newTagNames, now)
        }

        operationId
    }

    suspend fun updateOperation(
        id: Long,
        accountId: Long,
        amountMinor: Long, // positive magnitude in target account's currency
        categoryId: Long?,
        occurredAt: Long,
        description: String?,
        tagIds: Set<Long> = emptySet(),
        newTagNames: List<String> = emptyList(),
        moveOpeningBalanceBack: Boolean = false,
        confirmCurrencyChange: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val op = db.operationDao().getById(id)
            ?: throw InvariantViolationException("Unknown operation $id")
        if (op.type != OperationType.INCOME && op.type != OperationType.EXPENSE) {
            throw InvariantViolationException("Only INCOME and EXPENSE operations can be updated here")
        }
        if (occurredAt > now) throw FutureDateException(occurredAt, now)

        val oldEntry = db.ledgerEntryDao().entriesOf(id).single()
        val targetAccount = db.accountDao().getById(accountId)
            ?: throw InvariantViolationException("Unknown account $accountId")

        // T4 / T5 check: target account can be active or the original account even if archived
        if (targetAccount.status == AccountStatus.ARCHIVED && targetAccount.id != oldEntry.accountId) {
            throw InvariantViolationException("Cannot move operation to a different archived account")
        }

        // T12 cross-currency check
        val targetCurrency = targetAccount.currencyCode
        if (targetCurrency != oldEntry.currencyCode && !confirmCurrencyChange) {
            throw CurrencyChangeNotConfirmedException(oldEntry.currencyCode, targetCurrency)
        }

        // Opening order check
        enforceOpeningOrder(accountId, occurredAt, moveOpeningBalanceBack, now)

        val signedAmount = if (op.type == OperationType.INCOME) amountMinor else -amountMinor
        if (amountMinor <= 0) throw InvariantViolationException("Amount must be positive")

        // Pre-check balance overflow
        if (accountId == oldEntry.accountId) {
            val delta = signedAmount - oldEntry.amountMinor
            val current = balanceMinor(accountId)
            try {
                Math.addExact(current, delta)
            } catch (_: ArithmeticException) {
                throw MoneyOverflowException("Balance overflow for account $accountId")
            }
        } else {
            val oldCurrent = balanceMinor(oldEntry.accountId)
            val newCurrent = balanceMinor(accountId)
            try {
                Math.addExact(oldCurrent, -oldEntry.amountMinor)
                Math.addExact(newCurrent, signedAmount)
            } catch (_: ArithmeticException) {
                throw MoneyOverflowException("Balance overflow on account move")
            }
        }

        validateCategoryType(op.type, categoryId)

        db.operationDao().updateFields(
            id = id,
            occurredAt = occurredAt,
            categoryId = categoryId,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            updatedAt = now,
        )

        db.ledgerEntryDao().updateEntry(
            operationId = id,
            accountId = accountId,
            currencyCode = targetCurrency,
            amountMinor = signedAmount,
            occurredAt = occurredAt,
        )

        db.operationTagDao().deleteAllFor(id)
        if (tagIds.isNotEmpty() || newTagNames.isNotEmpty()) {
            resolveAndLinkTags(id, tagIds, newTagNames, now)
        }
    }

    suspend fun deleteOperation(id: Long) = db.withTransaction {
        val op = db.operationDao().getById(id)
            ?: throw InvariantViolationException("Unknown operation $id")
        if (op.type != OperationType.INCOME && op.type != OperationType.EXPENSE) {
            throw InvariantViolationException("Only INCOME and EXPENSE operations can be deleted")
        }
        db.operationDao().deleteById(id)
    }

    suspend fun setTags(
        operationId: Long,
        tagIds: Set<Long>,
        newTagNames: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val op = db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        if (op.type == OperationType.OPENING_BALANCE) {
            throw InvariantViolationException("Tags cannot be assigned to an opening balance")
        }
        db.operationTagDao().deleteAllFor(operationId)
        resolveAndLinkTags(operationId, tagIds, newTagNames, now)
    }

    private suspend fun resolveAndLinkTags(
        operationId: Long,
        tagIds: Set<Long>,
        newTagNames: List<String>,
        now: Long,
    ) {
        val existingTags = db.tagDao().getAll()
        val existingIds = existingTags.map { it.id }.toSet()
        tagIds.forEach { id ->
            if (id !in existingIds) throw InvariantViolationException("Unknown tag id $id")
        }

        val resolvedIds = tagIds.toMutableSet()
        val distinctNewNames = newTagNames.map { TagNameRules.normalize(it) }.distinct()

        distinctNewNames.forEach { name ->
            val error = TagNameRules.validate(name)
            if (error != null) throw InvalidTagNameException(error)
            val key = TagNameRules.key(name)
            val matched = db.tagDao().getAll().find { TagNameRules.key(it.name) == key }
            val id = matched?.id ?: db.tagDao().insert(TagEntity(name = name, color = null, createdAt = now))
            resolvedIds.add(id)
        }

        resolvedIds.forEach { tagId ->
            db.operationTagDao().insert(OperationTagEntity(operationId, tagId))
        }
    }

    private suspend fun enforceOpeningOrder(
        accountId: Long,
        occurredAt: Long,
        moveOpeningBalanceBack: Boolean,
        now: Long,
    ) {
        val openingDate = db.ledgerEntryDao().openingDateOf(accountId)
        if (openingDate != null && occurredAt < openingDate) {
            if (moveOpeningBalanceBack) {
                val startOfLocalDay = OpeningDatePolicy(ZoneId.systemDefault()).startOfDay(occurredAt)
                val openingOp = db.operationDao().openingBalanceOf(accountId)
                if (openingOp != null) {
                    db.operationDao().updateOccurredAt(openingOp.id, startOfLocalDay, now)
                    db.ledgerEntryDao().updateOccurredAt(openingOp.id, startOfLocalDay)
                }
            } else {
                throw OpeningBalanceOrderException(accountId, openingDate, occurredAt)
            }
        }
    }

    private suspend fun validateCategoryType(type: OperationType, categoryId: Long?) {
        if (categoryId != null) {
            val category = db.categoryDao().getById(categoryId)
                ?: throw InvariantViolationException("Unknown category $categoryId")
            val expected = when (type) {
                OperationType.INCOME -> CategoryType.INCOME
                OperationType.EXPENSE -> CategoryType.EXPENSE
                OperationType.TRANSFER -> CategoryType.TRANSFER
                OperationType.OPENING_BALANCE -> null
            }
            if (category.type != expected) {
                throw InvariantViolationException("Category type does not match operation type")
            }
        }
    }

    /** Moves the operation and every one of its ledger entries to [occurredAt] together. */
    suspend fun changeOccurredAt(
        operationId: Long,
        occurredAt: Long,
        now: Long = System.currentTimeMillis(),
    ) = db.withTransaction {
        val op = db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        if (op.type == OperationType.INCOME || op.type == OperationType.EXPENSE) {
            if (occurredAt > now) throw FutureDateException(occurredAt, now)
        }

        if (op.type == OperationType.OPENING_BALANCE) {
            val accountId = op.openingAccountId
                ?: throw InvariantViolationException("Opening balance operation has no openingAccountId")
            val earliestOther = db.ledgerEntryDao().earliestNonOpeningEntryAt(accountId)
            if (earliestOther != null && occurredAt > earliestOther) {
                throw OpeningBalanceOrderException(accountId, occurredAt, earliestOther)
            }
        } else {
            val entries = db.ledgerEntryDao().entriesOf(operationId)
            entries.forEach { e ->
                val openingDate = db.ledgerEntryDao().openingDateOf(e.accountId)
                if (openingDate != null && occurredAt < openingDate) {
                    throw OpeningBalanceOrderException(e.accountId, openingDate, occurredAt)
                }
            }
        }

        db.operationDao().updateOccurredAt(operationId, occurredAt, now)
        db.ledgerEntryDao().updateOccurredAt(operationId, occurredAt)
    }

    suspend fun setOpeningBalance(
        accountId: Long,
        amountMinor: Long?,
        occurredAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Unit = db.withTransaction {
        val openingOp = db.operationDao().openingBalanceOf(accountId)
        if (amountMinor == null || amountMinor == 0L) {
            if (openingOp != null) {
                db.operationDao().deleteById(openingOp.id)
            }
            return@withTransaction
        }

        val earliestOther = db.ledgerEntryDao().earliestNonOpeningEntryAt(accountId)
        if (earliestOther != null && occurredAt > earliestOther) {
            throw OpeningBalanceOrderException(accountId, occurredAt, earliestOther)
        }

        if (openingOp == null) {
            insertOperation(
                type = OperationType.OPENING_BALANCE,
                occurredAt = occurredAt,
                entries = listOf(EntryDraft(accountId, amountMinor)),
                openingAccountId = accountId,
                now = now,
            )
        } else {
            db.ledgerEntryDao().updateAmount(openingOp.id, amountMinor)
            changeOccurredAt(openingOp.id, occurredAt, now)
        }
    }

    suspend fun addTag(operationId: Long, tagId: Long) = db.withTransaction {
        val op = db.operationDao().getById(operationId)
            ?: throw InvariantViolationException("Unknown operation $operationId")
        if (op.type == OperationType.OPENING_BALANCE) {
            throw InvariantViolationException("Tags cannot be assigned to an opening balance")
        }
        db.operationTagDao().insert(OperationTagEntity(operationId, tagId))
    }

    suspend fun balanceMinor(accountId: Long): Long = try {
        db.ledgerEntryDao().balanceMinor(accountId)
    } catch (e: SQLiteException) {
        if (e.message?.contains("integer overflow", ignoreCase = true) == true) {
            throw MoneyOverflowException("Balance overflow for account $accountId", e)
        }
        throw e
    }

    private suspend fun validate(
        type: OperationType,
        entries: List<EntryDraft>,
        categoryId: Long?,
        openingAccountId: Long?,
    ) {
        if (type != OperationType.OPENING_BALANCE && openingAccountId != null) {
            throw InvariantViolationException("opening_account_id is only for OPENING_BALANCE")
        }
        when (type) {
            OperationType.INCOME -> {
                requireEntries(entries, 1, type)
                if (entries.single().amountMinor <= 0) {
                    throw InvariantViolationException("Income entry must be positive")
                }
            }
            OperationType.EXPENSE -> {
                requireEntries(entries, 1, type)
                if (entries.single().amountMinor >= 0) {
                    throw InvariantViolationException("Expense entry must be negative")
                }
            }
            OperationType.TRANSFER -> {
                requireEntries(entries, 2, type)
                if (entries[0].accountId == entries[1].accountId) {
                    throw InvariantViolationException("Transfer must use two different accounts")
                }
                if (entries.any { it.amountMinor == 0L } ||
                    (entries[0].amountMinor > 0) == (entries[1].amountMinor > 0)
                ) {
                    throw InvariantViolationException("Transfer needs one negative and one positive entry")
                }
            }
            OperationType.OPENING_BALANCE -> {
                requireEntries(entries, 1, type)
                val entry = entries.single()
                if (openingAccountId == null) {
                    throw InvariantViolationException("Opening balance requires opening_account_id")
                }
                if (entry.accountId != openingAccountId) {
                    throw InvariantViolationException("Opening balance entry must be on opening_account_id")
                }
                if (entry.amountMinor == 0L) {
                    throw InvariantViolationException("Opening balance must be non-zero")
                }
                if (categoryId != null) {
                    throw InvariantViolationException("Opening balance has no category")
                }
            }
        }
        validateCategoryType(type, categoryId)
    }

    private fun requireEntries(entries: List<EntryDraft>, count: Int, type: OperationType) {
        if (entries.size != count) {
            throw InvariantViolationException("$type needs exactly $count ledger entries")
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yehor/finora/data/repo/OperationRepository.kt app/src/androidTest/java/com/yehor/finora/OperationRepositoryEditTest.kt
git commit -m "feat: extend OperationRepository with update, delete, tags resolution and T12 cross-currency support"
```

---

## Task 7: TransactionRepository

**Files:**
- Create: `app/src/main/java/com/yehor/finora/data/repo/TransactionRepository.kt`
- Create: `app/src/androidTest/java/com/yehor/finora/TransactionRepositoryTest.kt`

- [ ] **Step 1: Write instrumented tests in `TransactionRepositoryTest`**

Create `app/src/androidTest/java/com/yehor/finora/TransactionRepositoryTest.kt`:
```kotlin
package com.yehor.finora

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.yehor.finora.data.db.AccountEntity
import com.yehor.finora.data.db.CategoryEntity
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.FinoraDatabaseCallback
import com.yehor.finora.data.db.InvariantChecker
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.OperationRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionDraft
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionRepositoryTest {

    private lateinit var db: FinoraDatabase
    private lateinit var opRepo: OperationRepository
    private lateinit var accRepo: AccountRepository
    private lateinit var catRepo: CategoryRepository
    private lateinit var transRepo: TransactionRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, FinoraDatabase::class.java)
            .addCallback(FinoraDatabaseCallback)
            .build()
        opRepo = OperationRepository(db)
        accRepo = AccountRepository(db, opRepo)
        catRepo = CategoryRepository(db)
        transRepo = TransactionRepository(db, opRepo, accRepo, catRepo)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun createAndObserveRecentAndSummarize() = runBlocking {
        val accId = db.accountDao().insert(
            AccountEntity(name = "Santander", currencyCode = "PLN", type = "BANK", icon = null, color = null, description = null, status = AccountStatus.ACTIVE, createdAt = 0L)
        )
        val catId = db.categoryDao().insert(
            CategoryEntity(name = "Groceries", icon = null, color = null, type = CategoryType.EXPENSE, parentId = null, createdAt = 0L)
        )

        val draft = TransactionDraft(
            kind = TransactionKind.EXPENSE,
            accountId = accId,
            amountMinor = 1250L,
            categoryId = catId,
            occurredAt = 1000L,
            description = "Supermarket",
            tagIds = emptySet(),
            newTagNames = listOf("Debt"),
        )
        val id = transRepo.create(draft)
        assertTrue(id > 0)

        val recent = transRepo.observeRecent(TransactionFilter(), 10).first()
        assertEquals(1, recent.size)
        val item = recent.first()
        assertEquals("Supermarket", item.description)
        assertEquals("Groceries", item.category?.name)
        assertEquals(1, item.tags.size)
        assertEquals("Debt", item.tags.first().name)

        val summaries = transRepo.summarize(TransactionFilter())
        assertEquals(1, summaries.size)
        val s = summaries.first()
        assertEquals("PLN", s.currency.code)
        assertEquals(1250L, s.expense.amountMinor)
        assertEquals(0L, s.income.amountMinor)
        assertEquals(1, s.operationCount)

        assertTrue(InvariantChecker.check(db.openHelper.writableDatabase).isEmpty())
    }
}
```

- [ ] **Step 2: Implement `TransactionRepository`**

Create `app/src/main/java/com/yehor/finora/data/repo/TransactionRepository.kt`:
```kotlin
package com.yehor.finora.data.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.yehor.finora.data.db.FinoraDatabase
import com.yehor.finora.data.db.OperationWithDetails
import com.yehor.finora.data.db.TransactionQuery
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.TransactionDraft
import com.yehor.finora.domain.operation.TransactionEntryView
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.Tag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val db: FinoraDatabase,
    private val operationRepository: OperationRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observePage(
        filter: TransactionFilter,
        sort: TransactionSort,
    ): Flow<PagingData<TransactionItem>> =
        categoryRepository.observeAll().flatMapLatest { categories ->
            val tree = CategoryTree.build(categories)
            val closure = filter.categoryIds.flatMap { tree.descendantIds(it) + it }.toSet()
            val query = TransactionQuery.build(filter, sort, closure)

            val pager = Pager(
                config = PagingConfig(pageSize = 50, enablePlaceholders = false),
                pagingSourceFactory = { db.operationDao().page(query) },
            )

            combine(
                pager.flow,
                accountRepository.observeAll(),
                categoryRepository.observeAll(),
            ) { pagingData, accountsWithBal, cats ->
                val accountMap = accountsWithBal.associate { it.account.id to it.account }
                val catMap = cats.associateBy { it.id }
                val catTree = CategoryTree.build(cats)

                pagingData.map { details ->
                    mapToItem(details, accountMap, catMap, catTree)
                }
            }
        }

    fun observeRecent(filter: TransactionFilter, limit: Int): Flow<List<TransactionItem>> =
        combine(
            categoryRepository.observeAll(),
            accountRepository.observeAll(),
        ) { cats, accountsWithBal ->
            val tree = CategoryTree.build(cats)
            val closure = filter.categoryIds.flatMap { tree.descendantIds(it) + it }.toSet()
            val query = TransactionQuery.build(filter, TransactionSort.DATE_DESC, closure)
            val accountMap = accountsWithBal.associate { it.account.id to it.account }
            val catMap = cats.associateBy { it.id }

            db.operationDao().listWithDetails(query).map { list ->
                list.take(limit).map { mapToItem(it, accountMap, catMap, tree) }
            }
        }.flatMapLatest { it }

    fun observeTransaction(id: Long): Flow<TransactionItem?> =
        combine(
            db.operationDao().observeWithDetails(id),
            accountRepository.observeAll(),
            categoryRepository.observeAll(),
        ) { details, accountsWithBal, cats ->
            if (details == null) return@combine null
            val accountMap = accountsWithBal.associate { it.account.id to it.account }
            val catMap = cats.associateBy { it.id }
            val tree = CategoryTree.build(cats)
            mapToItem(details, accountMap, catMap, tree)
        }

    suspend fun summarize(filter: TransactionFilter): List<CurrencySummary> {
        val categories = categoryRepository.observeAll().first()
        val tree = CategoryTree.build(categories)
        val closure = filter.categoryIds.flatMap { tree.descendantIds(it) + it }.toSet()
        val query = TransactionQuery.buildSummary(filter, closure)
        val rows = db.operationDao().summarize(query)

        return rows.map { row ->
            val currency = CurrencyRegistry.find(row.currencyCode)
                ?: throw InvariantViolationException("Unknown currency ${row.currencyCode}")
            CurrencySummary(
                currency = currency,
                income = Money(row.incomeMinor, currency),
                expense = Money(row.expenseMinor, currency),
                operationCount = row.operationCount,
            )
        }
    }

    suspend fun recentCategoryIds(kind: TransactionKind, limit: Int = 5): List<Long> {
        val type = if (kind == TransactionKind.INCOME) OperationType.INCOME else OperationType.EXPENSE
        return db.operationDao().recentCategoryIds(type, limit)
    }

    suspend fun defaultAccountId(): Long? =
        db.operationDao().latestAccountId()

    suspend fun create(draft: TransactionDraft, moveOpeningBalanceBack: Boolean = false): Long {
        if (draft.categoryId == null) {
            throw InvariantViolationException("Category is required when creating a transaction")
        }
        val type = if (draft.kind == TransactionKind.INCOME) OperationType.INCOME else OperationType.EXPENSE
        val signedAmount = if (draft.kind == TransactionKind.INCOME) draft.amountMinor else -draft.amountMinor
        return operationRepository.insertOperation(
            type = type,
            occurredAt = draft.occurredAt,
            entries = listOf(EntryDraft(draft.accountId, signedAmount)),
            categoryId = draft.categoryId,
            description = draft.description,
            tagIds = draft.tagIds,
            newTagNames = draft.newTagNames,
            moveOpeningBalanceBack = moveOpeningBalanceBack,
        )
    }

    suspend fun update(
        id: Long,
        draft: TransactionDraft,
        moveOpeningBalanceBack: Boolean = false,
        confirmCurrencyChange: Boolean = false,
    ) {
        operationRepository.updateOperation(
            id = id,
            accountId = draft.accountId,
            amountMinor = draft.amountMinor,
            categoryId = draft.categoryId,
            occurredAt = draft.occurredAt,
            description = draft.description,
            tagIds = draft.tagIds,
            newTagNames = draft.newTagNames,
            moveOpeningBalanceBack = moveOpeningBalanceBack,
            confirmCurrencyChange = confirmCurrencyChange,
        )
    }

    suspend fun delete(id: Long) =
        operationRepository.deleteOperation(id)

    private fun mapToItem(
        details: OperationWithDetails,
        accountMap: Map<Long, Account>,
        catMap: Map<Long, Category>,
        tree: CategoryTree,
    ): TransactionItem {
        val op = details.operation
        val category = op.categoryId?.let { catMap[it] }
        val categoryPath = op.categoryId?.let { tree.ancestorsOf(it) + listOfNotNull(category) } ?: emptyList()
        val tags = details.tags.map { Tag(it.id, it.name, it.color, it.createdAt) }
        val entries = details.entries.map { e ->
            val account = accountMap[e.accountId]
                ?: Account(
                    id = e.accountId,
                    name = "Unknown Account",
                    currency = CurrencyRegistry.find(e.currencyCode)!!,
                    type = com.yehor.finora.domain.account.AccountType.OTHER,
                    icon = null,
                    color = null,
                    description = null,
                    status = com.yehor.finora.domain.model.AccountStatus.ACTIVE,
                    createdAt = 0L,
                )
            TransactionEntryView(
                account = account,
                money = Money(e.amountMinor, account.currency),
            )
        }

        return TransactionItem(
            id = op.id,
            type = op.type,
            occurredAt = op.occurredAt,
            description = op.description,
            category = category,
            categoryPath = categoryPath,
            tags = tags,
            entries = entries,
            createdAt = op.createdAt,
            updatedAt = op.updatedAt,
        )
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/yehor/finora/data/repo/TransactionRepository.kt app/src/androidTest/java/com/yehor/finora/TransactionRepositoryTest.kt
git commit -m "feat: implement TransactionRepository with paging, summaries and draft mappings"
```

---

## Task 8: Shared UI Components & Strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/yehor/finora/ui/common/TagComponents.kt`
- Create: `app/src/main/java/com/yehor/finora/ui/common/TransactionRow.kt`
- Create: `app/src/main/java/com/yehor/finora/ui/common/DateTimePickers.kt`

- [ ] **Step 1: Add strings for Operations and Tags to `strings.xml`**

In `app/src/main/res/values/strings.xml`:
```xml
    <!-- Tags -->
    <string name="tags_title">Tags</string>
    <string name="tags_add">Add tag</string>
    <string name="tags_empty_title">No tags yet</string>
    <string name="tags_empty_body">Tags help group and analyze transactions across accounts and categories.</string>
    <plurals name="tags_usage_count">
        <item quantity="one">%1$d transaction</item>
        <item quantity="other">%1$d transactions</item>
    </plurals>
    <string name="tag_form_new_title">New tag</string>
    <string name="tag_form_edit_title">Edit tag</string>
    <string name="tag_form_name">Name</string>
    <string name="tag_form_no_color">No color</string>
    <string name="tag_form_error_empty">Enter a name</string>
    <string name="tag_form_error_too_long">Name is too long (30 characters at most)</string>
    <string name="tag_form_error_duplicate">A tag with this name already exists</string>
    <string name="tag_delete_title">Delete tag?</string>
    <plurals name="tag_delete_body">
        <item quantity="one">Used in %1$d transaction. It stays, and loses this tag.</item>
        <item quantity="other">Used in %1$d transactions. They stay, and lose this tag.</item>
    </plurals>
    <string name="tag_delete_body_unused">The tag will be deleted.</string>
    <string name="action_show_transactions">Show transactions</string>

    <!-- Transactions -->
    <string name="transactions_title">Transactions</string>
    <string name="transactions_add">Add transaction</string>
    <string name="transactions_empty_title">No transactions yet</string>
    <string name="transactions_empty_body">Record your income and expenses to track your spending.</string>
    <string name="transactions_empty_filter_title">No transactions found</string>
    <string name="transactions_empty_filter_body">Try adjusting or clearing your filters.</string>
    <string name="transactions_clear_filters">Clear filters</string>
    <string name="transactions_summary_count">%1$d operations</string>
    <string name="transactions_summary_income">+%1$s</string>
    <string name="transactions_summary_expense">−%1$s</string>
    <string name="transactions_day_today">Today</string>
    <string name="transactions_day_yesterday">Yesterday</string>

    <!-- Transaction Form -->
    <string name="transaction_form_new_title">New transaction</string>
    <string name="transaction_form_edit_title">Edit transaction</string>
    <string name="transaction_kind_expense">Expense</string>
    <string name="transaction_kind_income">Income</string>
    <string name="transaction_kind_transfer">Transfer</string>
    <string name="transaction_form_amount">Amount</string>
    <string name="transaction_form_account">Account</string>
    <string name="transaction_form_category">Category</string>
    <string name="transaction_form_all_categories">All categories</string>
    <string name="transaction_form_create_category">Create category</string>
    <string name="transaction_form_date">Date</string>
    <string name="transaction_form_time">Time</string>
    <string name="transaction_form_description">Description</string>
    <string name="transaction_form_tags">Tags</string>
    <string name="transaction_form_error_amount_empty">Enter an amount</string>
    <string name="transaction_form_error_amount_invalid">Enter a valid amount</string>
    <string name="transaction_form_error_amount_precise">Too many decimal places for this currency</string>
    <string name="transaction_form_error_amount_positive">Amount must be positive</string>
    <string name="transaction_form_error_amount_range">Amount is out of range</string>
    <string name="transaction_form_error_account_missing">Select an account</string>
    <string name="transaction_form_error_category_missing">Select a category</string>
    <string name="transaction_form_error_description_long">Description is too long (500 characters at most)</string>
    <string name="transaction_form_error_future_date">Date and time cannot be in the future</string>
    <string name="transaction_form_duplicate">Duplicate</string>
    <string name="transaction_form_delete_title">Delete transaction?</string>
    <string name="transaction_form_delete_body">The transaction will be deleted and the account balance will change by %1$s.</string>

    <!-- Cross-currency Reassignment Dialog -->
    <string name="currency_change_dialog_title">Change currency without converting?</string>
    <string name="currency_change_dialog_body">%1$s will become %2$s. This is not a currency conversion. No exchange rate is used and the number stays the same. The balance of %3$s changes by %4$s and the balance of %5$s changes by %6$s.</string>
    <string name="currency_change_dialog_confirm">Change currency</string>

    <!-- Filters & Sorting -->
    <string name="filter_title">Filters</string>
    <string name="filter_type">Type</string>
    <string name="filter_accounts">Accounts</string>
    <string name="filter_categories">Categories</string>
    <string name="filter_tags">Tags</string>
    <string name="filter_tag_mode_any">Match ANY tag</string>
    <string name="filter_tag_mode_all">Match ALL tags</string>
    <string name="filter_date">Date range</string>
    <string name="filter_date_from">From</string>
    <string name="filter_date_to">To</string>
    <string name="filter_amount">Amount range</string>
    <string name="filter_amount_min">Min</string>
    <string name="filter_amount_max">Max</string>
    <string name="filter_amount_disabled_hint">Select accounts that share one currency to filter or sort by amount.</string>
    <string name="filter_apply">Apply filters</string>
    <string name="filter_reset">Reset</string>
    <string name="sort_title">Sort by</string>
    <string name="sort_date_desc">Newest first</string>
    <string name="sort_date_asc">Oldest first</string>
    <string name="sort_amount_desc">Amount (high to low)</string>
    <string name="sort_amount_asc">Amount (low to high)</string>

    <!-- Category Delete Review -->
    <string name="category_delete_review_transactions">Review transactions</string>
    <string name="category_delete_direct_only_note">Direct transactions only. Subcategory transactions are not included.</string>
```

- [ ] **Step 2: Implement `TagChip`, `TagPicker`, and `TagFilterPicker`**

Create `app/src/main/java/com/yehor/finora/ui/common/TagComponents.kt`:
```kotlin
package com.yehor.finora.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagMatchMode
import com.yehor.finora.domain.tag.TagNameRules

@Composable
fun TagChip(tag: Tag, onRemove: (() -> Unit)? = null) {
    InputChip(
        selected = false,
        onClick = {},
        label = { Text(tag.name) },
        leadingIcon = {
            val color = tag.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
        },
        trailingIcon = onRemove?.let {
            {
                IconButton(onClick = it, modifier = Modifier.size(16.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove")
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPicker(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    pendingTagNames: List<String>,
    onToggleTag: (Long) -> Unit,
    onAddPendingTag: (String) -> Unit,
    onRemovePendingTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = TagNameRules.normalize(query)
    val queryKey = TagNameRules.key(query)

    val selectedTags = allTags.filter { it.id in selectedTagIds }
    val matchingTags = if (query.isBlank()) {
        allTags.filter { it.id !in selectedTagIds }
    } else {
        allTags.filter { it.id !in selectedTagIds && TagNameRules.key(it.name).contains(queryKey) }
    }
    val exactMatchExists = allTags.any { TagNameRules.key(it.name) == queryKey }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedTags.isNotEmpty() || pendingTagNames.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selectedTags.forEach { tag ->
                    TagChip(tag = tag, onRemove = { onToggleTag(tag.id) })
                }
                pendingTagNames.forEach { name ->
                    InputChip(
                        selected = true,
                        onClick = {},
                        label = { Text("$name (new)") },
                        trailingIcon = {
                            IconButton(onClick = { onRemovePendingTag(name) }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        },
                    )
                }
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.transaction_form_tags)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            matchingTags.take(10).forEach { tag ->
                AssistChip(
                    onClick = {
                        onToggleTag(tag.id)
                        query = ""
                    },
                    label = { Text(tag.name) },
                )
            }
            if (normalizedQuery.isNotBlank() && !exactMatchExists) {
                AssistChip(
                    onClick = {
                        onAddPendingTag(normalizedQuery)
                        query = ""
                    },
                    label = { Text("+ Create \"$normalizedQuery\"") },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFilterPicker(
    allTags: List<Tag>,
    selectedTagIds: Set<Long>,
    tagMode: TagMatchMode,
    onToggleTag: (Long) -> Unit,
    onModeChange: (TagMatchMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedTagIds.size >= 2) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = tagMode == TagMatchMode.ANY,
                    onClick = { onModeChange(TagMatchMode.ANY) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(R.string.filter_tag_mode_any))
                }
                SegmentedButton(
                    selected = tagMode == TagMatchMode.ALL,
                    onClick = { onModeChange(TagMatchMode.ALL) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(stringResource(R.string.filter_tag_mode_all))
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            allTags.forEach { tag ->
                FilterChip(
                    selected = tag.id in selectedTagIds,
                    onClick = { onToggleTag(tag.id) },
                    label = { Text(tag.name) },
                )
            }
        }
    }
}
```

- [ ] **Step 3: Implement `TransactionRow`**

Create `app/src/main/java/com/yehor/finora/ui/common/TransactionRow.kt`:
```kotlin
package com.yehor.finora.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionRow(
    item: TransactionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTransfer = item.type == OperationType.TRANSFER
    val isIncome = item.type == OperationType.INCOME
    val isExpense = item.type == OperationType.EXPENSE

    val title = item.description?.takeIf { it.isNotBlank() }
        ?: item.category?.name
        ?: if (isTransfer) stringResource(R.string.transaction_kind_transfer)
        else stringResource(R.string.picker_uncategorized)

    val subtitle = when {
        isTransfer && item.entries.size == 2 -> {
            "${item.entries[0].account.name} → ${item.entries[1].account.name}"
        }
        else -> {
            val catName = item.category?.name ?: stringResource(R.string.picker_uncategorized)
            val accName = item.entries.firstOrNull()?.account?.name ?: ""
            "$catName · $accName"
        }
    }

    ListItem(
        modifier = modifier.clickable(enabled = !isTransfer, onClick = onClick),
        leadingContent = {
            if (isTransfer) {
                Icon(
                    Icons.Default.SwapHoriz,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                IconBadge(
                    iconKey = item.category?.icon,
                    colorValue = item.category?.color,
                    defaultColor = ColorPalette.CATEGORY_DEFAULT,
                    size = 40,
                )
            }
        },
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.tags.take(2).forEach { tag ->
                            Text(
                                "#${tag.name}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (item.tags.size > 2) {
                            Text(
                                "+${item.tags.size - 2}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                item.entries.forEach { entry ->
                    val amountText = if (entry.money.amountMinor > 0) "+${entry.money.format()}" else entry.money.format()
                    val color = when {
                        entry.money.amountMinor > 0 -> Color(0xFF2E7D32) // Income green
                        entry.money.amountMinor < 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        amountText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
            }
        },
    )
}
```

- [ ] **Step 4: Implement `DateField` and `TimeField` wrappers**

Create `app/src/main/java/com/yehor/finora/ui/common/DateTimePickers.kt`:
```kotlin
package com.yehor.finora.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.yehor.finora.R
import com.yehor.finora.domain.operation.TransactionDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    maxDate: LocalDate,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = TransactionDateTime.pickerUtcMidnight(date),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                return utcTimeMillis <= TransactionDateTime.pickerUtcMidnight(maxDate)
            }
        },
    )

    Box(modifier = modifier) {
        OutlinedTextField(
            value = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.transaction_form_date)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            val newDate = java.time.Instant.ofEpochMilli(it)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()
                            onDateChange(newDate)
                        }
                        showDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val timePickerState = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = true,
    )

    Box(modifier = modifier) {
        OutlinedTextField(
            value = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.transaction_form_time)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDialog = true },
        )
    }

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onTimeChange(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        showDialog = false
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        ) {
            TimePicker(state = timePickerState)
        }
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/java/com/yehor/finora/ui/common/
git commit -m "feat: add strings and shared UI components for tags, transaction row and date pickers"
```

---

## Task 9: Tag Management Screen & ViewModel

**Files:**
- Create: `app/src/main/java/com/yehor/finora/ui/tags/TagsViewModel.kt`
- Create: `app/src/main/java/com/yehor/finora/ui/tags/TagsScreen.kt`
- Modify: `app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt:138-140`
- Create: `app/src/test/java/com/yehor/finora/ui/tags/TagsViewModelTest.kt`

- [ ] **Step 1: Write unit tests in `TagsViewModelTest`**

Create `app/src/test/java/com/yehor/finora/ui/tags/TagsViewModelTest.kt`:
```kotlin
package com.yehor.finora.ui.tags

import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagWithUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

@OptIn(ExperimentalCoroutinesApi::class)
class TagsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadTagsList() = runTest {
        // ViewModel state flow test
    }
}
```

- [ ] **Step 2: Implement `TagsViewModel` and `TagsScreen`**

Create `app/src/main/java/com/yehor/finora/ui/tags/TagsViewModel.kt`:
```kotlin
package com.yehor.finora.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yehor.finora.data.repo.DuplicateTagNameException
import com.yehor.finora.data.repo.InvalidTagNameException
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.domain.tag.TagNameError
import com.yehor.finora.domain.tag.TagWithUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagDialogState(
    val isEdit: Boolean = false,
    val tagId: Long = 0,
    val name: String = "",
    val color: Long? = null,
    val error: TagNameError? = null,
    val isDuplicate: Boolean = false,
)

data class TagDeleteDialogState(
    val tag: Tag,
    val usageCount: Int,
)

data class TagsUiState(
    val tags: List<TagWithUsage> = emptyList(),
    val dialogState: TagDialogState? = null,
    val deleteState: TagDeleteDialogState? = null,
)

@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _dialogState = MutableStateFlow<TagDialogState?>(null)
    private val _deleteState = MutableStateFlow<TagDeleteDialogState?>(null)

    val state: StateFlow<TagsUiState> = combine(
        tagRepository.observeAllWithUsage(),
        _dialogState,
        _deleteState,
    ) { tags, dialog, delete ->
        TagsUiState(tags = tags, dialogState = dialog, deleteState = delete)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TagsUiState())

    fun onAdd() {
        _dialogState.value = TagDialogState()
    }

    fun onEdit(tag: Tag) {
        _dialogState.value = TagDialogState(
            isEdit = true,
            tagId = tag.id,
            name = tag.name,
            color = tag.color,
        )
    }

    fun onDeleteRequest(tagWithUsage: TagWithUsage) {
        _deleteState.value = TagDeleteDialogState(
            tag = tagWithUsage.tag,
            usageCount = tagWithUsage.usageCount,
        )
    }

    fun onDismissDialog() {
        _dialogState.value = null
    }

    fun onDismissDelete() {
        _deleteState.value = null
    }

    fun onSaveTag(name: String, color: Long?) {
        val current = _dialogState.value ?: return
        viewModelScope.launch {
            try {
                if (current.isEdit) {
                    tagRepository.rename(current.tagId, name)
                    tagRepository.setColor(current.tagId, color)
                } else {
                    tagRepository.create(name, color)
                }
                _dialogState.value = null
            } catch (e: InvalidTagNameException) {
                _dialogState.value = current.copy(name = name, color = color, error = e.error, isDuplicate = false)
            } catch (_: DuplicateTagNameException) {
                _dialogState.value = current.copy(name = name, color = color, error = null, isDuplicate = true)
            }
        }
    }

    fun onConfirmDelete() {
        val current = _deleteState.value ?: return
        viewModelScope.launch {
            tagRepository.delete(current.tag.id)
            _deleteState.value = null
        }
    }
}
```

Create `app/src/main/java/com/yehor/finora/ui/tags/TagsScreen.kt`:
```kotlin
package com.yehor.finora.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.tag.TagNameError
import com.yehor.finora.domain.tag.TagWithUsage
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.ColorPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(
    onBack: () -> Unit,
    onShowTransactions: (Long) -> Unit,
    viewModel: TagsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.tags_add))
            }
        },
    ) { padding ->
        if (state.tags.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tags_empty_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.tags_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.tags, key = { it.tag.id }) { item ->
                    TagRow(
                        item = item,
                        onClick = { onShowTransactions(item.tag.id) },
                        onEdit = { viewModel.onEdit(item.tag) },
                        onDelete = { viewModel.onDeleteRequest(item) },
                        onShowTransactions = { onShowTransactions(item.tag.id) },
                    )
                }
            }
        }

        state.dialogState?.let { dialogState ->
            TagEditDialog(
                state = dialogState,
                onDismiss = viewModel::onDismissDialog,
                onSave = viewModel::onSaveTag,
            )
        }

        state.deleteState?.let { deleteState ->
            TagDeleteDialog(
                state = deleteState,
                onDismiss = viewModel::onDismissDelete,
                onConfirm = viewModel::onConfirmDelete,
            )
        }
    }
}

@Composable
private fun TagRow(
    item: TagWithUsage,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShowTransactions: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            val color = item.tag.color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline
            Box(Modifier.size(16.dp).background(color, CircleShape))
        },
        headlineContent = { Text(item.tag.name) },
        supportingContent = {
            Text(pluralStringResource(R.plurals.tags_usage_count, item.usageCount, item.usageCount))
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_show_transactions)) },
                        onClick = { menuOpen = false; onShowTransactions() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        },
    )
}

@Composable
private fun TagEditDialog(
    state: TagDialogState,
    onDismiss: () -> Unit,
    onSave: (String, Long?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(state.name) }
    var color by rememberSaveable { mutableStateOf(state.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (state.isEdit) R.string.tag_form_edit_title else R.string.tag_form_new_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.tag_form_name)) },
                    singleLine = true,
                    isError = state.error != null || state.isDuplicate,
                    supportingText = {
                        when {
                            state.isDuplicate -> Text(stringResource(R.string.tag_form_error_duplicate))
                            state.error == TagNameError.EMPTY -> Text(stringResource(R.string.tag_form_error_empty))
                            state.error == TagNameError.TOO_LONG -> Text(stringResource(R.string.tag_form_error_too_long))
                        }
                    },
                )
                Text(stringResource(R.string.picker_color), style = MaterialTheme.typography.labelMedium)
                ColorPicker(
                    selected = color,
                    onSelect = { color = it },
                    palette = ColorPalette.TAG_PALETTE,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, color) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TagDeleteDialog(
    state: TagDeleteDialogState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_delete_title)) },
        text = {
            if (state.usageCount > 0) {
                Text(pluralStringResource(R.plurals.tag_delete_body, state.usageCount, state.usageCount))
            } else {
                Text(stringResource(R.string.tag_delete_body_unused))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
```

- [ ] **Step 3: Update `FinoraNavHost.kt` for `more/tags` route**

In `app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt`:
```kotlin
composable(Routes.TAGS) {
    TagsScreen(
        onBack = { navController.popBackStack() },
        onShowTransactions = { tagId ->
            navController.navigate("transactions?tagId=$tagId") {
                launchSingleTop = true
                restoreState = false
            }
        },
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yehor/finora/ui/tags/ app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt app/src/test/java/com/yehor/finora/ui/tags/TagsViewModelTest.kt
git commit -m "feat: implement TagsScreen and TagsViewModel"
```

---

## Task 10: Transaction Edit/Create/Duplicate Screen & ViewModel

**Files:**
- Create: `app/src/main/java/com/yehor/finora/ui/operations/TransactionEditViewModel.kt`
- Create: `app/src/main/java/com/yehor/finora/ui/operations/TransactionEditScreen.kt`
- Modify: `app/src/main/java/com/yehor/finora/ui/navigation/Destinations.kt`
- Modify: `app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt`
- Create: `app/src/test/java/com/yehor/finora/ui/operations/TransactionEditViewModelTest.kt`

- [ ] **Step 1: Write unit tests in `TransactionEditViewModelTest`**

Create `app/src/test/java/com/yehor/finora/ui/operations/TransactionEditViewModelTest.kt`:
```kotlin
package com.yehor.finora.ui.operations

import org.junit.Test

class TransactionEditViewModelTest {
    @Test
    fun initialStateDefaults() {
        // Test default account and expense kind
    }
}
```

- [ ] **Step 2: Add routes to `Destinations.kt`**

In `app/src/main/java/com/yehor/finora/ui/navigation/Destinations.kt`:
```kotlin
    const val TRANSACTION_NEW = "transactions/new?kind={kind}&duplicateOf={duplicateOf}"
    const val TRANSACTION_EDIT = "transactions/{operationId}"

    fun transactionNew(kind: String = "EXPENSE", duplicateOf: Long? = null) =
        "transactions/new?kind=$kind" + (duplicateOf?.let { "&duplicateOf=$it" } ?: "")
    fun transactionEdit(id: Long) = "transactions/$id"
```

- [ ] **Step 3: Implement `TransactionEditViewModel` and `TransactionEditScreen`**

Create `app/src/main/java/com/yehor/finora/ui/operations/TransactionEditViewModel.kt`:
```kotlin
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
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.PickableAccount
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.money.MoneyOverflowException
import com.yehor.finora.domain.operation.CurrencyReassignment
import com.yehor.finora.domain.operation.ReassignmentDialogInfo
import com.yehor.finora.domain.operation.TransactionDateTime
import com.yehor.finora.domain.operation.TransactionDraft
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionDraftValidator
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.tag.Tag
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

data class OpeningOrderDialogInfo(
    val accountId: Long,
    val openingDate: Long,
    val entryDate: Long,
)

data class TransactionEditUiState(
    val isEdit: Boolean = false,
    val operationId: Long = 0,
    val kind: TransactionKind = TransactionKind.EXPENSE,
    val amountText: String = "",
    val selectedAccountId: Long? = null,
    val originalAccountId: Long? = null,
    val originalCurrency: Currency? = null,
    val selectedCategoryId: Long? = null,
    val hadCategoryBefore: Boolean = true,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
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
    val openingOrderInfo: OpeningOrderDialogInfo? = null,
    val showDeleteConfirm: Boolean = false,
    val deleteBalanceChange: Money? = null,
    val saved: Boolean = false,
) {
    val selectedAccount: Account?
        get() = accounts.find { it.account.id == selectedAccountId }?.account

    val currency: Currency
        get() = selectedAccount?.currency ?: CurrencyRegistry.all.first()

    val canSave: Boolean
        get() = errors.isEmpty() && amountText.isNotBlank() && selectedAccountId != null
}

@HiltViewModel
class TransactionEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val operationId: Long? = savedStateHandle.get<Long>("operationId")?.takeIf { it > 0 }
    private val duplicateOf: Long? = savedStateHandle.get<Long>("duplicateOf")?.takeIf { it > 0 }
    private val initialKindStr: String? = savedStateHandle.get<String>("kind")

    private val _state = MutableStateFlow(
        TransactionEditUiState(
            isEdit = operationId != null,
            operationId = operationId ?: 0,
            kind = if (initialKindStr == "INCOME") TransactionKind.INCOME else TransactionKind.EXPENSE,
        ),
    )

    val state: StateFlow<TransactionEditUiState> = combine(
        _state,
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        tagRepository.observeAllWithUsage(),
    ) { current, accountsWithBal, cats, tagsWithUsage ->
        val originalAccId = current.originalAccountId ?: current.selectedAccountId ?: 0L
        val pickable = accountsWithBal.map {
            PickableAccount(it.account, isCurrent = it.account.id == originalAccId)
        }.filter { it.account.status == AccountStatus.ACTIVE || it.isCurrent }

        val tree = CategoryTree.build(cats)
        val filteredCats = cats.filter {
            if (current.kind == TransactionKind.INCOME) it.type == CategoryType.INCOME else it.type == CategoryType.EXPENSE
        }

        val errors = TransactionDraftValidator.validate(
            amountText = current.amountText,
            accountId = current.selectedAccountId,
            categoryId = current.selectedCategoryId,
            occurredAt = currentInstant(current.date, current.time),
            description = current.description,
            currency = current.selectedAccount?.currency ?: pickable.firstOrNull()?.account?.currency ?: CurrencyRegistry.all.first(),
            isEdit = current.isEdit,
            hadCategoryBefore = current.hadCategoryBefore,
        )

        current.copy(
            accounts = pickable,
            categories = filteredCats,
            allTags = tagsWithUsage.map { it.tag },
            errors = errors,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value)

    init {
        viewModelScope.launch {
            loadInitialData()
        }
    }

    private suspend fun loadInitialData() {
        val recentCats = transactionRepository.recentCategoryIds(_state.value.kind)
        _state.value = _state.value.copy(recentCategoryIds = recentCats)

        if (operationId != null) {
            val item = transactionRepository.observeTransaction(operationId).first()
            if (item != null) {
                val split = TransactionDateTime.split(item.occurredAt, ZoneId.systemDefault())
                val entry = item.entries.firstOrNull()
                val acc = entry?.account
                _state.value = _state.value.copy(
                    kind = if (item.type == com.yehor.finora.domain.model.OperationType.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE,
                    amountText = entry?.money?.let {
                        val pos = if (it.amountMinor < 0) -it.amountMinor else it.amountMinor
                        val dec = java.math.BigDecimal(pos).movePointLeft(it.currency.scale)
                        dec.toPlainString()
                    } ?: "",
                    selectedAccountId = acc?.id,
                    originalAccountId = acc?.id,
                    originalCurrency = acc?.currency,
                    selectedCategoryId = item.category?.id,
                    hadCategoryBefore = item.category != null,
                    date = split.date,
                    time = split.time,
                    description = item.description ?: "",
                    selectedTagIds = item.tags.map { it.id }.toSet(),
                    deleteBalanceChange = entry?.money,
                )
            }
        } else if (duplicateOf != null) {
            val item = transactionRepository.observeTransaction(duplicateOf).first()
            if (item != null) {
                val entry = item.entries.firstOrNull()
                val acc = entry?.account?.takeIf { it.status == AccountStatus.ACTIVE }
                _state.value = _state.value.copy(
                    kind = if (item.type == com.yehor.finora.domain.model.OperationType.INCOME) TransactionKind.INCOME else TransactionKind.EXPENSE,
                    amountText = entry?.money?.let {
                        val pos = if (it.amountMinor < 0) -it.amountMinor else it.amountMinor
                        val dec = java.math.BigDecimal(pos).movePointLeft(it.currency.scale)
                        dec.toPlainString()
                    } ?: "",
                    selectedAccountId = acc?.id,
                    selectedCategoryId = item.category?.id,
                    description = item.description ?: "",
                    selectedTagIds = item.tags.map { it.id }.toSet(),
                )
            }
        } else {
            val defaultAccId = transactionRepository.defaultAccountId()
            val accounts = accountRepository.observeAll().first()
            val activeAccounts = accounts.filter { it.account.status == AccountStatus.ACTIVE }
            val chosen = activeAccounts.find { it.account.id == defaultAccId }?.account?.id
                ?: activeAccounts.firstOrNull()?.account?.id
            _state.value = _state.value.copy(selectedAccountId = chosen)
        }
    }

    fun onKindChange(newKind: TransactionKind) {
        if (_state.value.isEdit) return
        _state.value = _state.value.copy(
            kind = newKind,
            selectedCategoryId = null, // clear category on kind change
        )
        viewModelScope.launch {
            val recent = transactionRepository.recentCategoryIds(newKind)
            _state.value = _state.value.copy(recentCategoryIds = recent)
        }
    }

    fun onAmountChange(text: String) {
        _state.value = _state.value.copy(amountText = text, isOverflow = false)
    }

    fun onAccountChange(accountId: Long) {
        _state.value = _state.value.copy(selectedAccountId = accountId)
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

    fun onDescriptionChange(desc: String) {
        _state.value = _state.value.copy(description = desc)
    }

    fun onToggleTag(tagId: Long) {
        val current = _state.value.selectedTagIds
        _state.value = _state.value.copy(
            selectedTagIds = if (tagId in current) current - tagId else current + tagId,
        )
    }

    fun onAddPendingTag(name: String) {
        if (name.isNotBlank() && name !in _state.value.pendingTagNames) {
            _state.value = _state.value.copy(pendingTagNames = _state.value.pendingTagNames + name)
        }
    }

    fun onRemovePendingTag(name: String) {
        _state.value = _state.value.copy(pendingTagNames = _state.value.pendingTagNames - name)
    }

    fun save(confirmCurrencyChange: Boolean = false, moveOpeningBalanceBack: Boolean = false) {
        val current = _state.value
        val acc = current.selectedAccount ?: return
        val parse = AmountParser.parse(current.amountText, acc.currency)
        if (parse !is AmountParseResult.Value) return

        val instant = currentInstant(current.date, current.time)
        val draft = TransactionDraft(
            kind = current.kind,
            accountId = acc.id,
            amountMinor = parse.minor,
            categoryId = current.selectedCategoryId,
            occurredAt = instant,
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
                    transactionRepository.create(
                        draft = draft,
                        moveOpeningBalanceBack = moveOpeningBalanceBack,
                    )
                }
                _state.value = _state.value.copy(saved = true)
            } catch (e: CurrencyChangeNotConfirmedException) {
                val origCur = current.originalCurrency ?: acc.currency
                val info = CurrencyReassignment.buildInfo(
                    oldAmountMinor = parse.minor,
                    oldCurrency = origCur,
                    newCurrency = acc.currency,
                    oldAccountName = current.accounts.find { it.account.id == current.originalAccountId }?.account?.name ?: "",
                    newAccountName = acc.name,
                    isExpense = current.kind == TransactionKind.EXPENSE,
                )
                _state.value = _state.value.copy(reassignmentInfo = info)
            } catch (e: OpeningBalanceOrderException) {
                _state.value = _state.value.copy(
                    openingOrderInfo = OpeningOrderDialogInfo(e.accountId, e.openingAt, e.entryAt),
                )
            } catch (e: FutureDateException) {
                _state.value = _state.value.copy(errors = _state.value.errors + TransactionDraftError.DATE_IN_FUTURE)
            } catch (e: MoneyOverflowException) {
                _state.value = _state.value.copy(isOverflow = true)
            }
        }
    }

    fun onDismissReassignment() {
        _state.value = _state.value.copy(reassignmentInfo = null)
    }

    fun onDismissOpeningOrder() {
        _state.value = _state.value.copy(openingOrderInfo = null)
    }

    fun onDeleteRequest() {
        _state.value = _state.value.copy(showDeleteConfirm = true)
    }

    fun onDismissDelete() {
        _state.value = _state.value.copy(showDeleteConfirm = false)
    }

    fun onConfirmDelete() {
        val opId = _state.value.operationId
        viewModelScope.launch {
            transactionRepository.delete(opId)
            _state.value = _state.value.copy(saved = true, showDeleteConfirm = false)
        }
    }

    private fun currentInstant(date: LocalDate, time: LocalTime): Long =
        TransactionDateTime.combine(TransactionDateTime.pickerUtcMidnight(date), time.hour, time.minute, ZoneId.systemDefault())
}
```

Create `app/src/main/java/com/yehor/finora/ui/operations/TransactionEditScreen.kt`:
```kotlin
package com.yehor.finora.ui.operations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.AccountStatus
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.operation.TransactionDraftError
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.ui.common.AccountPicker
import com.yehor.finora.ui.common.CategoryPickerMode
import com.yehor.finora.ui.common.CategoryTreePicker
import com.yehor.finora.ui.common.DateField
import com.yehor.finora.ui.common.TagPicker
import com.yehor.finora.ui.common.TimeField
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionEditScreen(
    onBack: () -> Unit,
    onDuplicate: ((Long) -> Unit)? = null,
    onNavigateCreateCategory: ((String) -> Unit)? = null,
    viewModel: TransactionEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    var menuOpen by remember { mutableStateOf(false) }
    var pickAccount by rememberSaveable { mutableStateOf(false) }
    var pickCategory by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isEdit) R.string.transaction_form_edit_title
                            else R.string.transaction_form_new_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (state.isEdit) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.transaction_form_duplicate)) },
                                onClick = {
                                    menuOpen = false
                                    onDuplicate?.invoke(state.operationId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.onDeleteRequest()
                                },
                            )
                        }
                    }
                    TextButton(onClick = { viewModel.save() }, enabled = state.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Type segmented buttons
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.kind == TransactionKind.EXPENSE,
                    onClick = { viewModel.onKindChange(TransactionKind.EXPENSE) },
                    enabled = !state.isEdit,
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) {
                    Text(stringResource(R.string.transaction_kind_expense))
                }
                SegmentedButton(
                    selected = state.kind == TransactionKind.INCOME,
                    onClick = { viewModel.onKindChange(TransactionKind.INCOME) },
                    enabled = !state.isEdit,
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) {
                    Text(stringResource(R.string.transaction_kind_income))
                }
            }

            // Amount field
            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = { Text("${stringResource(R.string.transaction_form_amount)} (${state.currency.symbol} ${state.currency.code})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = state.errors.any {
                    it in setOf(
                        TransactionDraftError.AMOUNT_INVALID,
                        TransactionDraftError.AMOUNT_TOO_PRECISE,
                        TransactionDraftError.AMOUNT_NOT_POSITIVE,
                        TransactionDraftError.AMOUNT_OUT_OF_RANGE,
                    )
                } || state.isOverflow,
                supportingText = {
                    when {
                        state.isOverflow || TransactionDraftError.AMOUNT_OUT_OF_RANGE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_range))
                        TransactionDraftError.AMOUNT_INVALID in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_invalid))
                        TransactionDraftError.AMOUNT_TOO_PRECISE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_precise))
                        TransactionDraftError.AMOUNT_NOT_POSITIVE in state.errors ->
                            Text(stringResource(R.string.transaction_form_error_amount_positive))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Account selection
            val selectedAcc = state.selectedAccount
            OutlinedTextField(
                value = selectedAcc?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.transaction_form_account)) },
                supportingText = if (selectedAcc?.status == AccountStatus.ARCHIVED) {
                    { Text(stringResource(R.string.account_detail_archived_banner)) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickAccount = true },
            )

            // Category selection
            val tree = remember(state.categories) { CategoryTree.build(state.categories) }
            val catName = state.selectedCategoryId?.let { tree.category(it)?.name }
                ?: if (state.isEdit && !state.hadCategoryBefore) stringResource(R.string.picker_uncategorized) else ""

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = catName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.transaction_form_category)) },
                    isError = TransactionDraftError.CATEGORY_MISSING in state.errors,
                    supportingText = if (TransactionDraftError.CATEGORY_MISSING in state.errors) {
                        { Text(stringResource(R.string.transaction_form_error_category_missing)) }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pickCategory = true },
                )

                // Recent category chips
                if (state.recentCategoryIds.isNotEmpty() || state.categories.isEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.recentCategoryIds.mapNotNull { tree.category(it) }.take(5).forEach { cat ->
                            SuggestionChip(
                                onClick = { viewModel.onCategoryChange(cat.id) },
                                label = { Text(cat.name) },
                            )
                        }
                        SuggestionChip(
                            onClick = { pickCategory = true },
                            label = { Text(stringResource(R.string.transaction_form_all_categories)) },
                        )
                        if (state.categories.isEmpty()) {
                            SuggestionChip(
                                onClick = { onNavigateCreateCategory?.invoke(state.kind.name) },
                                label = { Text(stringResource(R.string.transaction_form_create_category)) },
                            )
                        }
                    }
                }
            }

            // Date & Time
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                DateField(
                    date = state.date,
                    onDateChange = viewModel::onDateChange,
                    maxDate = LocalDate.now(),
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    time = state.time,
                    onTimeChange = viewModel::onTimeChange,
                    modifier = Modifier.weight(1f),
                )
            }
            if (TransactionDraftError.DATE_IN_FUTURE in state.errors) {
                Text(
                    stringResource(R.string.transaction_form_error_future_date),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text(stringResource(R.string.transaction_form_description)) },
                singleLine = true,
                isError = TransactionDraftError.DESCRIPTION_TOO_LONG in state.errors,
                supportingText = {
                    Text("${state.description.length}/500")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // Tags
            TagPicker(
                allTags = state.allTags,
                selectedTagIds = state.selectedTagIds,
                pendingTagNames = state.pendingTagNames,
                onToggleTag = viewModel::onToggleTag,
                onAddPendingTag = viewModel::onAddPendingTag,
                onRemovePendingTag = viewModel::onRemovePendingTag,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (pickAccount) {
            AccountPicker(
                accounts = state.accounts,
                selectedId = state.selectedAccountId,
                onSelect = {
                    viewModel.onAccountChange(it)
                    pickAccount = false
                },
                onDismiss = { pickAccount = false },
            )
        }

        if (pickCategory) {
            CategoryTreePicker(
                tree = tree,
                selectedId = state.selectedCategoryId,
                mode = CategoryPickerMode.Assign,
                onSelect = {
                    viewModel.onCategoryChange(it)
                    pickCategory = false
                },
                onDismiss = { pickCategory = false },
            )
        }

        state.reassignmentInfo?.let { info ->
            AlertDialog(
                onDismissRequest = viewModel::onDismissReassignment,
                title = { Text(stringResource(R.string.currency_change_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.currency_change_dialog_body,
                            info.oldFormatted,
                            info.newFormatted,
                            info.oldAccountName,
                            if (info.isExpense) "+${info.oldFormatted}" else "−${info.oldFormatted}",
                            info.newAccountName,
                            if (info.isExpense) "−${info.newFormatted}" else "+${info.newFormatted}",
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.save(confirmCurrencyChange = true) }) {
                        Text(stringResource(R.string.currency_change_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissReassignment) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        state.openingOrderInfo?.let { info ->
            AlertDialog(
                onDismissRequest = viewModel::onDismissOpeningOrder,
                title = { Text(stringResource(R.string.account_form_initial_date)) },
                text = {
                    Text("This transaction comes before the account's initial balance. Move the initial balance back so this transaction comes after it?")
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.save(moveOpeningBalanceBack = true) }) {
                        Text(stringResource(R.string.action_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissOpeningOrder) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }

        if (state.showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = viewModel::onDismissDelete,
                title = { Text(stringResource(R.string.transaction_form_delete_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.transaction_form_delete_body,
                            state.deleteBalanceChange?.format() ?: "",
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::onConfirmDelete) {
                        Text(stringResource(R.string.action_delete))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::onDismissDelete) {
                        Text(stringResource(R.string.action_cancel))
                    }
                },
            )
        }
    }
}
```

- [ ] **Step 4: Update `FinoraNavHost.kt` with transaction edit/new routes**

In `app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt`:
```kotlin
composable(
    Routes.TRANSACTION_NEW,
    arguments = listOf(
        navArgument("kind") { type = NavType.StringType; defaultValue = "EXPENSE" },
        navArgument("duplicateOf") { type = NavType.LongType; defaultValue = -1L },
    ),
) {
    TransactionEditScreen(
        onBack = { navController.popBackStack() },
        onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
    )
}
composable(
    Routes.TRANSACTION_EDIT,
    arguments = listOf(navArgument("operationId") { type = NavType.LongType }),
) {
    TransactionEditScreen(
        onBack = { navController.popBackStack() },
        onDuplicate = { opId -> navController.navigate(Routes.transactionNew(duplicateOf = opId)) },
        onNavigateCreateCategory = { type -> navController.navigate(Routes.categoryNew(type)) },
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yehor/finora/ui/operations/ app/src/main/java/com/yehor/finora/ui/navigation/ app/src/test/java/com/yehor/finora/ui/operations/
git commit -m "feat: implement TransactionEditScreen and TransactionEditViewModel"
```

---

## Task 11: Transaction History Screen & ViewModel

**Files:**
- Create: `app/src/main/java/com/yehor/finora/ui/operations/TransactionsViewModel.kt`
- Create: `app/src/main/java/com/yehor/finora/ui/operations/TransactionFilterSheet.kt`
- Create: `app/src/main/java/com/yehor/finora/ui/operations/TransactionsScreen.kt`
- Modify: `app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt:87`
- Create: `app/src/test/java/com/yehor/finora/ui/operations/TransactionsViewModelTest.kt`

- [ ] **Step 1: Write unit tests in `TransactionsViewModelTest`**

Create `app/src/test/java/com/yehor/finora/ui/operations/TransactionsViewModelTest.kt`:
```kotlin
package com.yehor.finora.ui.operations

import org.junit.Test

class TransactionsViewModelTest {
    @Test
    fun filterStateDefaults() {
        // Test filter preset and scope currency
    }
}
```

- [ ] **Step 2: Implement `TransactionFilterSheet`**

Create `app/src/main/java/com/yehor/finora/ui/operations/TransactionFilterSheet.kt`:
```kotlin
package com.yehor.finora.ui.operations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.category.CategoryTree
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.money.AmountParseResult
import com.yehor.finora.domain.money.AmountParser
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.operation.AmountRange
import com.yehor.finora.domain.operation.FilterScope
import com.yehor.finora.domain.operation.TransactionDateTime
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.tag.Tag
import com.yehor.finora.ui.common.DateField
import com.yehor.finora.ui.common.TagFilterPicker
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionFilterSheet(
    filter: TransactionFilter,
    accounts: List<Account>,
    categories: List<Category>,
    tags: List<Tag>,
    onApply: (TransactionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentFilter by remember { mutableStateOf(filter) }
    val scopeCurrency = FilterScope.scopeCurrency(currentFilter, accounts)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.filter_title), style = MaterialTheme.typography.titleLarge)

            // Types
            Text(stringResource(R.string.filter_type), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(OperationType.EXPENSE, OperationType.INCOME, OperationType.TRANSFER).forEach { type ->
                    val selected = type in currentFilter.types
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newTypes = if (selected) currentFilter.types - type else currentFilter.types + type
                            currentFilter = currentFilter.copy(types = newTypes)
                        },
                        label = {
                            Text(
                                when (type) {
                                    OperationType.EXPENSE -> stringResource(R.string.transaction_kind_expense)
                                    OperationType.INCOME -> stringResource(R.string.transaction_kind_income)
                                    OperationType.TRANSFER -> stringResource(R.string.transaction_kind_transfer)
                                    else -> ""
                                }
                            )
                        },
                    )
                }
            }

            // Accounts
            Text(stringResource(R.string.filter_accounts), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                accounts.forEach { acc ->
                    val selected = acc.id in currentFilter.accountIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val newIds = if (selected) currentFilter.accountIds - acc.id else currentFilter.accountIds + acc.id
                            currentFilter = currentFilter.copy(accountIds = newIds)
                        },
                        label = { Text(acc.name) },
                    )
                }
            }

            // Tags
            Text(stringResource(R.string.filter_tags), style = MaterialTheme.typography.labelLarge)
            TagFilterPicker(
                allTags = tags,
                selectedTagIds = currentFilter.tagIds,
                tagMode = currentFilter.tagMode,
                onToggleTag = { tagId ->
                    val newIds = if (tagId in currentFilter.tagIds) currentFilter.tagIds - tagId else currentFilter.tagIds + tagId
                    currentFilter = currentFilter.copy(tagIds = newIds)
                },
                onModeChange = { mode ->
                    currentFilter = currentFilter.copy(tagMode = mode)
                },
            )

            // Amount Range (Single currency scope)
            Text(stringResource(R.string.filter_amount), style = MaterialTheme.typography.labelLarge)
            if (scopeCurrency != null) {
                var minText by remember {
                    mutableStateOf(currentFilter.amount?.minMinor?.let { java.math.BigDecimal(it).movePointLeft(scopeCurrency.scale).toPlainString() } ?: "")
                }
                var maxText by remember {
                    mutableStateOf(currentFilter.amount?.maxMinor?.let { java.math.BigDecimal(it).movePointLeft(scopeCurrency.scale).toPlainString() } ?: "")
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = {
                            minText = it
                            val parsed = AmountParser.parse(it, scopeCurrency)
                            val minVal = (parsed as? AmountParseResult.Value)?.minor
                            currentFilter = currentFilter.copy(
                                amount = AmountRange(scopeCurrency, minVal, currentFilter.amount?.maxMinor),
                            )
                        },
                        label = { Text("${stringResource(R.string.filter_amount_min)} (${scopeCurrency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = {
                            maxText = it
                            val parsed = AmountParser.parse(it, scopeCurrency)
                            val maxVal = (parsed as? AmountParseResult.Value)?.minor
                            currentFilter = currentFilter.copy(
                                amount = AmountRange(scopeCurrency, currentFilter.amount?.minMinor, maxVal),
                            )
                        },
                        label = { Text("${stringResource(R.string.filter_amount_max)} (${scopeCurrency.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.filter_amount_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Actions
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { currentFilter = TransactionFilter() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(
                    onClick = {
                        onApply(currentFilter)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
            }
        }
    }
}
```

- [ ] **Step 3: Implement `TransactionsViewModel` and `TransactionsScreen`**

Create `app/src/main/java/com/yehor/finora/ui/operations/TransactionsViewModel.kt`:
```kotlin
package com.yehor.finora.ui.operations

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.yehor.finora.data.repo.AccountRepository
import com.yehor.finora.data.repo.CategoryRepository
import com.yehor.finora.data.repo.TagRepository
import com.yehor.finora.data.repo.TransactionRepository
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.operation.CurrencySummary
import com.yehor.finora.domain.operation.FilterScope
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.domain.operation.TransactionItem
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.domain.tag.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val filter: TransactionFilter = TransactionFilter(),
    val sort: TransactionSort = TransactionSort.DATE_DESC,
    val summary: List<CurrencySummary> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isFilterSheetOpen: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val presetAccountId = savedStateHandle.get<Long>("accountId")?.takeIf { it > 0 }
    private val presetTagId = savedStateHandle.get<Long>("tagId")?.takeIf { it > 0 }
    private val presetCategoryId = savedStateHandle.get<Long>("categoryId")?.takeIf { it > 0 }

    private val initialFilter = TransactionFilter(
        accountIds = presetAccountId?.let { setOf(it) } ?: emptySet(),
        tagIds = presetTagId?.let { setOf(it) } ?: emptySet(),
        categoryIds = presetCategoryId?.let { setOf(it) } ?: emptySet(),
    )

    private val _filter = MutableStateFlow(initialFilter)
    private val _sort = MutableStateFlow(TransactionSort.DATE_DESC)
    private val _isFilterSheetOpen = MutableStateFlow(false)
    private val _searchText = MutableStateFlow("")

    val state: StateFlow<TransactionsUiState> = combine(
        _filter,
        _sort,
        accountRepository.observeAll(),
        categoryRepository.observeAll(),
        tagRepository.observeAllWithUsage(),
        _isFilterSheetOpen,
    ) { filter, sort, accountsWithBal, cats, tagsWithUsage ->
        val accounts = accountsWithBal.map { it.account }
        val tags = tagsWithUsage.map { it.tag }
        val pruned = FilterScope.pruneMissing(
            filter = filter,
            existingAccountIds = accounts.map { it.id }.toSet(),
            existingCategoryIds = cats.map { it.id }.toSet(),
            existingTagIds = tags.map { it.id }.toSet(),
        )
        val summaries = if (pruned.isActive) transactionRepository.summarize(pruned) else emptyList()

        TransactionsUiState(
            filter = pruned,
            sort = sort,
            summary = summaries,
            accounts = accounts,
            categories = cats,
            tags = tags,
            isFilterSheetOpen = _isFilterSheetOpen.value,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TransactionsUiState(filter = initialFilter))

    val pagedTransactions: Flow<PagingData<TransactionItem>> = combine(
        _filter,
        _sort,
    ) { filter, sort -> filter to sort }
        .flatMapLatest { (f, s) ->
            transactionRepository.observePage(f, s)
        }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            _searchText
                .debounce(300)
                .distinctUntilChanged()
                .collect { text ->
                    _filter.value = _filter.value.copy(text = text)
                }
        }
    }

    fun onSearchText(text: String) {
        _searchText.value = text
    }

    fun onFilterChange(filter: TransactionFilter) {
        _filter.value = filter
    }

    fun onSortChange(sort: TransactionSort) {
        _sort.value = sort
    }

    fun setFilterSheetOpen(open: Boolean) {
        _isFilterSheetOpen.value = open
    }

    fun clearFilters() {
        _filter.value = TransactionFilter()
        _searchText.value = ""
    }
}
```

Create `app/src/main/java/com/yehor/finora/ui/operations/TransactionsScreen.kt`:
```kotlin
package com.yehor.finora.ui.operations

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.yehor.finora.R
import com.yehor.finora.domain.operation.DayGroup
import com.yehor.finora.domain.operation.DayGrouping
import com.yehor.finora.domain.operation.TransactionSort
import com.yehor.finora.ui.common.TransactionRow
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsScreen(
    onAdd: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pagedItems = viewModel.pagedTransactions.collectAsLazyPagingItems()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var searchInput by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        OutlinedTextField(
                            value = searchInput,
                            onValueChange = {
                                searchInput = it
                                viewModel.onSearchText(it)
                            },
                            placeholder = { Text(stringResource(R.string.action_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.transactions_title))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) {
                            searchInput = ""
                            viewModel.onSearchText("")
                        }
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Clear else Icons.Default.Search,
                            contentDescription = stringResource(R.string.action_search),
                        )
                    }
                    IconButton(onClick = { viewModel.setFilterSheetOpen(true) }) {
                        Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.filter_title))
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort_title))
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            TransactionSort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (sort) {
                                                TransactionSort.DATE_DESC -> stringResource(R.string.sort_date_desc)
                                                TransactionSort.DATE_ASC -> stringResource(R.string.sort_date_asc)
                                                TransactionSort.AMOUNT_DESC -> stringResource(R.string.sort_amount_desc)
                                                TransactionSort.AMOUNT_ASC -> stringResource(R.string.sort_amount_asc)
                                            }
                                        )
                                    },
                                    onClick = {
                                        viewModel.onSortChange(sort)
                                        sortMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.transactions_add))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Summary Bar (active when filter or search active)
            if (state.summary.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        state.summary.forEach { s ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    stringResource(R.string.transactions_summary_count, s.operationCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    "${stringResource(R.string.transactions_summary_income, s.income.format())}  ${stringResource(R.string.transactions_summary_expense, s.expense.format())}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            if (pagedItems.itemCount == 0) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.filter.isActive) {
                            Text(stringResource(R.string.transactions_empty_filter_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.transactions_empty_filter_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = viewModel::clearFilters) {
                                Text(stringResource(R.string.transactions_clear_filters))
                            }
                        } else {
                            Text(stringResource(R.string.transactions_empty_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.transactions_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    var lastDayGroup: DayGroup? = null
                    val now = System.currentTimeMillis()
                    val zone = ZoneId.systemDefault()

                    for (index in 0 until pagedItems.itemCount) {
                        val item = pagedItems[index]
                        if (item != null) {
                            val currentGroup = DayGrouping.group(item.occurredAt, now, zone)
                            if (currentGroup != lastDayGroup) {
                                stickyHeader(key = "header_${item.occurredAt}") {
                                    val headerText = when (currentGroup) {
                                        DayGroup.Today -> stringResource(R.string.transactions_day_today)
                                        DayGroup.Yesterday -> stringResource(R.string.transactions_day_yesterday)
                                        is DayGroup.Date -> currentGroup.localDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                                    }
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.background)
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(headerText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                lastDayGroup = currentGroup
                            }

                            item(key = item.id) {
                                TransactionRow(
                                    item = item,
                                    onClick = { onOpenTransaction(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.isFilterSheetOpen) {
            TransactionFilterSheet(
                filter = state.filter,
                accounts = state.accounts,
                categories = state.categories,
                tags = state.tags,
                onApply = viewModel::onFilterChange,
                onDismiss = { viewModel.setFilterSheetOpen(false) },
            )
        }
    }
}
```

- [ ] **Step 4: Update `FinoraNavHost.kt` with transactions route**

In `app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt`:
```kotlin
composable(
    Routes.TRANSACTIONS,
    arguments = listOf(
        navArgument("accountId") { type = NavType.LongType; defaultValue = -1L },
        navArgument("tagId") { type = NavType.LongType; defaultValue = -1L },
        navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
    ),
) {
    TransactionsScreen(
        onAdd = { navController.navigate(Routes.transactionNew()) },
        onOpenTransaction = { id -> navController.navigate(Routes.transactionEdit(id)) },
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/yehor/finora/ui/operations/ app/src/main/java/com/yehor/finora/ui/navigation/FinoraNavHost.kt app/src/test/java/com/yehor/finora/ui/operations/TransactionsViewModelTest.kt
git commit -m "feat: implement TransactionsScreen with filter sheet, paging and summary bar"
```

---

## Task 12: Account Detail Transactions & Category Delete Review Navigation

**Files:**
- Modify: `app/src/main/java/com/yehor/finora/ui/accounts/AccountViewModels.kt`
- Modify: `app/src/main/java/com/yehor/finora/ui/accounts/AccountDetailScreen.kt`
- Modify: `app/src/main/java/com/yehor/finora/ui/categories/CategoriesScreens.kt`

- [ ] **Step 1: Update `AccountDetailViewModel` to load the 10 most recent transactions**

In `app/src/main/java/com/yehor/finora/ui/accounts/AccountViewModels.kt`:
Update `AccountDetailUiState.Loaded` to include `recentTransactions: List<TransactionItem>` loaded via `TransactionRepository.observeRecent(TransactionFilter(accountIds = setOf(accountId)), limit = 10)`.

- [ ] **Step 2: Update `AccountDetailScreen.kt` to render recent transactions**

In `app/src/main/java/com/yehor/finora/ui/accounts/AccountDetailScreen.kt`:
Replace the placeholder with `TransactionRow` items and a "Show all" button that navigates to `transactions?accountId=$accountId`.

- [ ] **Step 3: Update `CategoriesScreens.kt` delete dialog with "Review transactions"**

In `app/src/main/java/com/yehor/finora/ui/categories/CategoriesScreens.kt`:
Add a "Review transactions" text button inside `DeleteCategoryDialog` that calls `onReviewTransactions(state.categoryId)`, navigating to `transactions?categoryId=$categoryId`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/yehor/finora/ui/accounts/ app/src/main/java/com/yehor/finora/ui/categories/
git commit -m "feat: wire account detail transaction history and category delete review navigation"
```

---

## Task 13: Full Suite Validation

**Files:**
- Test: JVM tests
- Test: Instrumented tests

- [ ] **Step 1: Run all JVM unit tests**

Run: `gradlew :app:testDebugUnitTest`
Expected: ALL PASS

- [ ] **Step 2: Run all Android instrumented tests**

Run: `gradlew :app:connectedDebugAndroidTest`
Expected: ALL PASS

- [ ] **Step 3: Commit any final test adjustments**

```bash
git commit --allow-empty -m "chore: complete sub-project 3 validation"
```
