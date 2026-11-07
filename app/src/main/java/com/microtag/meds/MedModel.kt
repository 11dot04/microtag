package com.microtag.meds

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Medication reminders. Pure Kotlin (java.time only), tested on the JVM; the alarms and chips are in MedScheduler.
 * Everything stays on the phone. A dose is "this medicine, on this day, at this minute"; what happened to it
 * (taken, skipped, snoozed, missed) is kept in a small log keyed by that.
 */
data class Medication(
    val id: String,
    val name: String,
    val dose: String = "",
    /** Minutes after local midnight, one per daily dose: 8:00 is 480. */
    val minutes: List<Int>,
    /** 1 = Monday … 7 = Sunday. Empty means every day. */
    val weekdays: Set<Int> = emptySet(),
    /** 1 = daily, 2 = every other day … counted from [startDay]. */
    val everyNDays: Int = 1,
    val startDay: Long,
    val endDay: Long? = null,
    val note: String = "",
    /** Show "Meds" on the chip instead of the name, for a screen other people can see. */
    val discreet: Boolean = false,
    val active: Boolean = true,
    /** Pills left, or null when not tracked. */
    val pillsLeft: Int? = null,
    val perDose: Int = 1
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("dose", dose)
        put("min", JSONArray().also { a -> minutes.forEach { a.put(it) } })
        put("wd", JSONArray().also { a -> weekdays.sorted().forEach { a.put(it) } })
        put("n", everyNDays); put("start", startDay); endDay?.let { put("end", it) }
        put("note", note); put("disc", discreet); put("on", active)
        pillsLeft?.let { put("left", it) }; put("per", perDose)
    }

    companion object {
        fun fromJson(o: JSONObject): Medication? {
            val id = o.optString("id"); val name = o.optString("name")
            val m = o.optJSONArray("min") ?: return null
            if (id.isBlank() || name.isBlank() || m.length() == 0) return null
            val minutes = (0 until m.length()).map { m.optInt(it, -1) }.filter { it in 0..1439 }.distinct().sorted()
            if (minutes.isEmpty()) return null
            val wd = o.optJSONArray("wd")
            return Medication(
                id = id, name = name, dose = o.optString("dose"), minutes = minutes,
                weekdays = (0 until (wd?.length() ?: 0)).map { wd!!.optInt(it, 0) }.filter { it in 1..7 }.toSet(),
                everyNDays = o.optInt("n", 1).coerceAtLeast(1), startDay = o.optLong("start", 0L),
                endDay = if (o.has("end")) o.optLong("end", 0L) else null,
                note = o.optString("note"), discreet = o.optBoolean("disc", false), active = o.optBoolean("on", true),
                pillsLeft = if (o.has("left")) o.optInt("left", 0) else null, perDose = o.optInt("per", 1).coerceAtLeast(1)
            )
        }
    }
}

data class Dose(val med: Medication, val day: Long, val minute: Int) {
    val key: String get() = "${med.id}@$day:$minute"
    fun atMs(zone: ZoneId): Long = LocalDate.ofEpochDay(day).atStartOfDay(zone).plusMinutes(minute.toLong()).toInstant().toEpochMilli()
}

enum class DoseState { TAKEN, SKIPPED, SNOOZED, MISSED }

/** What happened to a dose. [until] is the snooze end. */
data class DoseEntry(val state: DoseState, val atMs: Long, val until: Long = 0L)

enum class DoseStatus { UPCOMING, DUE, LATE, SNOOZED, TAKEN, SKIPPED, MISSED }

enum class EventType { DUE, REPEAT, MISSED }
data class Event(val atMs: Long, val type: EventType, val dose: Dose)

object MedLogic {
    const val LATE_AFTER_MIN = 30
    const val MISSED_AFTER_MIN = 240
    const val REPEAT_EVERY_MIN = 15
    const val MAX_REPEATS = 3
    const val SNOOZE_MIN = 10
    const val REFILL_DAYS = 5

    fun today(nowMs: Long, zone: ZoneId): Long = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toEpochDay()

