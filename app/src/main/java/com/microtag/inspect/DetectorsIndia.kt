package com.microtag.inspect

import org.json.JSONObject
import java.util.Locale

// ==========================================================
// INDIA: UPI IDs, IFSC codes, PIN codes
// ==========================================================

internal object UpiDetector {
    private val shape = Regex("""^([A-Za-z0-9][A-Za-z0-9._-]{1,48})@([A-Za-z][A-Za-z0-9]{1,24})$""")

    private val handles = mapOf(
        "okhdfcbank" to "Google Pay · HDFC Bank", "okicici" to "Google Pay · ICICI Bank",
        "oksbi" to "Google Pay · SBI", "okaxis" to "Google Pay · Axis Bank",
        "ybl" to "PhonePe · Yes Bank", "ibl" to "PhonePe · ICICI Bank", "axl" to "PhonePe · Axis Bank",
        "paytm" to "Paytm Payments Bank", "pthdfc" to "Paytm · HDFC Bank", "ptyes" to "Paytm · Yes Bank",
        "ptaxis" to "Paytm · Axis Bank", "ptsbi" to "Paytm · SBI",
        "apl" to "Amazon Pay · Axis Bank", "yapl" to "Amazon Pay · Yes Bank", "rapl" to "Amazon Pay · RBL Bank",
        "upi" to "BHIM", "sbi" to "State Bank of India", "hdfcbank" to "HDFC Bank", "icici" to "ICICI Bank",
        "axisbank" to "Axis Bank", "okbizaxis" to "Google Pay for Business · Axis Bank", "ikwik" to "MobiKwik",
        "freecharge" to "Freecharge", "airtel" to "Airtel Payments Bank", "jupiteraxis" to "Jupiter · Axis Bank",
        "fbl" to "Federal Bank", "federal" to "Federal Bank", "postbank" to "India Post Payments Bank",
        "pnb" to "Punjab National Bank", "boi" to "Bank of India", "cnrb" to "Canara Bank",
        "unionbank" to "Union Bank of India", "barodampay" to "Bank of Baroda", "yesbank" to "Yes Bank",
        "idfcbank" to "IDFC First Bank", "kotak" to "Kotak Mahindra Bank", "indus" to "IndusInd Bank",
        "rbl" to "RBL Bank", "aubank" to "AU Small Finance Bank", "slice" to "Slice", "cred" to "CRED",
        "ezetap" to "Ezetap", "dbs" to "DBS Bank", "hsbc" to "HSBC", "sc" to "Standard Chartered",
        "uboi" to "Union Bank of India", "iob" to "Indian Overseas Bank", "centralbank" to "Central Bank of India",
        "indianbank" to "Indian Bank", "mahb" to "Bank of Maharashtra", "jsb" to "Janata Sahakari Bank"
    )

    fun candidates(text: String, source: Source): List<Candidate> {
        val t = text.trim()
        if (t.length > 80 || t.contains(' ')) return emptyList()
        val m = shape.matchEntire(t) ?: return emptyList()
        val handle = m.groupValues[2].lowercase(Locale.ROOT)
        val provider = handles[handle]
        // An unknown handle could be anything (a mention, a username); only trust it when selected.
        if (provider == null && source != Source.SELECTION) return emptyList()
        val score = if (provider != null) 0.9 else 0.45
        return listOf(Candidate("upi", score) { build(t, m.groupValues[1], handle, provider) })
    }

    private fun build(id: String, user: String, handle: String, provider: String?): InspectResult {
        val rows = mutableListOf("Name part" to user, "Handle" to "@$handle")
        rows.add("Provider" to (provider ?: "Not a handle I recognise"))
        return InspectResult(
            domain = "UPI",
            pill = provider?.substringBefore(" · ")?.take(14) ?: "UPI ID",
            title = id,
            subtitle = provider ?: "UPI ID",
            cardTitle = "UPI ID",
            cardText = id,
            rows = rows,
            copyText = id,
            iconName = "ic_upi_rupee",
            timeoutSeconds = 20,
            status = if (provider == null) Status.WARN else Status.OK
        )
    }
}

