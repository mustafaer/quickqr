package net.mustafaer.quickqr.utils

import java.net.IDN

/**
 * What a scanned link actually resolves to, and the specific ways it may be
 * dressed up to look like somewhere else.
 */
data class LinkVerdict(
    /** Host as the user should read it: Unicode, unless that would hide a look-alike. */
    val displayHost: String,
    /** Host the browser will actually resolve. */
    val asciiHost: String,
    /** The part of [displayHost] someone registered — the site the link really belongs to. */
    val registrableDomain: String,
    val flags: Set<Flag>,
    /** The real domain of the brand this host imitates, when [Flag.LOOKALIKE] or a brand flag is set. */
    val imitatedBrand: String? = null
) {
    /** Severe flags change the default action from "open" to "copy". */
    enum class Flag(val severe: Boolean) {
        /** `https://google.com@evil.com` — everything before `@` is decoration. */
        USERINFO(true),

        /** Latin mixed with Cyrillic or Greek inside one label: `аpple`. */
        MIXED_SCRIPT(true),

        /** The host's own name imitates a known brand: `paypa1.com`. */
        LOOKALIKE(true),

        /** A brand's real domain placed in front of another one: `paypal.com.evil.io`. */
        BRAND_IN_SUBDOMAIN(true),

        /** A numeric address on the public internet instead of a name. */
        PUBLIC_IP(true),

        /** A whole label in a script that can pass for Latin. */
        CONFUSABLE_SCRIPT(false),

        /** A brand's name used as a word on another site: `paypal-help.net`. */
        BRAND_NAME(false),

        /** A numeric address on a local network — a router, a printer. */
        LOCAL_IP(false),

        /** `http://` or `ftp://`: anyone on the network can read or alter it. */
        INSECURE(false),

        NON_STANDARD_PORT(false),

        /** The real destination is hidden behind a redirect that cannot be followed offline. */
        SHORTENER(false)
    }

    val isSevere: Boolean get() = flags.any { it.severe }

    /** [displayHost] up to, not including, [registrableDomain] — the part shown dimmed. */
    val subdomainPrefix: String get() = displayHost.removeSuffix(registrableDomain)
}

/**
 * Inspects a link before it is opened. Pure JVM and offline, like the rest of
 * `utils`, so every rule here has a plain unit test.
 */
object UrlInspector {

    /**
     * Scripts whose letters pass for Latin ones in most fonts. Grouped by Unicode
     * block rather than `Character.UnicodeScript`, which needs API 24 — and for
     * letters in host names the two agree.
     */
    private enum class Script(val confusableWithLatin: Boolean) {
        LATIN(false), CYRILLIC(true), GREEK(true), ARMENIAN(true), CHEROKEE(true), OTHER(false)
    }

    private fun scriptOf(codePoint: Int): Script = when (Character.UnicodeBlock.of(codePoint) ?: return Script.OTHER) {
        Character.UnicodeBlock.BASIC_LATIN,
        Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
        Character.UnicodeBlock.LATIN_EXTENDED_A,
        Character.UnicodeBlock.LATIN_EXTENDED_B,
        Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL,
        Character.UnicodeBlock.IPA_EXTENSIONS -> Script.LATIN
        Character.UnicodeBlock.CYRILLIC,
        Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY -> Script.CYRILLIC
        Character.UnicodeBlock.GREEK,
        Character.UnicodeBlock.GREEK_EXTENDED -> Script.GREEK
        Character.UnicodeBlock.ARMENIAN -> Script.ARMENIAN
        Character.UnicodeBlock.CHEROKEE -> Script.CHEROKEE
        else -> Script.OTHER
    }

    /** The scripts of the letters in [label]; digits, hyphens and marks belong to none. */
    private fun scriptsOf(label: String): Set<Script> {
        val scripts = mutableSetOf<Script>()
        var i = 0
        while (i < label.length) {
            val codePoint = label.codePointAt(i)
            if (Character.isLetter(codePoint)) scripts += scriptOf(codePoint)
            i += Character.charCount(codePoint)
        }
        return scripts
    }

