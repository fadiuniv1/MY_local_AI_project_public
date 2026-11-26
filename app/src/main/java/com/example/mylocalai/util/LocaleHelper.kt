package com.example.mylocalai.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.LayoutDirection
import java.util.Locale

object LocaleHelper {
    const val LANG_SYSTEM = "system"
    const val LANG_EN = "en"
    const val LANG_HE = "he"
    const val LANG_AR = "ar"
    private const val PREFS = "locale_prefs"
    private const val KEY_LANG = "language"

    fun isRtl(language: String): Boolean =
        language.equals(LANG_HE, ignoreCase = true) || language.equals(LANG_AR, ignoreCase = true)

    fun getLanguage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, LANG_SYSTEM) ?: LANG_SYSTEM

    fun setLanguage(context: Context, language: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANG, language).apply()
    }

    fun wrap(context: Context, language: String): Context {
        if (language == LANG_SYSTEM) return context
        val locale = Locale(language)
        Locale.setDefault(locale)
        val config = context.resources.configuration
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }
        return context.createConfigurationContext(config)
    }
}
