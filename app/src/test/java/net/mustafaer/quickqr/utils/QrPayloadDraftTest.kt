package net.mustafaer.quickqr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadDraftTest {

    @Test
    fun `an incomplete draft builds nothing`() {
        assertEquals("", QrPayloadDraft().build())
        assertEquals("", QrPayloadDraft(type = QrPayloadType.WIFI, wifiPassword = "secret").build())
        assertFalse(QrPayloadDraft(type = QrPayloadType.EMAIL, emailSubject = "Hi").isComplete)
    }

    @Test
    fun `text is emitted verbatim once trimmed`() {
        assertEquals(
            "hello world",
            QrPayloadDraft(type = QrPayloadType.TEXT, text = "  hello world  ").build()
        )
    }

    @Test
    fun `a url without a scheme gets https`() {
        assertEquals(
            "https://example.com",
            QrPayloadDraft(type = QrPayloadType.URL, url = "example.com").build()
        )
        assertEquals(
            "http://example.com",
            QrPayloadDraft(type = QrPayloadType.URL, url = "http://example.com").build()
        )
        assertEquals(
            "ftp://files.example.com",
            QrPayloadDraft(type = QrPayloadType.URL, url = " ftp://files.example.com ").build()
        )
    }

    @Test
    fun `wifi payloads follow the standard field order`() {
        val payload = QrPayloadDraft(
            type = QrPayloadType.WIFI,
            wifiSsid = "Cafe",
            wifiPassword = "latte123",
            wifiSecurity = WifiSecurity.WPA
        ).build()
        assertEquals("WIFI:T:WPA;S:Cafe;P:latte123;;", payload)
    }

    /**
     * The Wi-Fi format separates fields with `;` and `:`. Without escaping, a
     * network name containing either would truncate the payload and produce a QR
     * code for the wrong network.
     */
    @Test
    fun `wifi separators inside a name or password are escaped`() {
        val payload = QrPayloadDraft(
            type = QrPayloadType.WIFI,
            wifiSsid = "My;Net",
            wifiPassword = "pa:ss,word\\x"
        ).build()
        assertTrue(payload.contains("S:My\\;Net;"))
        assertTrue(payload.contains("P:pa\\:ss\\,word\\\\x;"))

        // And it round-trips back through the parser.
        val parsed = TypeDetector.parseWifi(payload)
        assertEquals("My;Net", parsed!!.ssid)
        assertEquals("pa:ss,word\\x", parsed.password)
    }

    @Test
    fun `an open network carries no password field`() {
        val payload = QrPayloadDraft(
            type = QrPayloadType.WIFI,
            wifiSsid = "Guest",
            wifiPassword = "ignored",
            wifiSecurity = WifiSecurity.NONE
        ).build()
        assertEquals("WIFI:T:nopass;S:Guest;;", payload)
    }

    @Test
    fun `a hidden network sets the H flag`() {
        val payload = QrPayloadDraft(
            type = QrPayloadType.WIFI,
            wifiSsid = "Hidden",
            wifiPassword = "pw",
            wifiHidden = true
        ).build()
        assertTrue(payload.contains("H:true;"))
        assertTrue(TypeDetector.parseWifi(payload)!!.hidden)
    }

    @Test
    fun `email payloads url-encode the subject and body`() {
        val payload = QrPayloadDraft(
            type = QrPayloadType.EMAIL,
            emailAddress = "ada@example.com",
            emailSubject = "Hi there",
            emailBody = "How are you?"
        ).build()
        assertTrue(payload.startsWith("mailto:ada@example.com?"))
        assertTrue(payload.contains("subject=Hi+there"))
        assertTrue(payload.contains("body=How+are+you%3F"))
    }

    @Test
    fun `an email with no subject or body has no query string`() {
        assertEquals(
            "mailto:ada@example.com",
            QrPayloadDraft(type = QrPayloadType.EMAIL, emailAddress = "ada@example.com").build()
        )
    }

    @Test
    fun `phone and sms payloads drop spacing from the number`() {
        assertEquals(
            "tel:+905551234567",
            QrPayloadDraft(type = QrPayloadType.PHONE, phoneNumber = "+90 555 123 45 67").build()
        )
        assertEquals(
            "SMSTO:+15551234567:on my way",
            QrPayloadDraft(
                type = QrPayloadType.SMS,
                smsNumber = "+1 555 123 4567",
                smsMessage = "on my way"
            ).build()
        )
    }

    @Test
    fun `switching type keeps what was typed in the other tabs`() {
        val draft = QrPayloadDraft(
            type = QrPayloadType.WIFI,
            wifiSsid = "Cafe",
            emailAddress = "ada@example.com"
        )
        val asEmail = draft.copy(type = QrPayloadType.EMAIL)
        assertEquals("Cafe", asEmail.wifiSsid)
        assertTrue(asEmail.build().startsWith("mailto:ada@example.com"))
    }

    @Test
    fun `every generated payload is detected as the type it was built for`() {
        val cases = mapOf(
            QrPayloadDraft(type = QrPayloadType.URL, url = "example.com") to ScanResultType.URL,
            QrPayloadDraft(type = QrPayloadType.WIFI, wifiSsid = "Cafe", wifiPassword = "pw")
                to ScanResultType.WIFI,
            QrPayloadDraft(type = QrPayloadType.EMAIL, emailAddress = "a@b.co")
                to ScanResultType.EMAIL,
            QrPayloadDraft(type = QrPayloadType.PHONE, phoneNumber = "+15551234567")
                to ScanResultType.PHONE,
            QrPayloadDraft(type = QrPayloadType.SMS, smsNumber = "+15551234567", smsMessage = "hi")
                to ScanResultType.SMS,
            QrPayloadDraft(type = QrPayloadType.TEXT, text = "just words") to ScanResultType.TEXT
        )
        cases.forEach { (draft, expected) ->
            assertEquals(
                "payload ${draft.build()} should detect as $expected",
                expected,
                TypeDetector.detect(draft.build())
            )
        }
    }
}