    /** Is [med] due on [day] at all (weekday, every-N-days, course dates)? */
    fun activeOn(med: Medication, day: Long): Boolean {
        if (!med.active || day < med.startDay) return false
        if (med.endDay != null && day > med.endDay) return false
        if (med.weekdays.isNotEmpty() && LocalDate.ofEpochDay(day).dayOfWeek.value !in med.weekdays) return false
        if (med.everyNDays > 1 && (day - med.startDay) % med.everyNDays != 0L) return false
        return true
    }

    fun dosesOn(med: Medication, day: Long): List<Dose> = if (activeOn(med, day)) med.minutes.map { Dose(med, day, it) } else emptyList()

    /** Every dose with its time in [fromMs, toMs], earliest first. */
    fun between(meds: List<Medication>, fromMs: Long, toMs: Long, zone: ZoneId): List<Dose> {
        val d0 = today(fromMs, zone) - 1; val d1 = today(toMs, zone) + 1
        return (d0..d1).flatMap { d -> meds.flatMap { dosesOn(it, d) } }
            .filter { val t = it.atMs(zone); t in fromMs..toMs }
            .sortedBy { it.atMs(zone) }
    }

    /** A dose becomes missed at the earlier of [MISSED_AFTER_MIN] and the medicine's next dose. */
    private fun missedAt(d: Dose, zone: ZoneId): Long {
        val base = d.atMs(zone) + MISSED_AFTER_MIN * 60_000L
        val next = d.med.minutes.firstOrNull { it > d.minute }?.let { Dose(d.med, d.day, it).atMs(zone) }
        return if (next != null) minOf(base, next) else base
    }

    fun status(d: Dose, log: Map<String, DoseEntry>, nowMs: Long, zone: ZoneId): DoseStatus {
        val e = log[d.key]
        when (e?.state) {
            DoseState.TAKEN -> return DoseStatus.TAKEN
            DoseState.SKIPPED -> return DoseStatus.SKIPPED
            DoseState.MISSED -> return DoseStatus.MISSED
            DoseState.SNOOZED -> if (nowMs < e.until) return DoseStatus.SNOOZED
            null -> {}
        }
        val at = d.atMs(zone)
        return when {
            nowMs < at -> DoseStatus.UPCOMING
            nowMs >= missedAt(d, zone) -> DoseStatus.MISSED
            nowMs >= at + LATE_AFTER_MIN * 60_000L -> DoseStatus.LATE
            else -> DoseStatus.DUE
        }
    }

    /** Doses to show a chip for right now: due or late, not snoozed. Earliest first. */
    fun pending(meds: List<Medication>, log: Map<String, DoseEntry>, nowMs: Long, zone: ZoneId): List<Dose> =
        between(meds, nowMs - MISSED_AFTER_MIN * 60_000L, nowMs, zone).filter {
            val s = status(it, log, nowMs, zone); s == DoseStatus.DUE || s == DoseStatus.LATE
        }

    /** The next moment something needs doing: a dose coming due, a repeat of the reminder, a dose turning missed, a snooze ending. */
    fun nextEvent(meds: List<Medication>, log: Map<String, DoseEntry>, nowMs: Long, zone: ZoneId): Event? {
        val events = ArrayList<Event>()
        for (d in between(meds, nowMs - MISSED_AFTER_MIN * 60_000L, nowMs + 36 * 3_600_000L, zone)) {
            val e = log[d.key]
            if (e != null && e.state != DoseState.SNOOZED) continue
            val at = d.atMs(zone)
            val miss = missedAt(d, zone)
            if (e?.state == DoseState.SNOOZED && e.until > nowMs) { events.add(Event(e.until, EventType.DUE, d)); continue }
            events.add(Event(at, EventType.DUE, d))
            for (k in 1..MAX_REPEATS) { val t = at + k * REPEAT_EVERY_MIN * 60_000L; if (t < miss) events.add(Event(t, EventType.REPEAT, d)) }
            events.add(Event(miss, EventType.MISSED, d))
        }
        return events.filter { it.atMs > nowMs }.minWithOrNull(compareBy({ it.atMs }, { it.type.ordinal }))
    }

    // ---- adherence

