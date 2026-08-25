package net.mustafaer.quickqr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeDetectorTest {

    // --- Detection -----------------------------------------------------------

    @Test
    fun `detects the standard payload prefixes`() {
        assertEquals(ScanResultType.WIFI, TypeDetector.detect("WIFI:T:WPA;S:home;P:secret;;"))
        assertEquals(ScanResultType.VCARD, TypeDetector.detect("BEGIN:VCARD\nFN:Ada\nEND:VCARD"))
        assertEquals(ScanResultType.VCARD, TypeDetector.detect("MECARD:N:Lovelace,Ada;;"))
        assertEquals(ScanResultType.CALENDAR, TypeDetector.detect("BEGIN:VEVENT\nSUMMARY:Standup"))
        assertEquals(ScanResultType.EMAIL, TypeDetector.detect("mailto:ada@example.com"))
        assertEquals(ScanResultType.EMAIL, TypeDetector.detect("ada@example.com"))
        assertEquals(ScanResultType.SMS, TypeDetector.detect("SMSTO:+15551234567:hello"))
        assertEquals(ScanResultType.PHONE, TypeDetector.detect("tel:+15551234567"))
        assertEquals(ScanResultType.GEO, TypeDetector.detect("geo:41.0082,28.9784"))
        assertEquals(ScanResultType.URL, TypeDetector.detect("https://example.com/path?a=1"))
        assertEquals(ScanResultType.TEXT, TypeDetector.detect("just some words here"))
    }

    /**
     * Regression: the e-mail pattern matches any whitespace-free `x@y.z`, so a URL
     * carrying userinfo used to be classified as an address and the Open button
     * tried to launch a mail client.
     */
    @Test
    fun `a url with userinfo is a link, not an email`() {
        assertEquals(ScanResultType.URL, TypeDetector.detect("https://user@example.com"))
        assertEquals(ScanResultType.URL, TypeDetector.detect("ftp://anon@files.example.org"))
        assertEquals(ScanResultType.URL, TypeDetector.detect("http://a@b.co/path"))
    }

    @Test
    fun `phone detection ignores dates and out-of-range digit counts`() {
        assertEquals(ScanResultType.PHONE, TypeDetector.detect("+90 555 123 45 67"))
        assertEquals(ScanResultType.PHONE, TypeDetector.detect("5551234"))
        // A date, not a phone number.
        assertEquals(ScanResultType.TEXT, TypeDetector.detect("2026-08-25"))
        // Too few digits.
        assertEquals(ScanResultType.TEXT, TypeDetector.detect("12345"))
        // Too many digits.
        assertEquals(ScanResultType.TEXT, TypeDetector.detect("12345678901234567"))
    }

    @Test
    fun `bare hostnames are links but ordinary sentences are not`() {
        assertEquals(ScanResultType.URL, TypeDetector.detect("example.com"))
        assertEquals(ScanResultType.URL, TypeDetector.detect("www.example.co.uk/a/b"))
        assertEquals(ScanResultType.TEXT, TypeDetector.detect("meet me at 5"))
        assertEquals(ScanResultType.TEXT, TypeDetector.detect("Merhaba dünya"))
    }

    // --- Action URLs ---------------------------------------------------------

    @Test
    fun `action urls get the scheme they need`() {
        assertEquals(
            "https://example.com",
            TypeDetector.getActionUrl("example.com", ScanResultType.URL)
        )
        assertEquals(
            "https://example.com",
            TypeDetector.getActionUrl("https://example.com", ScanResultType.URL)
        )
        assertEquals(
            "tel:+15551234567",
            TypeDetector.getActionUrl("+15551234567", ScanResultType.PHONE)
        )
        assertEquals(
            "mailto:ada@example.com",
            TypeDetector.getActionUrl("ada@example.com", ScanResultType.EMAIL)
        )
    }

    @Test
    fun `smsto payloads become sms uris with an encoded body`() {
        assertEquals(
            "sms:+15551234567?body=hello+there",
            TypeDetector.getActionUrl("SMSTO:+15551234567:hello there", ScanResultType.SMS)
        )
        assertEquals(
            "sms:+15551234567",
            TypeDetector.getActionUrl("SMSTO:+15551234567:", ScanResultType.SMS)
        )
    }

    @Test
    fun `matmsg becomes a mailto with subject and body`() {
        val url = TypeDetector.getActionUrl(
            "MATMSG:TO:ada@example.com;SUB:Hi there;BODY:How are you?;;",
            ScanResultType.EMAIL
        )
        assertTrue(url.startsWith("mailto:ada@example.com?"))
        assertTrue(url.contains("subject=Hi+there"))
        assertTrue(url.contains("body=How+are+you%3F"))
    }

    // --- Wi-Fi ---------------------------------------------------------------

    @Test
    fun `parses wifi payloads including escaped separators`() {
        val wifi = TypeDetector.parseWifi("WIFI:T:WPA;S:My\\;Net;P:pa\\:ss;H:true;;")
        assertNotNull(wifi)
        assertEquals("My;Net", wifi!!.ssid)
        assertEquals("pa:ss", wifi.password)
        assertEquals("WPA", wifi.security)
        assertTrue(wifi.hidden)
    }

    @Test
    fun `an open network reports nopass rather than an empty security type`() {
        val wifi = TypeDetector.parseWifi("WIFI:S:Cafe;;")
        assertEquals("nopass", wifi!!.security)
        assertEquals("", wifi.password)
    }

    // --- Contacts ------------------------------------------------------------

    /**
     * Regression: only the first TEL and EMAIL used to survive, so a business card
     * with a work line and a mobile silently lost one of them.
     */
    @Test
    fun `keeps every phone number and email on a vcard`() {
        val vcard = TypeDetector.parseVCard(
            """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ada Lovelace
            TEL;TYPE=WORK:+15550000001
            TEL;TYPE=CELL:+15550000002
            EMAIL;TYPE=WORK:ada@work.example
            EMAIL;TYPE=HOME:ada@home.example
            ORG:Analytical Engines;Research
            TITLE:Mathematician
            ADR;TYPE=WORK:;;12 Baker St;London;;NW1;UK
            NOTE:Met at the conference
            BDAY:1815-12-10
            END:VCARD
            """.trimIndent()
        )
        assertNotNull(vcard)
        assertEquals("Ada Lovelace", vcard!!.name)
        assertEquals(listOf("+15550000001", "+15550000002"), vcard.phones)
        assertEquals(listOf("ada@work.example", "ada@home.example"), vcard.emails)
        assertEquals("Analytical Engines, Research", vcard.organization)
        assertEquals("Mathematician", vcard.jobTitle)
        assertEquals("12 Baker St, London, NW1, UK", vcard.address)
        assertEquals("Met at the conference", vcard.note)
        assertEquals("1815-12-10", vcard.birthday)
    }

    @Test
    fun `falls back to the N property when FN is absent`() {
        val vcard = TypeDetector.parseVCard("BEGIN:VCARD\nN:Lovelace;Ada\nEND:VCARD")
        assertEquals("Ada Lovelace", vcard!!.name)
    }

    @Test
    fun `unfolds wrapped vcard lines`() {
        val vcard = TypeDetector.parseVCard(
            "BEGIN:VCARD\r\nFN:Ada\r\nNOTE:This note is quite long and was \r\n folded by the writer\r\nEND:VCARD"
        )
        assertEquals("This note is quite long and was folded by the writer", vcard!!.note)
    }

    @Test
    fun `parses mecard including the last-comma-first name order`() {
        val vcard = TypeDetector.parseVCard(
            "MECARD:N:Lovelace,Ada;TEL:+15550000001;EMAIL:ada@example.com;ORG:Engines;URL:https://example.com;;"
        )
        assertNotNull(vcard)
        assertEquals("Ada Lovelace", vcard!!.name)
        assertEquals(listOf("+15550000001"), vcard.phones)
        assertEquals(listOf("ada@example.com"), vcard.emails)
        assertEquals("Engines", vcard.organization)
        assertEquals("https://example.com", vcard.website)
    }

    @Test
    fun `a vcard with no usable fields parses to null`() {
        assertNull(TypeDetector.parseVCard("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD"))
    }

    // --- Escaping ------------------------------------------------------------

    /**
     * Regression: unescaping used to run as a chain of `replace` calls, so `\\n`
     * (an escaped backslash followed by the letter n) was first collapsed to `\n`
     * and then turned into a newline. One left-to-right pass consumes each escape
     * exactly once.
     */
    @Test
    fun `an escaped backslash before n stays a backslash`() {
        val vcard = TypeDetector.parseVCard("BEGIN:VCARD\nFN:Ada\nNOTE:C:\\\\new\nEND:VCARD")
        assertEquals("C:\\new", vcard!!.note)
    }

    @Test
    fun `escaped newlines in a note become real newlines`() {
        val vcard = TypeDetector.parseVCard("BEGIN:VCARD\nFN:Ada\nNOTE:line one\\nline two\nEND:VCARD")
        assertEquals("line one\nline two", vcard!!.note)
    }

    @Test
    fun `escaped commas and semicolons survive unescaping`() {
        val vcard = TypeDetector.parseVCard("BEGIN:VCARD\nFN:Lovelace\\, Ada\nEND:VCARD")
        assertEquals("Lovelace, Ada", vcard!!.name)
    }

    // --- Calendar ------------------------------------------------------------

    @Test
    fun `parses an event with a summary, location and times`() {
        val event = TypeDetector.parseEvent(
            """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            SUMMARY:Team standup
            LOCATION:Room 3
            DESCRIPTION:Daily sync
            DTSTART:20260825T090000Z
            DTEND:20260825T091500Z
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertNotNull(event)
        assertEquals("Team standup", event!!.summary)
        assertEquals("Room 3", event.location)
        assertEquals("Daily sync", event.description)
        assertNotNull(event.start)
        assertNotNull(event.end)
        assertTrue(event.end!! > event.start!!)
        assertEquals(false, event.isAllDay)
    }

    @Test
    fun `a date-only DTSTART marks the event as all day`() {
        val event = TypeDetector.parseEvent(
            "BEGIN:VEVENT\nSUMMARY:Holiday\nDTSTART:20260825\nEND:VEVENT"
        )
        assertTrue(event!!.isAllDay)
        assertNotNull(event.start)
    }

    /**
     * Regression: the offset pattern only accepted four-digit forms, so `+03` was
     * never captured and the value quietly became a zone-less local time. On a
     * UTC+3 machine that produced the right answer by coincidence; everywhere
     * else it was three hours out. Asserting the absolute instant — rather than
     * that two forms merely agree — is what keeps that coincidence from hiding
     * the bug again.
     */
    @Test
    fun `every timezone offset form resolves to the same instant`() {
        // 2026-08-25T09:00:00+03:00
        val expected = 1787637600000L
        listOf(
            "DTSTART:20260825T090000+03",
            "DTSTART:20260825T090000+0300",
            "DTSTART:20260825T090000+03:00"
        ).forEach { input ->
            assertEquals(input, expected, TypeDetector.parseEventTime(input, "DTSTART"))
        }
        // And an explicit Z is the same wall clock three hours later.
        assertEquals(expected, TypeDetector.parseEventTime("DTSTART:20260825T060000Z", "DTSTART"))
    }
}
