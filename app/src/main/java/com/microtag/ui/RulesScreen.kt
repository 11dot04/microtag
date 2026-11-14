package com.microtag.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import com.microtag.R
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.ai.Ai
import com.microtag.ai.AiRuleHelper
import com.microtag.rules.AppIcons
import com.microtag.rules.NotificationSamples
import com.microtag.rules.Redactor
import com.microtag.rules.RuleExport
import com.microtag.rules.RuleSuggester
import com.microtag.rules.UserRule
import com.microtag.rules.UserRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A notification to start a rule from, handed over by the Logs screen: the app and the raw title and text. */
class RulePrefill(val pkg: String, val label: String, val title: String, val text: String, val ruleId: String? = null)

private sealed interface RulesPage {
    object Home : RulesPage
    object Pick : RulesPage
    class Build(val pkg: String, val label: String, val rule: UserRule?) : RulesPage
}

private data class PickApp(val pkg: String, val label: String)

/** Rules you write yourself: choose an app, point at what you want from one of its notifications, and it becomes a chip. */
@Composable
fun RulesScreen(onBack: () -> Unit, onOpenAi: () -> Unit, prefill: RulePrefill? = null) {
    val context = LocalContext.current
    // Coming from a log entry, the builder opens straight away on that app and notification.
    var page by remember {
        mutableStateOf<RulesPage>(
            when {
                // "Not this one" from the log: that rule opens with the notification as a must-not-match.
                prefill?.ruleId != null -> UserRules.book(context).all().firstOrNull { it.id == prefill.ruleId }
                    ?.let { RulesPage.Build(it.pkg, it.appLabel, it) } ?: RulesPage.Build(prefill.pkg, prefill.label, null)
                prefill != null -> RulesPage.Build(prefill.pkg, prefill.label, null)
                else -> RulesPage.Home
            }
        )
    }
    var sample by remember { mutableStateOf(prefill) }
    BackHandler {
        when {
            // Back out of a builder opened from Logs goes back to Logs.
            sample != null -> onBack()
            page is RulesPage.Home -> onBack()
            page is RulesPage.Build && (page as RulesPage.Build).rule == null -> page = RulesPage.Pick
            else -> page = RulesPage.Home
        }
    }

    when (val p = page) {
        RulesPage.Home -> RulesHomePage(onBack, onOpenAi, onNew = { page = RulesPage.Pick }, onEdit = { r -> page = RulesPage.Build(r.pkg, r.appLabel, r) })
        RulesPage.Pick -> RuleAppPicker({ page = RulesPage.Home }) { e -> page = RulesPage.Build(e.pkg, e.label, null) }
        is RulesPage.Build -> RuleBuilderPage(p.pkg, p.label, p.rule, sample?.takeIf { it.pkg == p.pkg }, onDone = { sample = null; page = RulesPage.Home })
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun RulesHomePage(onBack: () -> Unit, onOpenAi: () -> Unit, onNew: () -> Unit, onEdit: (UserRule) -> Unit) {
    val context = LocalContext.current
    val book = remember { UserRules.book(context) }
    var version by remember { mutableIntStateOf(0) }
    val rules = remember(version) { book.all() }
    val aiOn = remember(version) { Ai.ready(context) }

    SubScreen(onBack, spacing = 10.dp) {
        FormGap(12)
        FormTitle("Rules")
        FormCaption("Your own rules, per app. When a notification matches, TAG shows what you chose instead of leaving it alone. They run before the built-in ones.")
        Row {
            FormButton("New rule", onNew)
            FormGapW()
            FormButton(if (aiOn) "AI · On" else "AI · Off", onOpenAi, filled = false)
        }
        if (rules.isEmpty()) {
            FormPlate { FormCaption("No rules yet. Pick an app, tap the words you want on the chip, and you are done.") }
        } else {
            rules.forEach { r ->
                FormPlate(onClick = { onEdit(r) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RuleAppGlyph(r.pkg, 36)
                        FormGapW(12)
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                listOf(r.appLabel, r.name).filter { it.isNotBlank() }.joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White)
                            )
                            Text(r.regex, maxLines = 1, overflow = TextOverflow.Ellipsis, style = FormMono)
                            if (r.contributed) Text("Shared", style = FormMono)
                        }
                        TagSwitch(r.enabled, { book.setEnabled(r.id, it); version++ })
                    }
                }
            }
        }
        FormGap(24)
    }
}

