package com.microtag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.inspect.MomentsLogic
import com.microtag.inspect.MomentsStore
import com.microtag.inspect.PillPrefs
import java.text.DateFormat
import java.util.Date

/** Moments found so far. The rest stay "???" so there is still something to stumble on. */
@Composable
fun MomentsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    remember { PillPrefs.attach(context) }
    val found = remember { MomentsStore.found() }
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    SubScreen(onBack, spacing = 10.dp) {
        Spacer(Modifier.height(12.dp))
        FitText(
            AnnotatedString("Moments"),
            TextStyle(
                fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 56.sp,
                lineHeight = 60.sp, letterSpacing = (-1.5).sp, color = TagColors.White
            ),
            Modifier.fillMaxWidth()
        )
        Text(
            "${found.size} of ${MomentsLogic.ALL.size} found. They turn up on their own: a minute on the clock, a round number, a second that only comes once.",
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, color = TagColors.Outline)
        )
        Spacer(Modifier.height(6.dp))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            MomentsLogic.ALL.forEach { m ->
                val f = found[m.id]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(TagColors.Surface)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (f != null) m.pill else "???", modifier = Modifier.width(64.dp),
                        style = TextStyle(
                            fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            color = if (f != null) TagColors.Lime else TagColors.Outline
                        )
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (f != null) m.title else m.group,
                            style = TextStyle(
                                fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                color = if (f != null) TagColors.White else TagColors.Outline
                            )
                        )
                        if (f != null) {
                            Text(
                                m.blurb, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline)
                            )
                            Text(
                                fmt.format(Date(f.first)) + if (f.times > 1) "  ·  ${f.times}×" else "",
                                style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = TagColors.Outline)
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