internal object IfscDetector {
    private val shape = Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""")

    private val banks = mapOf(
        "SBIN" to "State Bank of India", "HDFC" to "HDFC Bank", "ICIC" to "ICICI Bank", "UTIB" to "Axis Bank",
        "KKBK" to "Kotak Mahindra Bank", "PUNB" to "Punjab National Bank", "BARB" to "Bank of Baroda",
        "CNRB" to "Canara Bank", "IDIB" to "Indian Bank", "UBIN" to "Union Bank of India", "YESB" to "Yes Bank",
        "INDB" to "IndusInd Bank", "BKID" to "Bank of India", "IOBA" to "Indian Overseas Bank",
        "MAHB" to "Bank of Maharashtra", "CBIN" to "Central Bank of India", "FDRL" to "Federal Bank",
        "RATN" to "RBL Bank", "IDFB" to "IDFC First Bank", "AUBL" to "AU Small Finance Bank", "UCBA" to "UCO Bank",
        "PSIB" to "Punjab & Sind Bank", "KARB" to "Karnataka Bank", "KVBL" to "Karur Vysya Bank",
        "SIBL" to "South Indian Bank", "CIUB" to "City Union Bank", "TMBL" to "Tamilnad Mercantile Bank",
        "DBSS" to "DBS Bank India", "SCBL" to "Standard Chartered Bank", "HSBC" to "HSBC", "CITI" to "Citibank",
        "IPOS" to "India Post Payments Bank", "AIRP" to "Airtel Payments Bank", "PYTM" to "Paytm Payments Bank",
        "JAKA" to "Jammu & Kashmir Bank", "DLXB" to "Dhanlaxmi Bank", "NESF" to "Nesfb", "ESFB" to "Equitas Small Finance Bank",
        "UJVN" to "Ujjivan Small Finance Bank", "FINO" to "Fino Payments Bank", "JIOP" to "Jio Payments Bank"
    )

    fun candidates(text: String, source: Source): List<Candidate> {
        val raw = text.trim()
        if (raw.length != 11) return emptyList()
        // Lowercase is accepted only when selected; a copied lowercase 11-character word is not an IFSC.
        val code = if (source == Source.SELECTION) raw.uppercase(Locale.ROOT) else raw
        if (!shape.matches(code)) return emptyList()
        return listOf(Candidate("ifsc", 0.9, slow = true) { resolve(code) })
    }

    internal fun parse(body: String): Map<String, String>? = try {
        val o = JSONObject(body)
        if (!o.has("IFSC")) null else {
            val m = LinkedHashMap<String, String>()
            for (k in listOf("BANK", "BRANCH", "CITY", "DISTRICT", "STATE", "ADDRESS", "MICR", "SWIFT", "CONTACT", "IFSC")) {
                val v = o.optString(k, "")
                if (v.isNotBlank() && v != "null") m[k] = v
            }
            for (k in listOf("UPI", "IMPS", "NEFT", "RTGS")) m[k] = if (o.optString(k, "false") == "true") "yes" else "no"
            m
        }
    } catch (_: Exception) {
        null
    }

    private fun resolve(code: String): InspectResult? {
        val r = Http.get("https://ifsc.razorpay.com/$code", 3500)
        if (r.code == 404) return null // well-formed, but no such branch
        val info = if (r.ok) parse(r.body) else null
        if (info == null) {
            // Offline or the service is down: the bank is still known from the first four letters.
            val bank = banks[code.take(4)] ?: return null
            return InspectResult(
                domain = "IFSC", pill = bank.take(14), title = code, subtitle = bank, cardTitle = "IFSC",
                cardText = "$code, $bank", rows = listOf("Bank" to bank, "Branch" to "Needs a connection to look up"),
                copyText = code, iconName = "ic_upi_rupee", status = Status.OK
            )
        }
        return build(code, info)
    }

    internal fun build(code: String, info: Map<String, String>): InspectResult {
        val bank = info["BANK"] ?: banks[code.take(4)] ?: "Unknown bank"
        val rows = mutableListOf<Pair<String, String>>("Bank" to bank)
        info["BRANCH"]?.let { rows.add("Branch" to it) }
        val place = listOfNotNull(info["CITY"], info["STATE"]).distinct().joinToString(", ")
        if (place.isNotBlank()) rows.add("Place" to place)
        info["ADDRESS"]?.let { rows.add("Address" to it.replace(Regex("\\s+"), " ")) }
        info["MICR"]?.let { rows.add("MICR" to it) }
        info["SWIFT"]?.let { rows.add("SWIFT" to it) }
        info["CONTACT"]?.let { rows.add("Phone" to it) }
        val rails = listOf("UPI", "IMPS", "NEFT", "RTGS").filter { info[it] == "yes" }
        if (rails.isNotEmpty()) rows.add("Supports" to rails.joinToString(", "))
        return InspectResult(
            domain = "IFSC",
            pill = bank.take(14),
            title = code,
            subtitle = listOfNotNull(info["BRANCH"], info["CITY"]).joinToString(", ").ifBlank { bank },
            cardTitle = "IFSC",
            cardText = "$code, $bank" + (info["BRANCH"]?.let { ", $it" } ?: ""),
            rows = rows,
            copyText = code,
            iconName = "ic_upi_rupee",
            timeoutSeconds = 25
        )
    }
}

internal object PinCodeDetector {
    private val shape = Regex("""^[1-9]\d{5}$""")

    fun candidates(text: String, source: Source): List<Candidate> {
        // A bare six-digit number is far too often an OTP or an id to react to when merely copied.
        if (source != Source.SELECTION) return emptyList()
        val t = text.trim()
        if (!shape.matches(t)) return emptyList()
        return listOf(Candidate("pin", 0.45, slow = true) { resolve(t) })
    }

    internal class Office(val name: String, val district: String, val state: String, val circle: String, val branchType: String)

    internal fun parse(body: String): List<Office>? = try {
        val arr = org.json.JSONArray(body)
        val first = arr.optJSONObject(0)
        if (first == null || first.optString("Status", "") != "Success") null else {
            val offices = first.optJSONArray("PostOffice")
            val list = ArrayList<Office>()
            if (offices != null) for (i in 0 until offices.length()) {
                val o = offices.optJSONObject(i) ?: continue
                list.add(Office(o.optString("Name", ""), o.optString("District", ""), o.optString("State", ""), o.optString("Circle", ""), o.optString("BranchType", "")))
            }
            if (list.isEmpty()) null else list
        }
    } catch (_: Exception) {
        null
    }

    private fun resolve(pin: String): InspectResult? {
        val r = Http.get("https://api.postalpincode.in/pincode/$pin", 3500)
        if (!r.ok) return null
        val offices = parse(r.body) ?: return null
        return build(pin, offices)
    }

    internal fun build(pin: String, offices: List<Office>): InspectResult {
        val first = offices.first()
        val where = listOf(first.district, first.state).filter { it.isNotBlank() }.joinToString(", ")
        val rows = mutableListOf<Pair<String, String>>()
        rows.add("Area" to first.name)
        if (first.district.isNotBlank()) rows.add("District" to first.district)
        if (first.state.isNotBlank()) rows.add("State" to first.state)
        if (offices.size > 1) {
            val names = offices.map { it.name }.distinct()
            rows.add("Post offices" to (names.take(4).joinToString(", ") + if (names.size > 4) " and ${names.size - 4} more" else ""))
        }
        return InspectResult(
            domain = "PIN",
            pill = first.district.ifBlank { pin }.take(14),
            title = pin,
            subtitle = where,
            cardTitle = "PIN code",
            cardText = "$pin, $where",
            rows = rows,
            copyText = pin,
            iconName = null,
            timeoutSeconds = 20
        )
    }
}
