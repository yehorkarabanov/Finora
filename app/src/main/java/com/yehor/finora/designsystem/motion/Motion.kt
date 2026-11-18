package com.yehor.finora.designsystem.motion

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/** Shared animation specs for the Obsidian design language. */
object FinoraMotion {
    val press: SpringSpec<Float> = spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium)
    val sheet: TweenSpec<Float> = TweenSpec(durationMillis = 350, easing = FastOutSlowInEasing)
    val bouncy: SpringSpec<Float> = spring(0.6f, 400f)
    const val CHART_DRAW_MS = 500

    private fun spring(damping: Float, stiffness: Float) = SpringSpec<Float>(damping, stiffness)
}

/** True when the system animator duration scale is 0 (animations disabled): use instant/crossfade. */
@Composable
fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember(resolver) {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Springy press feedback: scales to 0.96 while [interactionSource] is pressed ([FinoraMotion.press]).
 * Stays at 1 when reduce-motion is on.
 */
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier = composed {
    val reduce = rememberReduceMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.96f else 1f,
        animationSpec = if (reduce) snap() else FinoraMotion.press,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
