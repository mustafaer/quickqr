package net.mustafaer.quickqr.utils

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

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
        fun fromString(str: String): ScanResultType =
            entries.firstOrNull { it.typeStr == str } ?: TEXT
    }
}

data class WifiData(
    val ssid: String,
    val security: String,
    val password: String,
    val hidden: Boolean
)

/**
 * A scanned contact card. Phone numbers and e-mail addresses are lists because a
 * real business card usually carries more than one of each — a work line and a
 * mobile, or a personal and a company address.
 */
data class VCardData(
    val name: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val organization: String? = null,
    val jobTitle: String? = null,
    val website: String? = null,
    val address: String? = null,
    val note: String? = null,
    val birthday: String? = null
) {
    val isEmpty: Boolean
        get() = name == null && phones.isEmpty() && emails.isEmpty() &&
                organization == null && jobTitle == null && website == null &&
                address == null && note == null && birthday == null
}

data class EventData(
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: Long? = null,
    val end: Long? = null,
    val isAllDay: Boolean = false
)

/**
 * Classifies raw QR payloads and pulls structured data out of the standard
 * encodings (Wi-Fi, vCard, MECARD, iCalendar, MATMSG, geo, tel, sms).
 *
 * Deliberately free of Android framework dependencies so the whole class can be
 * exercised by plain JVM unit tests.
 */
object TypeDetector {

    // --- Detection patterns ---------------------------------------------------

    private val emailRegex = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$".toRegex()
    private val dateRegex =
        "^(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}|\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{4})$".toRegex()
    private val phoneCharsRegex = "^\\+?[\\d\\s\\-()]+$".toRegex()

    /** Schemes that make something a link no matter what else the text looks like. */
    private val webSchemes = listOf("http://", "https://", "ftp://", "ftps://")

    /**
     * Replaces `android.util.Patterns.WEB_URL`, which cannot run in a plain JVM
     * test. Matches an optional scheme and userinfo, then a host (a dotted domain
     * with a 2–24 letter TLD, an IPv4 literal, or `localhost`), then an optional
     * port, path, query and fragment.
     */
    private val webUrlRegex = (
        "^(?:(?:https?|ftps?)://)?" +
            "(?:[\\w.~%+-]+(?::[^@\\s]*)?@)?" +
            "(?:localhost" +
            "|(?:\\d{1,3}\\.){3}\\d{1,3}" +
            "|(?:[\\p{L}\\p{N}](?:[\\p{L}\\p{N}-]{0,61}[\\p{L}\\p{N}])?\\.)+\\p{L}{2,24})" +
            "(?::\\d{1,5})?" +
            "(?:[/?#]\\S*)?$"
        ).toRegex(RegexOption.IGNORE_CASE)

    // --- vCard / iCalendar property patterns ---------------------------------

    private val fnRegex = property("FN")
    private val nRegex =
        "^N(?:;[^:]*)?:((?:[^\\\\;\\r\\n]|\\\\.)*);?((?:[^\\\\;\\r\\n]|\\\\.)*)".toRegex(MULTILINE_CI)
    private val telRegex = property("TEL")
    private val vcardEmailRegex = property("EMAIL")
    private val orgRegex = property("ORG")
    private val titleRegex = property("TITLE")
    private val urlRegex = property("URL")
    private val adrRegex = property("ADR")
    private val noteRegex = property("NOTE")
    private val bdayRegex = property("BDAY")
    private val summaryRegex = property("SUMMARY")
    private val descRegex = property("DESCRIPTION")
    private val locRegex = property("LOCATION")
    private val dtStartValueRegex = "^DTSTART(;[^:]*)?:(\\S+)".toRegex(MULTILINE_CI)

