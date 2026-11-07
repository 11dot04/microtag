package com.microtag.ui

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.meds.DoseState
import com.microtag.meds.DoseStatus
import com.microtag.meds.MedLogic
import com.microtag.meds.MedScheduler
import com.microtag.meds.MedStore
import com.microtag.meds.Medication
import com.microtag.meds.MedicationReceiver
import kotlinx.coroutines.delay

private fun medClock(context: Context, minute: Int): String {
    val h = minute / 60; val m = minute % 60
    return if (android.text.format.DateFormat.is24HourFormat(context)) "%02d:%02d".format(h, m)
    else "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

/** Medicine reminders: today's doses, and the medicines themselves. Everything stays on this phone. */
@Composable
fun MedsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val book = remember { MedStore.book(context) }
    var version by remember { mutableIntStateOf(0) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var editing by remember { mutableStateOf<Medication?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    fun changed() {
        version++
        MedScheduler.reschedule(context)
        MedicationReceiver.refresh(context)
    }

    if (editing != null || adding) {
        MedEditorPage(editing, onDone = { editing = null; adding = false; if (it) changed() })
        return
    }
    BackHandler(onBack = onBack)

    val zone = MedStore.zone()
    val meds = remember(version) { book.all() }
    val log = remember(version, now) { book.log() }
    val today = MedLogic.today(now, zone)
    val doses = remember(version, now) { meds.flatMap { MedLogic.dosesOn(it, today) }.sortedBy { it.minute } }
    val streak = remember(version, now) { MedLogic.streakDays(meds, log, now, zone) }
    val adherence = remember(version, now) { MedLogic.adherence(meds, log, now, zone) }
    val exactMissing = remember(now) {
        Build.VERSION.SDK_INT >= 31 && meds.isNotEmpty() &&
            !(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
    }

    SubScreen(onBack, spacing = 10.dp) {
        FormGap(12)
        FormTitle("Meds")
        FormCaption("Reminders show as a chip when a dose is due, with Taken, Snooze and Skip on it. They ignore quiet hours, and nothing leaves this phone.")
        FormButton("Add medicine", { adding = true })

        if (exactMissing) {
            FormPlate {
                Column {
                    FormCaption("Android may delay reminders unless exact alarms are allowed.", color = TagColors.White)
                    FormGap(8)
                    FormButton("Allow exact alarms", {
                        runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }, filled = false)
                }
            }
        }

        if (meds.isNotEmpty()) {
            FormPlate {
                Row(Modifier.fillMaxWidth()) {
                    MedStat("$streak", if (streak == 1) "day in a row" else "days in a row", Modifier.weight(1f))
                    MedStat(adherence?.let { "$it%" } ?: "—", "taken, last 30 days", Modifier.weight(1f))
                }
            }
        }

        FormLabel("Today")
        if (doses.isEmpty()) FormCaption(if (meds.isEmpty()) "Nothing set up yet." else "Nothing to take today.")
        doses.forEach { d ->
            val st = MedLogic.status(d, log, now, zone)
            FormPlate {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(medClock(context, d.minute), modifier = Modifier.width(86.dp), style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (st == DoseStatus.DUE || st == DoseStatus.LATE) TagColors.AccentText else TagColors.Outline))
                        Column(Modifier.weight(1f)) {
                            Text(d.med.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White))
                            Text(listOf(d.med.dose, statusWord(st)).filter { it.isNotBlank() }.joinToString(" · "), style = FormMono)
                        }
                    }
                    if (st != DoseStatus.TAKEN && st != DoseStatus.SKIPPED) {
                        FormGap(8)
                        Row {
                            FormButton("Taken", { book.mark(d, DoseState.TAKEN, System.currentTimeMillis()); changed() })
                            FormGapW()
                            FormButton("Skip", { book.mark(d, DoseState.SKIPPED, System.currentTimeMillis()); changed() }, filled = false)
                        }
                    } else {
                        FormGap(6)
                        FormButton("Undo", { book.unmark(d, System.currentTimeMillis()); changed() }, filled = false)
                    }
                }
            }
        }

        FormLabel("Medicines")
        meds.forEach { m ->
            FormPlate(onClick = { editing = m }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(listOf(m.name, m.dose).filter { it.isNotBlank() }.joinToString(" "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White))
                        val sup = MedLogic.daysOfSupply(m)
                        Text(
                            m.minutes.joinToString(", ") { medClock(context, it) } + (sup?.let { " · $it d of pills" } ?: ""),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = FormMono.copy(color = if (MedLogic.refillDue(m)) TagColors.Signal else TagColors.Outline)
                        )
                    }
                    TagSwitch(m.active, { book.upsert(m.copy(active = it)); changed() })
                }
            }
        }
        FormGap(28)
    }
}

@Composable
private fun MedStat(big: String, small: String, modifier: Modifier) {
    Column(modifier) {
        Text(big, style = TextStyle(fontFamily = TagFonts.display, fontWeight = FontWeight.Light, fontSize = 34.sp, color = TagColors.White, fontSynthesis = TagFonts.NoSynthesis))
        Text(small, style = FormMono)
    }
}

