package com.microtag.inspect

import java.net.IDN
import java.net.URLDecoder
import java.util.Locale

// ==========================================================
// LINKS: never fetched. Cleaned, unwrapped and checked offline.
// ==========================================================

internal object UrlInspector {

    private val trackerNames = setOf(
        "fbclid", "gclid", "dclid", "gbraid", "wbraid", "msclkid", "yclid", "twclid", "ttclid", "igshid", "igsh",
        "mc_eid", "mc_cid", "_hsenc", "_hsmi", "mkt_tok", "ref_src", "s_cid", "vero_id", "oly_enc_id", "_ga", "_gl"
    )
    private val shareIdHosts = setOf("youtube.com", "youtu.be", "spotify.com", "open.spotify.com", "music.youtube.com")

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly", "rebrand.ly", "shorturl.at",
        "rb.gy", "t.ly", "lnkd.in", "amzn.to", "fb.me", "tiny.cc", "bit.do", "s.id", "v.gd"
    )

    private val oddTlds = setOf(
        "zip", "mov", "top", "xyz", "click", "country", "gq", "tk", "ml", "cf", "ga", "work", "support", "rest",
        "icu", "cyou", "buzz", "monster", "sbs", "cfd", "loan", "men", "review", "download", "kim", "link"
    )

    private val commonTlds = setOf(
        "com", "org", "net", "io", "in", "co", "dev", "app", "me", "ai", "edu", "gov", "info", "xyz", "uk", "us", "ca",
        "de", "fr", "jp", "au", "tv", "gg", "ly", "so", "sh", "to", "cc", "page", "site", "online", "store", "tech", "link"
    )

    private val secondLevel = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.in", "net.in", "org.in", "ac.in",
        "gov.in", "nic.in", "com.br", "co.jp", "co.nz", "com.sg", "co.za", "com.cn", "com.mx", "com.tr", "com.hk"
    )

    private val brands = setOf(
        "google", "paypal", "amazon", "apple", "microsoft", "facebook", "instagram", "whatsapp", "netflix", "paytm",
        "phonepe", "flipkart", "github", "binance", "coinbase", "telegram", "discord", "spotify", "dropbox", "linkedin",
        "twitter", "outlook", "gmail", "youtube", "hdfcbank", "icicibank", "axisbank", "razorpay", "swiggy", "zomato",
        "airtel", "jio", "sbi"
    )
    private val legitHyphenated = setOf("youtube-nocookie.com", "google-analytics.com")

    private val schemeRe = Regex("""(?i)^https?://""")
    private val bareDomainRe = Regex("""(?i)^[a-z0-9-]+(\.[a-z0-9-]+)+(:\d+)?([/?#].*)?$""")
    private val partsRe = Regex("""^(?:([a-zA-Z][a-zA-Z0-9+.-]*)://)?([^/?#]*)([^?#]*)(?:\?([^#]*))?(?:#(.*))?$""")
    private val ipv4Re = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun candidates(text: String, source: Source): List<Candidate> {
        val t = text.trim()
        if (t.isEmpty() || t.length > 2000 || t.any { it.isWhitespace() }) return emptyList()
        val hasScheme = schemeRe.containsMatchIn(t)
        val isWww = t.startsWith("www.", ignoreCase = true)
        if (!hasScheme && !isWww) {
            if (source != Source.SELECTION || !bareDomainRe.matches(t)) return emptyList()
            val hostPart = t.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            if (hostPart.substringAfterLast('.').lowercase(Locale.ROOT) !in commonTlds) return emptyList()
        } else if (!hasScheme && source == Source.CLIPBOARD && !bareDomainRe.matches(t)) {
            return emptyList()
        }
        val analysis = analyze(t) ?: return emptyList()
        if (source == Source.CLIPBOARD && !analysis.noteworthy) return emptyList()
        return listOf(Candidate("url", 0.88) { analysis.toResult() })
    }

    class Analysis(
        val original: String,
        val cleaned: String,
        val host: String,
        val registrable: String,
        val strong: List<String>,
        val mild: List<String>,
        val trackersRemoved: Int,
        val unwrapped: Boolean,
        val assumedScheme: Boolean,
        val shortened: Boolean = false
    ) {
        val noteworthy: Boolean get() = strong.isNotEmpty() || mild.any { it.startsWith("Shortened") } || trackersRemoved > 0 || unwrapped
        val risky: Boolean get() = strong.isNotEmpty() || mild.size >= 2

        fun toResult(): InspectResult {
            val displayHost = try {
                IDN.toUnicode(host)
            } catch (_: Exception) {
                host
            }
            val rows = mutableListOf("Site" to displayHost)
            if (registrable != host) rows.add("Domain" to registrable)
            if (cleaned != original && !(assumedScheme && cleaned == "https://$original")) rows.add("Clean link" to cleaned)
            if (unwrapped) rows.add("Redirect" to "Wrapper removed, this is the real destination")
            if (trackersRemoved > 0) rows.add("Removed" to "$trackersRemoved tracking parameter" + if (trackersRemoved > 1) "s" else "")
            strong.forEach { rows.add("Warning" to it) }
            mild.forEach { rows.add("Note" to it) }
            val total = strong.size + mild.size
            return InspectResult(
                domain = "URL",
                pill = if (risky) "Check link" else displayHost,
                title = displayHost,
                subtitle = if (total == 0) "Link" else "Link · $total ${if (total == 1) "flag" else "flags"}",
                cardTitle = if (risky) "Check this link" else "Link",
                cardText = if (risky) "${strong.firstOrNull() ?: mild.first()}" else cleaned,
                rows = rows,
                copyText = cleaned,
                openUrl = cleaned,
                timeoutSeconds = 20,
                status = if (risky) Status.WARN else Status.OK,
                checkHost = if (host.startsWith("[") || ipv4Re.matches(host)) "" else host,
                resolveUrl = if (shortened) cleaned else ""
            )
        }
    }

    private class Parts(val scheme: String, val authority: String, val userInfo: String, val host: String, val path: String, val query: String, val fragment: String)

    private fun split(url: String): Parts? {
        val m = partsRe.find(url) ?: return null
        val scheme = m.groupValues[1].lowercase(Locale.ROOT)
        val authority = m.groupValues[2]
        val at = authority.lastIndexOf('@')
        val userInfo = if (at >= 0) authority.substring(0, at) else ""
        var hostPort = if (at >= 0) authority.substring(at + 1) else authority
        if (!hostPort.startsWith("[")) hostPort = hostPort.substringBefore(':')
        val host = hostPort.trimEnd('.').lowercase(Locale.ROOT)
        if (host.isEmpty()) return null
        return Parts(scheme, authority, userInfo, host, m.groupValues[3], m.groupValues[4], m.groupValues[5])
    }

    internal fun isShortener(host: String): Boolean = host in shorteners || registrableOf(host).first in shorteners

    internal fun hostOf(url: String): String? = split(url)?.host

    internal fun registrableDomain(host: String): String = registrableOf(host).first

    private fun registrableOf(host: String): Pair<String, String> {
        val labels = host.split('.')
        if (labels.size < 2) return host to host
        val last2 = labels.takeLast(2).joinToString(".")
        val take = if (labels.size >= 3 && last2 in secondLevel) 3 else 2
        val reg = labels.takeLast(take).joinToString(".")
        val sld = labels[labels.size - take]
        return reg to sld
    }

    private fun unwrap(parts: Parts): String? {
        val h = parts.host.removePrefix("www.")
        val key = when {
            h.endsWith("google.com") && parts.path == "/url" -> listOf("q", "url")
            h == "l.facebook.com" && parts.path == "/l.php" -> listOf("u")
            h.endsWith("youtube.com") && parts.path == "/redirect" -> listOf("q")
            h.endsWith("linkedin.com") && parts.path.startsWith("/safety/go") -> listOf("url")
            else -> return null
        }
        for (pair in parts.query.split('&')) {
            val k = pair.substringBefore('=')
            if (k in key) {
                val v = try {
                    URLDecoder.decode(pair.substringAfter('=', ""), "UTF-8")
                } catch (_: Exception) {
                    ""
                }
                if (schemeRe.containsMatchIn(v)) return v
            }
        }
        return null
    }

    private fun isTracker(name: String, host: String): Boolean {
        val n = name.lowercase(Locale.ROOT)
        if (n.startsWith("utm_") || n in trackerNames) return true
        if (n == "si" && host.removePrefix("www.") in shareIdHosts) return true
        return false
    }

    fun analyze(raw: String): Analysis? {
        var current = raw.trim()
        val assumed = !schemeRe.containsMatchIn(current)
        if (assumed) current = "https://$current"
        var unwrapped = false
        var parts = split(current) ?: return null
        if (parts.scheme != "http" && parts.scheme != "https") return null
        repeat(2) {
            val inner = unwrap(parts)
            if (inner != null) {
                val p = split(inner)
                if (p != null && (p.scheme == "http" || p.scheme == "https")) {
                    current = inner
                    parts = p
                    unwrapped = true
                }
            }
        }

        var removed = 0
        val keptQuery = parts.query.split('&').filter { it.isNotEmpty() }.filter { pair ->
            val drop = isTracker(pair.substringBefore('='), parts.host)
            if (drop) removed++
            !drop
        }
        val cleaned = buildString {
            append(parts.scheme).append("://").append(parts.authority)
            append(parts.path)
            if (keptQuery.isNotEmpty()) append('?').append(keptQuery.joinToString("&"))
            if (parts.fragment.isNotEmpty()) append('#').append(parts.fragment)
        }

        val host = parts.host
        val (registrable, sld) = registrableOf(host)
        val strong = mutableListOf<String>()
        val mild = mutableListOf<String>()

        if (parts.userInfo.isNotEmpty()) strong.add("Contains a login-style prefix before the real site, a common trick to hide where it goes")
        if (ipv4Re.matches(host) || host.startsWith("[")) strong.add("Points to a raw IP address, not a named site")
        if (host.any { it.code > 127 } || host.split('.').any { it.startsWith("xn--") }) {
            strong.add("Uses special characters that can imitate another site")
        }
        brandCheck(host, registrable, sld)?.let { strong.add(it) }

        if (registrable in shorteners || host in shorteners) mild.add("Shortened link, the real destination is hidden until opened")
        val tld = host.substringAfterLast('.')
        if (tld in oddTlds && !host.startsWith("[") && !ipv4Re.matches(host)) mild.add("Unusual domain ending (.$tld)")
        if (parts.scheme == "http") mild.add("Not encrypted (http)")
        val subCount = host.split('.').size - registrable.split('.').size
        if (subCount > 3) mild.add("Unusually many sub-domains")

        return Analysis(
            raw.trim(), cleaned, host, registrable, strong, mild, removed, unwrapped, assumed,
            shortened = registrable in shorteners || host in shorteners
        )
    }

    private fun skeleton(s: String): String {
        var x = s.lowercase(Locale.ROOT).replace("-", "")
        x = x.replace("rn", "m").replace("vv", "w")
        return x.map {
            when (it) {
                '0' -> 'o'
                '1' -> 'l'
                '3' -> 'e'
                '5' -> 's'
                '4' -> 'a'
                else -> it
            }
        }.joinToString("")
    }

    private fun editDistanceAtMostOne(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) i++
        return when {
            a.length == b.length -> a.substring(i + 1) == b.substring(i + 1)
            a.length > b.length -> a.substring(i + 1) == b.substring(i)
            else -> a.substring(i) == b.substring(i + 1)
        }
    }

    private fun brandCheck(host: String, registrable: String, sld: String): String? {
        if (registrable in legitHyphenated) return null
        if (sld in brands) return null
        val sk = skeleton(sld)
        for (b in brands) {
            if (sk == b) return "Looks like ${b.replaceFirstChar { it.uppercase() }} but is a different site"
            if (b.length >= 6 && sld.length >= 6 && editDistanceAtMostOne(sk, b)) {
                return "Looks like ${b.replaceFirstChar { it.uppercase() }} but is a different site"
            }
        }
        val sub = host.removeSuffix(registrable).trimEnd('.').split('.').filter { it.isNotEmpty() }
        val tokens = (sub + sld).flatMap { it.split('-') }
        for (b in brands) {
            if (b.length >= 4 && tokens.any { it == b }) {
                return "Has ${b.replaceFirstChar { it.uppercase() }} in the address but belongs to $registrable"
            }
        }
        return null
    }
}
