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
