package com.yehor.finora.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun regionalTagsMapToBaseLanguage() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTag("ru-RU"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromTag("ru"))
    }

    @Test fun emptyOrNullIsSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
    }

    @Test fun resolveSystemPicksRussianOnlyForPrimaryRu() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.resolveSystem("ru-RU"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.resolveSystem("ru"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveSystem("uk-UA"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveSystem("de"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveSystem("en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.resolveSystem(null))
    }

    @Test fun unknownIsSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("de-DE"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag("xx"))
    }
}
