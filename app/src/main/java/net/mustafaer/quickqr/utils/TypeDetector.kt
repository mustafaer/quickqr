package net.mustafaer.quickqr.utils

import android.net.Uri
import java.net.URLEncoder

enum class ScanResultType(val typeStr: String) {
    URL("url"),
    WIFI("wifi"),
    EMAIL("email"),
    PHONE("phone"),
    GEO("geo"),
    SMS("sms"),
    VCARD("vcard"),
    CALENDAR("calendar"),
    TEXT("text");

    companion object {
        fun fromString(str: String): ScanResultType {
            return entries.firstOrNull { it.typeStr == str } ?: TEXT
        }
    }
}

data class WifiData(
    val ssid: String,
    val security: String,
    val password: String,
    val hidden: Boolean
)

object TypeDetector {

    private val urlRegex = "^(https?|ftp)://.*$".toRegex(RegexOption.IGNORE_CASE)
    private val wwwRegex = "^www\\..*$".toRegex(RegexOption.IGNORE_CASE)
    private val emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
    private val phoneRegex = "^\\+?\\d[\\d\\s\\-()]{6,}$".toRegex()

    fun detect(text: String): ScanResultType {
        val t = text.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) || 
            t.startsWith("https://", ignoreCase = true) || 
            t.startsWith("ftp://", ignoreCase = true) ||
            wwwRegex.matches(t) -> ScanResultType.URL

            t.startsWith("WIFI:", ignoreCase = true) -> ScanResultType.WIFI
            t.startsWith("MATMSG:", ignoreCase = true) || 
            t.startsWith("mailto:", ignoreCase = true) || 
            emailRegex.matches(t) -> ScanResultType.EMAIL

            t.startsWith("sms:", ignoreCase = true) || 
            t.startsWith("smsto:", ignoreCase = true) -> ScanResultType.SMS

            t.startsWith("tel:", ignoreCase = true) || 
            phoneRegex.matches(t) -> ScanResultType.PHONE

            t.startsWith("geo:", ignoreCase = true) -> ScanResultType.GEO
            t.startsWith("BEGIN:VCARD", ignoreCase = true) -> ScanResultType.VCARD
            t.startsWith("BEGIN:VCALENDAR", ignoreCase = true) || 
            t.startsWith("BEGIN:VEVENT", ignoreCase = true) -> ScanResultType.CALENDAR

            else -> ScanResultType.TEXT
        }
    }

    fun getActionUrl(text: String, type: ScanResultType): String {
        val t = text.trim()
        return when (type) {
            ScanResultType.URL -> {
                if (t.startsWith("http://", ignoreCase = true) || 
                    t.startsWith("https://", ignoreCase = true) || 
                    t.startsWith("ftp://", ignoreCase = true)) {
                    t
                } else {
                    "https://$t"
                }
            }
            ScanResultType.EMAIL -> {
                if (t.startsWith("mailto:", ignoreCase = true)) {
                    t
                } else if (t.startsWith("MATMSG:", ignoreCase = true)) {
                    val to = getValueFromFormat(t, "TO")
                    val sub = getValueFromFormat(t, "SUB")
                    val body = getValueFromFormat(t, "BODY")
                    "mailto:$to?subject=${urlEncode(sub)}&body=${urlEncode(body)}"
                } else {
                    "mailto:$t"
                }
            }
            ScanResultType.PHONE -> {
                if (t.startsWith("tel:", ignoreCase = true)) t else "tel:$t"
            }
            ScanResultType.SMS -> {
                if (t.startsWith("smsto:", ignoreCase = true)) {
                    t.replaceFirst("smsto:", "sms:", ignoreCase = true)
                } else if (t.startsWith("sms:", ignoreCase = true)) {
                    t
                } else {
                    "sms:$t"
                }
            }
            ScanResultType.GEO -> t
            else -> t
        }
    }

    fun parseWifi(text: String): WifiData? {
        if (!text.startsWith("WIFI:", ignoreCase = true)) return null
        val ssid = getValueFromFormat(text, "S")
        val security = getValueFromFormat(text, "T").ifEmpty { "Open" }
        val password = getValueFromFormat(text, "P")
        val hidden = getValueFromFormat(text, "H").lowercase() == "true"
        return WifiData(ssid, security, password, hidden)
    }

    fun parseVCardName(text: String): String? {
        val fnRegex = "FN:(.+)".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = fnRegex.find(text)
        val fn = matchResult?.groupValues?.get(1)?.trim()
        if (!fn.isNullOrEmpty()) return fn

        val nRegex = "N:([^;]*);?([^;]*)".toRegex(RegexOption.IGNORE_CASE)
        val nMatch = nRegex.find(text)
        if (nMatch != null) {
            val lastName = nMatch.groupValues.getOrNull(1)?.trim() ?: ""
            val firstName = nMatch.groupValues.getOrNull(2)?.trim() ?: ""
            return "$firstName $lastName".trim().ifEmpty { null }
        }
        return null
    }

    fun parseEventSummary(text: String): String? {
        val summaryRegex = "SUMMARY:(.+)".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = summaryRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }

    private fun getValueFromFormat(text: String, key: String): String {
        val match = "(?i)$key:([^;]*)".toRegex().find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun urlEncode(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }
}
