package net.mustafaer.quickqr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LookalikeDetectorTest {

    @Test
    fun `skeletons fold look-alike letters, digits and accents onto plain latin`() {
        assertEquals("paypal", LookalikeDetector.skeleton("раураl"))      // Cyrillic р а у
        assertEquals("paypal", LookalikeDetector.skeleton("paypa1"))
        assertEquals("google", LookalikeDetector.skeleton("g00gle"))
        assertEquals("garanti", LookalikeDetector.skeleton("garantı"))    // Turkish dotless ı
        assertEquals("amazon", LookalikeDetector.skeleton("ａｍａｚｏｎ"))  // full-width
        assertEquals("microsoft", LookalikeDetector.skeleton("rnicrosoft"))
        assertEquals("cafe", LookalikeDetector.skeleton("café"))
    }

    @Test
    fun `osa distance counts a swap of neighbours as one edit`() {
        assertEquals(0, LookalikeDetector.osaDistance("paypal", "paypal"))
        assertEquals(1, LookalikeDetector.osaDistance("pyapal", "paypal"))
        assertEquals(1, LookalikeDetector.osaDistance("paypall", "paypal"))
        assertEquals(1, LookalikeDetector.osaDistance("paypa", "paypal"))
        assertEquals(2, LookalikeDetector.osaDistance("pypl", "paypal"))
    }

    @Test
    fun `a subdomain of a brand is the brand`() {
        assertNull(LookalikeDetector.check("login.paypal.com", "login.paypal.com"))
        assertNull(LookalikeDetector.check("www.garantibbva.com.tr", "www.garantibbva.com.tr"))
    }

    @Test
    fun `the brand list is lower-case registrable domains`() {
        LookalikeDetector.BRANDS.forEach { domain ->
            assertEquals(domain, domain.lowercase())
            assertEquals(domain, PublicSuffix.registrableDomain(domain))
        }
    }

    @Test
    fun `public suffixes keep the registered name with its country suffix`() {
        assertEquals("garantibbva.com.tr", PublicSuffix.registrableDomain("www.garantibbva.com.tr"))
        assertEquals("bbc.co.uk", PublicSuffix.registrableDomain("news.bbc.co.uk"))
        assertEquals("example.com", PublicSuffix.registrableDomain("a.b.example.com"))
        // On shared hosting, each subdomain belongs to someone different.
        assertEquals("paypal-login.github.io", PublicSuffix.registrableDomain("paypal-login.github.io"))
        assertEquals("localhost", PublicSuffix.registrableDomain("localhost"))
    }
}
