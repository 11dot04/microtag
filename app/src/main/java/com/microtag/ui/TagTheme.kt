@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.microtag.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.text.font.FontFamily as ComposeFontFamily
import androidx.compose.ui.text.font.FontSynthesis
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlin.math.hypot
import androidx.compose.ui.geometry.isSpecified
import android.graphics.RuntimeShader
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.PI
import kotlin.math.sin
import android.content.Context
import android.os.Build
import com.microtag.R
import kotlin.math.max

// ==========================================================
// PALETTE
// Every colour in the app comes from one palette with a handful of roles:
//   base    the page and the "off" tiles          (TagColors.Black)
//   ink     text and the always-on tiles          (TagColors.White)
//   accent  the poster and everything switched on (TagColors.Lime)
// The old names stay so existing screens keep working; on a light palette Black is simply
// cream and White is simply dark. Lime fill = on. Outline only = off.
// ==========================================================

/** Axis values for one use of a variable font. The weight axis always comes from the requested FontWeight. */
data class FlexAxes(
    val width: Float = 100f,      // wdth: 25 (very narrow) to 151 (very wide)
    val roundness: Float = 0f,    // ROND: 0 (sharp terminals) to 100 (fully round)
    val slant: Float = 0f,        // slnt: -10 (leaning) to 0 (upright)
    val opsz: Float = 18f         // opsz: 1 to 144, the size the letterforms are drawn for (small = sturdier)
)

/** How a variable font is set in each role. This is the one place to tune it. */
data class FlexTuning(
    val body: FlexAxes = FlexAxes(),
    val display: FlexAxes = FlexAxes(),
    val label: FlexAxes = FlexAxes()
)

data class TagPalette(
    val id: String,
    val name: String,
    val base: Color,
    val ink: Color,
    val accent: Color,
    /** Whatever reads on top of the accent. */
    val onAccent: Color,
    /** The accent as small text on the base, when the plain accent is too weak for that. */
    val accentText: Color = accent,
    /** Settings rows and other raised surfaces. */
    val surface: Color,
    /** The poster while TAG is paused. */
    val pausedPoster: Color,
    val outline: Color,
    val dim: Color,
    val signal: Color = Color(0xFFFF4A2B),
    /** Light tile colour for sitting on the accent poster (onboarding), and the ink that reads on it. */
    val paper: Color,
    val paperInk: Color,
    /**
     * Second and third accents, like Material You's secondary and tertiary. A palette without them is
     * single-accent on purpose (Lime). Dashboard tiles, chart series, log stripes and switches take
     * their colour from these when they exist.
     */
    val secondary: Color? = null,
    val tertiary: Color? = null,
    /** Extra colours for moments that want more than three (the onboarding pile, previews). */
    val extras: List<Color> = emptyList(),
    /** Optional font roles, as res/font base names (see TagFonts). Null keeps Montserrat. */
    val display: String? = null,
    val mono: String? = null,
    /** Optional body/label face (Montserrat everywhere else). The Material palette uses Google Sans Flex. */
    val body: String? = null,
    /** Axis settings when the faces above are one variable font file (see [FlexTuning]). */
    val flex: FlexTuning? = null
) {
    val isLight: Boolean get() = base.luminance() > 0.5f

    /** Everything beyond the primary accent, for previews and the onboarding pile. */
    val supporting: List<Color> get() = if (extras.isNotEmpty()) extras else listOfNotNull(secondary, tertiary)
}

object TagPalettes {
    /** The original: lime on black. */
    val Lime = TagPalette(
        id = "lime", name = "Lime",
        base = Color(0xFF000000), ink = Color(0xFFF6F6F6),
        accent = Color(0xFFCCFF00), onAccent = Color(0xFF000000),
        surface = Color(0xFF15161A), pausedPoster = Color(0xFF101114),
        outline = Color(0xFFC3C9D4), dim = Color(0xFF6E737C),
        paper = Color(0xFFF6F6F6), paperInk = Color(0xFF000000)
    )

    /** Cream paper, deep teal, a serif display face. Light. */
    val Atelier = TagPalette(
        id = "atelier", name = "Atelier",
        base = Color(0xFFF2EDE2), ink = Color(0xFF14201F),
        accent = Color(0xFF0E4A4B), onAccent = Color(0xFFF2EDE2),
        surface = Color(0xFFE8E1D2), pausedPoster = Color(0xFFDCD4C2),
        outline = Color(0xFF7C8D89), dim = Color(0xFF7A8683),
        paper = Color(0xFFF2EDE2), paperInk = Color(0xFF0E4A4B),
        secondary = Color(0xFFB8862B), tertiary = Color(0xFFB5543C),
        display = "bodoni_moda", mono = "dm_mono"
    )

    /** Near-black with a bright rouge and champagne. Dark. */
    val Rouge = TagPalette(
        id = "rouge", name = "Rouge",
        base = Color(0xFF15100F), ink = Color(0xFFEADFC6),
        accent = Color(0xFFD2455C), onAccent = Color(0xFF15100F),
        surface = Color(0xFF211A19), pausedPoster = Color(0xFF241C1B),
        outline = Color(0xFFB7AA90), dim = Color(0xFF7A6F60),
        paper = Color(0xFFEADFC6), paperInk = Color(0xFF15100F),
        secondary = Color(0xFFE3B15C), tertiary = Color(0xFFB5607F),
        display = "gfs_didot", mono = "dm_mono"
    )