    private val MULTILINE_CI: Set<RegexOption>
        get() = setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)

    private fun property(name: String): Regex =
        "^$name(?:;[^:]*)?:(.*)$".toRegex(MULTILINE_CI)

    // --- Detection -----------------------------------------------------------

    private fun isPhoneNumber(s: String): Boolean {
        val clean = s.trim()
        if (dateRegex.matches(clean)) return false
        if (!phoneCharsRegex.matches(clean)) return false
        val digitCount = clean.count { it.isDigit() }
        return digitCount in 7..15
    }

    private fun hasWebScheme(t: String): Boolean =
        webSchemes.any { t.startsWith(it, ignoreCase = true) }

    fun detect(text: String): ScanResultType {
        val t = text.trim()
        return when {
            t.startsWith("WIFI:", ignoreCase = true) -> ScanResultType.WIFI

            t.startsWith("BEGIN:VCARD", ignoreCase = true) ||
                t.startsWith("MECARD:", ignoreCase = true) -> ScanResultType.VCARD

            t.startsWith("BEGIN:VCALENDAR", ignoreCase = true) ||
                t.startsWith("BEGIN:VEVENT", ignoreCase = true) -> ScanResultType.CALENDAR

            // Checked before the e-mail branch: `https://user@host.tld` is a link,
            // not an address, and the e-mail pattern would happily match it.
            hasWebScheme(t) -> ScanResultType.URL

            t.startsWith("MATMSG:", ignoreCase = true) ||
                t.startsWith("mailto:", ignoreCase = true) ||
                emailRegex.matches(t) -> ScanResultType.EMAIL

            t.startsWith("sms:", ignoreCase = true) ||
                t.startsWith("smsto:", ignoreCase = true) ||
                t.startsWith("mms:", ignoreCase = true) -> ScanResultType.SMS

            t.startsWith("tel:", ignoreCase = true) || isPhoneNumber(t) -> ScanResultType.PHONE

            t.startsWith("geo:", ignoreCase = true) -> ScanResultType.GEO

            webUrlRegex.matches(t) -> ScanResultType.URL

            else -> ScanResultType.TEXT
        }
    }

    // --- Actions -------------------------------------------------------------

    fun getActionUrl(text: String, type: ScanResultType): String {
        val t = text.trim()
        return when (type) {
            ScanResultType.URL -> if (hasWebScheme(t)) t else "https://$t"

            ScanResultType.EMAIL -> when {
                t.startsWith("mailto:", ignoreCase = true) -> t
                t.startsWith("MATMSG:", ignoreCase = true) -> {
                    val to = mecardField(t, "TO").orEmpty()
                    val subject = mecardField(t, "SUB").orEmpty()
                    val body = mecardField(t, "BODY").orEmpty()
                    buildString {
                        append("mailto:").append(to)
                        val params = buildList {
                            if (subject.isNotEmpty()) add("subject=${urlEncode(subject)}")
                            if (body.isNotEmpty()) add("body=${urlEncode(body)}")
                        }
                        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
                    }
                }
                else -> "mailto:$t"
            }

            ScanResultType.PHONE -> if (t.startsWith("tel:", ignoreCase = true)) t else "tel:$t"

            ScanResultType.SMS -> {
                val payload = when {
                    t.startsWith("smsto:", ignoreCase = true) -> t.substring(6)
                    t.startsWith("sms:", ignoreCase = true) -> t.substring(4)
                    t.startsWith("mms:", ignoreCase = true) -> t.substring(4)
                    else -> t
                }
                // `SMSTO:<number>:<message>` is the common QR form; `sms:<number>?body=`
                // is the URI form. Normalise both onto the URI form.
                val existingBody = payload.substringAfter("?body=", "")
                if (existingBody.isNotEmpty()) {
                    "sms:${payload.substringBefore("?body=").trim()}?body=${urlEncode(existingBody)}"
                } else if (payload.contains(":")) {
                    val number = payload.substringBefore(":").trim()
                    val body = payload.substringAfter(":").trim()
                    if (body.isEmpty()) "sms:$number" else "sms:$number?body=${urlEncode(body)}"
                } else {
                    "sms:${payload.trim()}"
                }
            }

            else -> t
        }
    }

    // --- Wi-Fi ---------------------------------------------------------------

    fun parseWifi(text: String): WifiData? {
        if (!text.trim().startsWith("WIFI:", ignoreCase = true)) return null
        val ssid = mecardField(text, "S").orEmpty()
        val security = mecardField(text, "T")?.takeIf { it.isNotEmpty() } ?: "nopass"
        val password = mecardField(text, "P").orEmpty()
        val hidden = mecardField(text, "H")?.lowercase() == "true"
        return WifiData(ssid, security, password, hidden)
    }

    // --- Contacts ------------------------------------------------------------

    fun parseVCard(text: String): VCardData? {
        val t = text.trim()
        return when {
            t.startsWith("MECARD:", ignoreCase = true) -> parseMeCard(t)
            t.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCardBody(unfold(t))
            else -> null
        }?.takeUnless { it.isEmpty }
    }

    private fun parseMeCard(t: String): VCardData {
        val name = mecardField(t, "N")?.let { raw ->
            // MECARD writes `Last,First`.
            if (raw.contains(",")) {
                val parts = raw.split(",")
                val last = parts.getOrNull(0)?.trim().orEmpty()
                val first = parts.getOrNull(1)?.trim().orEmpty()
                "$first $last".trim().ifEmpty { raw }
            } else {
                raw
            }
        }?.takeIf { it.isNotEmpty() }

        return VCardData(
            name = name,
            phones = mecardFields(t, "TEL") + mecardFields(t, "TEL-AV"),
            emails = mecardFields(t, "EMAIL"),
            organization = mecardField(t, "ORG")?.takeIf { it.isNotEmpty() },
            // MECARD has no standardised job-title key; TITLE is what writers that
            // bother to emit one actually use.
            jobTitle = mecardField(t, "TITLE")?.takeIf { it.isNotEmpty() },
            website = mecardField(t, "URL")?.takeIf { it.isNotEmpty() },
            address = mecardField(t, "ADR")?.takeIf { it.isNotEmpty() }
                ?.replace(",", ", ")?.collapseSpaces(),
            note = mecardField(t, "NOTE")?.takeIf { it.isNotEmpty() },
            birthday = mecardField(t, "BDAY")?.takeIf { it.isNotEmpty() }
        )
    }

    private fun parseVCardBody(text: String): VCardData {
        val name = firstValue(fnRegex, text) ?: nRegex.find(text)?.let { match ->
            val last = match.groupValues.getOrNull(1)?.trim().orEmpty()
            val first = match.groupValues.getOrNull(2)?.trim().orEmpty()
            unescapeValue("$first $last").trim().takeIf { it.isNotEmpty() }
        }

        return VCardData(
            name = name,
            phones = allValues(telRegex, text),
            emails = allValues(vcardEmailRegex, text),
            // ORG is `Company;Department;…`.
            organization = joinStructured(rawFirstValue(orgRegex, text)),
            jobTitle = firstValue(titleRegex, text),
            website = firstValue(urlRegex, text),
            // ADR is `pobox;ext;street;locality;region;postcode;country`.
            address = joinStructured(rawFirstValue(adrRegex, text)),
            note = firstValue(noteRegex, text),
            birthday = firstValue(bdayRegex, text)
        )
    }

    /**
     * Renders a semicolon-structured vCard value (ORG, ADR) as a readable line.
     * Splitting happens before unescaping so a literal `\;` inside a component —
     * a street name with a semicolon in it — is not mistaken for a separator.
     */
    private fun joinStructured(raw: String?): String? =
        raw?.let { splitUnescaped(it) }
            ?.map { unescapeValue(it).trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(", ")
            ?.takeIf { it.isNotEmpty() }

    private fun splitUnescaped(value: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '\\' && i + 1 < value.length -> {
                    current.append(c).append(value[i + 1]); i += 2
                }
                c == ';' -> {
                    parts.add(current.toString()); current.clear(); i++
                }
                else -> {
                    current.append(c); i++
                }
            }
        }
        parts.add(current.toString())
        return parts
    }

    // --- Calendar ------------------------------------------------------------

    fun parseEvent(text: String): EventData? {
        val t = unfold(text.trim())
        if (!t.contains("BEGIN:VCALENDAR", ignoreCase = true) &&
            !t.contains("BEGIN:VEVENT", ignoreCase = true)
        ) {
            return null
        }
        // All-day is either an explicit VALUE=DATE parameter or a value with no
        // time component. Testing the whole match would always find the T in
        // "DTSTART" itself.
        val dtStart = dtStartValueRegex.find(t)
        val dtStartParams = dtStart?.groupValues?.get(1).orEmpty()
        val dtStartValue = dtStart?.groupValues?.get(2).orEmpty()
        val isAllDay = dtStartValue.isNotEmpty() &&
            (!dtStartValue.contains("T", ignoreCase = true) ||
                dtStartParams.contains("VALUE=DATE", ignoreCase = true))

        return EventData(
            summary = firstValue(summaryRegex, t),
            description = firstValue(descRegex, t),
            location = firstValue(locRegex, t),
            start = parseEventTime(t, "DTSTART"),
            end = parseEventTime(t, "DTEND"),
            isAllDay = isAllDay
        )
    }

    fun parseEventTime(text: String, key: String): Long? {
        val timeRegex =
            ("^$key(?:;[^:]*)?:([\\d\\-]+(?:T[\\d:]+(?:Z|[+-]\\d{2}:?\\d{2}|[+-]\\d{4})?)?)")
                .toRegex(MULTILINE_CI)
        val timeStr = timeRegex.find(text)?.groupValues?.get(1) ?: return null
        return try {
            if (timeStr.contains("T", ignoreCase = true)) {
                val parts = timeStr.split("T", ignoreCase = true)
                val datePart = parts[0].replace("-", "")
                val timeAndZone = parts[1]

                val zoneIndex = timeAndZone.indexOfAny(charArrayOf('+', '-', 'Z', 'z'))
                val timePart: String
                val zonePart: String
                if (zoneIndex >= 0) {
                    timePart = timeAndZone.substring(0, zoneIndex)
                    zonePart = timeAndZone.substring(zoneIndex)
                } else {
                    timePart = timeAndZone
                    zonePart = ""
                }

                val rawTime = timePart.replace(":", "")
                val cleanTime = when {
                    rawTime.length == 4 -> rawTime + "00"
                    rawTime.length >= 6 -> rawTime.substring(0, 6)
                    else -> rawTime
                }
                val normalizedZone = zonePart
                    .replace("Z", "+0000", ignoreCase = true)
                    .replace(":", "")
                // A two-digit offset such as +02 has to become +0200 for SimpleDateFormat.
                val finalZone = if (normalizedZone.length == 3) normalizedZone + "00" else normalizedZone

                val normalized = "${datePart}T$cleanTime$finalZone"
                if (finalZone.isNotEmpty()) {
                    SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.US).parse(normalized)?.time
                } else {
                    SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(normalized)?.time
                }
            } else {
                SimpleDateFormat("yyyyMMdd", Locale.US).parse(timeStr.replace("-", ""))?.time
            }
        } catch (_: Exception) {
            null
        }
    }

    // --- Shared helpers ------------------------------------------------------

    private fun rawFirstValue(regex: Regex, text: String): String? =
        regex.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    private fun firstValue(regex: Regex, text: String): String? =
        rawFirstValue(regex, text)?.let { unescapeValue(it).trim() }?.takeIf { it.isNotEmpty() }

    private fun allValues(regex: Regex, text: String): List<String> =
        regex.findAll(text)
            .map { unescapeValue(it.groupValues[1]).trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

    /**
     * vCard and iCalendar fold long lines by inserting CRLF followed by a single
     * space or tab. Joining them back up before matching keeps a wrapped NOTE or
     * DESCRIPTION from being truncated at the fold.
     */
    private fun unfold(text: String): String =
        text.replace("\r\n", "\n").replace(Regex("\n[ \t]"), "")

    /**
     * Reads one `KEY:value` field out of a `;`-separated payload (MECARD, WIFI,
     * MATMSG). Separators inside a value must be backslash-escaped per the format,
     * so an escaped `\;` never ends the field.
     */
    private fun mecardField(text: String, key: String): String? =
        mecardFields(text, key).firstOrNull()

    private fun mecardFields(text: String, key: String): List<String> {
        val regex = "(?:^[a-zA-Z]+:|;)$key:((?:[^\\\\;]|\\\\.)*)"
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.findAll(text)
            .map { unescapeValue(it.groupValues[1]).trim().trim('"') }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    /**
     * Resolves backslash escapes in a single left-to-right pass.
     *
     * Order matters: chaining `replace` calls made `\\n` (an escaped backslash
     * followed by the letter n) collapse into a newline, because the backslash
     * rule ran before the newline rule and handed it a sequence it should never
     * have seen. One pass consumes each escape exactly once.
     */
    private fun unescapeValue(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    '\\' -> out.append('\\')
                    ',' -> out.append(',')
                    ';' -> out.append(';')
                    ':' -> out.append(':')
                    else -> out.append(next)
                }
                i += 2
            } else {
                if (c != '\r') out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun String.collapseSpaces(): String = replace(Regex("\\s+"), " ").trim()

    private fun urlEncode(str: String): String =
        try {
            URLEncoder.encode(str, "UTF-8")
        } catch (_: Exception) {
            str
        }
}
