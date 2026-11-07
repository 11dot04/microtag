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

private sealed interface Page {
    object Home : Page
    object Pick : Page
    class Build(val pkg: String, val label: String, val rule: UserRule?) : Page
}

private data class AppEntry(val pkg: String, val label: String)

/** Rules you write yourself: choose an app, point at what you want from one of its notifications, and it becomes a chip. */
@Composable
fun RulesScreen(onBack: () -> Unit, onOpenAi: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf<Page>(Page.Home) }
    BackHandler { if (page is Page.Home) onBack() else page = if (page is Page.Build && (page as Page.Build).rule == null) Page.Pick else Page.Home }

    when (val p = page) {
        Page.Home -> RulesHome(onBack, onOpenAi, onNew = { page = Page.Pick }, onEdit = { r -> page = Page.Build(r.pkg, r.appLabel, r) })
        Page.Pick -> AppPicker({ page = Page.Home }) { e -> page = Page.Build(e.pkg, e.label, null) }
        is Page.Build -> RuleBuilder(p.pkg, p.label, p.rule, onDone = { page = Page.Home })
    }
}

// ------------------------------------------------------------------ home

@Composable
private fun RulesHome(onBack: () -> Unit, onOpenAi: () -> Unit, onNew: () -> Unit, onEdit: (UserRule) -> Unit) {
    val context = LocalContext.current
    val book = remember { UserRules.book(context) }
    var version by remember { mutableIntStateOf(0) }
    val rules = remember(version) { book.all() }
    val aiOn = remember(version) { Ai.ready(context) }

    SubScreen(onBack, spacing = 10.dp) {
        Gap(12)
        ScreenTitle("Rules")
        Caption("Your own rules, per app. When a notification matches, TAG shows what you chose instead of leaving it alone. They run before the built-in ones.")
        Row {
            TagButton("New rule", onNew)
            GapW()
            TagButton(if (aiOn) "AI · On" else "AI · Off", onOpenAi, filled = false)
        }
        if (rules.isEmpty()) {
            Plate { Caption("No rules yet. Pick an app, tap the words you want on the chip, and you are done.") }
        } else {
            rules.forEach { r ->
                Plate(onClick = { onEdit(r) }) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppGlyph(r.pkg, 36)
                        GapW(12)
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                listOf(r.appLabel, r.name).filter { it.isNotBlank() }.joinToString(" · "),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White)
                            )
                            Text(r.regex, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MonoStyle)
                            if (r.contributed) Text("Shared", style = MonoStyle)
                        }
                        TagSwitch(r.enabled, { book.setEnabled(r.id, it); version++ })
                    }
                }
            }
        }
        Gap(24)
    }
}

// ------------------------------------------------------------------ app picker

@Composable
private fun AppPicker(onBack: () -> Unit, onPick: (AppEntry) -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    val recent = remember { NotificationSamples.apps() }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }.filter { it.first != context.packageName }
                .map { AppEntry(it.first, it.second) }.sortedBy { it.label.lowercase() }
        }
    }
    fun labelOf(pkg: String) = apps.firstOrNull { it.pkg == pkg }?.label
        ?: runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)

    SubScreen(onBack, spacing = 10.dp) {
        Gap(12)
        ScreenTitle("Which app?")
        TagField(query, { query = it }, "Search apps")
        if (query.isBlank() && recent.isNotEmpty()) {
            SectionLabel("Seen lately")
            Caption("These sent a notification in the last half hour, so you can build from a real one.")
            recent.take(8).forEach { (pkg, n) -> AppRow(pkg, labelOf(pkg), "$n notification" + if (n == 1) "" else "s") { onPick(AppEntry(pkg, labelOf(pkg))) } }
        }
        SectionLabel(if (query.isBlank()) "All apps" else "Matches")
        val shown = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }.take(60)
        shown.forEach { AppRow(it.pkg, it.label, null) { onPick(it) } }
        if (apps.isEmpty()) Caption("Loading apps…")
        Gap(24)
    }
}