    /** Bone paper, black ink, one loud vermilion. Light. */
    val Vermilion = TagPalette(
        id = "vermilion", name = "Vermilion",
        base = Color(0xFFECE8E1), ink = Color(0xFF121212),
        accent = Color(0xFFE3492B), onAccent = Color(0xFF121212),
        accentText = Color(0xFFB8310F),
        surface = Color(0xFFE2DDD4), pausedPoster = Color(0xFFD8D2C7),
        outline = Color(0xFF8B867D), dim = Color(0xFF858078),
        paper = Color(0xFFECE8E1), paperInk = Color(0xFF121212),
        secondary = Color(0xFF1F5A6B), tertiary = Color(0xFFE6B22E)
    )

    /** Espresso with soft sage. Dark. */
    val Sage = TagPalette(
        id = "sage", name = "Sage",
        base = Color(0xFF2A211C), ink = Color(0xFFF1EBDD),
        accent = Color(0xFFA9B6A1), onAccent = Color(0xFF2A211C),
        surface = Color(0xFF342A24), pausedPoster = Color(0xFF392E27),
        outline = Color(0xFFB9B2A2), dim = Color(0xFF85796D),
        paper = Color(0xFFF1EBDD), paperInk = Color(0xFF2A211C),
        secondary = Color(0xFFD49A7C), tertiary = Color(0xFFC9C48A)
    )

    /** Warm black, orange-red, cyan and yellow: a bold split-complementary set. Dark. */
    val Signal = TagPalette(
        id = "signal", name = "Signal",
        base = Color(0xFF1A1717), ink = Color(0xFFF6E6D8),
        accent = Color(0xFFF04A00), onAccent = Color(0xFF1A1717),
        surface = Color(0xFF262222), pausedPoster = Color(0xFF2A2525),
        outline = Color(0xFFBDB1A2), dim = Color(0xFF7C7166),
        signal = Color(0xFFF2B227),
        paper = Color(0xFFF6E6D8), paperInk = Color(0xFF1A1717),
        secondary = Color(0xFF08A4C4), tertiary = Color(0xFFF2B227),
        extras = listOf(Color(0xFF08A4C4), Color(0xFFF2B227), Color(0xFFF6E6D8))
    )

    /** Forest, rose, mustard and powder blue. Dark. */
    val Maison = TagPalette(
        id = "maison", name = "Maison",
        base = Color(0xFF14201F), ink = Color(0xFFF5E9E4),
        accent = Color(0xFFE8828A), onAccent = Color(0xFF14201F),
        surface = Color(0xFF1D2E2C), pausedPoster = Color(0xFF223634),
        outline = Color(0xFF8FA5A1), dim = Color(0xFF6B7F7B),
        signal = Color(0xFFF2C14E),
        paper = Color(0xFFFDD2DA), paperInk = Color(0xFF14201F),
        secondary = Color(0xFFDDA83E), tertiary = Color(0xFFCEDDE8),
        extras = listOf(Color(0xFF2D4A47), Color(0xFFDDA83E), Color(0xFF8B3A3F), Color(0xFFCEDDE8)),
        display = "playfair_display", mono = "dm_mono"
    )

    /** Analogous: every colour sits in the green-to-yellow arc. A warm coral is the only outsider, for warnings. Dark. */
    val Verdant = TagPalette(
        id = "verdant", name = "Verdant",
        base = Color(0xFF0D1A14), ink = Color(0xFFE4F2E0),
        accent = Color(0xFFA8E05F), onAccent = Color(0xFF0D1A14),
        surface = Color(0xFF14261D), pausedPoster = Color(0xFF173024),
        outline = Color(0xFF7FA38B), dim = Color(0xFF5E7C68),
        signal = Color(0xFFFF7A59),
        paper = Color(0xFFE4F2E0), paperInk = Color(0xFF0D1A14),
        secondary = Color(0xFF4FD1A5), tertiary = Color(0xFFE5F28A),
        extras = listOf(Color(0xFF4FD1A5), Color(0xFFE5F28A), Color(0xFF2E7D5B))
    )

    /** Analogous: violet through orchid to blue, on a lilac-tinted white. Light. */
    val Dusk = TagPalette(
        id = "dusk", name = "Dusk",
        base = Color(0xFFF4F0FA), ink = Color(0xFF231A3A),
        accent = Color(0xFF5B3FD0), onAccent = Color(0xFFF4F0FA),
        surface = Color(0xFFEAE4F5), pausedPoster = Color(0xFFDDD5EE),
        outline = Color(0xFF8A7FA8), dim = Color(0xFF8A82A0),
        signal = Color(0xFFD9480F),
        paper = Color(0xFFF4F0FA), paperInk = Color(0xFF231A3A),
        secondary = Color(0xFFC44FB8), tertiary = Color(0xFF3F6BD1),
        extras = listOf(Color(0xFFF0A6E0), Color(0xFFA9C1FF), Color(0xFFD9CCFF)),
        display = "instrument_serif"
    )

    /**
     * Material 3 in its own right: on Android 12+ the colours come from the system's dynamic scheme (wallpaper
     * tones), light or dark with the phone; elsewhere the baseline purple. Set in Google Sans Flex.
     * The values here are the baseline dark scheme; TagColors swaps in the real ones (see materialPalettes).
     */
    val Material = TagPalette(
        id = "material", name = "Material",
        base = Color(0xFF1C1B1F), ink = Color(0xFFE6E1E5),
        accent = Color(0xFFD0BCFF), onAccent = Color(0xFF381E72),
        surface = Color(0xFF2B2930), pausedPoster = Color(0xFF322F37),
        outline = Color(0xFF938F99), dim = Color(0xFF7D7983),
        signal = Color(0xFFF2B8B5),
        paper = Color(0xFFE6E1E5), paperInk = Color(0xFF1C1B1F),
        secondary = Color(0xFFCCC2DC), tertiary = Color(0xFFEFB8C8),
        display = "google_sans_flex", body = "google_sans_flex", mono = "google_sans_flex",
        // Google Sans Flex has five axes here (weight comes from each text style): soft terminals everywhere, a wider
        // and fully round face for headings, a narrower one for the small caps labels.
        flex = FlexTuning(
            body = FlexAxes(width = 100f, roundness = 60f, opsz = 16f),
            display = FlexAxes(width = 110f, roundness = 100f, opsz = 72f),
            label = FlexAxes(width = 90f, roundness = 40f, opsz = 12f)
        )
    )

