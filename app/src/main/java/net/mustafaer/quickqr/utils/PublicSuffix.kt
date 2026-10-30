package net.mustafaer.quickqr.utils

/**
 * Finds the registrable domain — the part a person actually registered and
 * controls — of a host, so `paypal.com.secure-login.xyz` can be shown as what it
 * is: a page on `secure-login.xyz`.
 *
 * This is a curated subset of the Public Suffix List, not the whole list: the
 * multi-label suffixes of the countries QuickQR ships to and their neighbours,
 * plus the shared-hosting platforms where anyone can publish under a subdomain
 * (`*.github.io`, `*.web.app`, …) and phishing pages most often live. Any host
 * whose suffix is not listed here falls back to "last two labels", which is
 * right for every generic TLD and for most country codes.
 */
internal object PublicSuffix {

    private val MULTI_LABEL_SUFFIXES = setOf(
        // Türkiye
        "com.tr", "net.tr", "org.tr", "gov.tr", "edu.tr", "bel.tr", "k12.tr", "av.tr",
        "biz.tr", "info.tr", "gen.tr", "web.tr", "tv.tr", "name.tr", "pol.tr", "tsk.tr",
        "dr.tr", "bbs.tr", "tel.tr",
        // India
        "co.in", "net.in", "org.in", "gov.in", "nic.in", "ac.in", "edu.in", "res.in",
        "firm.in", "gen.in", "ind.in", "mil.in",
        // Gulf, Levant and North Africa
        "co.ae", "gov.ae", "ac.ae", "net.ae", "org.ae", "sch.ae",
        "com.sa", "gov.sa", "edu.sa", "net.sa", "org.sa", "med.sa", "sch.sa",
        "com.eg", "gov.eg", "edu.eg", "net.eg", "org.eg", "sci.eg",
        "com.qa", "gov.qa", "edu.qa", "com.kw", "gov.kw", "edu.kw",
        "com.om", "gov.om", "com.bh", "gov.bh", "com.jo", "gov.jo", "com.lb", "gov.lb",
        "com.ma", "gov.ma", "com.dz", "gov.dz", "com.tn", "gov.tn", "com.iq", "gov.iq",
        // Europe
        "co.uk", "org.uk", "ac.uk", "gov.uk", "ltd.uk", "plc.uk", "me.uk", "net.uk",
        "nhs.uk", "sch.uk", "com.cy", "gov.cy", "com.gr", "gov.gr", "com.pl", "gov.pl",
        "com.ua", "gov.ua", "co.at", "gv.at", "or.at",
        // Rest of the world
        "com.au", "net.au", "org.au", "gov.au", "edu.au", "asn.au", "id.au",
        "co.jp", "ne.jp", "or.jp", "ac.jp", "go.jp", "gr.jp", "ed.jp", "lg.jp",
        "com.br", "gov.br", "net.br", "org.br", "edu.br",
        "com.cn", "gov.cn", "net.cn", "org.cn", "edu.cn",
        "co.za", "gov.za", "org.za", "com.mx", "gob.mx", "co.nz", "govt.nz", "org.nz",
        "com.sg", "gov.sg", "com.hk", "gov.hk", "co.kr", "go.kr", "or.kr",
        "com.pk", "gov.pk", "com.bd", "gov.bd", "co.id", "go.id", "or.id",
        "com.my", "gov.my", "com.ar", "gob.ar", "com.tw", "gov.tw",
        "co.il", "gov.il", "org.il", "ac.il", "com.ng", "gov.ng", "co.ke", "go.ke",
        "com.ph", "gov.ph", "com.vn", "gov.vn", "co.th", "go.th", "in.th", "ac.th",
        "com.np", "gov.np", "com.lk", "gov.lk",
        // Shared hosting: every subdomain belongs to a different person.
        "github.io", "gitlab.io", "blogspot.com", "appspot.com", "herokuapp.com",
        "netlify.app", "vercel.app", "pages.dev", "workers.dev", "web.app",
        "firebaseapp.com", "azurewebsites.net", "cloudfront.net", "glitch.me",
        "ngrok.io", "ngrok-free.app", "ngrok.app", "wixsite.com", "weebly.com",
        "square.site", "framer.website", "webflow.io", "repl.co", "replit.app",
        "onrender.com", "fly.dev", "surge.sh", "duckdns.org", "000webhostapp.com",
        "s3.amazonaws.com"
    )

    /** Longest suffix in [MULTI_LABEL_SUFFIXES], in labels. */
    private const val MAX_SUFFIX_LABELS = 3

    /**
     * Number of trailing labels of [asciiHost] that make up its registrable
     * domain. Counting labels rather than returning a string lets the caller cut
     * the same span out of the Unicode form of the host, because IDNA converts
     * label by label.
     */
    fun registrableLabelCount(asciiHost: String): Int {
        val labels = asciiHost.lowercase().split('.')
        for (n in MAX_SUFFIX_LABELS downTo 2) {
            if (labels.size > n && labels.takeLast(n).joinToString(".") in MULTI_LABEL_SUFFIXES) {
                return n + 1
            }
        }
        return minOf(2, labels.size)
    }

    /** The registrable domain of [host], keeping whatever script [host] is written in. */
    fun registrableDomain(host: String, asciiHost: String = host): String {
        val labels = host.split('.')
        return labels.takeLast(registrableLabelCount(asciiHost).coerceAtMost(labels.size)).joinToString(".")
    }

    /** [registrableDomain] minus its public suffix: `garantibbva` for `garantibbva.com.tr`. */
    fun registrableLabel(registrableDomain: String): String = registrableDomain.substringBefore('.')
}
