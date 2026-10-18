package com.microtag.inspect

/**
 * Which reading wins when a piece of text could be several things ("1.5" is a number and maybe a measure,
 * "tea" is a word and maybe a food). Pure logic, no Android.
 *
 *  - Readings only compete when their scores are close ([BAND]). A clear winner (a unit, a UPI id) is never overruled.
 *  - Among those that compete, the user's order decides, top of the list first.
 *  - Choosing a different reading on a card is a quiet vote. [LEARN_AT] votes the same way move the winner just above
 *    the loser in the list.
 *  - Moving a reading by hand locks it against the one it passed: learning never undoes what the user placed.
 */
data class PriorityState(
    val order: List<String> = PriorityLogic.DEFAULT,
    /** "a>b": the user put a above b. */
    val locks: Set<String> = emptySet(),
    /** "a>b" -> how many times a was chosen over b. */
    val votes: Map<String, Int> = emptyMap()
)

object PriorityLogic {
    const val BAND = 0.2
    const val LEARN_AT = 2
    const val MAX_ALTS = 3

    val DEFAULT = listOf(
        "units", "dimensions", "money", "date", "timezone", "math", "colour", "url", "upi", "ifsc",
        "pin", "food", "number", "define", "entity", "reading", "barcode"
    )

    /** Several detectors can share a reading; this is the name the list uses. */
    fun kind(candidateName: String): String = when (candidateName) {
        "colour-name" -> "colour"
        "number-words" -> "number"
        "dimension-standard" -> "dimensions"
        else -> candidateName
    }

    fun label(kind: String): String = when (kind) {
        "units" -> "Unit conversion"
        "dimensions" -> "Dimensions"
        "money" -> "Money"
        "date" -> "Date"
        "timezone" -> "Time zone"
        "math" -> "Maths"
        "colour" -> "Colour"
        "url" -> "Link"
        "upi" -> "UPI id"
        "ifsc" -> "IFSC code"
        "pin" -> "Pincode"
        "food" -> "Food"
        "number" -> "Number facts"
        "define" -> "Definition"
        "entity" -> "Wikipedia"
        "reading" -> "Reading"
        "barcode" -> "Barcode"
        else -> kind.replaceFirstChar { it.uppercase() }
    }

    private fun rank(order: List<String>, kind: String): Int {
        val i = order.indexOf(kind)
        return if (i < 0) order.size else i
    }

    /** Candidates best first: the close contenders in the user's order, then the rest by score. */
    fun arrange(cands: List<Candidate>, s: PriorityState): List<Candidate> {
        if (cands.size < 2) return cands
        val byScore = cands.sortedByDescending { it.score }
        val top = byScore.first().score
        val band = byScore.filter { it.score >= top - BAND }
        val rest = byScore.filter { it.score < top - BAND }
        // sortedBy is stable: the same kind keeps its score order.
        return band.sortedBy { rank(s.order, kind(it.name)) } + rest
    }

    /** The other readings that were close contenders for [chosen], by kind, in the order they would have won. Max [MAX_ALTS]. */
    fun alternatives(arranged: List<Candidate>, chosen: String, s: PriorityState): List<String> {
        if (arranged.isEmpty()) return emptyList()
        val top = arranged.maxOf { it.score }
        val used = kind(chosen)
        return arranged.filter { it.score >= top - BAND }
            .map { kind(it.name) }
            .filter { it != used }
            .distinct()
            .take(MAX_ALTS)
    }

    /** The user picked [winner] over [loser] on a card. Returns the new state. */
    fun vote(s: PriorityState, winner: String, loser: String): PriorityState {
        if (winner == loser) return s
        if ("$loser>$winner" in s.locks) return s
        val key = "$winner>$loser"
        val n = (s.votes[key] ?: 0) + 1
        if (n < LEARN_AT) return s.copy(votes = s.votes + (key to n))
        val votes = s.votes - key - "$loser>$winner"
        val order = s.order.toMutableList()
        if (winner !in order) order.add(winner)
        if (loser !in order) order.add(loser)
        if (order.indexOf(winner) > order.indexOf(loser)) {
            order.remove(winner)
            order.add(order.indexOf(loser), winner)
        }
        return s.copy(order = order, votes = votes)
    }

    /** Move [kind] one place up (delta -1) or down (+1) by hand. The pair it passed is locked. */
    fun move(s: PriorityState, kind: String, delta: Int): PriorityState {
        val order = s.order.toMutableList()
        val i = order.indexOf(kind)
        val j = i + delta
        if (i < 0 || j !in order.indices) return s
        val other = order[j]
        order[j] = kind
        order[i] = other
        // After the swap, whichever is now higher is the one the user put above the other.
        val (above, below) = if (delta < 0) kind to other else other to kind
        val locks = s.locks - "$below>$above" + "$above>$below"
        val votes = s.votes - "$kind>$other" - "$other>$kind"
        return s.copy(order = order, locks = locks, votes = votes)
    }

    fun reset(): PriorityState = PriorityState()

    /** A kind missing from the list (a detector added later) is appended, so the list screen always shows everything. */
    fun complete(s: PriorityState): PriorityState {
        val missing = DEFAULT.filter { it !in s.order }
        return if (missing.isEmpty()) s else s.copy(order = s.order + missing)
    }

    // ------------------------------------------------------------ storage

    fun encode(s: PriorityState): String =
        "o=" + s.order.joinToString(",") + "\nl=" + s.locks.sorted().joinToString(",") +
            "\nv=" + s.votes.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(text: String?): PriorityState {
        if (text.isNullOrBlank()) return PriorityState()
        var order = DEFAULT
        var locks = emptySet<String>()
        var votes = emptyMap<String, Int>()
        for (line in text.lines()) {
            val v = line.substringAfter('=', "")
            when {
                line.startsWith("o=") -> order = v.split(',').filter { it.isNotBlank() }.distinct().ifEmpty { DEFAULT }
                line.startsWith("l=") -> locks = v.split(',').filter { '>' in it }.toSet()
                line.startsWith("v=") -> votes = v.split(',').mapNotNull {
                    val k = it.substringBeforeLast(':', "")
                    val n = it.substringAfterLast(':', "").toIntOrNull()
                    if (k.isBlank() || n == null) null else k to n
                }.toMap()
            }
        }
        return complete(PriorityState(order, locks, votes))
    }
}

/** The live state, loaded from and saved to the same store as the pill choices. */
object PriorityStore {
    private const val KEY = "PRIORITY"
    @Volatile private var cached: PriorityState? = null

    var state: PriorityState
        get() {
            cached?.let { return it }
            val loaded = PriorityLogic.decode(PillConfig.store?.read(KEY))
            if (PillConfig.store != null) cached = loaded
            return loaded
        }
        private set(v) {
            cached = v
            PillConfig.store?.write(KEY, PriorityLogic.encode(v))
        }

    fun vote(winnerKind: String, loserKind: String) {
        state = PriorityLogic.vote(state, winnerKind, loserKind)
    }

    fun move(kind: String, delta: Int) {
        state = PriorityLogic.move(state, kind, delta)
    }

    fun reset() {
        state = PriorityLogic.reset()
    }
}
