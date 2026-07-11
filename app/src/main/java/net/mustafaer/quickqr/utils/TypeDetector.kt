package net.mustafaer.quickqr.utils

import android.net.Uri
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

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

    private val emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
    private val dateRegex = "^(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})$".toRegex()

    private fun isPhoneNumber(s: String): Boolean {
        val clean = s.trim()
        if (dateRegex.matches(clean)) return false
        if (!"^\\+?[\\d\\s\\-()]+$".toRegex().matches(clean)) return false
        val digitCount = clean.count { it.isDigit() }
        if (digitCount < 7 || digitCount > 15) return false
        val isOnlyDigits = clean.all { it.isDigit() }
        if (isOnlyDigits) {
            return digitCount in 7..15
        }
        return true
    }

    fun detect(text: String): ScanResultType {
        val t = text.trim()
        return when {
            t.startsWith("WIFI:", ignoreCase = true) -> ScanResultType.WIFI
            t.startsWith("BEGIN:VCARD", ignoreCase = true) ||
            t.startsWith("MECARD:", ignoreCase = true) -> ScanResultType.VCARD
            t.startsWith("BEGIN:VCALENDAR", ignoreCase = true) || 
            t.startsWith("BEGIN:VEVENT", ignoreCase = true) -> ScanResultType.CALENDAR

            t.startsWith("MATMSG:", ignoreCase = true) || 
            t.startsWith("mailto:", ignoreCase = true) || 
            emailRegex.matches(t) -> ScanResultType.EMAIL

            t.startsWith("sms:", ignoreCase = true) || 
            t.startsWith("smsto:", ignoreCase = true) -> ScanResultType.SMS

            t.startsWith("tel:", ignoreCase = true) || 
            isPhoneNumber(t) -> ScanResultType.PHONE

            t.startsWith("geo:", ignoreCase = true) -> ScanResultType.GEO

            t.startsWith("http://", ignoreCase = true) || 
            t.startsWith("https://", ignoreCase = true) || 
            t.startsWith("ftp://", ignoreCase = true) ||
            android.util.Patterns.WEB_URL.matcher(t).matches() -> ScanResultType.URL

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
                val clean = if (t.startsWith("smsto:", ignoreCase = true)) {
                    t.substring(6)
                } else if (t.startsWith("sms:", ignoreCase = true)) {
                    t.substring(4)
                } else {
                    t
                }
                if (clean.contains(":")) {
                    val parts = clean.split(":", limit = 2)
                    val number = parts[0].trim()
                    val body = parts[1].trim()
                    "sms:$number?body=${urlEncode(body)}"
                } else {
                    "sms:$clean"
                }
            }
            ScanResultType.GEO -> t
            else -> t
        }
    }

    fun parseWifi(text: String): WifiData? {
        if (!text.startsWith("WIFI:", ignoreCase = true)) return null
        val ssid = cleanValue(getValueFromFormat(text, "S"))
        val security = getValueFromFormat(text, "T").ifEmpty { "Open" }
        val password = cleanValue(getValueFromFormat(text, "P"))
        val hidden = getValueFromFormat(text, "H").lowercase() == "true"
        return WifiData(ssid, security, password, hidden)
    }

    fun parseVCardName(text: String): String? {
        val t = text.trim()
        if (t.startsWith("MECARD:", ignoreCase = true)) {
            val n = cleanValue(getValueFromFormat(t, "N"))
            if (n.isNotEmpty()) {
                if (n.contains(",")) {
                    val parts = n.split(",")
                    val lastName = parts.getOrNull(0)?.trim() ?: ""
                    val firstName = parts.getOrNull(1)?.trim() ?: ""
                    return "$firstName $lastName".trim().ifEmpty { n }
                }
                return n
            }
            return null
        }

        val fnRegex = "^FN(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = fnRegex.find(text)
        val fn = matchResult?.groupValues?.get(1)?.trim()
        if (!fn.isNullOrEmpty()) return unescapeVCardValue(fn)

        val nRegex = "^N(?:;[^:]*)?:((?:[^\\\\;]|\\\\.)*);?((?:[^\\\\;]|\\\\.)*)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val nMatch = nRegex.find(text)
        if (nMatch != null) {
            val lastName = nMatch.groupValues.getOrNull(1)?.trim() ?: ""
            val firstName = nMatch.groupValues.getOrNull(2)?.trim() ?: ""
            val fullName = "$firstName $lastName".trim()
            return if (fullName.isNotEmpty()) unescapeVCardValue(fullName) else null
        }
        return null
    }

    fun parseEventSummary(text: String): String? {
        val summaryRegex = "^SUMMARY(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = summaryRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseVCardPhone(text: String): String? {
        val t = text.trim()
        if (t.startsWith("MECARD:", ignoreCase = true)) {
            return getValueFromFormat(t, "TEL").ifEmpty { null }
        }
        val telRegex = "^TEL(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = telRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseVCardEmail(text: String): String? {
        val t = text.trim()
        if (t.startsWith("MECARD:", ignoreCase = true)) {
            return getValueFromFormat(t, "EMAIL").ifEmpty { null }
        }
        val vcardEmailRegex = "^EMAIL(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = vcardEmailRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseVCardOrg(text: String): String? {
        val t = text.trim()
        if (t.startsWith("MECARD:", ignoreCase = true)) {
            return getValueFromFormat(t, "ORG").ifEmpty { null }
        }
        val orgRegex = "^ORG(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = orgRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseVCardTitle(text: String): String? {
        val t = text.trim()
        if (t.startsWith("MECARD:", ignoreCase = true)) {
            return getValueFromFormat(t, "TIL").ifEmpty { null }
        }
        val titleRegex = "^TITLE(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = titleRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseVCardWebsite(text: String): String? {
        val t = text.trim()
        if (t.startsWith("MECARD:", ignoreCase = true)) {
            return getValueFromFormat(t, "URL").ifEmpty { null }
        }
        val urlRegex = "^URL(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = urlRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseEventDescription(text: String): String? {
        val descRegex = "^DESCRIPTION(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = descRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseEventLocation(text: String): String? {
        val locRegex = "^LOCATION(?:;[^:]*)?:(.+)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = locRegex.find(text)
        return matchResult?.groupValues?.get(1)?.trim()?.let { unescapeVCardValue(it) }?.ifEmpty { null }
    }

    fun parseEventTime(text: String, key: String): Long? {
        val timeRegex = "^$key(?:;[^:]*)?:([\\d\\-]+(?:T[\\d:]+(?:Z|[+-]\\d{2}:?\\d{2}|[+-]\\d{4})?)?)".toRegex(setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
        val matchResult = timeRegex.find(text)
        val timeStr = matchResult?.groupValues?.get(1) ?: return null
        return try {
            val hasT = timeStr.contains("T", ignoreCase = true)
            if (hasT) {
                val parts = timeStr.split("T", ignoreCase = true)
                val datePart = parts[0].replace("-", "")
                val timeAndZone = parts[1]
                
                val zoneIndex = timeAndZone.indexOfAny(charArrayOf('+', '-', 'Z', 'z'))
                val (timePart, zonePart) = if (zoneIndex >= 0) {
                    timeAndZone.substring(0, zoneIndex) to timeAndZone.substring(zoneIndex)
                } else {
                    timeAndZone to ""
                }
                
                val rawTime = timePart.replace(":", "")
                val cleanTime = when {
                    rawTime.length == 4 -> rawTime + "00"
                    rawTime.length >= 6 -> rawTime.substring(0, 6)
                    else -> rawTime
                }
                val normalizedZone = zonePart
                    .replace("Z", "+0000", ignoreCase = true)
                    .replace("z", "+0000")
                    .replace(":", "")
                
                // If the timezone offset is only 2 digits (e.g. +02 or -05), pad with zeros to make it 4 digits (e.g. +0200)
                val finalZone = if (normalizedZone.length == 3) normalizedZone + "00" else normalizedZone
                
                val normalized = "${datePart}T${cleanTime}$finalZone"
                
                if (finalZone.isNotEmpty()) {
                    SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.US).parse(normalized)?.time
                } else {
                    SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(normalized)?.time
                }
            } else {
                val cleanDate = timeStr.replace("-", "")
                SimpleDateFormat("yyyyMMdd", Locale.US).parse(cleanDate)?.time
            }
        } catch (e: Exception) {
            null
        }
    }


    private fun getValueFromFormat(text: String, key: String): String {
        val match = "(?:^[a-zA-Z]+:|;)(?i)$key:((?:[^\\\\;]|\\\\.)*)".toRegex().find(text)
        return match?.groupValues?.get(1) ?: ""
    }

    private fun cleanValue(value: String): String {
        var v = value.trim()
        if (v.startsWith("\"") && v.endsWith("\"") && v.length >= 2) {
            v = v.substring(1, v.length - 1)
        }
        return v.replace("\\;", ";")
            .replace("\\,", ",")
            .replace("\\:", ":")
            .replace("\\\\", "\\")
    }

    private fun urlEncode(str: String): String {
        return try {
            URLEncoder.encode(str, "UTF-8")
        } catch (e: Exception) {
            str
        }
    }

    private fun unescapeVCardValue(value: String): String {
        return value.replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\N", "\n")
    }
}
