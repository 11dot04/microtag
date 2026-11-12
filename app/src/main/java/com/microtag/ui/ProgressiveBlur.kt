package com.microtag.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.asComposeRenderEffect

/**
 * Progressive blur: content gets blurrier toward an edge instead of being cut or faded to nothing, so nothing hard
 * shows and the tiles keep their own colour (a plain fade over black turned the lime ones olive).
 *
 * One AGSL shader on the whole scrolling layer. Each pixel's blur radius depends only on how far down it is: full for
 * the first [holdPx] from the top, easing to nothing by [bandPx]. Below that it is a single sample, so the cost is
 * small. Needs Android 13 (AGSL); older versions get a short gradient instead (see DashboardScreen).
 */
object ProgressiveBlur {
    private const val AGSL = """
uniform shader content;
uniform float holdPx;
uniform float bandPx;
uniform float maxR;
uniform float strength;
half4 main(float2 p) {
    float t = 1.0 - smoothstep(holdPx, bandPx, p.y);
    float r = maxR * t * strength;
    half4 c = content.eval(p);
    if (r < 0.75) return c;
    half4 acc = half4(0.0);
    float ws = 0.0;
    for (int i = 0; i < 24; i++) {
        float a = float(i) * 2.39996;
        float d = sqrt((float(i) + 0.5) / 24.0) * r;
        float w = 1.0 - 0.5 * (d / r);
        acc += content.eval(p + float2(cos(a), sin(a)) * d) * w;
        ws += w;
    }
    half4 b = acc / ws;
    // A little dimming toward the very top so the numeral reads over it; the blur does most of the work.
    return b * (1.0 - 0.35 * t * strength);
}
"""

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 33

    fun newShader(): RuntimeShader? = if (supported) RuntimeShader(AGSL) else null

    /** Sets the effect on a layer. [strength] is 0 (nothing) to 1 (full). */
    fun apply(scope: GraphicsLayerScope, shader: RuntimeShader, holdPx: Float, bandPx: Float, maxRadiusPx: Float, strength: Float) {
        if (strength <= 0.001f) { scope.renderEffect = null; return }
        shader.setFloatUniform("holdPx", holdPx)
        shader.setFloatUniform("bandPx", bandPx)
        shader.setFloatUniform("maxR", maxRadiusPx)
        shader.setFloatUniform("strength", strength)
        scope.renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
