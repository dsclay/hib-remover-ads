package com.dex.adfree

import android.os.Build
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** Dot-matrix display font (res/font/ndot.ttf) used for titles and counters, in the style of Nothing OS. */
val Dot = FontFamily(Font(R.font.ndot))

/** "Liquid glass" look: translucent panels with a faint bright edge, over a blurred page. */
val GlassFill = Color(0xFF2C2C2E).copy(alpha = 0.55f)
val GlassScrim = Color(0xFF000000).copy(alpha = 0.5f)
val GlassDialog = Color(0xFF1C1C1E).copy(alpha = 0.8f)

fun glassBorder(): BorderStroke =
    BorderStroke(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))))

/** Blurs a view (Android 12+); used on the page while a panel is open on top of it. */
private val blurState = java.util.WeakHashMap<View, Boolean>()

fun blurView(v: View, on: Boolean) {
    if ((blurState[v] ?: false) == on) return // called on every recomposition; only touch the view when it changes
    blurState[v] = on
    if (Build.VERSION.SDK_INT >= 31) {
        v.setRenderEffect(
            if (on) android.graphics.RenderEffect.createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP) else null,
        )
    }
}
