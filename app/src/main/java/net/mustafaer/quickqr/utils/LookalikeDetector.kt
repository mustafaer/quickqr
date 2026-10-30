package net.mustafaer.quickqr.utils

import java.text.Normalizer

/**
 * Spots hosts dressed up as a well-known brand: `paypa1.com`, `раypal.com` in
 * Cyrillic, `garantibbva.com.tr.hesap-onay.xyz`, `sparkasse-login.de`.
 *
 * Everything runs on the device against a bundled list, so a scanned link is
 * checked without being sent anywhere. The list is weighted toward the brands
 * that phishing in QuickQR's markets — Türkiye, Germany, India and the Arabic-
 * speaking countries — actually imitates: banks, e-government, parcel carriers
 * and payment apps, plus the global names every campaign uses.
 *
 * With a few hundred entries a straight scan costs well under a millisecond, so
 * there is deliberately no trie or BK-tree here: contiguous arrays beat
 * pointer-chasing at this size, and the code stays obvious.
 */
internal object LookalikeDetector {

    enum class Kind(val severe: Boolean) {
        /** The host's own name imitates the brand: `paypa1.com`, `аpple.com`. */
        LOOKALIKE(true),

        /** The brand's real domain sits in front of someone else's: `paypal.com.evil.io`. */
        BRAND_DOMAIN_IN_SUBDOMAIN(true),

        /** The brand's name appears as a word on another site: `paypal-help.net`. */
        BRAND_NAME_IN_HOST(false)
    }

    data class Match(val brandDomain: String, val kind: Kind)

    /** Registrable domains, lower-case ASCII. */
    internal val BRANDS: List<String> = listOf(
        // Global
        "google.com", "youtube.com", "gmail.com", "apple.com", "icloud.com",
        "microsoft.com", "live.com", "outlook.com", "office.com", "amazon.com",
        "paypal.com", "facebook.com", "instagram.com", "whatsapp.com", "netflix.com",
        "linkedin.com", "twitter.com", "dropbox.com", "adobe.com", "binance.com",
        "coinbase.com", "metamask.io", "steamcommunity.com", "steampowered.com",
        "ebay.com", "dhl.com", "fedex.com", "usps.com", "spotify.com", "telegram.org",
        "tiktok.com", "yahoo.com", "booking.com", "airbnb.com", "revolut.com",
        "docusign.com", "wetransfer.com",
        // Türkiye
        "turkiye.gov.tr", "ziraatbank.com.tr", "garantibbva.com.tr", "isbank.com.tr",
        "akbank.com", "yapikredi.com.tr", "halkbank.com.tr", "vakifbank.com.tr",
        "denizbank.com", "qnb.com.tr", "enpara.com", "papara.com", "trendyol.com",
        "hepsiburada.com", "sahibinden.com", "turkcell.com.tr", "turktelekom.com.tr",
        "vodafone.com.tr", "ptt.gov.tr", "yurticikargo.com", "araskargo.com.tr",
        "mngkargo.com.tr", "getir.com", "yemeksepeti.com",
        // Germany
        "sparkasse.de", "deutsche-bank.de", "commerzbank.de", "postbank.de", "dkb.de",
        "ing.de", "comdirect.de", "n26.com", "dhl.de", "deutschepost.de", "deutsche-post.de", "amazon.de",
        "elster.de", "bahn.de", "telekom.de", "vodafone.de", "gmx.net", "web.de",
        "check24.de", "kleinanzeigen.de", "zalando.de", "otto.de",
        // India
        "onlinesbi.sbi", "sbi.co.in", "hdfcbank.com", "icicibank.com", "axisbank.com",
        "kotak.com", "pnbindia.in", "bankofbaroda.in", "paytm.com", "phonepe.com",
        "npci.org.in", "bhimupi.org.in", "uidai.gov.in", "incometax.gov.in",
        "irctc.co.in", "flipkart.com", "amazon.in", "jio.com", "airtel.in",
        "indiapost.gov.in", "gst.gov.in", "myntra.com",
        // Arabic-speaking countries
        "alrajhibank.com.sa", "alahli.com", "riyadbank.com", "stcpay.com.sa",
        "absher.sa", "emiratesnbd.com", "adcb.com", "mashreq.com", "aramex.com",
        "noon.com", "careem.com", "nbe.com.eg", "banquemisr.com", "cibeg.com",
        "kfh.com", "nbk.com", "qnb.com"
    )

    /** Below this length a brand name is too likely to be an ordinary word. */
    private const val MIN_WORD_LENGTH = 5

    /** Below this length one typo turns the brand into an unrelated real word. */
    private const val MIN_TYPO_LENGTH = 6

    private class Brand(val domain: String, val label: String, val skeleton: String)

    // Lazy: skeleton() reads CONFUSABLES, which is declared further down and
    // would still be null if this ran during the object's own initialisation.
    private val brands: List<Brand> by lazy {
        BRANDS.map { domain ->
            val label = PublicSuffix.registrableLabel(domain)
            Brand(domain, label, skeleton(label))
        }
    }

