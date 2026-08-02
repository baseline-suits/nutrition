package de.baseline.nutrition.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleController {
    fun apply(languageTag: String) {
        val locale = LocaleListCompat.forLanguageTags(
            if (languageTag == "ru") "ru" else "de",
        )
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != locale.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(locale)
        }
    }
}
