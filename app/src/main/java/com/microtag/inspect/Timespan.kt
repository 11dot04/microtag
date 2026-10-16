package com.microtag.inspect

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** One line of the timespan drawing: a month (or a year, for long spans) and the part of it the span covers. */
data class SpanRow(
    val label: String,
    /** Days of this month or year inside the span, both ends counted. */
    val days: Int,
    /** Days into the month or year where the span starts here (0 = from the first day). */
    val offset: Int,
    /** Length of the month or year, the line's full scale. */
    val capacity: Int,
    val first: Boolean,
    val last: Boolean
)

/** Pure helpers behind the date and time cards' drawings. */
object Timespan {

    /**
     * The months between two dates, one row each. Lines are proportional: a full 31-day month is the longest,
     * a part-month is shorter and starts where the span starts. Beyond [maxRows] months it switches to years.
     */
    fun rows(epochA: Long, epochB: Long, maxRows: Int = 12): List<SpanRow> {
        val a = LocalDate.ofEpochDay(minOf(epochA, epochB))
        val b = LocalDate.ofEpochDay(maxOf(epochA, epochB))
        val startYm = YearMonth.from(a)
        val endYm = YearMonth.from(b)
        val monthCount = (endYm.year - startYm.year) * 12 + (endYm.monthValue - startYm.monthValue) + 1
        val out = ArrayList<SpanRow>()
        if (monthCount <= maxRows) {
            val crossesYear = startYm.year != endYm.year
            var ym = startYm
            while (!ym.isAfter(endYm)) {
                val from = if (ym == startYm) a else ym.atDay(1)
                val to = if (ym == endYm) b else ym.atEndOfMonth()
                val name = ym.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase(Locale.ROOT)
                // The year shows only where it changes (January), so OCT NOV DEC JAN ’27 reads cleanly.
                val label = if (crossesYear && ym != startYm && ym.monthValue == 1) "$name ’${(ym.year % 100).toString().padStart(2, '0')}" else name
                out.add(SpanRow(label, (to.toEpochDay() - from.toEpochDay() + 1).toInt(), from.dayOfMonth - 1, 31, ym == startYm, ym == endYm))
                ym = ym.plusMonths(1)
            }
        } else {
            var y = a.year
            while (y <= b.year && out.size < maxRows) {
                val from = if (y == a.year) a else LocalDate.of(y, 1, 1)
                val to = if (y == b.year) b else LocalDate.of(y, 12, 31)
                out.add(SpanRow(y.toString(), (to.toEpochDay() - from.toEpochDay() + 1).toInt(), from.dayOfYear - 1, 366, y == a.year, y == b.year))
                y++
            }
        }
        return out
    }

    /** Difference between two clock readings of the same instant, in minutes, folded into -12h..+12h. */
    fun gapMinutes(sourceMinuteOfDay: Int, localMinuteOfDay: Int): Int =
        ((localMinuteOfDay - sourceMinuteOfDay + 1440 + 720) % 1440) - 720

    fun gapText(gap: Int): String {
        if (gap == 0) return "same time"
        val sign = if (gap > 0) "+" else "−"
        val a = abs(gap)
        val h = a / 60
        val m = a % 60
        return sign + (if (h > 0) "${h}h" else "") + (if (m > 0) (if (h > 0) " " else "") + "${m}m" else "")
    }
}
