package com.yehor.finora.ui.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.AmountStyle
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge

// The Accounts tab: each account's money in, out and net over the period.

internal fun LazyListScope.accountItems(rows: List<AccountPeriodView>, actions: StatisticsActions) {
    if (rows.isEmpty()) {
        item(key = "accounts_empty") {
            Text(
                stringResource(R.string.stats_accounts_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val (archived, active) = rows.partition { it.account.status == AccountStatus.ARCHIVED }
    if (active.isNotEmpty()) {
        item(key = "accounts_active") { AccountPeriodCard(active, onOpen = actions.onOpenAccountAnalytics) }
    }
    if (archived.isNotEmpty()) {
        item(key = "accounts_archived") {
            Column {
                SectionHeader(stringResource(R.string.accounts_archived_section, archived.size))
                AccountPeriodCard(archived, onOpen = actions.onOpenAccountAnalytics, Modifier.alpha(0.6f))
            }
        }
    }
}

/** Grouped rows: icon, name, "Income +x · Expenses −y", and the net change; a row opens account analytics. */
@Composable
internal fun AccountPeriodCard(views: List<AccountPeriodView>, onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val finance = MaterialTheme.finance
    GroupedCard(modifier.fillMaxWidth()) {
        views.forEach { view ->
            val account = view.account
            val row = view.row
            val net = row.netChange.amountMinor
            ListRow(
                icon = { IconBadge(account.icon, account.color, ColorPalette.ACCOUNT_DEFAULT, size = 38) },
                title = account.name,
                subtitle = stringResource(R.string.stats_account_income) + " " + row.income.format() + " · " +
                    stringResource(R.string.stats_account_expense) + " " + row.expense.format(),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.End) {
                            AmountText(
                                signedMoney(row.netChange),
                                style = AmountStyle,
                                color = when {
                                    net > 0 -> finance.income
                                    net < 0 -> finance.negative
                                    else -> scheme.onSurface
                                },
                                animate = false,
                            )
                            Text(
                                stringResource(R.string.stats_account_net),
                                style = MaterialTheme.typography.labelSmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            AppIcons.Next,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                },
                onClick = { onOpen(account.id) },
            )
        }
    }
}