    /** Taken doses over taken + missed, for the [days] days up to and including yesterday. Null when there is nothing to judge. */
    fun adherence(meds: List<Medication>, log: Map<String, DoseEntry>, nowMs: Long, zone: ZoneId, days: Int = 30): Int? {
        val t = today(nowMs, zone)
        var taken = 0; var total = 0
        for (day in (t - days)..t) for (m in meds) for (d in dosesOn(m, day)) {
            when (status(d, log, nowMs, zone)) {
                DoseStatus.TAKEN -> { taken++; total++ }
                DoseStatus.MISSED -> total++
                else -> {}
            }
        }
        return if (total == 0) null else (taken * 100.0 / total).toInt()
    }

    /** Consecutive days, ending today if it is already complete or yesterday if not, on which every dose was taken. */
    fun streakDays(meds: List<Medication>, log: Map<String, DoseEntry>, nowMs: Long, zone: ZoneId): Int {
        val t = today(nowMs, zone)
        fun complete(day: Long): Boolean? {
            val doses = meds.flatMap { dosesOn(it, day) }
            if (doses.isEmpty()) return null
            return doses.all { status(it, log, nowMs, zone) == DoseStatus.TAKEN }
        }
        var day = if (complete(t) == true) t else t - 1
        var n = 0
        var guard = 0
        while (guard++ < 400) {
            when (complete(day)) {
                true -> n++
                false -> return n
                null -> {}      // nothing scheduled that day: neither breaks nor adds
            }
            day--
            if (day < meds.minOfOrNull { it.startDay } ?: t) break
        }
        return n
    }

    // ---- supply

    /** Days of pills left at the usual pace, or null when pills are not tracked. */
    fun daysOfSupply(m: Medication): Int? {
        val left = m.pillsLeft ?: return null
        val perDay = m.minutes.size * m.perDose * ((if (m.weekdays.isEmpty()) 7 else m.weekdays.size) / 7.0) / m.everyNDays
        return if (perDay <= 0) null else (left / perDay).toInt()
    }

    fun refillDue(m: Medication): Boolean = m.active && (daysOfSupply(m) ?: Int.MAX_VALUE) <= REFILL_DAYS

    // ---- wording

    /** The chip: one name, or a count when several are due together; "Meds" when discreet. Cut to whole words. */
    fun pill(due: List<Dose>, limit: Int = 12): String {
        if (due.isEmpty()) return "Meds"
        if (due.size > 1) return "${due.size} meds"
        val m = due[0].med
        return if (m.discreet) "Meds" else com.microtag.listener.PillText.cut(m.name, limit)
    }

    fun title(due: List<Dose>): String = when {
        due.isEmpty() -> "Medication"
        due.size > 1 -> "${due.size} medicines to take"
        due[0].med.discreet -> "Time for your medication"
        else -> "Take ${listOf(due[0].med.name, due[0].med.dose).filter { it.isNotBlank() }.joinToString(" ")}"
    }

    fun content(due: List<Dose>, zone: ZoneId): String {
        if (due.isEmpty()) return ""
        val fmt = { d: Dose -> "%02d:%02d".format(d.minute / 60, d.minute % 60) }
        return if (due.size == 1) listOfNotNull(fmt(due[0]), due[0].med.note.takeIf { it.isNotBlank() && !due[0].med.discreet }).joinToString(" · ")
        else due.joinToString(", ") { if (it.med.discreet) "Meds" else it.med.name } + " · " + fmt(due[0])
    }
}

/**
 * The medicines and their log on disk. meds.json is replaced atomically; the log is append-only (last line for a dose
 * wins) and trimmed to about 120 days. A damaged line costs only itself; an unreadable meds file is set aside, never erased.
 */
class MedBook(private val dir: File) {
    companion object {
        const val MEDS_FILE = "medications.json"
        const val LOG_FILE = "medication_log.tsv"
        private const val KEEP_DAYS = 120
        private const val TRIM_AT_LINES = 6000
    }

    private val medsFile = File(dir, MEDS_FILE)
    private val logFile = File(dir, LOG_FILE)
    private val lock = Any()
    @Volatile private var meds: List<Medication> = loadMeds()
    private val log = HashMap<String, DoseEntry>().also { loadLog(it) }

