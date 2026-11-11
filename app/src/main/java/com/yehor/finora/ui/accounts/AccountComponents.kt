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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.Balance
import com.yehor.finora.domain.money.Currency
import com.yehor.finora.domain.money.Money
import com.yehor.finora.ui.analytics.signedMoney
import com.yehor.finora.ui.common.ColorPalette
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.PaddingValues
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.theme.PreviewTheme

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

/** The account's own color, lifted for dark theme. */
@Composable
internal fun accountTint(account: Account): Color =
    MaterialTheme.finance.accent(ColorPalette.resolve(account.color, ColorPalette.ACCOUNT_DEFAULT))

/** A balance with the decimals dimmed and the currency symbol small; negative in the negative color. */
@Composable
internal fun AccountBalanceAmount(balance: Balance, style: TextStyle, modifier: Modifier = Modifier) {
    when (balance) {
        Balance.Overflow -> Text(
            stringResource(R.string.accounts_balance_overflow),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = modifier,
        )
        is Balance.Value -> {
            val money = balance.money
            val color = if (money.amountMinor < 0) MaterialTheme.finance.negative else MaterialTheme.colorScheme.onSurface
            Row(modifier, verticalAlignment = Alignment.Bottom) {
                AmountText(money.toPlainString(), style, color, animate = false)
                Text(" ${money.currency.symbol}", style = style.copy(fontSize = style.fontSize * 0.6f), color = color, maxLines = 1)
            }
        }
    }
}

/** "+₴3,240" in the income color, "−₴120" in the negative color, a muted dash for no change. */
@Composable
internal fun AccountChangeText(change: Long?, currency: Currency, modifier: Modifier = Modifier) {
    val finance = MaterialTheme.finance
    val (text, color) = when {
        change == null || change == 0L -> "—" to MaterialTheme.colorScheme.onSurfaceVariant
        change > 0 -> signedMoney(Money(change, currency)) to finance.income
        else -> signedMoney(Money(change, currency)) to finance.negative
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1, modifier = modifier)
}

/** Round icon button with its label underneath (account detail actions). */
@Composable
internal fun AccountRoundAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RoundIconButton(icon, label, onClick, enabled = enabled)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
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
        }
    }
}
