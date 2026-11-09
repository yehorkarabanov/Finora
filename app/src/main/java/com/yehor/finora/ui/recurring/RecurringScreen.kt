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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.yehor.finora.ui.common.AppIcons
import com.yehor.finora.ui.common.ColorPalette
import com.yehor.finora.ui.common.IconBadge
import com.yehor.finora.ui.common.IconSize
import com.yehor.finora.ui.theme.PreviewTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringContent(
    state: RecurringListUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.more_recurring)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(AppIcons.Add, contentDescription = stringResource(R.string.recurring_add))
            }
        },
    ) { padding ->
        val sections = state.sections
        if (!state.loading && sections.isEmpty) {
            RecurringEmptyState(onAdd, Modifier.fillMaxSize().padding(padding).padding(16.dp))
        } else {
            val blocked = RecurringListLogic.blockedCount(sections)
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (blocked > 0) {
                    item(key = "attention") {
                        RecurringAttentionBanner(blocked, Modifier.padding(bottom = 12.dp))
                    }
                }
                section(R.string.recurring_section_active, sections.active, onOpen)
                section(R.string.recurring_section_paused, sections.paused, onOpen)
                section(R.string.recurring_section_ended, sections.ended, onOpen)
            }
        }
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
        RecurringCard(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Recurring,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Text(
                    stringResource(R.string.recurring_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.recurring_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onAdd) { Text(stringResource(R.string.recurring_empty_cta)) }
            }
        }
    }
}

private fun LazyListScope.section(
    titleRes: Int,
    rows: List<RecurringRow>,
    onOpen: (Long) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$titleRes") {
        RecurringSectionHeader(
            stringResource(R.string.recurring_section_with_count, stringResource(titleRes), rows.size),
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
        )
    }
    rows.forEachIndexed { index, row ->
        item(key = row.schedule.id) {
            RecurringListRow(row, recurringGroupShape(index, rows.size), onOpen)
        }
    }
}

@Composable
internal fun RecurringListRow(
    row: RecurringRow,
    shape: androidx.compose.ui.graphics.Shape,
    onOpen: (Long) -> Unit,
) {
    val schedule = row.schedule
    val line = RecurringListLogic.statusLine(row)
    val isBlocked = line is StatusLine.Blocked
    val dimmed = row.state != ScheduleDisplayState.ACTIVE
    RecurringRowCard(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        shape = shape,
        onClick = { onOpen(schedule.id) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecurringScheduleBadge(schedule, 44)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    schedule.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.recurring_row_subtitle, schedule.account.name, ruleText(schedule.rule)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(
                        if (isBlocked) AppIcons.Warning else AppIcons.Calendar,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(statusLineText(line), style = MaterialTheme.typography.bodySmall, color = tint)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    RecurringListLogic.signedAmount(schedule.kind, schedule.money),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else recurringSignedAmountColor(schedule.kind),
                )
                when (row.state) {
                    ScheduleDisplayState.PAUSED -> RecurringPill(stringResource(R.string.recurring_section_paused))
                    ScheduleDisplayState.ENDED -> RecurringPill(stringResource(R.string.recurring_section_ended))
                    ScheduleDisplayState.ACTIVE -> Unit
                }
            }
        }
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
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun RecurringListPreview() = RecurringListPreviewContent(dark = false)

@Preview(showBackground = true, heightDp = 700, backgroundColor = 0xFF0F1412)
@Composable
private fun RecurringListDarkPreview() = RecurringListPreviewContent(dark = true)
