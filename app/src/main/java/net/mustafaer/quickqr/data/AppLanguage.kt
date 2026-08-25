package net.mustafaer.quickqr.data

import java.util.Locale

/**
 * The single source of truth for which languages QuickQR ships.
 *
 * Adding a language means adding one entry here, one `values-<code>/strings.xml`,
 * and one `<locale>` in `res/xml/locales_config.xml` — nothing else. The settings
 * picker, the label shown next to it and the system-language fallback all read
 * from this list.
 *
 * [displayName] is intentionally written in the language itself: a picker that
 * labels every option in the *current* language is useless to someone who cannot
 * read the current language and is trying to escape it.
 */
enum class AppLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    TURKISH("tr", "Türkçe"),
    GERMAN("de", "Deutsch"),
    HINDI("hi", "हिन्दी"),
    ARABIC("ar", "العربية");

    companion object {
        val DEFAULT = ENGLISH

        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: DEFAULT

        /**
         * The shipped language that best matches the device locale, falling back
         * to [DEFAULT] when the device speaks something QuickQR has not been
         * translated into.
         */
        fun matchingSystemLocale(locale: Locale = Locale.getDefault()): AppLanguage =
            fromCode(locale.language)
    }
}
