package com.yehor.finora.domain.recurring

import com.yehor.finora.domain.money.CurrencyRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ScheduleDraftValidatorTest {

    private val usd = CurrencyRegistry.get("USD") // scale 2
    private val jpy = CurrencyRegistry.get("JPY") // scale 0
    private val bhd = CurrencyRegistry.get("BHD") // scale 3
    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 11, 6)
    private val now = today.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `valid draft has no errors`() {
        val errors = ScheduleDraftValidator.validate(
            name = "Salary",
            amountText = "1500.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `name errors - empty and too long`() {
        val emptyErrors = ScheduleDraftValidator.validate(
            name = "   ",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(emptyErrors.contains(ScheduleDraftError.NAME_EMPTY))

        val name60 = "a".repeat(60)
        val errors60 = ScheduleDraftValidator.validate(
            name = name60,
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(errors60.isEmpty())

        val name61 = "a".repeat(61)
        val errors61 = ScheduleDraftValidator.validate(
            name = name61,
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(errors61.contains(ScheduleDraftError.NAME_TOO_LONG))
    }

    @Test
    fun `amount errors - empty, invalid, too precise, not positive, out of range`() {
        fun testAmount(text: String, currency: com.yehor.finora.domain.money.Currency = usd) =
            ScheduleDraftValidator.validate(
                name = "Test",
                amountText = text,
                currency = currency,
                accountId = 1L,
                categoryId = 2L,
                rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
                firstDate = today,
                endDate = null,
                now = now,
                zone = zone,
            )

        assertTrue(testAmount("").contains(ScheduleDraftError.AMOUNT_EMPTY))
        assertTrue(testAmount("abc").contains(ScheduleDraftError.AMOUNT_INVALID))
        assertTrue(testAmount("10.123", usd).contains(ScheduleDraftError.AMOUNT_TOO_PRECISE))
        assertTrue(testAmount("10.1", jpy).contains(ScheduleDraftError.AMOUNT_TOO_PRECISE))
        assertTrue(testAmount("10.1234", bhd).contains(ScheduleDraftError.AMOUNT_TOO_PRECISE))
        assertTrue(testAmount("0.00").contains(ScheduleDraftError.AMOUNT_NOT_POSITIVE))
        assertTrue(testAmount("-5.00").contains(ScheduleDraftError.AMOUNT_NOT_POSITIVE))
        assertTrue(testAmount("99999999999999999999.00").contains(ScheduleDraftError.AMOUNT_OUT_OF_RANGE))
    }

    @Test
    fun `account and category missing errors`() {
        val noAccount = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = null,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(noAccount.contains(ScheduleDraftError.ACCOUNT_MISSING))

        val noCategoryCreate = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = null,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
            isEdit = false,
        )
        assertTrue(noCategoryCreate.contains(ScheduleDraftError.CATEGORY_MISSING))

        val noCategoryEditHadCategory = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = null,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
            isEdit = true,
            hadCategoryBefore = true,
        )
        assertTrue(noCategoryEditHadCategory.contains(ScheduleDraftError.CATEGORY_MISSING))

        val noCategoryEditWasUncategorized = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = null,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
            isEdit = true,
            hadCategoryBefore = false,
        )
        assertTrue(noCategoryEditWasUncategorized.isEmpty())
    }

    @Test
    fun `interval out of range`() {
        fun testInterval(interval: Int) = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, interval),
            firstDate = today,
            endDate = null,
            now = now,
            zone = zone,
        )

        assertTrue(testInterval(0).contains(ScheduleDraftError.INTERVAL_OUT_OF_RANGE))
        assertTrue(testInterval(366).contains(ScheduleDraftError.INTERVAL_OUT_OF_RANGE))
        assertTrue(testInterval(1).isEmpty())
        assertTrue(testInterval(365).isEmpty())
    }

    @Test
    fun `date bounds and end before start`() {
        val before2000 = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = LocalDate.of(1999, 12, 31),
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(before2000.contains(ScheduleDraftError.DATE_OUT_OF_RANGE))

        val after2100 = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = LocalDate.of(2101, 1, 1),
            now = now,
            zone = zone,
        )
        assertTrue(after2100.contains(ScheduleDraftError.DATE_OUT_OF_RANGE))

        val endBeforeStart = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = today,
            endDate = today.minusDays(1),
            now = now,
            zone = zone,
        )
        assertTrue(endBeforeStart.contains(ScheduleDraftError.END_BEFORE_START))
    }

    @Test
    fun `too many past occurrences at 1000 versus 1001`() {
        // Daily rule starting 999 days ago -> 1000 occurrences due up to today
        val start1000 = today.minusDays(999)
        val errors1000 = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = start1000,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(errors1000.isEmpty())

        // Daily rule starting 1000 days ago -> 1001 occurrences due up to today
        val start1001 = today.minusDays(1000)
        val errors1001 = ScheduleDraftValidator.validate(
            name = "Test",
            amountText = "10.00",
            currency = usd,
            accountId = 1L,
            categoryId = 2L,
            rule = RecurrenceRule(RecurrenceUnit.DAY, 1),
            firstDate = start1001,
            endDate = null,
            now = now,
            zone = zone,
        )
        assertTrue(errors1001.contains(ScheduleDraftError.TOO_MANY_PAST_OCCURRENCES))
    }
}
