package com.microtag.inspect

import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// ==========================================================
// Things a link only does when the user asks:
//  CHECK   - sends the site NAME (never the path or query) to DNS resolvers and a domain registry
//  RESOLVE - follows a shortener's redirects, stopping before it touches the real destination
// ==========================================================

internal class Doh(val status: Int, val answers: List<String>)

internal class CheckOutcome(val rows: List<Pair<String, String>>, val status: Status)

internal class ResolveOutcome(val finalUrl: String?, val hops: Int, val problem: String?)

internal enum class DnsVerdict { CLEAN, BLOCKED, NOT_FOUND, UNKNOWN }

internal object LinkCheck {

    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "inspect-linkcheck").apply { isDaemon = true } }

    private const val GOOGLE = "https://dns.google/resolve?type=A&name="                         // unfiltered
    private const val CLOUDFLARE_SECURITY = "https://security.cloudflare-dns.com/dns-query?type=A&name=" // blocks malware
    private const val QUAD9 = "https://dns.quad9.net:5053/dns-query?type=A&name="                  // blocks malware

    // ---- parsing ------------------------------------------------------------------------------

    internal fun parseDoh(body: String): Doh? = try {
        val o = JSONObject(body)
        if (!o.has("Status")) null else {
            val answers = ArrayList<String>()
            val arr = o.optJSONArray("Answer")
            if (arr != null) for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val type = a.optInt("type", 0)
                if (type == 1 || type == 28) answers.add(a.optString("data", ""))
            }
            Doh(o.optInt("Status", -1), answers)
        }
    } catch (_: Exception) {
        null
    }

    private fun sinkhole(ip: String) = ip == "0.0.0.0" || ip == "::" || ip == "127.0.0.1" || ip.isBlank()

    /**
     * The same name is asked of an unfiltered resolver and of malware-filtering ones. If the plain resolver finds
     * a real address and a filtering one refuses (NXDOMAIN or a 0.0.0.0 sinkhole), the filter has blocked it.
     */
    internal fun dnsVerdict(unfiltered: Doh?, filtered: List<Doh?>): DnsVerdict {
        if (unfiltered == null) return DnsVerdict.UNKNOWN
        val real = unfiltered.status == 0 && unfiltered.answers.any { !sinkhole(it) }
        if (!real) return if (unfiltered.status == 3) DnsVerdict.NOT_FOUND else DnsVerdict.UNKNOWN
        val reached = filtered.filterNotNull()
        if (reached.isEmpty()) return DnsVerdict.UNKNOWN
        val blocked = reached.any { it.status == 3 || (it.status == 0 && it.answers.all { a -> sinkhole(a) }) }
        return if (blocked) DnsVerdict.BLOCKED else DnsVerdict.CLEAN
    }

    /** RDAP "events" -> the registration instant. */
    internal fun parseRdapRegistration(body: String): Instant? = try {
        val events = JSONObject(body).optJSONArray("events")
        var found: Instant? = null
        if (events != null) for (i in 0 until events.length()) {
            val e = events.optJSONObject(i) ?: continue
            if (e.optString("eventAction", "") == "registration") {
                val d = e.optString("eventDate", "")
                found = try {
                    Instant.parse(d)
                } catch (_: Exception) {
                    OffsetDateTime.parse(d).toInstant()
                }
                break
            }
        }
        found
    } catch (_: Exception) {
        null
    }

    internal fun ageText(days: Long): String = when {
        days < 1 -> "today"
        days == 1L -> "yesterday"
        days < 60 -> "$days days ago"
        days < 730 -> "${days / 30} months ago"
        else -> "${days / 365} years ago"
    }

    // ---- network ------------------------------------------------------------------------------

    private fun doh(prefix: String, host: String): Doh? {
        val r = Http.get(prefix + Http.encode(host), 3500, accept = "application/dns-json")
        return if (r.ok) parseDoh(r.body) else null
    }

    private fun registration(registrable: String): Instant? {
        val r = Http.get("https://rdap.org/domain/" + Http.encode(registrable), 4000, accept = "application/rdap+json")
        return if (r.ok) parseRdapRegistration(r.body) else null
    }

    fun check(host: String, now: Instant = Instant.now()): CheckOutcome {
        val registrable = UrlInspector.registrableDomain(host)
        val tasks = listOf(
            Callable<Any?> { doh(GOOGLE, host) },
            Callable<Any?> { doh(CLOUDFLARE_SECURITY, host) },
            Callable<Any?> { doh(QUAD9, host) },
            Callable<Any?> { registration(registrable) }
        )
        val done = try {
            pool.invokeAll(tasks, 5, TimeUnit.SECONDS)
        } catch (_: Exception) {
            emptyList()
        }
        fun <T> value(i: Int): T? = try {
            @Suppress("UNCHECKED_CAST")
            if (i < done.size && !done[i].isCancelled) done[i].get() as T? else null
        } catch (_: Exception) {
            null
        }

        val verdict = dnsVerdict(value<Doh>(0), listOf(value<Doh>(1), value<Doh>(2)))
        return outcome(verdict, value<Instant>(3), now)
    }

    internal fun outcome(verdict: DnsVerdict, registered: Instant?, now: Instant): CheckOutcome {
        val rows = ArrayList<Pair<String, String>>()
        var status = Status.OK
        when (verdict) {
            DnsVerdict.BLOCKED -> {
                rows.add("Warning" to "Blocked by malware filtering (Cloudflare or Quad9). Do not open it.")
                status = Status.ERROR
            }
            DnsVerdict.NOT_FOUND -> {
                rows.add("Note" to "This site name does not exist. A typo, or a dead link.")
                status = Status.WARN
            }
            DnsVerdict.CLEAN -> rows.add("Check" to "Not flagged by malware filtering. That is not a guarantee.")
            DnsVerdict.UNKNOWN -> rows.add("Check" to "Couldn't reach the checking services. Try again with a connection.")
        }
        if (registered != null) {
            val days = Duration.between(registered, now).toDays()
            val date = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).format(registered.atZone(ZoneId.of("UTC")))
            rows.add("Registered" to "$date (${ageText(days)})")
            if (days in 0..29 && status != Status.ERROR) {
                rows.add("Warning" to "Registered only ${ageText(days).removeSuffix(" ago")} ago. Brand-new sites are a common phishing sign.")
                status = Status.WARN
            }
        } else if (verdict != DnsVerdict.UNKNOWN) {
            rows.add("Registered" to "Unknown (this domain ending doesn't publish it)")
        }
        rows.add("Sent" to "Only the site name, never the full link")
        return CheckOutcome(rows, status)
    }

    /**
     * Follows a shortener's redirects one hop at a time. It stops as soon as the address points at a site that
     * is not a shortener, without ever requesting that site, so the real destination never sees the user.
     */
    fun resolveShort(url: String): ResolveOutcome {
        var current = url
        var hops = 0
        while (hops < 5) {
            val host = UrlInspector.hostOf(current) ?: return ResolveOutcome(null, hops, "unreadable link")
            if (!UrlInspector.isShortener(host)) return ResolveOutcome(current, hops, null)
            val h = Http.hop(current)
            if (h.code == -1) return ResolveOutcome(null, hops, "no connection")
            val next = h.location
            if (h.code !in 300..399 || next.isNullOrBlank()) {
                return ResolveOutcome(null, hops, "the shortener did not redirect")
            }
            current = next
            hops++
        }
        return ResolveOutcome(null, hops, "too many redirects")
    }
}
