package com.yehor.finora.domain.dataio

import org.junit.Assert.assertEquals
import org.junit.Test

class ImportReportFormatterTest {

    @Test
    fun `format limits to 50 lines with and N more`() {
        val problems = (1..60).map { ImportProblem("check $it", "subject $it") }
        val formatted = ImportReportFormatter.format(problems, totalProblems = 60)

        assertEquals(51, formatted.size)
        assertEquals("check 1: subject 1", formatted.first())
        assertEquals("check 50: subject 50", formatted[49])
        assertEquals("and 10 more", formatted[50])
    }

    @Test
    fun `format does not append more line when problems count is 50 or fewer`() {
        val problems = (1..50).map { ImportProblem("check $it", "subject $it") }
        val formatted = ImportReportFormatter.format(problems, totalProblems = 50)

        assertEquals(50, formatted.size)
        assertEquals("check 50: subject 50", formatted.last())
    }

    @Test
    fun `enrichSubject enriches account category tag schedule with names`() {
        val accounts = mapOf(7L to "Santander", 12L to "Cash")
        val categories = mapOf(3L to "Groceries")
        val tags = mapOf(5L to "trip-2026")
        val schedules = mapOf(2L to "Rent")

        assertEquals(
            "account 7 \"Santander\"",
            ImportReportFormatter.enrichSubject("account 7", accounts = accounts),
        )
        assertEquals(
            "category 3 \"Groceries\"",
            ImportReportFormatter.enrichSubject("category 3", categories = categories),
        )
        assertEquals(
            "tag 5 \"trip-2026\"",
            ImportReportFormatter.enrichSubject("tag 5", tags = tags),
        )
        assertEquals(
            "schedule 2 \"Rent\"",
            ImportReportFormatter.enrichSubject("schedule 2", schedules = schedules),
        )

        // Unknown subject or unknown ID kept as is
        assertEquals("account 99", ImportReportFormatter.enrichSubject("account 99", accounts = accounts))
        assertEquals("operation 14", ImportReportFormatter.enrichSubject("operation 14"))
        assertEquals("raw string", ImportReportFormatter.enrichSubject("raw string"))
    }
}