    private val brandDomains: Set<String> by lazy { BRANDS.toSet() }

    /**
     * @param host the host as the user would read it, lower-case, in any script.
     * @param asciiHost the same host in its punycode form.
     */
    fun check(host: String, asciiHost: String): Match? {
        val registrableAscii = PublicSuffix.registrableDomain(asciiHost)
        // A brand's own domain, or any subdomain of it, is the brand.
        if (registrableAscii in brandDomains) return null

        val registrable = PublicSuffix.registrableDomain(host, asciiHost)
        val label = PublicSuffix.registrableLabel(registrable)
        val labelSkeleton = skeleton(label)
        val subdomain = host.removeSuffix(registrable).trimEnd('.')
        // Everything left of the public suffix, folded, with a separator at each
        // end so a word match can demand a boundary on both sides.
        val words = "." + skeleton(listOf(subdomain, label).filter { it.isNotEmpty() }.joinToString(".")) + "."

        var best: Match? = null
        for (brand in brands) {
            // Same name under another suffix — paypal.de, google.com.tr — is
            // usually the brand's own country site, so nothing below fires on it.
            val sameName = label == brand.label
            val kind = when {
                containsDomain(subdomain, brand.domain) -> Kind.BRAND_DOMAIN_IN_SUBDOMAIN
                !sameName && labelSkeleton == brand.skeleton -> Kind.LOOKALIKE
                !sameName && brand.label.length >= MIN_TYPO_LENGTH &&
                    osaDistance(labelSkeleton, brand.skeleton) <= 1 -> Kind.LOOKALIKE
                !sameName && brand.label.length >= MIN_WORD_LENGTH &&
                    containsWord(words, brand.skeleton) -> Kind.BRAND_NAME_IN_HOST
                else -> null
            } ?: continue
            val current = best
            if (current == null || (kind.severe && !current.kind.severe)) best = Match(brand.domain, kind)
            if (kind.severe) break
        }
        return best
    }

    /** True when [word] occurs in [haystack] bounded by `.` or `-` on both sides. */
    private fun containsWord(haystack: String, word: String): Boolean {
        var from = 0
        while (true) {
            val at = haystack.indexOf(word, from)
            if (at < 0) return false
            val end = at + word.length
            if (at > 0 && haystack[at - 1].isSeparator() && end < haystack.length && haystack[end].isSeparator()) {
                return true
            }
            from = at + 1
        }
    }

    private fun Char.isSeparator() = this == '.' || this == '-'

    private fun containsDomain(subdomain: String, domain: String): Boolean {
        if (subdomain.isEmpty()) return false
        val padded = ".$subdomain."
        return padded.contains(".$domain.") || padded.contains(".$domain-")
    }

    /**
     * Folds a label to the letters it *looks* like, so two strings that read the
     * same on screen compare equal. Compatibility decomposition first turns
     * full-width and accented letters into plain ones; the table then covers the
     * Cyrillic and Greek letters that are indistinguishable from Latin in most
     * fonts, and the digit and letter-pair swaps phishing kits rely on.
     */
    internal fun skeleton(value: String): String {
        val decomposed = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFKD)
        val folded = buildString(decomposed.length) {
            for (c in decomposed) {
                if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
                append(CONFUSABLES[c] ?: c)
            }
        }
        return folded.replace("rn", "m").replace("vv", "w")
    }

    private val CONFUSABLES: Map<Char, Char> = mapOf(
        // Cyrillic
        'а' to 'a', 'е' to 'e', 'ё' to 'e', 'о' to 'o', 'р' to 'p', 'с' to 'c',
        'у' to 'y', 'х' to 'x', 'і' to 'i', 'ї' to 'i', 'ј' to 'j', 'һ' to 'h',
        'ԁ' to 'd', 'ӏ' to 'l', 'ѕ' to 's', 'ԛ' to 'q', 'ԝ' to 'w', 'к' to 'k',
        'ь' to 'b', 'п' to 'n',
        // Greek
        'α' to 'a', 'ε' to 'e', 'ι' to 'i', 'κ' to 'k', 'ν' to 'v', 'ο' to 'o',
        'ρ' to 'p', 'τ' to 't', 'υ' to 'u', 'χ' to 'x',
        // Latin look-alikes
        'ı' to 'i', 'ł' to 'l', 'ɡ' to 'g', 'ɑ' to 'a', 'ǀ' to 'l', 'ℓ' to 'l',
        // Digits standing in for letters
        '0' to 'o', '1' to 'l'
    )

    /**
     * Optimal string alignment distance: edits, where swapping two neighbouring
     * characters counts as one — the common `pyapal` typo-squat.
     */
    internal fun osaDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        var prevPrev = IntArray(b.length + 1)
        var prev = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var value = minOf(prev[j] + 1, current[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    value = minOf(value, prevPrev[j - 2] + 1)
                }
                current[j] = value
            }
            val recycled = prevPrev
            prevPrev = prev
            prev = current
            current = recycled
        }
        return prev[b.length]
    }
}
