package com.yehor.finora.designsystem.component.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline

data class NavBarItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Floating 56dp pill nav bar. Inactive items are icon-only; the active one grows into a
 * primaryContainer pill with its label (item weights spring with [FinoraMotion.bouncy]).
 */
@Composable
fun FinoraNavBar(
    items: List<NavBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(PillShape)
            .background(scheme.surfaceContainer.copy(alpha = 0.95f), PillShape)
            .border(hairline(MaterialTheme.finance.isDark), PillShape)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val reduce = rememberReduceMotion()
        items.forEachIndexed { index, item ->
            val active = index == selectedIndex
            val weight by animateFloatAsState(
                targetValue = if (active) 2.1f else 1f,
                animationSpec = if (reduce) snap() else FinoraMotion.bouncy,
                label = "navWeight",
            )
            val container by animateColorAsState(
                if (active) scheme.primaryContainer else scheme.primaryContainer.copy(alpha = 0f),
                animationSpec = if (reduce) snap() else spring(stiffness = Spring.StiffnessMedium),
                label = "navPill",
            )
            val content = if (active) scheme.onPrimaryContainer else scheme.onSurfaceVariant
            // 48dp hit area around the 42dp visual pill.
            Box(
                modifier = Modifier
                    .weight(weight.coerceAtLeast(0.1f))
                    .height(48.dp)
                    .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(PillShape)
                    .background(container, PillShape),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = if (active) item.selectedIcon else item.icon,
                    contentDescription = if (active) null else item.label,
                    tint = content,
                    modifier = Modifier.size(20.dp),
                )
                AnimatedVisibility(
                    visible = active,
                    enter = if (reduce) EnterTransition.None else fadeIn() + expandHorizontally(),
                    exit = if (reduce) ExitTransition.None else fadeOut() + shrinkHorizontally(),
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = content,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            }
        }
    }
}
