package com.yehor.finora.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.account.Account
import com.yehor.finora.domain.account.AccountType
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.AccountStatus
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.money.CurrencyRegistry
import com.yehor.finora.domain.money.Money
import com.yehor.finora.domain.operation.TransactionKind
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.RecurrenceRule
import com.yehor.finora.domain.recurring.RecurrenceUnit
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.ScheduleStatus
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.theme.AmountStyle
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.analytics.UpcomingOccurrences
import com.yehor.finora.ui.common.OrganizeTopBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import java.time.temporal.ChronoUnit
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.designsystem.theme.PreviewTheme
import java.time.LocalDate

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    viewModel: RecurringListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RecurringContent(state, onBack, onAdd, onOpen)
}

@Composable
fun RecurringContent(
    state: RecurringListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            OrganizeTopBar(stringResource(R.string.more_recurring), onBack) {
                RoundIconButton(AppIcons.Add, stringResource(R.string.recurring_add), onAdd)
            }
        },
    ) { padding ->
        val sections = state.sections
        if (!state.loading && sections.isEmpty) {
            RecurringEmptyState(onAdd, Modifier.fillMaxSize().padding(padding).padding(18.dp))
        } else {
            val blocked = RecurringListLogic.blockedCount(sections)
            val totals = remember(sections.active, today) {
                val blockedIds = sections.active.filter { it.issue != null }.map { it.schedule.id }.toSet()
                nextThirtyDaysByCurrency(
                    UpcomingOccurrences.build(sections.active.map { it.schedule }, blockedIds, today, limit = UPCOMING_LIMIT),
                    today,
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (blocked > 0) {
                    item(key = "attention") { RecurringAttentionBanner(blocked) }
                }
                if (totals.isNotEmpty()) {
                    item(key = "next30") { NextThirtyDaysCard(totals) }
                }
                if (sections.active.isNotEmpty()) {
                    item(key = "upcoming") {
                        RecurringGroup(R.string.recurring_list_upcoming, sections.active, today, onOpen)
                    }
                }
                if (sections.paused.isNotEmpty()) {
                    item(key = "paused") {
                        RecurringGroup(R.string.recurring_section_paused, sections.paused, today, onOpen, Modifier.alpha(0.6f))
                    }
                }
                if (sections.ended.isNotEmpty()) {
                    item(key = "ended") {
                        RecurringGroup(R.string.recurring_section_ended, sections.ended, today, onOpen, Modifier.alpha(0.6f))
                    }
                }
            }
        }
    }
}

/** Schedules per currency with an occurrence inside the horizon; enough for any real list. */
private const val UPCOMING_LIMIT = 1000