    val All = listOf(Lime, Material, Atelier, Signal, Maison, Rouge, Verdant, Vermilion, Dusk, Sage)
    fun byId(id: String?): TagPalette = All.firstOrNull { it.id == id } ?: Lime
}

/** How a palette's own light or dark nature is treated. */
enum class Appearance(val id: String, val label: String) {
    Native("native", "Original"),     // each palette as designed
    Light("light", "Light"),          // every palette on a light base
    Dark("dark", "Dark"),             // every palette on a dark base
    System("system", "System");       // follow the phone

    fun next(): Appearance = values()[(ordinal + 1) % values().size]

    companion object {
        fun fromId(id: String?): Appearance = values().firstOrNull { it.id == id } ?: Native
    }
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Nudges [c] toward black or white (whichever helps) until it has at least [min]:1 against [against]. */
private fun fitContrast(c: Color, against: Color, min: Float): Color {
    if (contrastRatio(c, against) >= min) return c
    val target = if (against.luminance() > 0.5f) Color.Black else Color.White
    var out = c
    for (i in 1..14) {
        out = lerp(c, target, i * 0.07f)
        if (contrastRatio(out, against) >= min) break
    }
    return out
}

/**
 * The opposite-brightness version of a palette: base and ink trade places (a cream-and-teal palette becomes
 * deep teal-black with cream ink), and every accent is nudged until it still reads on its new base. Fonts,
 * name and id are kept, so a serif palette stays serif in the dark.
 */
fun TagPalette.inverted(): TagPalette {
    val newBase = ink
    val newInk = base
    val dark = if (newBase.luminance() < newInk.luminance()) newBase else newInk
    val light = if (dark == newBase) newInk else newBase
    val newAccent = fitContrast(accent, newBase, 4.5f)
    return copy(
        base = newBase,
        ink = newInk,
        accent = newAccent,
        onAccent = if (contrastRatio(newAccent, dark) >= contrastRatio(newAccent, light)) dark else light,
        accentText = fitContrast(accentText, newBase, 4.5f),
        surface = lerp(newBase, newInk, 0.06f),
        pausedPoster = lerp(newBase, newInk, 0.09f),
        outline = lerp(newBase, newInk, 0.55f),
        dim = lerp(newBase, newInk, 0.40f),
        signal = fitContrast(signal, newBase, 3f),
        secondary = secondary?.let { fitContrast(it, newBase, 3f) },
        tertiary = tertiary?.let { fitContrast(it, newBase, 3f) },
        extras = extras.map { fitContrast(it, newBase, 2f) },
        paper = light,
        paperInk = dark
    )
}

/** What a palette looks like under the current Appearance setting. */
fun TagPalettes.resolve(p: TagPalette, appearance: Appearance, systemDark: Boolean): TagPalette {
    val wantDark = when (appearance) {
        Appearance.Native -> return p
        Appearance.Light -> false
        Appearance.Dark -> true
        Appearance.System -> systemDark
    }
    return if (wantDark == !p.isLight) p else p.inverted()
}

/** Pure black page for dark palettes (OLED pixels switch off). Only the base changes; surfaces and accents are untouched. */
fun TagPalette.amoled(): TagPalette = if (isLight) this else copy(base = Color.Black)

/** The system's Material You tones as (light, dark) TagPalettes. Null below Android 12. */
private fun systemMaterial(context: Context, baseline: TagPalette): Pair<TagPalette, TagPalette>? {
    if (Build.VERSION.SDK_INT < 31) return null
    fun c(id: Int) = Color(context.getColor(id))
    val dark = baseline.copy(
        base = c(android.R.color.system_neutral1_900), ink = c(android.R.color.system_neutral1_100),
        accent = c(android.R.color.system_accent1_200), onAccent = c(android.R.color.system_accent1_800),
        accentText = c(android.R.color.system_accent1_200),
        surface = c(android.R.color.system_neutral1_800),
        pausedPoster = c(android.R.color.system_neutral1_800),
        outline = c(android.R.color.system_neutral2_400), dim = c(android.R.color.system_neutral2_500),
        secondary = c(android.R.color.system_accent2_200), tertiary = c(android.R.color.system_accent3_200),
        signal = Color(0xFFF2B8B5),
        paper = c(android.R.color.system_neutral1_100), paperInk = c(android.R.color.system_neutral1_900)
    )
    val light = baseline.copy(
        base = c(android.R.color.system_neutral1_10), ink = c(android.R.color.system_neutral1_900),
        accent = c(android.R.color.system_accent1_600), onAccent = c(android.R.color.system_accent1_0),
        accentText = c(android.R.color.system_accent1_600),
        surface = c(android.R.color.system_neutral1_100),
        pausedPoster = c(android.R.color.system_neutral1_200),
        outline = c(android.R.color.system_neutral2_500), dim = c(android.R.color.system_neutral2_400),
        secondary = c(android.R.color.system_accent2_600), tertiary = c(android.R.color.system_accent3_600),
        signal = Color(0xFFB3261E),
        paper = c(android.R.color.system_neutral1_10), paperInk = c(android.R.color.system_neutral1_900)
    )
    return light to dark
}

/** Baseline Material 3 (the purple scheme) for light, derived from the dark one. */
private fun baselineLight(dark: TagPalette) = dark.copy(
    base = Color(0xFFFFFBFE), ink = Color(0xFF1C1B1F),
    accent = Color(0xFF6750A4), onAccent = Color(0xFFFFFFFF), accentText = Color(0xFF6750A4),
    surface = Color(0xFFF3EDF7), pausedPoster = Color(0xFFE7E0EC),
    outline = Color(0xFF79747E), dim = Color(0xFF938F99),
    secondary = Color(0xFF625B71), tertiary = Color(0xFF7D5260), signal = Color(0xFFB3261E),
    paper = Color(0xFFFFFBFE), paperInk = Color(0xFF1C1B1F)
)

object TagColors {
    private var _basePalette by mutableStateOf(TagPalettes.Lime)
    private var _appearance by mutableStateOf(Appearance.Native)
    private var _systemDark by mutableStateOf(true)
    private var _amoledOn by mutableStateOf(false)
    private var _microOn by mutableStateOf(true)
    // The Material palette's real colours (light, dark); baseline until the system's are read.
    private var _materialLight by mutableStateOf(baselineLight(TagPalettes.Material))
    private var _materialDark by mutableStateOf(TagPalettes.Material)
    private var _multicolourOn by mutableStateOf(false)

