package com.yehor.finora.ui.operations

import com.yehor.finora.designsystem.icon.AppIcons
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.yehor.finora.designsystem.component.button.PrimaryButton
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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

/** Title on the left and "Clear" (when something is selected) on the right; "Done" is the sheet's primary button. */
@Composable
internal fun FilterSheetHeader(title: String, showClear: Boolean, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (showClear) TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear)) }
    }
}

/** The sheet's closing action; filters apply immediately, so this only dismisses. */
@Composable
internal fun FilterSheetDone(onDone: () -> Unit) {
    PrimaryButton(
        text = stringResource(R.string.selector_done),
        onClick = onDone,
        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
    )
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
        leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            { IconButton(onClick = { onValueChange("") }) { Icon(AppIcons.Close, stringResource(R.string.action_remove)) } }
        } else {
            null
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}
