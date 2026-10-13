package com.microtag.inspect

import org.json.JSONObject
import java.io.File
import java.time.Clock
import java.util.Currency

/**
 * Exchange rates for 160+ currencies from a single request, kept on disk.
 *
 *  - fresh (under 6 h): used as is
 *  - stale (6 h to 7 days): used immediately, refreshed in the background
 *  - missing or older than 7 days: blocks on the network once
 *  - network down: the old copy is returned and flagged offlineCopy
 *
 * Primary is open.er-api.com; the keyless jsDelivr mirror is the fallback.
 */
class CurrencyRates(
    private val cacheFile: File,
    private val clock: Clock = Clock.systemUTC()
) : RateSource {

    @Volatile private var mem: RateTable? = null
    @Volatile private var refreshing = false

    override fun table(): RateTable? {
        val now = clock.millis()
        val cached = mem ?: loadDisk()?.also { mem = it }
        if (cached != null) {
            val age = now - cached.fetchedAtMillis
            if (age < FRESH_MS) return cached
            if (age < HARD_STALE_MS) {
                refreshAsync()
                return cached
            }
        }
        val fresh = fetch(now)
        if (fresh != null) {
            mem = fresh
            saveDisk(fresh)
            return fresh
        }
        return cached?.let { RateTable(it.perUsd, it.fetchedAtMillis, offlineCopy = true) }
    }

    private fun refreshAsync() {
        synchronized(this) {
            if (refreshing) return
            refreshing = true
        }
        Thread({
            try {
                val fresh = fetch(clock.millis())
                if (fresh != null) {
                    mem = fresh
                    saveDisk(fresh)
                }
            } finally {
                refreshing = false
            }
        }, "inspect-rates").apply { isDaemon = true }.start()
    }

    private fun fetch(now: Long): RateTable? {
        val a = Http.get(PRIMARY_URL, timeoutMs = 3500)
        if (a.ok) RateParsers.openErApi(a.body, now)?.let { return it }
        val b = Http.get(MIRROR_URL, timeoutMs = 3500)
        if (b.ok) RateParsers.fawazMirror(b.body, now)?.let { return it }
        return null
    }

    private fun loadDisk(): RateTable? = try {
        if (!cacheFile.exists()) null else {
            val o = JSONObject(cacheFile.readText())
            val rates = o.optJSONObject("rates")
            val map = HashMap<String, Double>()
            rates?.keys()?.forEach { k -> map[k] = rates.optDouble(k, 0.0) }
            map.values.removeAll { it <= 0.0 }
            if (map.size < 10) null else RateTable(map, o.optLong("t", 0L))
        }
    } catch (_: Exception) {
        null
    }

    private fun saveDisk(t: RateTable) {
        try {
            val rates = JSONObject()
            t.perUsd.forEach { (k, v) -> rates.put(k, v) }
            val o = JSONObject().put("t", t.fetchedAtMillis).put("rates", rates)
            val tmp = File(cacheFile.parentFile, cacheFile.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(cacheFile)) {
                cacheFile.writeText(o.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
        }
    }

    companion object {
        const val FRESH_MS = 6L * 60 * 60 * 1000
        const val HARD_STALE_MS = 7L * 24 * 60 * 60 * 1000
        const val PRIMARY_URL = "https://open.er-api.com/v6/latest/USD"
        const val MIRROR_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json"
    }
}

internal object RateParsers {

    private fun validCode(code: String): Boolean = try {
        Currency.getInstance(code)
        true
    } catch (_: Exception) {
        false
    }

    private fun finish(map: Map<String, Double>, now: Long): RateTable? {
        if (map.size < 10 || map["USD"] == null) return null
        return RateTable(map, now)
    }

    /** {"result":"success","rates":{"USD":1,"INR":83.1,...}} */
    fun openErApi(body: String, now: Long): RateTable? = try {
        val o = JSONObject(body)
        if (o.optString("result") != "success") null else {
            val rates = o.optJSONObject("rates")
            val map = HashMap<String, Double>()
            rates?.keys()?.forEach { k ->
                val v = rates.optDouble(k, 0.0)
                if (v > 0.0 && k.length == 3 && validCode(k)) map[k] = v
            }
            finish(map, now)
        }
    } catch (_: Exception) {
        null
    }

    /** {"date":"2026-10-12","usd":{"inr":83.1,...}} with lowercase codes. */
    fun fawazMirror(body: String, now: Long): RateTable? = try {
        val o = JSONObject(body)
        val rates = o.optJSONObject("usd")
        val map = HashMap<String, Double>()
        rates?.keys()?.forEach { k ->
            val v = rates.optDouble(k, 0.0)
            val code = k.uppercase()
            if (v > 0.0 && code.length == 3 && validCode(code)) map[code] = v
        }
        finish(map, now)
    } catch (_: Exception) {
        null
    }
}
