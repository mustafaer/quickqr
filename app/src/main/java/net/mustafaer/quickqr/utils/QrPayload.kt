package net.mustafaer.quickqr.utils

import java.net.URLEncoder

/** The content types QuickQR can build a QR code for. */
enum class QrPayloadType {
    TEXT, URL, WIFI, EMAIL, PHONE, SMS
}

enum class WifiSecurity(val qrValue: String, val label: String) {
    WPA("WPA", "WPA/WPA2"),
    WEP("WEP", "WEP"),
    NONE("nopass", "Open");

    companion object {
        fun fromQrValue(value: String?): WifiSecurity =
            entries.firstOrNull { it.qrValue.equals(value, ignoreCase = true) } ?: WPA
    }
}

/**
 * Everything the generator form holds, across every content type.
 *
 * One flat value rather than a sealed hierarchy so switching type keeps whatever
 * the user already typed into the other tabs — going Wi-Fi → e-mail → Wi-Fi does
 * not wipe the network name.
 */
data class QrPayloadDraft(
    val type: QrPayloadType = QrPayloadType.TEXT,
    val text: String = "",
    val url: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurity: WifiSecurity = WifiSecurity.WPA,
    val wifiHidden: Boolean = false,
    val emailAddress: String = "",
    val emailSubject: String = "",
    val emailBody: String = "",
    val phoneNumber: String = "",
    val smsNumber: String = "",
    val smsMessage: String = ""
) {

    /** True when the required field for the current type has something in it. */
    val isComplete: Boolean
        get() = when (type) {
            QrPayloadType.TEXT -> text.isNotBlank()
            QrPayloadType.URL -> url.isNotBlank()
            QrPayloadType.WIFI -> wifiSsid.isNotBlank()
            QrPayloadType.EMAIL -> emailAddress.isNotBlank()
            QrPayloadType.PHONE -> phoneNumber.isNotBlank()
            QrPayloadType.SMS -> smsNumber.isNotBlank()
        }

    /**
     * Renders the draft into the string that actually gets encoded. Returns an
     * empty string when the required field is still blank, which the caller
     * treats as "nothing to encode yet".
     */
    fun build(): String {
        if (!isComplete) return ""
        return when (type) {
            QrPayloadType.TEXT -> text.trim()

            QrPayloadType.URL -> {
                val trimmed = url.trim()
                val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)
                if (hasScheme) trimmed else "https://$trimmed"
            }

            QrPayloadType.WIFI -> buildString {
                append("WIFI:")
                append("T:").append(wifiSecurity.qrValue).append(';')
                append("S:").append(escapeWifi(wifiSsid.trim())).append(';')
                if (wifiSecurity != WifiSecurity.NONE && wifiPassword.isNotEmpty()) {
                    append("P:").append(escapeWifi(wifiPassword)).append(';')
                }
                if (wifiHidden) append("H:true;")
                append(';')
            }

            QrPayloadType.EMAIL -> buildString {
                append("mailto:").append(emailAddress.trim())
                val params = buildList {
                    if (emailSubject.isNotBlank()) add("subject=" + encode(emailSubject.trim()))
                    if (emailBody.isNotBlank()) add("body=" + encode(emailBody.trim()))
                }
                if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
            }

            QrPayloadType.PHONE -> "tel:" + phoneNumber.trim().replace(" ", "")

            QrPayloadType.SMS -> {
                val number = smsNumber.trim().replace(" ", "")
                if (smsMessage.isBlank()) {
                    "SMSTO:$number:"
                } else {
                    // SMSTO is the form every scanner understands; the message is
                    // taken verbatim after the second colon.
                    "SMSTO:$number:${smsMessage.trim()}"
                }
            }
        }
    }

    private fun encode(value: String): String =
        try {
            URLEncoder.encode(value, "UTF-8")
        } catch (_: Exception) {
            value
        }

    /**
     * The Wi-Fi QR format separates fields with `;` and `:`, so those characters —
     * plus backslash, comma and quote — have to be escaped inside a network name
     * or password or the payload silently truncates.
     */
    private fun escapeWifi(value: String): String = buildString(value.length) {
        value.forEach { c ->
            if (c == '\\' || c == ';' || c == ',' || c == ':' || c == '"') append('\\')
            append(c)
        }
    }
}
