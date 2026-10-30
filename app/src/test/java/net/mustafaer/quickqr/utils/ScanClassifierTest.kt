package net.mustafaer.quickqr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanClassifierTest {

    @Test
    fun `each structured type parses into its own content`() {
        assertTrue(ScanClassifier.classify("https://example.com") is ScanContent.Link)
        assertTrue(ScanClassifier.classify("WIFI:T:WPA;S:Cafe;P:latte;;") is ScanContent.Wifi)
        assertTrue(ScanClassifier.classify("MECARD:N:Lovelace,Ada;TEL:+15550000001;;") is ScanContent.Contact)
        assertTrue(ScanClassifier.classify("BEGIN:VEVENT\nSUMMARY:Launch\nDTSTART:20261201T090000Z\nEND:VEVENT") is ScanContent.Event)
        assertTrue(ScanClassifier.classify("upi://pay?pa=shop@ybl") is ScanContent.Payment)
        assertEquals(ScanResultType.PHONE, ScanClassifier.classify("tel:+15551234567").type)
    }

    @Test
    fun `a link without a scheme is inspected as https`() {
        val link = ScanClassifier.classify("example.com/menu") as ScanContent.Link
        assertEquals("https://example.com/menu", link.url)
        assertEquals("example.com", link.verdict?.displayHost)
    }

    /**
     * Regression: history rows showed the raw payload, so every saved Wi-Fi code
     * printed its password in plain text in the list.
     */
    @Test
    fun `a wifi summary is the network name and never the password`() {
        val summary = ScanClassifier.summarise(ScanClassifier.classify("WIFI:T:WPA;S:Cafe;P:latte123;;"))
        assertEquals("Cafe", summary)
        assertFalse(summary!!.contains("latte123"))
        // No name to show: fall back to the type label, not the payload.
        assertNull(ScanClassifier.summarise(ScanClassifier.classify("WIFI:T:WPA;P:latte123;;")))
    }

    @Test
    fun `summaries name the thing, not the format`() {
        fun title(raw: String) = ScanClassifier.summarise(ScanClassifier.classify(raw))
        assertEquals("Ada Lovelace", title("MECARD:N:Lovelace,Ada;TEL:+15550000001;;"))
        assertEquals("Launch", title("BEGIN:VEVENT\nSUMMARY:Launch\nEND:VEVENT"))
        assertEquals("shop.example.com", title("https://shop.example.com/cart?id=1"))
        assertEquals("Chai Stall", title("upi://pay?pa=chai@okaxis&pn=Chai%20Stall"))
        assertEquals("ada@example.com", title("mailto:ada@example.com?subject=Hi"))
        assertEquals("+15551234567", title("tel:+15551234567"))
        assertEquals("41.0082, 28.9784", title("geo:41.0082,28.9784"))
        assertEquals("first line", title("  \nfirst line\nsecond line"))
    }
}
