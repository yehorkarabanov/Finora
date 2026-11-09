package com.yehor.finora.ui.accounts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.PreviewTheme

// Connected group: only its outer corners are rounded.
private val GroupRadius = 20.dp
private val GroupInnerRadius = 4.dp

internal val AccountGroupSingleShape = RoundedCornerShape(GroupRadius)
internal val AccountGroupTopShape = RoundedCornerShape(
    topStart = GroupRadius, topEnd = GroupRadius,
    bottomStart = GroupInnerRadius, bottomEnd = GroupInnerRadius,
)
internal val AccountGroupMiddleShape = RoundedCornerShape(GroupInnerRadius)
internal val AccountGroupBottomShape = RoundedCornerShape(
    topStart = GroupInnerRadius, topEnd = GroupInnerRadius,
    bottomStart = GroupRadius, bottomEnd = GroupRadius,
)

internal fun groupShape(index: Int, count: Int): Shape = when {
    count <= 1 -> AccountGroupSingleShape
    index == 0 -> AccountGroupTopShape
    index == count - 1 -> AccountGroupBottomShape
    else -> AccountGroupMiddleShape
}

@Composable
internal fun AccountSectionCard(
    shape: Shape,
    onClick: (() -> Unit)? = null,
    isError: Boolean = false,
    content: @Composable () -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val clickModifier = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = if (isError) BorderStroke(1.dp, errorColor) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(clickModifier.padding(14.dp)) { content() }
    }
}

@Composable
internal fun AccountsCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), content = content)
    }
}

@Composable
internal fun AccountsSectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(start = 12.dp, end = 4.dp)) {
                Text(actionLabel)
                Icon(AppIcons.Next, contentDescription = null, modifier = Modifier.size(IconSize.Small))
            }
        }
    }
}

@Composable
internal fun AccountLeadingIcon(icon: ImageVector) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.Default))
    }
}

@Composable
internal fun AccountPill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
internal fun AccountSmallPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.heightIn(min = 32.dp),
    ) {
        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
            if (text != null) Text(text, style = MaterialTheme.typography.labelLarge)
            icon?.invoke()
        }
    }
}

@Composable
internal fun AccountQuickAction(
    label: String,
    icon: ImageVector,
    container: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val alpha = if (enabled) 1f else 0.38f
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(container.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = contentColor.copy(alpha = alpha))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AccountsEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
            Button(onClick = onAction) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(IconSize.Small))
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
internal fun AccountBottomCta(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: (@Composable () -> Unit)? = null,
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) {
                Text(label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            secondary?.invoke()
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(showBackground = true)
@Composable
private fun AccountGroupLightPreview() = AccountGroupPreviewContent(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun AccountGroupDarkPreview() = AccountGroupPreviewContent(dark = true)

@Composable
private fun AccountGroupPreviewContent(dark: Boolean) {
    PreviewTheme(dark) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) { i ->
                AccountSectionCard(shape = groupShape(i, 3), onClick = {}) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AccountLeadingIcon(AppIcons.Account)
                        Text("Account ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        AccountPill("Bank")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AccountEmptyLightPreview() = AccountEmptyPreviewContent(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun AccountEmptyDarkPreview() = AccountEmptyPreviewContent(dark = true)

@Composable
private fun AccountEmptyPreviewContent(dark: Boolean) {
    PreviewTheme(dark) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountsEmptyState(
                icon = AppIcons.Account,
                title = "Add your first account",
                body = "Accounts hold your balances.",
                actionLabel = "Add account",
                onAction = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AccountQuickAction(
                    "Add", AppIcons.Add,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    enabled = true, onClick = {}, modifier = Modifier.weight(1f),
                )
                AccountQuickAction(
                    "Disabled", AppIcons.Account,
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    enabled = false, onClick = {}, modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
