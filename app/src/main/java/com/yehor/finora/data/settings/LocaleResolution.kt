package com.yehor.finora.data.settings

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.LocaleList
import com.yehor.finora.domain.settings.AppLanguage
import java.util.Locale

/** DI-free locale resolution shared by Application/Activity attachBaseContext and the settings UI. */
object LocaleResolution {

    /** The language the app uses when the choice is "System default": Russian only if the primary system locale is ru. */
    fun resolvedSystemLanguage(): AppLanguage {
        val primary = Resources.getSystem().configuration.locales.let { if (it.isEmpty) null else it[0] }
        return AppLanguage.resolveSystem(primary?.toLanguageTag())
    }

    /**
     * Returns [base] wrapped with the deterministic locale when no explicit app locale is set;
     * otherwise returns [base] unchanged (the system already applies the explicit choice).
     */
    fun wrap(base: Context): Context {
        val lm = base.getSystemService(LocaleManager::class.java) ?: return base
        if (!lm.applicationLocales.isEmpty) return base
        val tag = resolvedSystemLanguage().tag ?: return base
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return base.createConfigurationContext(config)
    }
}
