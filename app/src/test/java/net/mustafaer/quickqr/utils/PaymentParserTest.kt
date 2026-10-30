package net.mustafaer.quickqr.utils

import net.mustafaer.quickqr.utils.PaymentRequest.Flag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {

    private fun parse(text: String) = requireNotNull(PaymentParser.parse(text)) { text }

    @Test
    fun `a merchant code yields payee, name, amount and note`() {
        val payment = parse("upi://pay?pa=chai.stall@okaxis&pn=Chai%20Stall&am=40.00&cu=INR&tn=Order%2012")
        assertEquals("chai.stall@okaxis", payment.payeeAddress)
        assertEquals("Chai Stall", payment.payeeName)
        assertEquals("40.00", payment.amount)
        assertEquals("INR", payment.currency)
        assertEquals("Order 12", payment.note)
        assertEquals(setOf(Flag.AMOUNT_PREFILLED), payment.flags)
        assertFalse(payment.isSevere)
    }

    @Test
    fun `an open-amount code raises nothing`() {
        assertEquals(emptySet<Flag>(), parse("upi://pay?pa=shop@ybl&pn=Shop").flags)
    }

    @Test
    fun `a code with nobody to pay is severe`() {
        assertEquals(setOf(Flag.MISSING_PAYEE), parse("upi://pay?pn=Lottery%20Prize&am=1").flags - Flag.AMOUNT_PREFILLED)
        assertTrue(parse("upi://pay?pn=Prize").isSevere)
    }

    @Test
    fun `a malformed payee or amount is severe`() {
        assertTrue(Flag.MALFORMED_PAYEE in parse("upi://pay?pa=not-an-address").flags)
        assertTrue(Flag.MALFORMED_AMOUNT in parse("upi://pay?pa=a.b@okicici&am=1e9").flags)
    }

    @Test
    fun `a mandate sets up recurring payments`() {
        assertTrue(Flag.RECURRING in parse("upi://mandate?pa=ott@okhdfc&pn=Streaming").flags)
    }

    @Test
    fun `the first of a repeated key wins and stray percents survive`() {
        val payment = parse("upi://pay?pa=first@ybl&pa=second@ybl&tn=100%")
        assertEquals("first@ybl", payment.payeeAddress)
        assertEquals("100%", payment.note)
    }

    @Test
    fun `anything that is not upi is not a payment`() {
        assertNull(PaymentParser.parse("https://pay.example.com"))
    }
}
