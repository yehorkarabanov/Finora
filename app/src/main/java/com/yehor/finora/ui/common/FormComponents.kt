package com.yehor.finora.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.list.GroupedCardShape

// Shared building blocks of the full-screen edit forms (account, category, recurring): Obsidian top bar,
// scrolling column of grouped cards and a pinned full-width mint CTA. Row styling reuses the editor's
// ShelfRow / ShelfIcon / ShelfDivider so every form reads like the transaction editor.

/**
 * Scaffold of an edit form: [OrganizeTopBar], the scrolling [content] with 18dp margins and [bottomBar]
 * (normally a [FormCtaBar]). The floating nav bar, when the route shows it, is cleared by the shell.
 */
@Composable
internal fun FormScreen(
    title: String,
    onBack: () -> Unit,
    bottomBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier.imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OrganizeTopBar(title, onBack) },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** Pinned bottom bar: an optional centered [error] line above the full-width pill CTA. */
@Composable
internal fun FormCtaBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        PrimaryButton(text = label, onClick = onClick, enabled = enabled)
    }
}

/** Red outline over a [com.yehor.finora.designsystem.component.list.GroupedCard] holding an invalid field. */
@Composable
internal fun Modifier.errorOutline(isError: Boolean): Modifier =
    if (isError) border(1.dp, MaterialTheme.colorScheme.error, GroupedCardShape) else this

/** A validation or helper line under a field. */
@Composable
internal fun FieldNote(text: String, isError: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * A card row with a leading [icon], a muted [label] and an inline text field; [notes] holds the
 * validation lines below the field.
 */
@Composable
internal fun FormTextRow(
    icon: @Composable () -> Unit,
    label: String?,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        capitalization = KeyboardCapitalization.Sentences,
        imeAction = ImeAction.Done,
    ),
    trailing: (@Composable () -> Unit)? = null,
    notes: @Composable ColumnScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                maxLines = maxLines,
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(hint, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        inner()
                    }
                },
            )
            notes()
        }
        trailing?.invoke()
    }
}
