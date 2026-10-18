package com.microtag.inspect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import com.microtag.ui.TagColors
import com.microtag.ui.TagIcon
import com.microtag.ui.Tick
import com.microtag.ui.tick
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ==========================================================
// PINNED. The text you pinned, large, and when it goes.
//   header   the label and the share arrow
//   hero     the text itself, sized to its length; tap to copy
//   rows     when it was pinned, when it ends, and how long it stays on the status bar
//   buttons  UNPIN, and +1 HOUR for a timed pin (only when the host can act on them)
// Every other card gets the small pin glyph beside its label (PinGlyph), faint until used.
// ==========================================================

@Composable
internal fun PinnedCard(c: CardCtx, modifier: Modifier) {
    val p = c.p
    val view = LocalView.current
    val created = p.nums.getOrNull(0)?.toLong() ?: 0L
    val expires = p.nums.getOrNull(1)?.toLong() ?: 0L
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    fun at(ms: Long): String = fmt.format(Instant.ofEpochMilli(ms).atZone(zone))

    val chipEnds = created + PinLogic.CHIP_WINDOW_MS
    val now = System.currentTimeMillis()
    val chipLine = when {
        created == 0L -> ""
        now >= chipEnds -> "Now in the notification shade"
        expires in 1 until chipEnds -> "Status bar until ${at(expires)}"
        else -> "Status bar until ${at(chipEnds)}, then the shade"
    }
    val len = p.title.length
    val heroSize = when {
        len <= 8 -> 64f
        len <= 14 -> 46f
        len <= 28 -> 32f
        len <= 60 -> 24f
        else -> 19f
    }

    CardFrame(p, modifier, c.drag, Surface.Black, 400.dp) {
        CardHeader(c, arrowHere = true)
        Spacer(Modifier.height(14.dp))
        Text(
            p.title,
            style = sans(heroSize, FontWeight.Bold, if (c.flash == p.title) c.accent else TagColors.White, -0.5f)
                .copy(lineHeight = (heroSize * 1.1f).sp),
            maxLines = 6, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().plainClick { c.onCopy(p.title) }
        )
        Spacer(Modifier.height(16.dp))
        if (created > 0L) DetailRow("Pinned", at(created), false, c.accent, separator = true, onCopy = c.onCopy)
        DetailRow(
            "Lasts", if (expires == 0L) "Until you unpin it" else "Until ${at(expires)}",
            false, c.accent, separator = chipLine.isNotEmpty(), onCopy = c.onCopy
        )
        if (chipLine.isNotEmpty()) DetailRow("Showing", chipLine, false, c.accent, separator = false, onCopy = c.onCopy)

        val act = c.onPin
        if (act != null) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionPill("UNPIN", c.accent, filled = true) { view.tick(Tick.Press); act("unpin") }
                if (expires > 0L) ActionPill("+1 HOUR", c.accent, filled = false) { view.tick(Tick.Press); act("extend") }
            }
        }
    }
}

/**
 * A small pin beside the card's label. Outline and faint until tapped, then filled. Hidden when the host
 * gives the card no way to pin (the demo cards on the Inspect screen), and on a pin's own card.
 */
@Composable
internal fun PinGlyph(c: CardCtx) {
    val act = c.onPin ?: return
    if (c.p.domain == "PINNED") return
    val view = LocalView.current
    var done by remember { mutableStateOf(false) }
    Box(
        Modifier.size(36.dp).plainClick {
            if (!done) {
                view.tick(Tick.Confirm)
                done = true
                act("pin")
            }
        },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(
            if (done) R.drawable.ic_pin_filled else R.drawable.ic_pin,
            Paper.Ink.copy(alpha = if (done) 0.95f else 0.42f), Modifier, 18.dp
        )
    }
}
