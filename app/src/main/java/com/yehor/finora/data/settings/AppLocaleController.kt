package com.yehor.finora.data.settings

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.yehor.finora.domain.settings.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the framework per-app locale API; the system persists the choice and recreates the activity. */
@Singleton
class AppLocaleController @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val localeManager: LocaleManager = context.getSystemService(LocaleManager::class.java)

    private val _language = MutableStateFlow(read())
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(lang: AppLanguage) {
        val tag = lang.tag
        localeManager.applicationLocales =
            if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        _language.value = lang
    }

    private fun read(): AppLanguage {
        val locales = localeManager.applicationLocales
        return AppLanguage.fromTag(if (locales.isEmpty) null else locales[0].toLanguageTag())
    }
}
