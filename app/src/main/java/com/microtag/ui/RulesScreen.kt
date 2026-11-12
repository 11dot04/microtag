package com.microtag.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
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
class RulePrefill(val pkg: String, val label: String, val title: String, val text: String)

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
    var page by remember { mutableStateOf<RulesPage>(if (prefill != null) RulesPage.Build(prefill.pkg, prefill.label, null) else RulesPage.Home) }
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

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var regex by remember { mutableStateOf(existing?.regex ?: "") }
    var pill by remember { mutableStateOf(existing?.pill ?: "$1") }
    var field by remember { mutableStateOf(existing?.field ?: UserRule.Field.BOTH) }
    var pillMode by remember { mutableStateOf(existing?.pillMode ?: UserRule.PillMode.FIT) }
    var iconMode by remember { mutableStateOf(existing?.iconMode ?: UserRule.IconMode.APP) }
    var timeout by remember { mutableIntStateOf(existing?.timeoutSeconds ?: 15) }
    var ignoreCase by remember { mutableStateOf(existing?.ignoreCase ?: true) }
    var wanted by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }

    fun draft() = UserRule(
        id = existing?.id ?: java.util.UUID.randomUUID().toString().take(8), pkg = pkg, appLabel = label, name = name.trim(),
        regex = regex, pill = pill, field = field, timeoutSeconds = timeout, ignoreCase = ignoreCase, iconMode = iconMode,
        iconName = existing?.iconName ?: "ic_capsule_bolt", pillMode = pillMode, enabled = existing?.enabled ?: true,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(), contributed = existing?.contributed ?: false
    )
    val aiReady = remember { Ai.ready(context) && Ai.config(context).ruleHelp }
    val aiPills = remember { Ai.ready(context) }

    SubScreen(onDone, spacing = 10.dp) {
        FormGap(12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuleAppGlyph(pkg, 44)
            FormGapW(12)
            Column { ScreenTitleSmall(label); Text(pkg, style = FormMono) }
        }

        // ---- 1. a real notification to work from
        FormLabel("1 · A notification from this app")
        if (seen.isEmpty()) FormCaption("None seen in the last half hour. Let the app send one, then come back, or type one below.")
        samples.forEachIndexed { i, s ->
            FormPlate(onClick = { chosen = i; picks.clear() }) {
                Column {
                    Text(
                        s.title.ifBlank { "(no title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (i == chosen) TagColors.AccentText else TagColors.White)
                    )
                    Text(s.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline))
                }
            }
        }
        FormField(manualTitle, { manualTitle = it }, "Or type a title")
        FormField(manualText, { manualText = it }, "…and its text", singleLine = false, minHeight = 72)

        // ---- 2. tap what you want
        if (sample != null) {
            FormLabel("2 · Tap what you want on the chip")
            val words = remember(sample) { "${sample.title} ${sample.text}".trim().split(Regex("""\s+""")).map { it.trim(',', '.', ';', ':', '!', '?', '(', ')', '"', '\'') }.filter { it.isNotEmpty() }.distinct().take(60) }
            FormWrap {
                words.forEach { w ->
                    FormChoice(w, w in picks, { if (w in picks) picks.remove(w) else if (picks.size < 3) picks.add(w) })
                }
            }
            Row {
                FormButton("Build rule", {
                    val s = RuleSuggester.suggest(sample.target, picks.toList())
                    if (s == null) { problem = "That didn’t work from those words. Try different ones, or type the rule yourself."; note = null }
                    else { regex = s.regex; pill = s.pill; note = s.explain; problem = null; field = UserRule.Field.BOTH }
                }, enabled = picks.isNotEmpty())
            }
            if (aiReady) {
                FormLabel("Or describe it")
                FormField(wanted, { wanted = it }, "e.g. the minutes left, the sender")
                FormButton(if (asking) "Asking…" else "Ask AI", {
                    asking = true; problem = null
                    val req = AiRuleHelper.Request(label, pkg, positive = samples.take(5), wanted = wanted.ifBlank { "the important part" })
                    scope.launch {
                        val a = withContext(Dispatchers.IO) { runCatching { Ai.suggestRule(req) }.getOrNull() }
                        asking = false
                        when (a) {
                            is AiRuleHelper.Answer.Ok -> { regex = a.regex; pill = a.pill; note = a.explain; field = UserRule.Field.BOTH }
                            is AiRuleHelper.Answer.Failed -> problem = a.message
                            null -> problem = "The AI could not be reached"
                        }
                    }
                }, filled = false, enabled = !asking)
            }
        }

        note?.let { FormPlate { FormCaption(it, color = TagColors.White) } }

        // ---- 3. the rule itself
        FormLabel("3 · The rule")
        FormField(name, { name = it }, "Name (optional)")
        FormField(regex, { regex = it; note = null }, "Pattern", mono = true, singleLine = false, minHeight = 64)
        FormField(pill, { pill = it }, "Chip text, e.g. \$1 or \$1 left", mono = true)
        FormCaption("\$1, \$2 … are the bracketed parts of the pattern.")

        FormLabel("Look in")
        FormWrap {
            FormChoice("Title", field == UserRule.Field.TITLE, { field = UserRule.Field.TITLE })
            FormChoice("Text", field == UserRule.Field.TEXT, { field = UserRule.Field.TEXT })
            FormChoice("Both", field == UserRule.Field.BOTH, { field = UserRule.Field.BOTH })
        }
        FormLabel("Chip length")
        FormWrap {
            FormChoice("Fit", pillMode == UserRule.PillMode.FIT, { pillMode = UserRule.PillMode.FIT })
            FormChoice("Exact", pillMode == UserRule.PillMode.EXACT, { pillMode = UserRule.PillMode.EXACT })
            if (aiPills || pillMode == UserRule.PillMode.AI) FormChoice("Shorten with AI", pillMode == UserRule.PillMode.AI, { pillMode = UserRule.PillMode.AI })
        }
        FormCaption(when (pillMode) {
            UserRule.PillMode.FIT -> "Cut to whole words so it fits the chip."
            UserRule.PillMode.EXACT -> "Show as written, up to ${com.microtag.rules.UserRuleBook.EXACT_CAP} characters."
            UserRule.PillMode.AI -> "Cut now, then replaced by a shorter version from the AI a moment later."
        })
        FormLabel("Icon")
        FormWrap {
            FormChoice("App’s icon", iconMode == UserRule.IconMode.APP, { iconMode = UserRule.IconMode.APP })
            FormChoice("TAG’s bolt", iconMode == UserRule.IconMode.BUILTIN, { iconMode = UserRule.IconMode.BUILTIN })
            FormChoice("None", iconMode == UserRule.IconMode.NONE, { iconMode = UserRule.IconMode.NONE })
        }
        FormLabel("Stays up")
        FormWrap { listOf(10, 15, 30, 60, 0).forEach { s -> FormChoice(if (s == 0) "Until gone" else "$s s", timeout == s, { timeout = s }) } }
        FormSwitch("Ignore capital letters", null, ignoreCase) { ignoreCase = it }

        // ---- live test
        val rule = draft()
        val err = remember(regex, pill, field, ignoreCase) { if (regex.isBlank()) null else book.validate(rule) }
        FormLabel("Test")
        if (regex.isBlank()) FormCaption("Build or write a pattern to see what it does to each notification above.")
        err?.let { FormNotice(it) }
        if (regex.isNotBlank() && err == null) {
            if (samples.isEmpty()) FormCaption("Nothing to test against yet.")
            samples.forEach { s ->
                val out = book.preview(rule.copy(pillMode = if (pillMode == UserRule.PillMode.AI) UserRule.PillMode.FIT else pillMode), s.title, s.text)
                FormPlate {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            out ?: "no match", modifier = Modifier.width(110.dp), maxLines = 1,
                            style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (out != null) TagColors.AccentText else TagColors.Dim)
                        )
                        Text(s.target, maxLines = 1, overflow = TextOverflow.Ellipsis, style = FormMono)
                    }
                }
            }
        }
        problem?.let { FormNotice(it) }

        // ---- save / share
        FormGap(6)
        Row {
            FormButton("Save", {
                val e = book.upsert(draft())
                if (e == null) onDone() else problem = e
            }, enabled = regex.isNotBlank())
            if (existing != null) { FormGapW(); FormButton("Delete", { book.delete(existing.id); onDone() }, filled = false, danger = true) }
        }
        FormLabel("Share")
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
        FormGap(28)
    }
}

@Composable
private fun ScreenTitleSmall(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 28.sp, color = TagColors.White))
}
