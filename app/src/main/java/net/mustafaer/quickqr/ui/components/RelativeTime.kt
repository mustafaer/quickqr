package net.mustafaer.quickqr.ui.components

import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "5 min. ago", "yesterday", then a plain date — in the app's language, read
 * from the Compose configuration because the activity is not recreated on a
 * language change.
 */
class RelativeTimeFormatter(locale: Locale) {

    private val absolute = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "dMMMyyyy"), locale)

    private val formatter = RelativeDateTimeFormatter.getInstance(ULocale.forLocale(locale))

    fun format(then: Long, now: Long): String {
        val minutes = ((now - then).coerceAtLeast(0) / 60_000L).toDouble()
        return when {
            minutes < 1 -> formatter.format(RelativeDateTimeFormatter.Direction.PLAIN, RelativeDateTimeFormatter.AbsoluteUnit.NOW)
            minutes < 60 -> formatter.format(minutes, RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
            minutes < 24 * 60 -> formatter.format(
                floor(minutes / 60), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.HOURS
            )
            minutes < 2 * 24 * 60 -> formatter.format(RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.AbsoluteUnit.DAY)
            minutes < 7 * 24 * 60 -> formatter.format(
                floor(minutes / (24 * 60)), RelativeDateTimeFormatter.Direction.LAST, RelativeDateTimeFormatter.RelativeUnit.DAYS
            )
            else -> absolute.format(Date(then))
        }
    }

    private fun floor(value: Double) = kotlin.math.floor(value)
}

@Composable
fun rememberRelativeTimeFormatter(): RelativeTimeFormatter {
    val configuration = LocalConfiguration.current
    return remember(configuration) {
        RelativeTimeFormatter(ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault())
    }
}
