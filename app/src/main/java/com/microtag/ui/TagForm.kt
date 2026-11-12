package com.microtag.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The small pieces the new screens (Rules, AI, Meds, city search) share, drawn from the palette like everything else:
 * Surface plates, the accent for "on", mono caps for section labels.
 */

/** The big screen title, the same as Moments and Pin. */
@Composable
fun FormTitle(text: String) {
    FitText(
        AnnotatedString(text),
        TextStyle(
            fontFamily = TagFonts.display, fontWeight = FontWeight.Black, fontSize = 56.sp, lineHeight = 60.sp,
            letterSpacing = (-1.5).sp, color = TagColors.White, fontSynthesis = TagFonts.NoSynthesis
        ),
        Modifier.fillMaxWidth()
    )
}

@Composable
fun FormCaption(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = TagColors.Outline) {
    Text(text, modifier, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp, color = color))
}

@Composable
fun FormLabel(text: String) {
    Text(
        text.uppercase(), modifier = Modifier.padding(top = 14.dp, start = 4.dp),
        style = TextStyle(fontFamily = TagFonts.mono, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.2.sp, color = TagColors.Outline)
    )
}

/** A sentence about what went wrong, on the warning colour. */
@Composable
fun FormNotice(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TagColors.Signal).padding(horizontal = 18.dp, vertical = 12.dp),
        style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 19.sp, color = TagColors.OnSignal)
    )
}

@Composable
fun FormPlate(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val view = LocalView.current
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TagColors.Surface)
            .then(if (onClick != null) Modifier.clickable { view.tick(Tick.Press); onClick() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
fun FormField(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    mono: Boolean = false, singleLine: Boolean = true, secret: Boolean = false, number: Boolean = false, minHeight: Int = 48
) {
    val ink = TagColors.White
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = TextStyle(fontFamily = if (mono) TagFonts.data else Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = ink),
        cursorBrush = SolidColor(TagColors.Lime),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().heightIn(min = minHeight.dp).clip(RoundedCornerShape(18.dp)).background(TagColors.Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart
            ) {
                if (value.isEmpty()) Text(hint, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = TagColors.Dim))
                inner()
            }
        }
    )
}

/** A pill button. Filled is the main action; outlined is everything else. */
@Composable
fun FormButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, enabled: Boolean = true, danger: Boolean = false) {
    val view = LocalView.current
    val bg = if (!filled) androidx.compose.ui.graphics.Color.Transparent else if (danger) TagColors.Signal else TagColors.Lime
    val fg = if (!filled) (if (danger) TagColors.Signal else TagColors.White) else if (danger) TagColors.OnSignal else TagColors.OnLime
    Box(
        modifier.heightIn(min = 46.dp).clip(CircleShape).background(bg)
            .border(2.dp, if (filled) bg else if (danger) TagColors.Signal else TagColors.Outline, CircleShape)
            .then(if (enabled) Modifier.clickable { view.tick(Tick.Press); onClick() } else Modifier)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = fg.copy(alpha = if (enabled) 1f else 0.4f)))
    }
}

/** One option of a small set. Selected fills with the accent. */
@Composable
fun FormChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Box(
        modifier.heightIn(min = 38.dp).clip(CircleShape)
            .background(if (selected) TagColors.Lime else androidx.compose.ui.graphics.Color.Transparent)
            .border(2.dp, if (selected) TagColors.Lime else TagColors.Outline, CircleShape)
            .clickable { view.tick(if (selected) Tick.Off else Tick.On); onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (selected) TagColors.OnLime else TagColors.White))
    }
}

@Composable
fun FormSwitch(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    FormPlate(onClick = { onChange(!checked) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TagColors.White))
                if (sub != null) Text(sub, style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = TagColors.Outline))
            }
            TagSwitch(checked, onChange)
        }
    }
}

/** Choices that wrap onto new lines. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun FormWrap(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

@Composable
fun FormGap(h: Int = 8) = Spacer(Modifier.height(h.dp))

@Composable
fun FormGapW(w: Int = 8) = Spacer(Modifier.width(w.dp))

val FormMono get() = TextStyle(fontFamily = TagFonts.data, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = TagColors.Outline)