    private fun resolve(p: TagPalette, a: Appearance, sysDark: Boolean): TagPalette {
        val base = if (p.id == "material") {
            // Material has no "native" side: it follows the phone unless told otherwise.
            val dark = when (a) { Appearance.Light -> false; Appearance.Dark -> true; else -> sysDark }
            if (dark) _materialDark else _materialLight
        } else TagPalettes.resolve(p, a, sysDark)
        return if (_amoledOn) base.amoled() else base
    }

    // Cached until the palette, the _appearance, the AMOLED switch or the phone's mode changes.
    private val _resolved by derivedStateOf { resolve(_basePalette, _appearance, _systemDark) }
    private val palette: TagPalette get() = _resolved

    /** The accent. */
    val Lime: Color get() = palette.accent
    val Accent: Color get() = Lime

    /** Whatever reads on top of the accent. */
    val OnLime: Color get() = palette.onAccent

    /** Second and third accents. Fall back to the primary when a palette has none. */
    val Secondary: Color get() = palette.secondary ?: Lime
    val Tertiary: Color get() = palette.tertiary ?: Lime
    /** True when this palette really has more than one accent to hand out. */
    val HasTones: Boolean get() = palette.secondary != null

    /** The "Micrographics" setting: tiny marks, rules and annotations. Everything decorative checks this. */
    val MicroOn: Boolean get() = _microOn
    fun useMicro(on: Boolean) { _microOn = on }

    /** Pure black on dark palettes. */
    val AmoledOn: Boolean get() = _amoledOn
    fun useAmoled(on: Boolean) { _amoledOn = on }

    /** The "Multicolour tiles" setting. Off by default: one accent carries the layout, the others punctuate. */
    val Multicolour: Boolean get() = _multicolourOn
    fun useMulticolour(on: Boolean) { _multicolourOn = on }

    /** Decorative variety: the i-th accent when Multicolour is on, otherwise the primary. */
    fun patch(i: Int): Color = if (_multicolourOn) tone(i % 3) else Lime

    /** 0 = accent, 1 = secondary, 2 = tertiary. */
    fun tone(i: Int): Color = when (i) {
        1 -> Secondary
        2 -> Tertiary
        else -> Lime
    }

    /** Whatever reads best on top of tone(i): the dark end or the light end of the palette. */
    fun onTone(i: Int): Color {
        if (i == 0) return OnLime
        val bg = tone(i)
        val dark = if (palette.base.luminance() < palette.ink.luminance()) palette.base else palette.ink
        val light = if (dark == palette.base) palette.ink else palette.base
        fun contrast(a: Color, b: Color): Float {
            val la = a.luminance(); val lb = b.luminance()
            return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
        }
        return if (contrast(bg, dark) >= contrast(bg, light)) dark else light
    }

    /**
     * The colour of the i-th series in a chart or list. A multi-accent palette gives each series its
     * own accent; a single-accent one keeps one colour and steps the alpha instead.
     */
    fun series(i: Int, alpha: Float = 1f): Color = if (HasTones) tone(i % 3) else Lime.copy(alpha = alpha)

    /** The accent as small text on the base. */
    val AccentText: Color get() = palette.accentText

    val Black: Color get() = palette.base     // the base: page, off tiles, plates
    val White: Color get() = palette.ink      // ink: text, always-on tiles
    val Base: Color get() = palette.base
    val Ink: Color get() = palette.ink
    val Surface: Color get() = palette.surface
    val PausedPoster: Color get() = palette.pausedPoster
    val Outline: Color get() = palette.outline
    val Steel: Color get() = palette.outline
    /** A rare warning colour: the permission banner and the armed bin, nothing else. */
    val Signal: Color get() = palette.signal
    val Dim: Color get() = palette.dim
    /** Whatever reads on top of [Signal]: dark on a bright warning colour, light on a deep one. */
    val OnSignal: Color
        get() = if (palette.signal.luminance() > 0.4f) Color(0xFF14110F) else Color(0xFFF6F6F6)
    val Extras: List<Color> get() = palette.supporting
    val Name: String get() = palette.name
    /** Axis settings of the active palette's variable font, if it has one. */
    val Flex: FlexTuning? get() = palette.flex
    /** res/font base names of this palette's display, body and mono faces, or null for Montserrat. */
    val BodyBase: String? get() = palette.body
    val DisplayBase: String? get() = palette.display
    val MonoBase: String? get() = palette.mono
    val Paper: Color get() = palette.paper
    val PaperInk: Color get() = palette.paperInk
    val IsLight: Boolean get() = palette.isLight
    val Id: String get() = palette.id

