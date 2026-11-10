package com.yehor.finora.domain.settings

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    RUSSIAN("ru");

    companion object {
        /** Maps a BCP-47 tag (e.g. "ru-RU") or null/blank to a supported language; unknown becomes SYSTEM. */
        fun fromTag(tag: String?): AppLanguage {
            val lang = tag?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase().orEmpty()
            return entries.firstOrNull { it.tag != null && it.tag == lang } ?: SYSTEM
        }

        /** Language used for SYSTEM: Russian if the primary system locale is Russian, otherwise English. */
        fun resolveSystem(primarySystemLanguageTag: String?): AppLanguage =
            if (fromTag(primarySystemLanguageTag) == RUSSIAN) RUSSIAN else ENGLISH
    }
}