@Composable
private fun NextThirtyDaysCard(totals: List<ThirtyDayTotals>) {
    val finance = MaterialTheme.finance
    GroupedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader(stringResource(R.string.recurring_next30_title), Modifier.padding(0.dp))
            totals.forEach { total ->
                Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    NextThirtyDaysFigure(R.string.recurring_next30_out, "−" + total.out.format(), finance.expense, Modifier.weight(1f))
                    NextThirtyDaysFigure(R.string.recurring_next30_in, "+" + total.income.format(), finance.income, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NextThirtyDaysFigure(labelRes: Int, amount: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountText(amount, MaterialTheme.typography.titleLarge, color, animate = false)
    }
}

@Composable
private fun RecurringAttentionBanner(count: Int, modifier: Modifier = Modifier) {
    RecurringCard(modifier, color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            Modifier.padding(16.dp).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(
                pluralStringResource(R.plurals.recurring_attention_banner, count, count),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun RecurringEmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        GroupedCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RoundIcon(AppIcons.Recurring, MaterialTheme.colorScheme.primary, size = 64.dp)
                Text(
                    stringResource(R.string.recurring_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.recurring_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                PrimaryButton(stringResource(R.string.recurring_empty_cta), onAdd)
            }
        }
    }
}

/** A titled [GroupedCard] with one [RecurringListRow] per schedule. */
@Composable
private fun RecurringGroup(
    titleRes: Int,
    rows: List<RecurringRow>,
    today: LocalDate,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        SectionHeader(stringResource(titleRes))
        GroupedCard(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                RecurringListRow(row, today, onOpen)
            }
        }
    }
}

@Composable
internal fun RecurringListRow(
    row: RecurringRow,
    today: LocalDate,
    onOpen: (Long) -> Unit,
) {
    val schedule = row.schedule
    val line = RecurringListLogic.statusLine(row)
    val isBlocked = line is StatusLine.Blocked
    val paused = row.state == ScheduleDisplayState.PAUSED
    val rule = ruleText(schedule.rule)
    val subtitle = when (line) {
        is StatusLine.Next -> stringResource(R.string.recurring_row_subtitle, rule, dueText(line.date, today))
        else -> stringResource(R.string.recurring_row_subtitle, rule, statusLineText(line))
    }
    val resumeLabel = stringResource(R.string.recurring_action_resume)
    val signed = RecurringListLogic.signedAmount(schedule.kind, schedule.money)
    ListRow(
        icon = { RecurringScheduleBadge(schedule, 40) },
        title = schedule.name,
        subtitle = subtitle,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        signed,
                        style = AmountStyle,
                        color = if (row.state == ScheduleDisplayState.ACTIVE) {
                            recurringSignedAmountColor(schedule.kind)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                    val date = (line as? StatusLine.Next)?.date ?: (line as? StatusLine.Ended)?.date
                    if (isBlocked) {
                        Icon(
                            AppIcons.Warning,
                            contentDescription = statusLineText(line),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    } else if (date != null) {
                        Text(
                            formatScheduleDate(date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                if (paused) {
                    // Resuming can need a choice about the occurrences that came due, which the detail screen offers.
                    Switch(
                        checked = false,
                        onCheckedChange = { onOpen(schedule.id) },
                        modifier = Modifier.semantics { contentDescription = resumeLabel },
                    )
                }
            }
        },
        modifier = Modifier.semantics(mergeDescendants = true) {},
        onClick = { onOpen(schedule.id) },
    )
}

/** "in 3 days" / "today"; a date already behind [today] (overdue, not yet generated) reads as today. */
@Composable
private fun dueText(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return if (days <= 0) {
        stringResource(R.string.recurring_due_today)
    } else {
        pluralStringResource(R.plurals.recurring_due_in_days, days, days)
    }
}

/** Category badge when the schedule has a category, otherwise the Income / Expense kind badge. */
@Composable
internal fun RecurringScheduleBadge(schedule: RecurringSchedule, size: Int) {
    val category = schedule.category
    if (category != null) {
        IconBadge(
            icon = category.icon,
            color = category.color,
            fallbackColor = ColorPalette.defaultFor(category.type),
            size = size,
        )
    } else {
        RecurringKindBadge(schedule.kind, size.dp)
    }
}

// ---- Previews ----

private val PreviewUsd = CurrencyRegistry.get("USD")

internal fun previewSchedule(
    id: Long = 1,
    name: String = "Rent",
    kind: TransactionKind = TransactionKind.EXPENSE,
    category: Category? = Category(1, "Housing", "home", null, CategoryType.EXPENSE, null, 0),
    status: ScheduleStatus = ScheduleStatus.ACTIVE,
    next: LocalDate? = LocalDate.of(2026, 12, 1),
) = RecurringSchedule(
    id = id,
    name = name,
    kind = kind,
    account = Account(1, "Main", PreviewUsd, AccountType.BANK, null, null, null, AccountStatus.ACTIVE, 0),
    money = Money(1_250_00, PreviewUsd),
    category = category,
    categoryPath = listOfNotNull(category),
    tags = emptyList(),
    rule = RecurrenceRule(RecurrenceUnit.MONTH, 1),
    startDate = LocalDate.of(2026, 1, 1),
    endDate = null,
    nextDate = next,
    status = status,
    createdAt = 0,
    updatedAt = 0,
)

@Composable
private fun RecurringListPreviewContent(dark: Boolean) {
    PreviewTheme(dark) {
        val sections = RecurringListLogic.sections(
            listOf(
                previewSchedule(1),
                previewSchedule(2, "Salary", TransactionKind.INCOME, category = null, next = LocalDate.of(2026, 12, 5)),
                previewSchedule(3, "Gym", status = ScheduleStatus.PAUSED),
            ),
            mapOf(1L to GenerationIssue.ACCOUNT_ARCHIVED),
        )
        RecurringContent(
            state = RecurringListUiState(loading = false, sections = sections),
            onBack = {},
            onAdd = {},
            onOpen = {},
            today = LocalDate.of(2026, 11, 11),
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun RecurringListPreview() = RecurringListPreviewContent(dark = false)

@Preview(showBackground = true, heightDp = 700, backgroundColor = 0xFF0F1412)
@Composable
private fun RecurringListDarkPreview() = RecurringListPreviewContent(dark = true)