    fun usePalette(context: Context, id: String) {
        // The system's own Material You tones, when this Android has them.
        systemMaterial(context, TagPalettes.Material)?.let { (light, dark) ->
            _materialLight = light
            _materialDark = dark
        }
        _basePalette = TagPalettes.byId(id)
        TagFonts.apply(context, _basePalette)
    }

    /** Original, Light, Dark or System (see [Appearance]). */
    fun useAppearance(id: String) { _appearance = Appearance.fromId(id) }
    val AppearanceId: String get() = _appearance.id

    /** Fed by TagTheme from the phone's own dark mode. */
    fun setSystemDark(dark: Boolean) { if (_systemDark != dark) _systemDark = dark }

    /** A palette as it would look right now, for previews (the palette picker). */
    fun preview(p: TagPalette): TagPalette = resolve(p, _appearance, _systemDark)
}

/** Global motion and haptics switches, driven from Settings. */
object TagMotion {
    var enabled by mutableStateOf(true)
    var haptics by mutableStateOf(true)
}

// ==========================================================
// TYPE: Montserrat. Weight does the talking: the first word is Bold,
// the rest of the label drops to Light. Drop the TTFs in res/font.
// ==========================================================

private val MontserratBase = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_black, FontWeight.Black)
)

/**
 * The body and label face used all over the app. Still Montserrat unless the palette brings its own
 * (the Material palette uses Google Sans Flex), so every existing `fontFamily = Montserrat` follows the palette.
 */
val Montserrat: FontFamily get() = TagFonts.body

/**
 * Font roles a palette can change. Montserrat stays the label face everywhere; a palette may
 * swap the display face (big headings) and the mono face (small data and captions).
 *
 * Drop static TTFs into res/font named <base>_<weight>.ttf, for example bodoni_moda_regular,
 * bodoni_moda_bold, bodoni_moda_black, gfs_didot_regular, xanh_mono_regular. Weights looked
 * for: light, regular, medium, bold, black; a bare <base>.ttf also works. Nothing found means
 * Montserrat is used, so a palette never breaks for want of a file.
 */
object TagFonts {
    private var _bodyFamily by mutableStateOf<ComposeFontFamily>(MontserratBase)
    private var _displayFamily by mutableStateOf<ComposeFontFamily>(MontserratBase)
    private var _monoFamily by mutableStateOf<ComposeFontFamily>(MontserratBase)

    private var _dataFamily by mutableStateOf<ComposeFontFamily>(FontFamily.Monospace)

    val body: ComposeFontFamily get() = _bodyFamily
    val display: ComposeFontFamily get() = _displayFamily
    val mono: ComposeFontFamily get() = _monoFamily

    /** Timestamps, repeat counts, log lines: the system monospace, or the palette's own mono face. */
    val data: ComposeFontFamily get() = _dataFamily

    /** Single-weight faces must not be faux-bolded. Use this on any style that uses [display]. */
    val NoSynthesis = FontSynthesis.None

    fun apply(context: Context, p: TagPalette) {
        val flex = p.flex
        // One variable file drawn with role-specific axes when the palette has a tuning; plain static files otherwise.
        fun face(base: String?, axes: FlexAxes?): ComposeFontFamily? = base?.let { b ->
            (if (axes != null) variableFamily(context, b, axes) else null) ?: family(context, b)
        }
        _bodyFamily = face(p.body, flex?.body) ?: MontserratBase
        _displayFamily = face(p.display, flex?.display) ?: _bodyFamily
        val customMono = face(p.mono, flex?.label)
        _monoFamily = customMono ?: _bodyFamily
        // Timestamps and log lines want a real monospace unless the palette brings a mono face of its own.
        _dataFamily = if (flex != null) FontFamily.Monospace else (customMono ?: FontFamily.Monospace)
    }

    /** One variable font file as a family of weights, with the axes set for a role. */
    fun variableFamily(context: Context, base: String, axes: FlexAxes): ComposeFontFamily? {
        val id = context.resources.getIdentifier(base, "font", context.packageName)
        if (id == 0) return null
        fun font(w: FontWeight) = Font(
            id, w,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(w.weight),
                FontVariation.width(axes.width),
                FontVariation.slant(axes.slant),
                FontVariation.Setting("ROND", axes.roundness),
                FontVariation.Setting("opsz", axes.opsz)
            )
        )
        return ComposeFontFamily(
            font(FontWeight.Light), font(FontWeight.Normal), font(FontWeight.Medium), font(FontWeight.Bold), font(FontWeight.Black)
        )
    }

    /** The same axes as a string for android.graphics.Paint.setFontVariationSettings (the shared stats card). */
    fun variationString(weight: Int, axes: FlexAxes): String =
        "'wght' $weight, 'wdth' ${axes.width}, 'slnt' ${axes.slant}, 'ROND' ${axes.roundness}, 'opsz' ${axes.opsz}"

    /** A native Typeface from res/font for canvas drawing (the shared card): the first of [weights] that exists. */
    fun typeface(context: Context, base: String?, weights: List<String> = listOf("black", "bold", "regular", "")): android.graphics.Typeface? {
        if (base == null) return null
        for (w in weights) {
            val name = if (w.isEmpty()) base else "${base}_$w"
            val id = context.resources.getIdentifier(name, "font", context.packageName)
            if (id != 0) return androidx.core.content.res.ResourcesCompat.getFont(context, id)
        }
        return null
    }

    fun family(context: Context, base: String): ComposeFontFamily? {
        fun res(name: String) = context.resources.getIdentifier(name, "font", context.packageName)
        val fonts = buildList {
            listOf(
                "light" to FontWeight.Light, "regular" to FontWeight.Normal, "medium" to FontWeight.Medium,
                "bold" to FontWeight.Bold, "black" to FontWeight.Black
            ).forEach { (suffix, weight) ->
                res("${base}_$suffix").takeIf { it != 0 }?.let { add(Font(it, weight)) }
            }
            if (isEmpty()) res(base).takeIf { it != 0 }?.let { add(Font(it, FontWeight.Normal)) }
        }
        return if (fonts.isEmpty()) null else ComposeFontFamily(fonts)
    }
}

