package com.yehor.finora.designsystem.component.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.motion.pressScale
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline

/**
 * Equal-width segmented selector in a pill track; the selected segment is a raised pill.
 * When not [enabled] the choice is fixed: the selected segment stays readable and the others are dimmed.
 */
@Composable
fun SegmentedPill(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = MaterialTheme.finance.isDark
    val selectedFill = if (isDark) scheme.surfaceContainerHighest else scheme.surfaceContainerLowest
    val reduce = rememberReduceMotion()
    val lightHairline = (hairline(false).brush as SolidColor).value
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow, PillShape)
            .border(hairline(isDark), PillShape)
            .padding(horizontal = 4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val container by animateColorAsState(
                if (isSelected) selectedFill else Color.Transparent,
                animationSpec = if (reduce) snap() else spring(),
                label = "segmentBg",
            )
            val borderAlpha by animateFloatAsState(
                if (isSelected && !isDark) 1f else 0f,
                animationSpec = if (reduce) snap() else spring(),
                label = "segmentBorder",
            )
            val interaction = remember { MutableInteractionSource() }
            // 48dp hit area without indication; feedback is drawn on the inner pill so it stays pill-shaped.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pressScale(interaction)
                        .clip(PillShape)
                        .background(container)
                        .border(BorderStroke(1.dp, lightHairline.copy(alpha = lightHairline.alpha * borderAlpha)), PillShape)
                        .indication(interaction, ripple()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = when {
                            isSelected -> scheme.onSurface
                            enabled -> scheme.onSurfaceVariant
                            else -> scheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}
