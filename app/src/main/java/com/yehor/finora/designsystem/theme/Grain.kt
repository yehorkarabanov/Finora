package com.yehor.finora.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import java.util.Random

private const val DARK_GRAIN_ALPHA = 0.05f
private const val LIGHT_GRAIN_ALPHA = 0.035f

/** Deterministic noise tile: [size]x[size] ARGB pixels, luminance noise, alpha 255, seeded so previews are stable. */
fun grainPixels(size: Int = 128, seed: Long = 7L): IntArray {
    val random = Random(seed)
    return IntArray(size * size) {
        val l = random.nextInt(256)
        (0xFF shl 24) or (l shl 16) or (l shl 8) or l
    }
}

/** Brush that tiles the grain noise; remembered so the bitmap is built once. */
@Composable
fun rememberGrainBrush(): ShaderBrush = remember {
    val size = 128
    val bitmap = Bitmap.createBitmap(grainPixels(size), size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** Default grain alpha for the current theme (dark 0.05, light 0.035). */
@Composable
fun rememberGrainAlpha(): Float = if (MaterialTheme.finance.isDark) DARK_GRAIN_ALPHA else LIGHT_GRAIN_ALPHA

/** Overlays static film grain on this node's content. */
@Composable
fun Modifier.grain(alpha: Float? = null): Modifier {
    val brush = rememberGrainBrush()
    val a = alpha ?: rememberGrainAlpha()
    return drawWithContent {
        drawContent()
        drawRect(brush, alpha = a, blendMode = BlendMode.Overlay)
    }
}

/** Draws grain inside a Canvas, clipped to [path] when given. */
fun DrawScope.drawGrain(brush: ShaderBrush, alpha: Float, path: Path? = null) {
    if (path != null) {
        clipPath(path) { drawRect(brush, alpha = alpha, blendMode = BlendMode.Overlay) }
    } else {
        drawRect(brush, alpha = alpha, blendMode = BlendMode.Overlay)
    }
}