/**
 * Single-purpose text that never wraps mid-word. It keeps every line whole and
 * shrinks the type until the widest line fits the space it is given.
 * Measured up front, so there is no visible resize flicker.
 */
@Composable
fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 9.sp,
    contentAlignment: Alignment = Alignment.TopStart
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val fitted = remember(text, style, maxW) {
            val ratio = if (style.lineHeight.isSpecified && style.fontSize.isSpecified)
                style.lineHeight.value / style.fontSize.value else 0f
            fun at(size: Float) = style.copy(
                fontSize = size.sp,
                lineHeight = if (ratio > 0f) (size * ratio).sp else style.lineHeight
            )
            var size = style.fontSize.value
            while (size > minSize.value) {
                val width = measurer.measure(text = text, style = at(size), softWrap = false).size.width
                if (width <= maxW) break
                size *= 0.96f
            }
            at(size.coerceAtLeast(minSize.value))
        }
        Text(text, style = fitted, softWrap = false, maxLines = text.text.count { it == '\n' } + 1)
    }
}

@Composable
fun TagLabel(
    bold: String,
    light: String? = null,
    color: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 17.sp,
    align: TextAlign = TextAlign.Start,
    inline: Boolean = false
) {
    FitText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            if (light != null) {
                append(if (inline) " " else "\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(light) }
            }
        },
        style = TextStyle(
            fontFamily = Montserrat,
            fontSize = size,
            lineHeight = size * 1.12f,
            letterSpacing = (-0.2).sp,
            color = color,
            textAlign = align
        ),
        modifier = modifier,
        contentAlignment = when (align) {
            TextAlign.End -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        }
    )
}

@Composable
fun TagTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    SideEffect { TagColors.setSystemDark(systemDark) }
    // No dynamic colour of its own: the palette is the identity (Material You is an accent override).
    val scheme = if (TagColors.IsLight) {
        lightColorScheme(
            primary = TagColors.Lime,
            onPrimary = TagColors.OnLime,
            background = TagColors.Black,
            onBackground = TagColors.White,
            surface = TagColors.Black,
            onSurface = TagColors.White,
            surfaceContainerHigh = TagColors.Black,
            outline = TagColors.Outline
        )
    } else {
        darkColorScheme(
            primary = TagColors.Lime,
            onPrimary = TagColors.OnLime,
            background = TagColors.Black,
            onBackground = TagColors.White,
            surface = TagColors.Black,
            onSurface = TagColors.White,
            surfaceContainerHigh = TagColors.Black,
            outline = TagColors.Outline
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ==========================================================
// SHAPES
// ==========================================================

object TagShapes {
    val Rect = RoundedCornerShape(22.dp)
    val Big = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Circle = CircleShape
    /** Left cap round, right edge square. Pairs with CapRight to interlock. */
    val CapLeft = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0)
    val CapRight = RoundedCornerShape(topStartPercent = 0, bottomStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50)
    /** Onboarding pills that nearly touch: round outside, tight inside. */
    val JoinLeft = RoundedCornerShape(topStart = CornerSize(50), bottomStart = CornerSize(50), topEnd = CornerSize(6.dp), bottomEnd = CornerSize(6.dp))
    val JoinRight = RoundedCornerShape(topStart = CornerSize(6.dp), bottomStart = CornerSize(6.dp), topEnd = CornerSize(50), bottomEnd = CornerSize(50))
}


// ==========================================================
// HAPTICS
// ==========================================================

enum class Tick { Press, On, Off, Detent, Confirm, Minor }

fun View.tick(kind: Tick) {
    if (!TagMotion.haptics) return
    performHapticFeedback(
        when (kind) {
            Tick.Press -> HapticFeedbackConstants.CONTEXT_CLICK
            Tick.On -> HapticFeedbackConstants.TOGGLE_ON
            Tick.Off -> HapticFeedbackConstants.TOGGLE_OFF
            Tick.Detent -> HapticFeedbackConstants.SEGMENT_TICK
            Tick.Confirm -> HapticFeedbackConstants.CONFIRM
            // the lightest tick: between the marks of a dial
            Tick.Minor -> if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.SEGMENT_FREQUENT_TICK else HapticFeedbackConstants.CLOCK_TICK
        }
    )
}

// ==========================================================
// THE INVISIBLE GRID
// A square cell, a fixed gutter, and tiles that span whole cells.
// 1x1 and 2x2 are exact squares, 2x1 is exactly two cells wide.
// A tile's position is (col, row); it never floats.
// ==========================================================

/** The one gutter used everywhere, so tiles that bridge it (like the music pair) line up. */
val TagGap = 18.dp

data class CellData(
    val col: Float,
    val row: Float,
    val w: Float = 1f,
    val h: Float = 1f,
    val outset: Dp = 0.dp
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@CellData
}

/** Fractional cells are allowed so tiles can glide between grid positions. */
fun Modifier.cell(col: Float, row: Float, w: Float = 1f, h: Float = 1f, outset: Dp = 0.dp): Modifier =
    this.then(CellData(col, row, w, h, outset))

fun Modifier.cell(col: Int, row: Int, w: Int = 1, h: Int = 1, outset: Dp = 0.dp): Modifier =
    cell(col.toFloat(), row.toFloat(), w.toFloat(), h.toFloat(), outset)

@Composable
fun TagGrid(
    modifier: Modifier = Modifier,
    columns: Int = 4,
    gap: Dp = TagGap,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val g = gap.roundToPx().toFloat()
        val cell = (constraints.maxWidth - g * (columns - 1)) / columns
        var rows = 0f
        val placed = measurables.map { m ->
            val d = m.parentData as? CellData ?: CellData(0f, 0f)
            rows = max(rows, d.row + d.h)
            val o = d.outset.roundToPx()
            val w = (d.w * cell + (d.w - 1f) * g).roundToInt() + 2 * o
            val h = (d.h * cell + (d.h - 1f) * g).roundToInt() + 2 * o
            Triple(m.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0))), d, o)
        }
        val height = if (rows <= 0f) 0 else (rows * cell + (rows - 1f) * g).roundToInt()
        layout(constraints.maxWidth, height) {
            placed.forEach { (p, d, o) ->
                p.place((d.col * (cell + g)).roundToInt() - o, (d.row * (cell + g)).roundToInt() - o)
            }
        }
    }
}

