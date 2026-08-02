package com.lucasserafin94.iptvburo.ui.localization

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object AppLocaleController {
    const val DEFAULT_LANGUAGE_TAG = "pt-BR"
    val supportedLanguages =
        listOf(
            AppLanguage("pt-BR", "Português (Brasil)"),
            AppLanguage("en", "English"),
            AppLanguage("de", "Deutsch"),
            AppLanguage("it", "Italiano"),
        )

    fun hasSelection(context: Context): Boolean = preferences(context).contains(KEY_LANGUAGE)

    fun selectedLanguageTag(context: Context): String =
        preferences(context).getString(KEY_LANGUAGE, null) ?: DEFAULT_LANGUAGE_TAG

    fun applySelection(activity: Activity, languageTag: String) {
        require(supportedLanguages.any { it.tag == languageTag }) { "Unsupported app language." }
        preferences(activity).edit().putString(KEY_LANGUAGE, languageTag).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageTag)
        } else {
            activity.recreate()
        }
    }

    fun wrapBaseContext(context: Context): Context {
        if (!hasSelection(context) || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context
        }
        val locale = Locale.forLanguageTag(selectedLanguageTag(context))
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "app_locale"
    private const val KEY_LANGUAGE = "language_tag"
}

data class AppLanguage(val tag: String, val displayName: String)
