package com.yehor.finora.ui.operations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yehor.finora.R

// Shared pieces of the per-dimension filter sheets (categories, tags).

internal val FilterRowRadius = 20.dp
private val FilterRowInnerRadius = 4.dp

/** Show a search field once a list is longer than this. */
internal const val FILTER_SEARCH_THRESHOLD = 10

/** Rows of one list form a connected group: only the outer corners are rounded. */
internal fun filterGroupShape(index: Int, size: Int): Shape {
    val top = if (index == 0) FilterRowRadius else FilterRowInnerRadius
    val bottom = if (index == size - 1) FilterRowRadius else FilterRowInnerRadius
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** Title on the left, "Clear" (when something is selected) and "Done" on the right. */
@Composable
internal fun FilterSheetHeader(title: String, showClear: Boolean, onClear: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (showClear) TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear)) }
        TextButton(onClick = onDone) { Text(stringResource(R.string.selector_done)) }
    }
}

@Composable
internal fun FilterSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.action_remove)) } }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

internal data class SegmentOption(val label: String, val selected: Boolean, val color: Color, val onClick: () -> Unit)

/** The segmented pill track from the transaction form: selected segment = its color at 16%. */
@Composable
internal fun SegmentedTrack(options: List<SegmentOption>, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxWidth(), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { option ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .background(if (option.selected) option.color.copy(alpha = 0.16f) else Color.Transparent, CircleShape)
                        .clickable(role = Role.Tab, onClick = option.onClick)
                        .semantics { selected = option.selected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (option.selected) FontWeight.SemiBold else null,
                        color = if (option.selected) option.color else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