/**
 * A border that wraps a block of tiles and sits in the gutter, so the tiles stay on the grid.
 * The line is drawn behind its children so the label's black plate cuts the line cleanly.
 */
@Composable
fun TagFrame(label: String, color: Color, modifier: Modifier = Modifier, labelBias: Float = 0f) {
    Box(
        modifier.drawBehind {
            val w = 2.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(28.dp.toPx()),  // tile radius 22 + 6 outset = concentric
                style = Stroke(w)
            )
        }
    ) {
        Text(
            text = label,
            modifier = Modifier
                .align(BiasAlignment(labelBias, -1f))
                .offset(y = (-9).dp)
                .background(TagColors.Black)
                .padding(horizontal = 10.dp),
            style = TextStyle(
                fontFamily = TagFonts.mono,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = color
            )
        )
    }
}

// ==========================================================
// TILE
// ==========================================================

enum class TileState { Off, On, Static, Signal }

/** Which accent switched-on tiles and switches below this point use: 0 accent, 1 secondary, 2 tertiary. */
val LocalTileTone = compositionLocalOf { 0 }

/**
 * Gives everything inside it the chosen accent, but only when "Multicolour tiles" is on, unless
 * `always` is set (small fixed accents such as the upload/download pair). A palette with a single
 * accent shows no difference.
 */
@Composable
fun TileTone(tone: Int, always: Boolean = false, content: @Composable () -> Unit) {
    val effective = if (always || TagColors.Multicolour) tone else 0
    CompositionLocalProvider(LocalTileTone provides effective, content = content)
}

private fun fillOf(state: TileState, tone: Int): Color = when (state) {
    TileState.On -> TagColors.tone(tone)
    TileState.Off -> TagColors.Black
    TileState.Static -> TagColors.White
    TileState.Signal -> TagColors.Signal
}

private fun fgOf(state: TileState, tone: Int): Color = when (state) {
    TileState.Off -> TagColors.White
    TileState.On -> TagColors.onTone(tone)
    TileState.Static -> TagColors.Black
    TileState.Signal -> TagColors.OnSignal
}

private fun edgeOf(state: TileState, tone: Int): Color =
    if (state == TileState.Off) TagColors.Outline else fillOf(state, tone)

/** The one decelerating curve used for reveals and transitions. */
object TagEase { val Reveal = CubicBezierEasing(0.2f, 0f, 0f, 1f) }

private const val FLOOD_EDGE = 0.22f

/** Size of one grain in physical pixels: 1 is the finest the screen can draw. */
private const val FLOOD_GRAIN_PX = 1f

/**
 * Grainy dissolve. Every pixel gets a random threshold; the front sweeps out from the touch
 * point and flips pixels whose threshold it has passed, so the edge is a speckled band
 * instead of a hard circle.
 */
private const val FLOOD_AGSL = """
uniform float2 touch;
uniform float reach;
uniform float prog;
uniform float grain;
uniform float band;
layout(color) uniform half4 fromColor;
layout(color) uniform half4 toColor;

float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

half4 main(float2 fragCoord) {
    float d = distance(fragCoord, touch) / reach;
    float n = hash(floor(fragCoord / grain));
    float t = prog * (1.0 + band);
    float covered = clamp((t - d) / band, 0.0, 1.0);
    half m = half(step(n, covered));
    return mix(fromColor, toColor, m);
}
"""

/**
 * Motion that lives here:
 *  - springy squish while pressed
 *  - the new state grains outward from the exact point you touched; text and border change
 *    colour as the front reaches them, so contrast never breaks mid-way
 *  - the content gives a small lift while the fill travels
 *  - tiles spring in on first show, cascading down and across the screen
 * Everything snaps instead when Reduce motion is on or the system animation scale is 0.
 */
