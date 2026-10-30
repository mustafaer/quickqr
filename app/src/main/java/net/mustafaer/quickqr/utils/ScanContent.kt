package net.mustafaer.quickqr.utils

/**
 * A scanned payload, parsed once. The screen renders this and never re-parses
 * the raw text, and parsing happens off the main thread.
 */
sealed interface ScanContent {
    val raw: String
    val type: ScanResultType

    data class Link(override val raw: String, val url: String, val verdict: LinkVerdict?) : ScanContent {
        override val type get() = ScanResultType.URL
    }

    data class Wifi(override val raw: String, val wifi: WifiData) : ScanContent {
        override val type get() = ScanResultType.WIFI
    }

    data class Contact(override val raw: String, val card: VCardData) : ScanContent {
        override val type get() = ScanResultType.VCARD
    }

    data class Event(override val raw: String, val event: EventData) : ScanContent {
        override val type get() = ScanResultType.CALENDAR
    }

    data class Payment(override val raw: String, val payment: PaymentRequest) : ScanContent {
        override val type get() = ScanResultType.PAYMENT
    }

    /** Email, phone, SMS, location, plain text — or a structured type that failed to parse. */
    data class Other(override val raw: String, override val type: ScanResultType) : ScanContent
}

object ScanClassifier {

    fun classify(raw: String): ScanContent {
        val type = TypeDetector.detect(raw)
        val parsed = when (type) {
            ScanResultType.URL -> TypeDetector.getActionUrl(raw, type).let { url ->
                ScanContent.Link(raw, url, UrlInspector.inspect(url))
            }
            ScanResultType.WIFI -> TypeDetector.parseWifi(raw)?.let { ScanContent.Wifi(raw, it) }
            ScanResultType.VCARD -> TypeDetector.parseVCard(raw)?.let { ScanContent.Contact(raw, it) }
            ScanResultType.CALENDAR -> TypeDetector.parseEvent(raw)?.let { ScanContent.Event(raw, it) }
            ScanResultType.PAYMENT -> PaymentParser.parse(raw)?.let { ScanContent.Payment(raw, it) }
            else -> null
        }
        return parsed ?: ScanContent.Other(raw, type)
    }

    /**
     * A one-line title for a history row: the network name, the contact, the
     * site — never the raw payload, which for Wi-Fi includes the password.
     * Null means "nothing better than the type label".
     */
    fun summarise(content: ScanContent): String? {
        val summary = when (content) {
            is ScanContent.Link -> content.verdict?.displayHost ?: content.url
            is ScanContent.Wifi -> content.wifi.ssid
            is ScanContent.Contact -> content.card.name
                ?: content.card.organization
                ?: content.card.phones.firstOrNull()
                ?: content.card.emails.firstOrNull()
            is ScanContent.Event -> content.event.summary
            is ScanContent.Payment -> content.payment.payeeName ?: content.payment.payeeAddress
            is ScanContent.Other -> summariseOther(content.raw.trim(), content.type)
        }
        return summary?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.take(MAX_SUMMARY)
    }

    private fun summariseOther(t: String, type: ScanResultType): String? = when (type) {
        // A Wi-Fi or contact code that did not parse falls back to the label,
        // never to a raw payload that may carry a password.
        ScanResultType.WIFI, ScanResultType.VCARD, ScanResultType.CALENDAR, ScanResultType.PAYMENT -> null
        ScanResultType.EMAIL -> when {
            t.startsWith("mailto:", ignoreCase = true) -> t.substring(7).substringBefore('?')
            t.startsWith("MATMSG:", ignoreCase = true) ->
                Regex("TO:([^;]*)", RegexOption.IGNORE_CASE).find(t)?.groupValues?.get(1)
            else -> t
        }
        ScanResultType.PHONE -> if (t.startsWith("tel:", ignoreCase = true)) t.substring(4) else t
        ScanResultType.SMS -> t.substringAfter(':').substringBefore(':').substringBefore('?')
        ScanResultType.GEO -> t.substringAfter(':').substringBefore('?').replace(",", ", ")
        ScanResultType.URL, ScanResultType.TEXT -> t
    }?.trim()?.takeIf { it.isNotEmpty() }

    private const val MAX_SUMMARY = 80
}