    private fun loadMeds(): List<Medication> {
        if (!medsFile.exists()) return emptyList()
        return runCatching {
            val a = JSONArray(medsFile.readText())
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Medication::fromJson) }
        }.getOrElse {
            runCatching { medsFile.renameTo(File(dir, "$MEDS_FILE.damaged")) }
            emptyList()
        }
    }

    private fun loadLog(into: HashMap<String, DoseEntry>) {
        if (!logFile.exists()) return
        runCatching {
            logFile.forEachLine { raw ->
                val f = raw.trim().split('\t')
                if (f.size == 4) {
                    val st = DoseState.values().firstOrNull { it.name == f[1] }; val at = f[2].toLongOrNull(); val un = f[3].toLongOrNull()
                    if (st != null && at != null && un != null) into[f[0]] = DoseEntry(st, at, un)
                }
            }
        }
    }

    fun all(): List<Medication> = meds
    fun get(id: String): Medication? = meds.firstOrNull { it.id == id }
    fun log(): Map<String, DoseEntry> = synchronized(lock) { HashMap(log) }

    fun validate(m: Medication): String? = when {
        m.name.isBlank() -> "Give it a name"
        m.minutes.isEmpty() -> "Add at least one time"
        m.minutes.any { it !in 0..1439 } -> "A time is not valid"
        m.endDay != null && m.endDay < m.startDay -> "The course ends before it starts"
        else -> null
    }

    fun upsert(m: Medication): String? {
        validate(m)?.let { return it }
        synchronized(lock) {
            val l = meds.toMutableList(); val i = l.indexOfFirst { it.id == m.id }
            if (i >= 0) l[i] = m else l.add(m)
            meds = l; saveMeds()
        }
        return null
    }

    fun delete(id: String) = synchronized(lock) { meds = meds.filter { it.id != id }; saveMeds() }

    private fun saveMeds() {
        val tmp = File(dir, "$MEDS_FILE.tmp")
        val json = JSONArray().also { a -> meds.forEach { a.put(it.toJson()) } }.toString()
        runCatching { tmp.writeText(json); if (!tmp.renameTo(medsFile)) { medsFile.writeText(json); tmp.delete() } }
    }

    /** Records what happened to [d]. Taking a dose counts a pill off when pills are tracked. */
    fun mark(d: Dose, state: DoseState, nowMs: Long, until: Long = 0L) = synchronized(lock) {
        val before = log[d.key]
        log[d.key] = DoseEntry(state, nowMs, until)
        runCatching { java.io.FileOutputStream(logFile, true).use { it.write("\n${d.key}\t${state.name}\t$nowMs\t$until".toByteArray()) } }
        if (state == DoseState.TAKEN && before?.state != DoseState.TAKEN) {
            get(d.med.id)?.let { m -> m.pillsLeft?.let { upsertQuiet(m.copy(pillsLeft = (it - m.perDose).coerceAtLeast(0))) } }
        }
        if (log.size > TRIM_AT_LINES) trim(nowMs)
    }

    private fun upsertQuiet(m: Medication) {
        val l = meds.toMutableList(); val i = l.indexOfFirst { it.id == m.id }
        if (i >= 0) { l[i] = m; meds = l; saveMeds() }
    }

    /** Undo a mistaken tap. */
    fun unmark(d: Dose, nowMs: Long) = synchronized(lock) {
        val before = log.remove(d.key)
        runCatching { java.io.FileOutputStream(logFile, true).use { it.write("\n${d.key}\tSNOOZED\t$nowMs\t0".toByteArray()) } }
        if (before?.state == DoseState.TAKEN) get(d.med.id)?.let { m -> m.pillsLeft?.let { upsertQuiet(m.copy(pillsLeft = it + m.perDose)) } }
    }

    private fun trim(nowMs: Long) {
        val cutoff = nowMs - KEEP_DAYS * 86_400_000L
        log.entries.removeAll { it.value.atMs < cutoff }
        val tmp = File(dir, "$LOG_FILE.tmp")
        runCatching {
            tmp.bufferedWriter().use { w -> log.forEach { (k, e) -> w.write("\n$k\t${e.state.name}\t${e.atMs}\t${e.until}") } }
            tmp.renameTo(logFile)
        }
    }
}