// ------------------------------------------------------------------ app picker

@Composable
private fun RuleAppPicker(onBack: () -> Unit, onPick: (PickApp) -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var apps by remember { mutableStateOf<List<PickApp>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val recent = remember { NotificationSamples.apps() }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }.filter { it.first != context.packageName }
                .map { PickApp(it.first, it.second) }.sortedBy { it.label.lowercase() }
        }
    }
    fun labelOf(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label
        ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    SubScreen(onBack, spacing = 10.dp) {
        FormGap(12)
        FormTitle("Which app?")
        FormField(query, { query = it }, "Search apps")
        if (query.isBlank() && recent.isNotEmpty()) {
            FormLabel("Seen lately")
            FormCaption("These sent a notification in the last half hour, so you can build from a real one.")
            recent.take(8).forEach { (pkg, n) -> RuleAppRow(pkg, labelOf(pkg), "$n notification" + if (n == 1) "" else "s") { onPick(PickApp(pkg, labelOf(pkg))) } }
        }
        FormLabel(if (query.isBlank()) "All apps" else "Matches")
        val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        shown.forEach { RuleAppRow(it.pkg, it.label, null) { onPick(it) } }
        if (apps.isEmpty()) FormCaption("Loading apps…")
        FormGap(24)
    }
}

@Composable
private fun RuleAppRow(pkg: String, label: String, sub: String?, onClick: () -> Unit) {
    FormPlate(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            RuleAppGlyph(pkg, 32, colour = true)
            FormGapW(12)
            Column {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White))
                if (sub != null) Text(sub, style = FormMono)
            }
        }
    }
}

/** The app's icon as the chip will draw it: a one-colour glyph, in the ink colour. */
@Composable
private fun RuleAppGlyph(pkg: String, size: Int, colour: Boolean = false) {
    val context = LocalContext.current
    // Rendered off the main thread so a few hundred rows do not stall the list.
    val bmp by produceState<android.graphics.Bitmap?>(null, pkg) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { if (colour) AppIcons.colour(context, pkg) else AppIcons.render(context, pkg) }.getOrNull() }
    }
    if (colour) {
        // The picker shows the app as it really looks, for recognition.
        Box(Modifier.size(size.dp).clip(CircleShape).background(TagColors.Black), contentAlignment = Alignment.Center) {
            bmp?.let { b -> Image(b.asImageBitmap(), null, Modifier.size(size.dp)) }
        }
    } else {
        Box(Modifier.size(size.dp).clip(CircleShape).background(TagColors.Black), contentAlignment = Alignment.Center) {
            bmp?.let { b -> Image(b.asImageBitmap(), null, Modifier.size((size * 0.75f).dp), colorFilter = ColorFilter.tint(TagColors.White)) }
        }
    }
}