private fun statusWord(s: DoseStatus) = when (s) {
    DoseStatus.UPCOMING -> "later"
    DoseStatus.DUE -> "due now"
    DoseStatus.LATE -> "running late"
    DoseStatus.SNOOZED -> "snoozed"
    DoseStatus.TAKEN -> "taken"
    DoseStatus.SKIPPED -> "skipped"
    DoseStatus.MISSED -> "missed"
}

// ------------------------------------------------------------------ editor

private val MED_DAYS = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

@Composable
private fun MedEditorPage(existing: Medication?, onDone: (changed: Boolean) -> Unit) {
    val context = LocalContext.current
    val book = remember { MedStore.book(context) }
    BackHandler { onDone(false) }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var dose by remember { mutableStateOf(existing?.dose ?: "") }
    val times = remember { mutableStateListOf<Int>().also { it.addAll(existing?.minutes ?: listOf(480)) } }
    val days = remember { mutableStateListOf<Int>().also { it.addAll(existing?.weekdays ?: emptySet()) } }
    var every by remember { mutableIntStateOf(existing?.everyNDays ?: 1) }
    val startDay = existing?.startDay ?: MedLogic.today(System.currentTimeMillis(), MedStore.zone())
    var course by remember { mutableIntStateOf(existing?.endDay?.let { (it - startDay + 1).toInt() } ?: 0) }
    var pills by remember { mutableStateOf(existing?.pillsLeft?.toString() ?: "") }
    var perDose by remember { mutableStateOf((existing?.perDose ?: 1).toString()) }
    var discreet by remember { mutableStateOf(existing?.discreet ?: false) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var problem by remember { mutableStateOf<String?>(null) }

    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h * 60 + m) }, initial / 60, initial % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
    }

    SubScreen({ onDone(false) }, spacing = 10.dp) {
        FormGap(12)
        FormTitle(if (existing == null) "New" else "Edit")
        FormField(name, { name = it }, "Name")
        FormField(dose, { dose = it }, "Dose, e.g. 500 mg")

        FormLabel("Times")
        times.sorted().forEach { t ->
            FormPlate(onClick = { pickTime(t) { n -> val i = times.indexOf(t); if (i >= 0) times[i] = n } }) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(medClock(context, t), Modifier.weight(1f), style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TagColors.White))
                    if (times.size > 1) FormButton("Remove", { times.remove(t) }, filled = false)
                }
            }
        }
        FormButton("Add a time", { pickTime(1200) { n -> if (n !in times) times.add(n) } }, filled = false)

        FormLabel("Days")
        FormWrap { MED_DAYS.forEach { (n, l) -> FormChoice(l, n in days, { if (n in days) days.remove(n) else days.add(n) }) } }
        FormCaption("None picked means every day.")
        FormLabel("Repeat")
        FormWrap { listOf(1 to "Daily", 2 to "Every 2 days", 3 to "Every 3 days").forEach { (n, l) -> FormChoice(l, every == n, { every = n }) } }
        FormLabel("Course")
        FormWrap {
            (listOf(0, 7, 14, 30) + listOfNotNull(course.takeIf { it > 0 && it !in listOf(7, 14, 30) })).forEach { n ->
                FormChoice(if (n == 0) "No end" else "$n days", course == n, { course = n })
            }
        }

        FormLabel("Pills")
        Row(verticalAlignment = Alignment.CenterVertically) {
            FormField(pills, { pills = it.filter(Char::isDigit) }, "Pills left (optional)", Modifier.weight(1f), number = true)
            FormGapW()
            FormField(perDose, { perDose = it.filter(Char::isDigit) }, "Per dose", Modifier.width(110.dp), number = true)
        }
        FormCaption("With pills counted, TAG tells you when about 5 days are left.")

        FormLabel("Privacy")
        FormSwitch("Discreet", "The chip says “Meds” instead of the name.", discreet) { discreet = it }
        if (!discreet) FormField(note, { note = it }, "Note, e.g. with food")

        problem?.let { FormNotice(it) }
        FormGap(6)
        Row {
            FormButton("Save", {
                val m = Medication(
                    id = existing?.id ?: java.util.UUID.randomUUID().toString().take(8), name = name.trim(), dose = dose.trim(),
                    minutes = times.distinct().sorted(), weekdays = days.toSet(), everyNDays = every, startDay = startDay,
                    endDay = if (course > 0) startDay + course - 1 else null, note = if (discreet) "" else note.trim(), discreet = discreet,
                    active = existing?.active ?: true, pillsLeft = pills.toIntOrNull(), perDose = perDose.toIntOrNull()?.coerceAtLeast(1) ?: 1
                )
                val e = book.upsert(m)
                if (e == null) onDone(true) else problem = e
            })
            if (existing != null) { FormGapW(); FormButton("Delete", { book.delete(existing.id); onDone(true) }, filled = false, danger = true) }
        }
        FormGap(28)
    }
}
