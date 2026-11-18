package com.yehor.finora.designsystem.theme

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import java.util.Random

private const val DARK_GRAIN_ALPHA = 0.05f
private const val LIGHT_GRAIN_ALPHA = 0.035f

/** Deterministic noise tile: [size]x[size] ARGB pixels, luminance noise, alpha 255, seeded so previews are stable. */
fun grainPixels(size: Int = TILE_SIZE, seed: Long = 7L): IntArray {
    val random = Random(seed)
    return IntArray(size * size) {
        val l = random.nextInt(256)
        (0xFF shl 24) or (l shl 16) or (l shl 8) or l
    }
}

private const val TILE_SIZE = 128

/** Process-wide grain brush; the tile bitmap is built once, on first use. */
val GrainBrush: ShaderBrush by lazy {
    val bitmap = Bitmap.createBitmap(grainPixels(TILE_SIZE), TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()
    ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated))
}

/** Default grain alpha for the current theme (dark 0.05, light 0.035). */
@Composable
fun rememberGrainAlpha(): Float = if (MaterialTheme.finance.isDark) DARK_GRAIN_ALPHA else LIGHT_GRAIN_ALPHA

/**
 * Fills this node with [brush] (clipped to [shape]) and film grain, in one offscreen layer. The grain is
 * painted with SrcAtop, so it follows the brush's alpha and stays invisible where the brush is transparent.
 */
@Composable
fun Modifier.grainyBackground(brush: Brush, shape: Shape = RectangleShape): Modifier {
    val a = rememberGrainAlpha()
    return graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen).drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline, brush)
        drawOutline(outline, GrainBrush, alpha = a, blendMode = BlendMode.SrcAtop)
        drawContent()
    }
}

/**
 * Draws grain inside a Canvas, clipped to [path] when given. Must run inside an offscreen layer
 * (`graphicsLayer(compositingStrategy = Offscreen)`) so SrcAtop masks it by what is already drawn.
 */
fun DrawScope.drawGrain(brush: ShaderBrush, alpha: Float, path: Path? = null) {
    if (path != null) {
        clipPath(path) { drawRect(brush, alpha = alpha, blendMode = BlendMode.SrcAtop) }
    } else {
        drawRect(brush, alpha = alpha, blendMode = BlendMode.SrcAtop)
    }
}