@Composable
private fun AppRow(pkg: String, label: String, sub: String?, onClick: () -> Unit) {
    Plate(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppGlyph(pkg, 32)
            GapW(12)
            Column {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White))
                if (sub != null) Text(sub, style = MonoStyle)
            }
        }
    }
}

/** The app's icon as the chip will draw it: a one-colour glyph, in the ink colour. */
@Composable
private fun AppGlyph(pkg: String, size: Int) {
    val context = LocalContext.current
    val bmp = remember(pkg) { runCatching { AppIcons.render(context, pkg) }.getOrNull() }
    Box(Modifier.size(size.dp).clip(CircleShape).background(TagColors.Black), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.size((size * 0.75f).dp), colorFilter = ColorFilter.tint(TagColors.White))
    }
}

// ------------------------------------------------------------------ builder

@Composable
private fun RuleBuilder(pkg: String, label: String, existing: UserRule?, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val book = remember { UserRules.book(context) }
    BackHandler(onBack = onDone)

    // What this app has sent lately, plus one the person types or pastes.
    val seen = remember { NotificationSamples.recent(pkg).map { AiRuleHelper.Sample(it.title, it.text) } }
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
        Gap(12)
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGlyph(pkg, 44)
            GapW(12)
            Column { ScreenTitleSmall(label); Text(pkg, style = MonoStyle) }
        }

        // ---- 1. a real notification to work from
        SectionLabel("1 · A notification from this app")
        if (seen.isEmpty()) Caption("None seen in the last half hour. Let the app send one, then come back, or type one below.")
        samples.forEachIndexed { i, s ->
            Plate(onClick = { chosen = i; picks.clear() }) {
                Column {
                    Text(
                        s.title.ifBlank { "(no title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (i == chosen) TagColors.AccentText else TagColors.White)
                    )
                    Text(s.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline))
                }
            }
        }
        TagField(manualTitle, { manualTitle = it }, "Or type a title")
        TagField(manualText, { manualText = it }, "…and its text", singleLine = false, minHeight = 72)

        // ---- 2. tap what you want
        if (sample != null) {
            SectionLabel("2 · Tap what you want on the chip")
            val words = remember(sample) { "${sample.title} ${sample.text}".trim().split(Regex("""\s+""")).map { it.trim(',', '.', ';', ':', '!', '?', '(', ')', '"', '\'') }.filter { it.isNotEmpty() }.distinct().take(60) }
            Wrap {
                words.forEach { w ->
                    TagChoice(w, w in picks, { if (w in picks) picks.remove(w) else if (picks.size < 3) picks.add(w) })
                }
            }
            Row {
                TagButton("Build rule", {
                    val s = RuleSuggester.suggest(sample.target, picks.toList())
                    if (s == null) { problem = "That didn’t work from those words. Try different ones, or type the rule yourself."; note = null }
                    else { regex = s.regex; pill = s.pill; note = s.explain; problem = null; field = UserRule.Field.BOTH }
                }, enabled = picks.isNotEmpty())
            }
            if (aiReady) {
                SectionLabel("Or describe it")
                TagField(wanted, { wanted = it }, "e.g. the minutes left, the sender")
                TagButton(if (asking) "Asking…" else "Ask AI", {
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

        note?.let { Plate { Caption(it, color = TagColors.White) } }

        // ---- 3. the rule itself
        SectionLabel("3 · The rule")
        TagField(name, { name = it }, "Name (optional)")
        TagField(regex, { regex = it; note = null }, "Pattern", mono = true, singleLine = false, minHeight = 64)
        TagField(pill, { pill = it }, "Chip text, e.g. \$1 or \$1 left", mono = true)
        Caption("\$1, \$2 … are the bracketed parts of the pattern.")

        SectionLabel("Look in")
        Wrap {
            TagChoice("Title", field == UserRule.Field.TITLE, { field = UserRule.Field.TITLE })
            TagChoice("Text", field == UserRule.Field.TEXT, { field = UserRule.Field.TEXT })
            TagChoice("Both", field == UserRule.Field.BOTH, { field = UserRule.Field.BOTH })
        }
        SectionLabel("Chip length")
        Wrap {
            TagChoice("Fit", pillMode == UserRule.PillMode.FIT, { pillMode = UserRule.PillMode.FIT })
            TagChoice("Exact", pillMode == UserRule.PillMode.EXACT, { pillMode = UserRule.PillMode.EXACT })
            if (aiPills || pillMode == UserRule.PillMode.AI) TagChoice("Shorten with AI", pillMode == UserRule.PillMode.AI, { pillMode = UserRule.PillMode.AI })
        }
        Caption(when (pillMode) {
            UserRule.PillMode.FIT -> "Cut to whole words so it fits the chip."
            UserRule.PillMode.EXACT -> "Show as written, up to ${com.microtag.rules.UserRuleBook.EXACT_CAP} characters."
            UserRule.PillMode.AI -> "Cut now, then replaced by a shorter version from the AI a moment later."
        })
        SectionLabel("Icon")
        Wrap {
            TagChoice("App’s icon", iconMode == UserRule.IconMode.APP, { iconMode = UserRule.IconMode.APP })
            TagChoice("TAG’s bolt", iconMode == UserRule.IconMode.BUILTIN, { iconMode = UserRule.IconMode.BUILTIN })
            TagChoice("None", iconMode == UserRule.IconMode.NONE, { iconMode = UserRule.IconMode.NONE })
        }
        SectionLabel("Stays up")
        Wrap { listOf(10, 15, 30, 60, 0).forEach { s -> TagChoice(if (s == 0) "Until gone" else "$s s", timeout == s, { timeout = s }) } }
        SwitchRow("Ignore capital letters", null, ignoreCase) { ignoreCase = it }

        // ---- live test
        val rule = draft()
        val err = remember(regex, pill, field, ignoreCase) { if (regex.isBlank()) null else book.validate(rule) }
        SectionLabel("Test")
        if (regex.isBlank()) Caption("Build or write a pattern to see what it does to each notification above.")
        err?.let { Notice(it) }
        if (regex.isNotBlank() && err == null) {
            if (samples.isEmpty()) Caption("Nothing to test against yet.")
            samples.forEach { s ->
                val out = book.preview(rule.copy(pillMode = if (pillMode == UserRule.PillMode.AI) UserRule.PillMode.FIT else pillMode), s.title, s.text)
                Plate {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            out ?: "no match", modifier = Modifier.width(110.dp), maxLines = 1,
                            style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (out != null) TagColors.AccentText else TagColors.Dim)
                        )
                        Text(s.target, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MonoStyle)
                    }
                }
            }
        }
        problem?.let { Notice(it) }

        // ---- save / share
        Gap(6)
        Row {
            TagButton("Save", {
                val e = book.upsert(draft())
                if (e == null) onDone() else problem = e
            }, enabled = regex.isNotBlank())
            if (existing != null) { GapW(); TagButton("Delete", { book.delete(existing.id); onDone() }, filled = false, danger = true) }
        }
        SectionLabel("Share")
        Caption("Think others would want it? This opens a ready-filled issue on the project’s GitHub page. Numbers, emails and names in the sample are masked first, and nothing is sent until you press Submit there.")
        TagButton("Contribute this rule", {
            val r = draft()
            val e = book.upsert(r)
            if (e != null) problem = e else {
                book.markContributed(r.id)
                val url = RuleExport.issueUrl(r, sample?.let { Redactor.redact(it.target) })
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { problem = "No browser to open it with" }
            }
        }, filled = false, enabled = regex.isNotBlank())
        Gap(28)
    }
}

@Composable
private fun ScreenTitleSmall(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 28.sp, color = TagColors.White))
}