// ------------------------------------------------------------------ builder

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RuleBuilderPage(pkg: String, label: String, existing: UserRule?, prefill: RulePrefill?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = remember { UserRules.book(context) }
    BackHandler(onBack = onDone)

    // What this app has sent lately, plus one the person types or pastes.
    val seen = remember {
        // The log entry comes first, so it is the sample that is picked when the builder opens.
        val fromLog = prefill?.let { listOf(AiRuleHelper.Sample(it.title, it.text)) }.orEmpty()
        fromLog + NotificationSamples.recent(pkg).map { AiRuleHelper.Sample(it.title, it.text) }
            .filterNot { s -> prefill != null && s.title == prefill.title && s.text == prefill.text }
    }
    var manualTitle by remember { mutableStateOf("") }
    var manualText by remember { mutableStateOf("") }
    val samples = remember(manualTitle, manualText, seen) {
        seen + if (manualTitle.isNotBlank() || manualText.isNotBlank()) listOf(AiRuleHelper.Sample(manualTitle, manualText)) else emptyList()
    }
    var chosen by remember { mutableIntStateOf(0) }
    val sample = samples.getOrNull(chosen.coerceAtMost((samples.size - 1).coerceAtLeast(0)))
    val picks = remember { mutableStateListOf<String>() }
    // Typing a sample of your own selects it.
    LaunchedEffect(manualTitle.isNotBlank() || manualText.isNotBlank()) {
        if (manualTitle.isNotBlank() || manualText.isNotBlank()) { chosen = seen.size; picks.clear() }
    }

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var regex by remember { mutableStateOf(existing?.regex ?: "") }
    var pill by remember { mutableStateOf(existing?.pill ?: "$1") }
    var field by remember { mutableStateOf(existing?.field ?: UserRule.Field.BOTH) }
    var pillMode by remember { mutableStateOf(existing?.pillMode ?: UserRule.PillMode.FIT) }
    var iconMode by remember { mutableStateOf(existing?.iconMode ?: UserRule.IconMode.APP) }
    var timeout by remember { mutableIntStateOf(existing?.timeoutSeconds ?: 15) }
    var ignoreCase by remember { mutableStateOf(existing?.ignoreCase ?: true) }
    var note by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }

    val aiReady = remember { Ai.ready(context) && Ai.config(context).ruleHelp }
    val aiPills = remember { Ai.ready(context) }

    // The chat with the helper, and the notifications the person said must not match.
    val chat = remember { mutableStateListOf<AiRuleHelper.Turn>() }
    val negatives = remember { mutableStateListOf<AiRuleHelper.Sample>() }
    var message by remember { mutableStateOf("") }
    var moreSamples by remember { mutableStateOf(false) }
    var typeOwn by remember { mutableStateOf(false) }
    var tapOpen by remember { mutableStateOf(!aiReady) }
    var tweakOpen by remember { mutableStateOf(existing != null) }

    fun draft() = UserRule(
        id = existing?.id ?: java.util.UUID.randomUUID().toString().take(8), pkg = pkg, appLabel = label, name = name.trim(),
        regex = regex, pill = pill, field = field, timeoutSeconds = timeout, ignoreCase = ignoreCase, iconMode = iconMode,
        iconName = existing?.iconName ?: "ic_capsule_bolt", pillMode = pillMode, enabled = existing?.enabled ?: true,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(), contributed = existing?.contributed ?: false
    )

    /** One turn of the chat: the message goes with the chat so far, the current rule and the samples. */
    fun ask(text: String) {
        if (asking || text.isBlank()) return
        val before = chat.toList()
        chat.add(AiRuleHelper.Turn(true, text.trim()))
        message = ""; asking = true; problem = null
        val positive = (listOfNotNull(sample) + samples.filter { it !== sample }).filter { s -> negatives.none { it.target == s.target } }.take(5)
        val req = AiRuleHelper.Request(
            label, pkg, positive = positive, negative = negatives.toList(), wanted = text.trim(),
            history = before, current = if (regex.isNotBlank()) regex to pill else null
        )
        scope.launch {
            val a = withContext(Dispatchers.IO) { runCatching { Ai.suggestRule(req) }.getOrNull() }
            asking = false
            when (a) {
                is AiRuleHelper.Answer.Ok -> {
                    regex = a.regex; pill = a.pill; field = UserRule.Field.BOTH; note = null
                    chat.add(AiRuleHelper.Turn(false, a.explain.ifBlank { "Updated the pattern." }))
                }
                is AiRuleHelper.Answer.Failed -> chat.add(AiRuleHelper.Turn(false, "I couldn’t make that work. ${a.message}"))
                null -> chat.add(AiRuleHelper.Turn(false, "I couldn’t reach the AI."))
            }
        }
    }

    // Arriving from a log entry that a rule promoted wrongly: it is a must-not-match, and the helper is asked to fix the rule.
    LaunchedEffect(Unit) {
        if (prefill?.ruleId != null && sample != null) {
            negatives.add(sample)
            if (aiReady) ask("That notification must not match: ${sample.target.take(120)}")
        }
    }

    val rule = draft()
    val err = remember(regex, pill, field, ignoreCase) { if (regex.isBlank()) null else book.validate(rule) }
    val ruleOk = regex.isNotBlank() && err == null
    val previewMode = if (pillMode == UserRule.PillMode.AI) UserRule.PillMode.FIT else pillMode
    val previewText = if (ruleOk) sample?.let { book.preview(rule.copy(pillMode = previewMode), it.title, it.text) } else null

    SubScreen(onDone, spacing = 10.dp) {
        FormGap(12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuleAppGlyph(pkg, 44)
            FormGapW(12)
            Column { ScreenTitleSmall(label); Text(pkg, style = FormMono) }
        }

        // ---- 01 the notification to work from
        StepHeader("01", "Sample", if (seen.isEmpty()) "none seen" else "${seen.size} seen")
        if (sample == null) {
            FormCaption("None seen in the last half hour. Let the app send one, then come back, or type one below.")
        } else {
            FormPlate {
                Column {
                    QuietTag("using this one")
                    FormGap(6)
                    Text(
                        sample.title.ifBlank { "(no title)" }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TagColors.White)
                    )
                    Text(sample.text, maxLines = 5, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, color = TagColors.Outline))
                }
            }
        }
        FormWrap {
            if (samples.size > 1) FormChoice(if (moreSamples) "Hide others" else "Others (${samples.size - 1})", moreSamples, { moreSamples = !moreSamples })
            FormChoice(if (typeOwn) "Hide" else "Type your own", typeOwn, { typeOwn = !typeOwn })
        }
        Reveal(moreSamples && samples.size > 1) {
            samples.forEachIndexed { i, s ->
                if (i != chosen) FormPlate(onClick = { chosen = i; picks.clear(); moreSamples = false }) {
                    Column {
                        Text(s.title.ifBlank { "(no title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White))
                        Text(s.text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline))
                    }
                }
            }
        }
        Reveal(typeOwn) {
            FormField(manualTitle, { manualTitle = it }, "A title")
            FormField(manualText, { manualText = it }, "…and its text", singleLine = false, minHeight = 72)
        }

        // ---- 02 say what you want
        if (sample != null) {
            StepHeader("02", "Describe it", if (aiReady) "ask, then refine" else "tap the words")
            if (aiReady) {
                // A chat, so follow-ups work: the helper sees the conversation, the rule so far and the samples each time.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chat.isEmpty()) {
                        ChatBubble(false, "What should the chip show? For example “the minutes left”, “the amount” or “who it is from”.")
                        FormWrap {
                            listOf(
                                "The most important number" to "Show the most important number",
                                "Who it’s from" to "Show who it is from",
                                "Time left" to "Show the time left"
                            ).forEach { (shown, sent) -> FormChoice(shown, false, { ask(sent) }) }
                        }
                    }
                    chat.forEach { ChatBubble(it.fromPerson, it.text) }
                    if (asking) {
                        // Expressive's loading indicator: a shape that morphs while the helper thinks.
                        Box(
                            Modifier.clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
                                .background(TagColors.Surface).padding(horizontal = 18.dp, vertical = 10.dp)
                        ) { androidx.compose.material3.LoadingIndicator(Modifier.size(34.dp), color = TagColors.Lime) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            FormField(message, { message = it }, if (chat.isEmpty()) "Describe it" else "Reply, e.g. “only the number, not the unit”")
                        }
                        FormGapW(8)
                        FormButton("Send", { ask(message) }, enabled = message.isNotBlank() && !asking)
                    }
                }
                FormGap(2)
                Collapsible("Or tap the words yourself", tapOpen, { tapOpen = !tapOpen }) { TapWords(sample, picks, onBuild = { s ->
                    if (s == null) { problem = "That didn’t work from those words. Try different ones, or type the rule yourself." }
                    else { regex = s.regex; pill = s.pill; note = s.explain; problem = null; field = UserRule.Field.BOTH }
                }) }
            } else {
                TapWords(sample, picks, onBuild = { s ->
                    if (s == null) { problem = "That didn’t work from those words. Try different ones, or type the rule yourself."; note = null }
                    else { regex = s.regex; pill = s.pill; note = s.explain; problem = null; field = UserRule.Field.BOTH }
                })
                FormCaption("Turn on the AI helper in the AI settings to describe it in your own words instead.", color = TagColors.Dim)
            }
        }

        // ---- 03 what you get
        StepHeader("03", "Result", if (ruleOk) (if (previewText != null) "matches" else "no match") else "not built yet")
        ChipPreview(pkg, iconMode, previewText)
        note?.let { FormCaption(it, color = TagColors.White) }
        problem?.let { FormNotice(it) }
        err?.let { FormNotice(it) }
        Row {
            FormButton("Save", {
                val e = book.upsert(draft())
                if (e == null) onDone() else problem = e
            }, enabled = regex.isNotBlank())
            if (existing != null) { FormGapW(); FormButton("Delete", { book.delete(existing.id); onDone() }, filled = false, danger = true) }
        }

        // The test: every sample through the rule, and a way to say "not this one" so the helper can fix it.
        if (ruleOk && samples.isNotEmpty()) {
            FormLabel("Test")
            samples.forEach { s ->
                val out = book.preview(rule.copy(pillMode = previewMode), s.title, s.text)
                val isNeg = negatives.any { it.target == s.target }
                FormPlate {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val good = if (isNeg) out == null else out != null
                            Text(
                                out ?: "no match", modifier = Modifier.width(110.dp), maxLines = 1,
                                style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (!good) TagColors.Signal else if (out != null) TagColors.AccentText else TagColors.Dim)
                            )
                            Text(s.target, maxLines = 1, overflow = TextOverflow.Ellipsis, style = FormMono, modifier = Modifier.weight(1f))
                        }
                        if (isNeg || (aiReady && out != null)) {
                            FormGap(6)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isNeg) QuietTag("should not match", color = TagColors.Signal)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    if (isNeg) "UNDO" else "NOT THIS ONE",
                                    Modifier.clickable {
                                        if (isNeg) negatives.removeAll { it.target == s.target }
                                        else { negatives.add(s); ask("That notification must not match: ${s.target.take(120)}") }
                                    }.padding(vertical = 4.dp),
                                    style = TextStyle(fontFamily = TagFonts.data, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, color = TagColors.AccentText)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- fine-tune
        FormGap(4)
        Collapsible("Fine-tune the rule", tweakOpen, { tweakOpen = !tweakOpen }) {
            FormField(name, { name = it }, "Name (optional)")
            FormField(regex, { regex = it; note = null }, "Pattern", mono = true, singleLine = false, minHeight = 64)
            FormField(pill, { pill = it }, "Chip text, e.g. \$1 or \$1 left", mono = true)
            FormCaption("\$1, \$2 … are the bracketed parts of the pattern.")
            FormLabel("Look in")
            val fields = listOf(UserRule.Field.TITLE, UserRule.Field.TEXT, UserRule.Field.BOTH)
            TagSegments(listOf("Title", "Text", "Both"), fields.indexOf(field), { field = fields[it] })
            FormLabel("Chip length")
            val modes = buildList { add(UserRule.PillMode.FIT); add(UserRule.PillMode.EXACT); if (aiPills || pillMode == UserRule.PillMode.AI) add(UserRule.PillMode.AI) }
            TagSegments(modes.map { when (it) { UserRule.PillMode.FIT -> "Fit"; UserRule.PillMode.EXACT -> "Exact"; UserRule.PillMode.AI -> "AI" } }, modes.indexOf(pillMode).coerceAtLeast(0), { pillMode = modes[it] })
            FormCaption(when (pillMode) {
                UserRule.PillMode.FIT -> "Cut to whole words so it fits the chip."
                UserRule.PillMode.EXACT -> "Show as written, up to ${com.microtag.rules.UserRuleBook.EXACT_CAP} characters."
                UserRule.PillMode.AI -> "Cut now, then replaced by a shorter version from the AI a moment later."
            })
            FormLabel("Icon")
            val icons = listOf(UserRule.IconMode.APP, UserRule.IconMode.BUILTIN, UserRule.IconMode.NONE)
            TagSegments(listOf("App’s icon", "TAG’s bolt", "None"), icons.indexOf(iconMode), { iconMode = icons[it] })
            FormLabel("Stays up")
            FormWrap { listOf(10, 15, 30, 60, 0).forEach { s -> FormChoice(if (s == 0) "Until gone" else "$s s", timeout == s, { timeout = s }) } }
            FormSwitch("Ignore capital letters", null, ignoreCase) { ignoreCase = it }
        }

        // ---- share
        StepHeader("04", "Share", "optional")
        FormCaption("Think others would want it? This opens a ready-filled issue on the project’s GitHub page. Numbers, emails and names in the sample are masked first, and nothing is sent until you press Submit there.")
        FormButton("Contribute this rule", {
            val r = draft()
            val e = book.upsert(r)
            if (e != null) problem = e else {
                book.markContributed(r.id)
                val url = RuleExport.issueUrl(r, sample?.let { Redactor.redact(it.target) })
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { problem = "No browser to open it with" }
            }
        }, filled = false, enabled = regex.isNotBlank())
        MicroFooter("TAG_RULE", if (regex.isNotBlank()) "${regex.length} CH PATTERN" else null, Micro.Diamond)
        FormGap(28)
    }
}