@Composable
fun TagTile(
    modifier: Modifier = Modifier,
    shape: Shape = TagShapes.Rect,
    state: TileState,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(fg: Color) -> Unit
) {
    val view = LocalView.current
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val tone = LocalTileTone.current
    val systemScale = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
    val animate = TagMotion.enabled && systemScale > 0f

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && animate) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )

    // `base` is what is painted underneath; `target` grains over it from `origin`.
    var base by remember { mutableStateOf(state) }
    var target by remember { mutableStateOf(state) }
    val reveal = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    var tileSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(state) {
        if (state != target) {
            if (!animate) {
                base = state; target = state; reveal.snapTo(1f)
            } else {
                base = target
                target = state
                reveal.snapTo(0f)
                reveal.animateTo(1f, tween(520, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
                base = state
            }
            origin = Offset.Unspecified   // programmatic changes flood from the centre
        }
    }

    // Colours follow the front: they flip when it reaches the middle of the tile.
    val t = reveal.value
    val o = if (origin.isSpecified) origin else Offset(tileSize.width / 2f, tileSize.height / 2f)
    val far = hypot(maxOf(o.x, tileSize.width - o.x), maxOf(o.y, tileSize.height - o.y)).coerceAtLeast(1f)
    val toCentre = hypot(tileSize.width / 2f - o.x, tileSize.height / 2f - o.y)
    val crossAt = (toCentre / far + FLOOD_EDGE * 0.5f) / (1f + FLOOD_EDGE)
    val mix = if (base == target) 1f else ((t - (crossAt - 0.1f)) / 0.2f).coerceIn(0f, 1f)
    val fg = lerp(fgOf(base, tone), fgOf(target, tone), mix)
    val edgeColor = lerp(edgeOf(base, tone), edgeOf(target, tone), mix)

    val shader = remember { RuntimeShader(FLOOD_AGSL) }

    // Entrance: delay grows with distance from the top-left, capped so nothing feels slow.
    val enter = remember { Animatable(0f) }
    var enterDelay by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(enterDelay) {
        if (enterDelay >= 0) {
            if (!animate) enter.snapTo(1f) else {
                delay(enterDelay)
                enter.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow))
            }
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { c ->
                tileSize = c.size
                if (enterDelay < 0) {
                    val pos = c.positionInRoot()
                    enterDelay = ((pos.y + pos.x * 0.35f) / density.density * 0.3f).toLong().coerceIn(0L, 420L)
                }
            }
            .graphicsLayer {
                val e = enter.value
                val s = press * (0.88f + 0.12f * e)
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 36f
            }
            .clip(shape)
            .drawBehind {
                val p = reveal.value
                if (p >= 1f) {
                    drawRect(fillOf(target, tone))
                } else {
                    val origin2 = if (origin.isSpecified) origin else center
                    val far2 = hypot(maxOf(origin2.x, size.width - origin2.x), maxOf(origin2.y, size.height - origin2.y))
                    shader.setFloatUniform("touch", origin2.x, origin2.y)
                    shader.setFloatUniform("reach", far2.coerceAtLeast(1f))
                    shader.setFloatUniform("prog", p)
                    shader.setFloatUniform("grain", FLOOD_GRAIN_PX)
                    shader.setFloatUniform("band", FLOOD_EDGE)
                    shader.setColorUniform("fromColor", fillOf(base, tone).toArgb())
                    shader.setColorUniform("toColor", fillOf(target, tone).toArgb())
                    drawRect(ShaderBrush(shader))
                }
            }
            .border(2.dp, edgeColor, shape)
            .pointerInput(Unit) {
                // Watch only: remember where the finger landed, never consume.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    origin = down.position
                }
            }
            .then(
                if (onClick != null) Modifier
                    .semantics {
                        if (state == TileState.On || state == TileState.Off) toggleableState = ToggleableState(state == TileState.On)
                    }
                    .clickable(
                        interactionSource = source,
                        indication = null,
                        role = if (state == TileState.On || state == TileState.Off) Role.Switch else Role.Button
                    ) {
                        view.tick(
                            when (state) {
                                TileState.Off -> Tick.On
                                TileState.On -> Tick.Off
                                TileState.Static, TileState.Signal -> Tick.Press
                            }
                        )
                        onClick()
                    }
                else Modifier
            )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val v = reveal.value
                    val pop = if (v < 1f) 1f + 0.06f * sin(PI.toFloat() * v) else 1f
                    scaleX = pop
                    scaleY = pop
                }
        ) { content(fg) }
    }
}

@Composable
fun TagIcon(res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Marks a tile as "has options". Turns to point down when open. */
@Composable
fun ChevronButton(open: Boolean, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    val rot by animateFloatAsState(
        if (open) 90f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "chevron"
    )
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable { view.tick(Tick.Press); onClick() },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(R.drawable.ic_chevron_right, tint, Modifier.rotate(rot), size = 20.dp)
    }
}

@Composable
fun TagSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackOn: Color = TagColors.tone(LocalTileTone.current),
    thumbOn: Color = TagColors.onTone(LocalTileTone.current),
    trackOff: Color = TagColors.White,
    thumbOff: Color = TagColors.Black
) {
    val view = LocalView.current
    val x by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "thumb"
    )
    val track by animateColorAsState(if (checked) trackOn else trackOff, label = "track")
    val thumb by animateColorAsState(if (checked) thumbOn else thumbOff, label = "thumbColor")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .clickable {
                view.tick(if (checked) Tick.Off else Tick.On)
                onChange(!checked)
            }
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(22.dp)
                .clip(CircleShape)
                .background(thumb)
        )
    }
}

// ==========================================================
// CONNECTED GROUPS (Material 3 Expressive)
// Buttons and tiles that sit side by side with tight inner corners open every corner out to a full round
// when pressed, then spring back.
// ==========================================================

/** A rounded rectangle at the given corner radii, opening to a full half-height round as `t` goes 0 to 1. */
class PressCornerShape(
    private val tl: Dp, private val tr: Dp, private val br: Dp, private val bl: Dp, private val t: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val full = min(size.width, size.height) / 2f
        fun r(d: Dp): CornerRadius {
            val from = with(density) { d.toPx() }.coerceAtMost(full)
            val v = from + (full - from) * t
            return CornerRadius(v, v)
        }
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, r(tl), r(tr), r(br), r(bl)))
    }
}

/** 0 at rest, 1 while pressed, animated with a spring. Feed it to [PressCornerShape]. */
@Composable
fun pressMorph(source: InteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val t by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (TagMotion.enabled) spring(dampingRatio = 0.55f, stiffness = 650f) else snap(),
        label = "pressMorph"
    )
    return t
}

/** A row in a connected block: big corners on the block's outside, tight (6dp) where rows meet. */
fun connectedShape(index: Int, count: Int): Shape {
    val big = CornerSize(22.dp)
    val small = CornerSize(6.dp)
    val top = if (index == 0) big else small
    val bottom = if (index == count - 1) big else small
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}
