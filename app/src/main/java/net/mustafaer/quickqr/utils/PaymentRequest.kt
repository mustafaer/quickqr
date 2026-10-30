package net.mustafaer.quickqr.utils

import java.net.URLDecoder

/**
 * A UPI payment code (`upi://pay?pa=…`), India's most-scanned kind of QR.
 *
 * The one fact worth surfacing before any UPI app opens: scanning a QR can only
 * ever *send* money. The commonest UPI fraud is a stranger who "wants to pay
 * you" and sends a code to scan — so QuickQR always shows who is being paid,
 * how much, and that warning, in the user's own language, before handing off.
 */
data class PaymentRequest(
    /** The payee's virtual payment address, `name@bank`. */
    val payeeAddress: String?,
    /** The name the code *claims* for the payee; nothing verifies it. */
    val payeeName: String?,
    /** Pre-filled amount, normalised to digits and an optional two-digit fraction. */
    val amount: String?,
    val currency: String?,
    val note: String?,
    val flags: Set<Flag>
) {
    enum class Flag(val severe: Boolean) {
        /** No `pa=`: there is nobody to pay, so the code is broken or a lure. */
        MISSING_PAYEE(true),

        /** `pa=` is not a `handle@provider` address. */
        MALFORMED_PAYEE(true),

        /** `am=` is present but not a plain amount. */
        MALFORMED_AMOUNT(true),

        /** The code fixes the amount; the user should check it before approving. */
        AMOUNT_PREFILLED(false),

        /** A `upi://mandate` code sets up recurring debits rather than one payment. */
        RECURRING(false)
    }

    val isSevere: Boolean get() = flags.any { it.severe }
}

object PaymentParser {

    private val VPA = Regex("""^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$""")
    private val AMOUNT = Regex("""^\d{1,9}(?:\.\d{1,2})?$""")

    fun parse(text: String): PaymentRequest? {
        val t = text.trim()
        if (!t.startsWith("upi://", ignoreCase = true)) return null

        val path = t.substring("upi://".length).substringBefore('?').trim('/').lowercase()
        val params = t.substringAfter('?', "")
            .split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=').trim().lowercase()
                if (key.isEmpty()) null else key to decode(pair.substringAfter('=', ""))
            }
            // A repeated key is ambiguous; trusting the first is what UPI apps do.
            .distinctBy { it.first }
            .toMap()

        val flags = mutableSetOf<PaymentRequest.Flag>()
        if (path == "mandate") flags += PaymentRequest.Flag.RECURRING

        val payee = params["pa"]?.trim()?.takeIf { it.isNotEmpty() }
        when {
            payee == null -> flags += PaymentRequest.Flag.MISSING_PAYEE
            !VPA.matches(payee) -> flags += PaymentRequest.Flag.MALFORMED_PAYEE
        }

        val rawAmount = params["am"]?.trim()?.takeIf { it.isNotEmpty() }
        val amount = when {
            rawAmount == null -> null
            AMOUNT.matches(rawAmount) -> rawAmount.also {
                if (it.toBigDecimal().signum() > 0) flags += PaymentRequest.Flag.AMOUNT_PREFILLED
            }
            else -> {
                flags += PaymentRequest.Flag.MALFORMED_AMOUNT
                rawAmount
            }
        }

        return PaymentRequest(
            payeeAddress = payee,
            payeeName = params["pn"]?.trim()?.takeIf { it.isNotEmpty() },
            amount = amount,
            currency = params["cu"]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            note = params["tn"]?.trim()?.takeIf { it.isNotEmpty() },
            flags = flags
        )
    }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: IllegalArgumentException) {
            // A stray `%` makes the whole value undecodable; show it as written.
            value
        }
}