/** Tap up to three words from the sample to build a rule without the AI. */
@Composable
private fun TapWords(sample: AiRuleHelper.Sample, picks: androidx.compose.runtime.snapshots.SnapshotStateList<String>, onBuild: (RuleSuggester.Suggestion?) -> Unit) {
    val words = remember(sample) { "${sample.title} ${sample.text}".trim().split(Regex("""\s+""")).map { it.trim(',', '.', ';', ':', '!', '?', '(', ')', '"', '\'') }.filter { it.isNotEmpty() }.distinct().take(60) }
    FormCaption("Tap what you want on the chip.", color = TagColors.Outline)
    FormWrap {
        words.forEach { w ->
            FormChoice(w, w in picks, { if (w in picks) picks.remove(w) else if (picks.size < 3) picks.add(w) })
        }
    }
    Row {
        FormButton("Build rule", { onBuild(RuleSuggester.suggest(sample.target, picks.toList())) }, enabled = picks.isNotEmpty())
    }
}

/** A chat bubble: the person's in the accent, the helper's on a plate. */
@Composable
private fun ChatBubble(fromPerson: Boolean, text: String, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromPerson) Arrangement.End else Arrangement.Start) {
        Text(
            text,
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = if (fromPerson) 20.dp else 6.dp, bottomEnd = if (fromPerson) 6.dp else 20.dp))
                .background(if (fromPerson) TagColors.Lime else TagColors.Surface)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            style = TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp,
                color = if (fromPerson) TagColors.OnLime else if (dim) TagColors.Outline else TagColors.White
            )
        )
    }
}