    private val SHORTENERS = setOf(
        "bit.ly", "t.co", "tinyurl.com", "goo.gl", "ow.ly", "is.gd", "buff.ly",
        "rebrand.ly", "cutt.ly", "shorturl.at", "t.ly", "s.id", "rb.gy", "tiny.cc",
        "qrco.de", "qr.codes", "bl.ink", "short.io", "lnkd.in"
    )

    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /** Returns null for anything that is not a `scheme://authority` link. */
    fun inspect(url: String): LinkVerdict? {
        val schemeEnd = url.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = url.substring(0, schemeEnd).lowercase()
        val authority = url.substring(schemeEnd + 3)
            .substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore('\\')
        if (authority.isEmpty()) return null

        val flags = mutableSetOf<LinkVerdict.Flag>()
        if ('@' in authority) flags += LinkVerdict.Flag.USERINFO
        if (scheme == "http" || scheme == "ftp") flags += LinkVerdict.Flag.INSECURE

        val hostPort = authority.substringAfterLast('@')
        val isIpv6 = hostPort.startsWith("[")
        val rawHost = if (isIpv6) hostPort.substringBefore(']') + "]" else hostPort.substringBeforeLast(':')
        val port = if (isIpv6) hostPort.substringAfter("]:", "") else hostPort.substringAfterLast(':', "")
        if (port.isNotEmpty() && port != "80" && port != "443") flags += LinkVerdict.Flag.NON_STANDARD_PORT
        if (rawHost.isEmpty() || rawHost == "]") return null

        val ipv4 = IPV4.matchEntire(rawHost)
        if (isIpv6 || ipv4 != null) {
            flags += if (ipv4 != null && isPrivate(ipv4.groupValues.drop(1).map { it.toInt() })) {
                LinkVerdict.Flag.LOCAL_IP
            } else {
                LinkVerdict.Flag.PUBLIC_IP
            }
            return LinkVerdict(rawHost, rawHost, rawHost, flags)
        }

        // `java.net.URI` gives up on a Unicode host and returns null, so the host
        // goes through IDNA itself: that is exactly the conversion a browser makes.
        val asciiHost = runCatching { IDN.toASCII(rawHost.trimEnd('.'), IDN.ALLOW_UNASSIGNED) }
            .getOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val unicodeHost = IDN.toUnicode(asciiHost, IDN.ALLOW_UNASSIGNED).lowercase()

        var hideUnicode = false
        for (label in unicodeHost.split('.')) {
            val scripts = scriptsOf(label)
            if (scripts.none { it.confusableWithLatin }) continue
            hideUnicode = true
            flags += if (scripts.size > 1) LinkVerdict.Flag.MIXED_SCRIPT else LinkVerdict.Flag.CONFUSABLE_SCRIPT
        }

        if (SHORTENERS.any { asciiHost == it || asciiHost.endsWith(".$it") }) {
            flags += LinkVerdict.Flag.SHORTENER
        }

        val match = LookalikeDetector.check(unicodeHost, asciiHost)
        when (match?.kind) {
            LookalikeDetector.Kind.LOOKALIKE -> flags += LinkVerdict.Flag.LOOKALIKE
            LookalikeDetector.Kind.BRAND_DOMAIN_IN_SUBDOMAIN -> flags += LinkVerdict.Flag.BRAND_IN_SUBDOMAIN
            LookalikeDetector.Kind.BRAND_NAME_IN_HOST -> flags += LinkVerdict.Flag.BRAND_NAME
            null -> Unit
        }

        // A host that hides a look-alike behind Unicode is shown in the punycode
        // form the browser will really resolve.
        val displayHost = if (hideUnicode) asciiHost else unicodeHost
        return LinkVerdict(
            displayHost = displayHost,
            asciiHost = asciiHost,
            registrableDomain = PublicSuffix.registrableDomain(displayHost, asciiHost),
            flags = flags,
            imitatedBrand = match?.brandDomain
        )
    }

    /** RFC 1918 private ranges, loopback and link-local. */
    private fun isPrivate(octets: List<Int>): Boolean {
        if (octets.any { it > 255 }) return false
        val (a, b) = octets
        return a == 10 || a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254)
    }
}
