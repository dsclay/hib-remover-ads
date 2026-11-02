package com.dex.adfree

import android.os.Build
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** "Liquid glass" look: translucent panels with a faint bright edge, over a blurred page. */
val GlassFill = Color(0xFF2B313B).copy(alpha = 0.55f)
val GlassScrim = Color(0xFF0B0D11).copy(alpha = 0.45f)
val GlassDialog = Color(0xFF232830).copy(alpha = 0.78f)

fun glassBorder(): BorderStroke =
    BorderStroke(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.06f))))

/** Blurs a view (Android 12+); used on the page while a panel is open on top of it. */
fun blurView(v: View, on: Boolean) {
    if (Build.VERSION.SDK_INT >= 31) {
        v.setRenderEffect(
            if (on) android.graphics.RenderEffect.createBlurEffect(40f, 40f, android.graphics.Shader.TileMode.CLAMP) else null,
        )
    }
}
