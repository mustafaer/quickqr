package net.mustafaer.quickqr.utils

import net.mustafaer.quickqr.utils.LinkVerdict.Flag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlInspectorTest {

    private fun inspect(url: String) = requireNotNull(UrlInspector.inspect(url)) { url }

    @Test
    fun `an ordinary https link raises nothing`() {
        val verdict = inspect("https://www.example.com/path?q=1")
        assertEquals(emptySet<Flag>(), verdict.flags)
        assertEquals("www.example.com", verdict.displayHost)
        assertEquals("example.com", verdict.registrableDomain)
        assertEquals("www.", verdict.subdomainPrefix)
        assertFalse(verdict.isSevere)
    }

    @Test
    fun `text before an at sign is decoration and the real host is shown`() {
        val verdict = inspect("https://google.com@evil.com/login")
        assertTrue(Flag.USERINFO in verdict.flags)
        assertEquals("evil.com", verdict.displayHost)
        assertTrue(verdict.isSevere)
    }

    @Test
    fun `a cyrillic letter inside a latin name is shown as punycode`() {
        // The first letter is U+0430 CYRILLIC SMALL LETTER A.
        val verdict = inspect("https://аpple.com/id")
        assertTrue(Flag.MIXED_SCRIPT in verdict.flags)
        assertEquals("xn--pple-43d.com", verdict.displayHost)
        assertTrue(verdict.isSevere)
    }

    @Test
    fun `an all-cyrillic look-alike is flagged and shown as punycode`() {
        val verdict = inspect("https://аррӏе.com")
        assertTrue(Flag.CONFUSABLE_SCRIPT in verdict.flags)
        assertTrue(verdict.displayHost.startsWith("xn--"))
        // Its skeleton reads "apple", so it is caught as a look-alike too.
        assertTrue(Flag.LOOKALIKE in verdict.flags)
        assertEquals("apple.com", verdict.imitatedBrand)
    }

    @Test
    fun `a legitimate international domain is shown in its own script`() {
        val verdict = inspect("https://güzel.com.tr/menü")
        assertEquals(emptySet<Flag>(), verdict.flags)
        assertEquals("güzel.com.tr", verdict.displayHost)
        assertEquals("xn--gzel-0ra.com.tr", verdict.asciiHost)
        assertEquals("güzel.com.tr", verdict.registrableDomain)
    }

    @Test
    fun `numeric hosts are split into public and local`() {
        val public = inspect("http://185.12.4.9:8080/x")
        assertEquals(setOf(Flag.INSECURE, Flag.NON_STANDARD_PORT, Flag.PUBLIC_IP), public.flags)
        assertTrue(public.isSevere)

        val router = inspect("http://192.168.1.1/")
        assertTrue(Flag.LOCAL_IP in router.flags)
        assertFalse(router.isSevere)
    }

    @Test
    fun `short links say the destination is hidden`() {
        assertEquals(setOf(Flag.SHORTENER), inspect("https://bit.ly/3xYz").flags)
    }

    @Test
    fun `a brand domain in front of another site is caught`() {
        val verdict = inspect("https://paypal.com.secure-login.xyz/signin")
        assertTrue(Flag.BRAND_IN_SUBDOMAIN in verdict.flags)
        assertEquals("secure-login.xyz", verdict.registrableDomain)
        assertEquals("paypal.com", verdict.imitatedBrand)
        assertTrue(verdict.isSevere)
    }

    @Test
    fun `typo-squats and digit swaps are look-alikes`() {
        assertEquals("paypal.com", inspect("https://paypa1.com").imitatedBrand)
        assertTrue(Flag.LOOKALIKE in inspect("https://paypa1.com").flags)
        assertTrue(Flag.LOOKALIKE in inspect("https://pay-pal.com").flags)
        assertTrue(Flag.LOOKALIKE in inspect("https://hepsibruada.com").flags)
    }

    @Test
    fun `a brand's own domains and country sites are left alone`() {
        listOf(
            "https://www.paypal.com/signin",
            "https://accounts.google.com",
            "https://www.google.com.tr",
            "https://www.sparkasse.de",
            "https://onlinesbi.sbi",
            "https://www.turkiye.gov.tr"
        ).forEach { url -> assertEquals(url, emptySet<Flag>(), inspect(url).flags) }
    }

    @Test
    fun `a brand name used as a word elsewhere is a warning, not an alarm`() {
        val verdict = inspect("https://paypal-help-center.net")
        assertEquals(setOf(Flag.BRAND_NAME), verdict.flags)
        assertFalse(verdict.isSevere)
    }

    @Test
    fun `a brand name glued to a fake suffix is still named`() {
        assertEquals(setOf(Flag.BRAND_NAME), inspect("https://garantibbva-com.tr").flags)
    }

    @Test
    fun `things that are not scheme-authority links are not inspected`() {
        assertNull(UrlInspector.inspect("mailto:ada@example.com"))
        assertNull(UrlInspector.inspect("https://"))
    }
}