/** The chip as it will look in the status bar: the icon, then the text the rule made from the sample. */
@Composable
private fun ChipPreview(pkg: String, iconMode: UserRule.IconMode, text: String?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TagColors.Surface).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.clip(CircleShape).background(TagColors.Black).border(2.dp, TagColors.Outline, CircleShape).padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (iconMode) {
                UserRule.IconMode.APP -> RuleAppGlyph(pkg, 24)
                UserRule.IconMode.BUILTIN -> TagIcon(R.drawable.ic_capsule_bolt, TagColors.White, size = 22.dp)
                UserRule.IconMode.NONE -> {}
            }
            FormGapW(8)
            Text(
                text ?: "–", maxLines = 1,
                style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (text != null) TagColors.White else TagColors.Dim)
            )
        }
        Spacer(Modifier.weight(1f))
        QuietTag("chip preview")
    }
}

/** A section that opens and closes. */
@Composable
private fun Collapsible(title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormPlate(onClick = onToggle) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TagColors.White))
                TagIcon(R.drawable.ic_chevron_right, TagColors.Outline, Modifier.rotate(if (open) 90f else 0f), 20.dp)
            }
        }
        Reveal(open, content)
    }
}

/** Content that slides open and closed on the app's springs. */
@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible,
        enter = androidx.compose.animation.expandVertically(TagMotion.spatial()) + androidx.compose.animation.fadeIn(TagMotion.effects()),
        exit = androidx.compose.animation.shrinkVertically(TagMotion.fastEffects()) + androidx.compose.animation.fadeOut(TagMotion.fastEffects())
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun ScreenTitleSmall(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 28.sp, color = TagColors.White))
}

/** A small caps note: states a fact about what is next to it, nothing decorative. */
@Composable
private fun QuietTag(text: String, color: androidx.compose.ui.graphics.Color = TagColors.Dim) {
    Text(text.uppercase(), style = TextStyle(fontFamily = TagFonts.data, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.2.sp, color = color))
}
