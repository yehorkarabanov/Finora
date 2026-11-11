package com.yehor.finora.designsystem.component.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline

/** Equal-width segmented selector in a pill container; the selected segment is filled. */
@Composable
fun SegmentedPill(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, PillShape)
            .border(hairline(MaterialTheme.finance.isDark), PillShape)
            .padding(horizontal = 4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selected
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                label = "segmentBg",
            )
            // 48dp hit area; the filled pill inside keeps the compact look.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(PillShape).background(container),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
