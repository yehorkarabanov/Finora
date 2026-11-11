package com.yehor.finora.designsystem.component.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import kotlinx.coroutines.delay

data class SpeedDialItem(val label: String, val icon: ImageVector, val onClick: () -> Unit, val primary: Boolean)

private const val STAGGER_MS = 50L

/**
 * Floating add button above the nav bar. Fills its parent so the scrim can dim the whole screen;
 * place it as the last child of the shell Box (it never consumes touches while collapsed).
 * [speedDial] is listed top to bottom; the last item sits nearest the button and appears first.
 */
@Composable
fun AddFab(
    mode: FabMode,
    expanded: Boolean,
    onToggle: () -> Unit,
    label: String,
    speedDial: List<SpeedDialItem>?,
    modifier: Modifier = Modifier,
) {
    val reduce = rememberReduceMotion()
    val haptics = LocalHapticFeedback.current
    val hasDial = !speedDial.isNullOrEmpty()
    val open = expanded && hasDial

    LaunchedEffect(open) { if (open) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    BackHandler(enabled = open, onBack = onToggle)

    Box(modifier.fillMaxSize()) {
        val scrim by animateFloatAsState(if (open) 1f else 0f, label = "fabScrim")
        if (scrim > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .alpha(scrim)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 22.dp, bottom = 84.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            speedDial?.forEachIndexed { index, item ->
                DialRow(item, visible = open, delayMs = (speedDial.lastIndex - index) * STAGGER_MS, reduce = reduce) {
                    item.onClick()
                }
            }
            Fab(mode = if (open) FabMode.COLLAPSED else mode, open = open, label = label, reduce = reduce, onClick = onToggle)
        }
    }
}

@Composable
private fun Fab(mode: FabMode, open: Boolean, label: String, reduce: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val hidden by animateFloatAsState(
        if (mode == FabMode.HIDDEN) 1f else 0f,
        animationSpec = if (reduce) snap() else FinoraMotion.bouncy,
        label = "fabHidden",
    )
    val rotation by animateFloatAsState(
        if (open) 135f else 0f,
        animationSpec = if (reduce) snap() else FinoraMotion.bouncy,
        label = "fabRotation",
    )
    val container by animateColorAsState(if (open) scheme.surfaceContainerHighest else scheme.primary, label = "fabBg")
    val content by animateColorAsState(if (open) scheme.onSurface else scheme.onPrimary, label = "fabFg")
    val coerced = hidden.coerceIn(0f, 1f)
    Row(
        modifier = Modifier
            .graphicsLayer {
                translationY = coerced * 80.dp.toPx()
                alpha = 1f - coerced
            }
            .scale(1f - 0.6f * coerced)
            .shadow(if (open) 0.dp else 8.dp, PillShape, spotColor = MaterialTheme.finance.accentGlow)
            .heightIn(min = 52.dp)
            .background(container, PillShape)
            .clickable(role = Role.Button, enabled = mode != FabMode.HIDDEN, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.Add,
            contentDescription = if (mode == FabMode.EXTENDED) null else label,
            tint = content,
            modifier = Modifier.size(24.dp).rotate(rotation),
        )
        AnimatedVisibility(
            visible = mode == FabMode.EXTENDED,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
        }
    }
}

@Composable
private fun DialRow(item: SpeedDialItem, visible: Boolean, delayMs: Long, reduce: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            if (!reduce) delay(delayMs)
            shown = true
        } else {
            shown = false
        }
    }
    val progress by animateFloatAsState(
        if (shown) 1f else 0f,
        animationSpec = if (reduce) snap() else FinoraMotion.bouncy,
        label = "dialItem",
    )
    if (progress <= 0.001f && !shown) return
    Row(
        modifier = Modifier
            .graphicsLayer {
                translationY = (1f - progress) * 20.dp.toPx()
                alpha = progress.coerceIn(0f, 1f)
                scaleX = 0.8f + 0.2f * progress
                scaleY = 0.8f + 0.2f * progress
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            item.label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            modifier = Modifier
                .background(scheme.surfaceContainerHigh, PillShape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(if (item.primary) scheme.primary else scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = if (item.primary) scheme.onPrimary else scheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
