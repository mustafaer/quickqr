package net.mustafaer.quickqr.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.android.material.color.MaterialColors
import net.mustafaer.quickqr.R
import net.mustafaer.quickqr.utils.ScanResultType

/**
 * The colours one scan type is drawn in. [container] and [onContainer] are a
 * Material tonal pair — tone 90 on tone 10 in light, 30 on 90 in dark — so text
 * on a type badge clears WCAG AA by construction. The old fixed hex badges with
 * white text measured as low as 2.1:1.
 */
@Immutable
data class TypeColors(val container: Color, val onContainer: Color, val accent: Color)

/** Brand hue per type. Never drawn as-is: it seeds [rememberTypeColors]. */
private val ScanResultType.seed: Int
    get() = when (this) {
        ScanResultType.URL -> 0xFF5B3FF2
        ScanResultType.WIFI -> 0xFFE5A93B
        ScanResultType.EMAIL -> 0xFF43A047
        ScanResultType.PHONE -> 0xFF1E88E5
        ScanResultType.GEO -> 0xFFE53935
        ScanResultType.SMS -> 0xFF00ACC1
        ScanResultType.VCARD -> 0xFF8E24AA
        ScanResultType.CALENDAR -> 0xFFF4511E
        ScanResultType.PAYMENT -> 0xFF00897B
        ScanResultType.TEXT -> 0xFF757575
    }.toInt()

val ScanResultType.icon: ImageVector
    get() = when (this) {
        ScanResultType.URL -> Icons.Outlined.Link
        ScanResultType.WIFI -> Icons.Outlined.Wifi
        ScanResultType.EMAIL -> Icons.Outlined.Email
        ScanResultType.PHONE -> Icons.Outlined.Call
        ScanResultType.GEO -> Icons.Outlined.Map
        ScanResultType.SMS -> Icons.Outlined.Sms
        ScanResultType.VCARD -> Icons.Outlined.Person
        ScanResultType.CALENDAR -> Icons.Outlined.Event
        ScanResultType.PAYMENT -> Icons.Outlined.CurrencyRupee
        ScanResultType.TEXT -> Icons.Outlined.TextFormat
    }

@get:StringRes
val ScanResultType.labelRes: Int
    get() = when (this) {
        ScanResultType.URL -> R.string.type_url
        ScanResultType.WIFI -> R.string.type_wifi
        ScanResultType.EMAIL -> R.string.type_email
        ScanResultType.PHONE -> R.string.type_phone
        ScanResultType.GEO -> R.string.type_geo
        ScanResultType.SMS -> R.string.type_sms
        ScanResultType.VCARD -> R.string.type_vcard
        ScanResultType.CALENDAR -> R.string.type_calendar
        ScanResultType.PAYMENT -> R.string.type_payment
        ScanResultType.TEXT -> R.string.type_text
    }

/**
 * Derives [TypeColors] from the type's hue, first nudged toward the current
 * primary colour so the palette stays coherent under Material You wallpaper
 * colours instead of clashing with them.
 */
@Composable
fun rememberTypeColors(type: ScanResultType): TypeColors {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    val primary = scheme.primary.toArgb()
    return remember(type, primary, isLight) {
        val roles = MaterialColors.getColorRoles(MaterialColors.harmonize(type.seed, primary), isLight)
        TypeColors(
            container = Color(roles.accentContainer),
            onContainer = Color(roles.onAccentContainer),
            accent = Color(roles.accent)
        )
    }
}
