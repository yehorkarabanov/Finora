package com.yehor.finora.domain.dataio

import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CsvFormatTest {

    @Test
    fun `header matches section 7 point 2 contract exactly`() {
        val expected = listOf(
            "id",
            "type",
            "date",
            "time",
            "timestamp_utc",
            "account",
            "amount",
            "currency",
            "to_account",
            "to_amount",
            "to_currency",
            "category",
            "category_path",
            "tags",
            "description",
            "created_at_utc",
            "updated_at_utc",
        )
        assertEquals(expected, CsvFormat.HEADER)
    }

    @Test
    fun `writeRow encodes RFC 4180 quoting and CRLF endings`() {
        val out = StringBuilder()
        CsvFormat.writeRow(out, listOf("1", "INCOME", "Hello, World", "He said \"Hi\"", "Line1\nLine2", "CR\rLF"))
        assertEquals("1,INCOME,\"Hello, World\",\"He said \"\"Hi\"\"\",\"Line1\nLine2\",\"CR\rLF\"\r\n", out.toString())
    }

    @Test
    fun `textField guards formula injection characters`() {
        assertEquals("'=1+1", CsvFormat.textField("=1+1"))
        assertEquals("'+sum()", CsvFormat.textField("+sum()"))
        assertEquals("'-cmd", CsvFormat.textField("-cmd"))
        assertEquals("'@alert", CsvFormat.textField("@alert"))
        assertEquals("'\ttab", CsvFormat.textField("\ttab"))
        assertEquals("'\rreturn", CsvFormat.textField("\rreturn"))

        // Plain text should not be altered
        assertEquals("Coffee", CsvFormat.textField("Coffee"))
        assertEquals("", CsvFormat.textField(""))
        assertEquals("", CsvFormat.textField(null))
    }

    @Test
    fun `amount formatting does not apply formula guard to negative numbers`() {
        val usd = Currency("USD", 2, "$", "US Dollar")
        val negativeMoney = Money(-1250L, usd)
        val formattedAmount = CsvFormat.amount(negativeMoney)
        assertEquals("-12.50", formattedAmount)
        assertFalse(formattedAmount.startsWith("'"))
    }

    @Test
    fun `tagList escapes backslashes and semicolons and joins with semicolon`() {
        val tags = listOf("grocery", "food;drinks", "backslash\\tag", "mixed;\\tag")
        val formatted = CsvFormat.tagList(tags)
        assertEquals("grocery;food\\;drinks;backslash\\\\tag;mixed\\;\\\\tag", formatted)
    }

    @Test
    fun `amount renders correctly for scales 0 2 3 4 negative and extreme values`() {
        val jpy = Currency("JPY", 0, "¥", "Yen")
        val usd = Currency("USD", 2, "$", "US Dollar")
        val bhd = Currency("BHD", 3, "BD", "Bahraini Dinar")
        val custom = Currency("CST", 4, "C", "Custom Scale 4")

        assertEquals("1000", CsvFormat.amount(Money(1000L, jpy)))
        assertEquals("-1000", CsvFormat.amount(Money(-1000L, jpy)))
        assertEquals("0", CsvFormat.amount(Money(0L, jpy)))

        assertEquals("12.50", CsvFormat.amount(Money(1250L, usd)))
        assertEquals("-12.50", CsvFormat.amount(Money(-1250L, usd)))
        assertEquals("0.05", CsvFormat.amount(Money(5L, usd)))

        assertEquals("0.005", CsvFormat.amount(Money(5L, bhd)))
        assertEquals("12.345", CsvFormat.amount(Money(12345L, bhd)))
        assertEquals("-0.005", CsvFormat.amount(Money(-5L, bhd)))

        assertEquals("1.2345", CsvFormat.amount(Money(12345L, custom)))
        assertEquals("0.0001", CsvFormat.amount(Money(1L, custom)))

        // Extreme Longs
        val maxMoney = Money(Long.MAX_VALUE, usd)
        assertTrue(CsvFormat.amount(maxMoney).startsWith("92233720368547758."))
    }

    @Test
    fun `dateParts handles time zones correctly`() {
        // 2026-11-07T12:49:28Z -> 1794055768000L
        val epochMs = Instant.parse("2026-11-07T12:49:28Z").toEpochMilli()

        // UTC
        val utcParts = CsvFormat.dateParts(epochMs, ZoneId.of("UTC"))
        assertEquals("2026-11-07", utcParts.first)
        assertEquals("12:49:28", utcParts.second)
        assertEquals("2026-11-07T12:49:28Z", utcParts.third)

        // Half hour offset: Asia/Kolkata (UTC+5:30) -> 18:19:28
        val kolkataParts = CsvFormat.dateParts(epochMs, ZoneId.of("Asia/Kolkata"))
        assertEquals("2026-11-07", kolkataParts.first)
        assertEquals("18:19:28", kolkataParts.second)
        assertEquals("2026-11-07T12:49:28Z", kolkataParts.third)

        // Near midnight UTC: 2026-11-07T23:55:00Z -> next day in Asia/Tokyo (UTC+9)
        val midnightEpoch = Instant.parse("2026-11-07T23:55:00Z").toEpochMilli()
        val tokyoParts = CsvFormat.dateParts(midnightEpoch, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-11-08", tokyoParts.first)
        assertEquals("08:55:00", tokyoParts.second)
        assertEquals("2026-11-07T23:55:00Z", tokyoParts.third)
    }
}
