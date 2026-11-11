package com.yehor.finora.ui.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.ScheduleStatus
import com.yehor.finora.domain.recurring.displayState
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.ui.common.TagChip
import com.yehor.finora.designsystem.theme.PreviewTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    viewModel: RecurringDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The schedule disappears after a successful delete.
    LaunchedEffect(state.isMissing) {
        if (state.isMissing) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recurring_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.Back, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    val schedule = state.schedule
                    if (schedule != null) {
                        IconButton(onClick = { onEdit(schedule.id) }) {
                            Icon(AppIcons.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.More, contentDescription = stringResource(R.string.recurring_detail_more))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = {
                                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                                    },
                                    leadingIcon = {
                                        Icon(AppIcons.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.onDeleteRequest()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        val schedule = state.schedule
        if (schedule == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        val displayState = schedule.displayState()

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.issue?.let { IssueBanner(schedule, it, state, viewModel) }
            RecurringHero(schedule)
            if (displayState != ScheduleDisplayState.ENDED) {
                ActionTiles(displayState, viewModel)
            }
            DetailsGroup(schedule)
            UpcomingCard(schedule, state.upcoming, displayState)
            HistoryCard(state.history, onOpenTransaction)
        }

        DetailDialogs(state.dialog, viewModel)
    }
}

@Composable
internal fun RecurringHero(schedule: RecurringSchedule, modifier: Modifier = Modifier) {
    val displayState = schedule.displayState()
    RecurringCard(modifier, shape = RoundedCornerShape(28.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RecurringScheduleBadge(schedule, 56)
            Text(schedule.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                RecurringListLogic.signedAmount(schedule.kind, schedule.money),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = recurringSignedAmountColor(schedule.kind),
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RecurringPill(ruleText(schedule.rule), icon = AppIcons.Recurring)
                RecurringPill(
                    when (displayState) {
                        ScheduleDisplayState.ACTIVE -> statusText(schedule)
                        ScheduleDisplayState.PAUSED -> stringResource(R.string.recurring_section_paused)
                        ScheduleDisplayState.ENDED -> stringResource(R.string.recurring_section_ended)
                    },
                    icon = if (displayState == ScheduleDisplayState.ACTIVE) AppIcons.Calendar else null,
                )
            }
        }
    }
}

@Composable
private fun statusText(schedule: RecurringSchedule): String = statusLineText(
    when (schedule.displayState()) {
        ScheduleDisplayState.ACTIVE -> StatusLine.Next(schedule.nextDate ?: schedule.startDate)
        ScheduleDisplayState.PAUSED -> StatusLine.Paused
        ScheduleDisplayState.ENDED -> StatusLine.Ended(schedule.endDate ?: schedule.startDate)
    },
)

@Composable
private fun ActionTiles(displayState: ScheduleDisplayState, viewModel: RecurringDetailViewModel) {
    val paused = displayState == ScheduleDisplayState.PAUSED
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionTile(
            icon = if (paused) AppIcons.Resume else AppIcons.Pause,
            label = stringResource(if (paused) R.string.recurring_action_resume else R.string.recurring_action_pause),
            onClick = if (paused) viewModel::onResumeRequest else viewModel::onPause,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
        ActionTile(
            icon = AppIcons.SkipNext,
            label = stringResource(R.string.recurring_action_skip_next),
            onClick = viewModel::onSkipNextRequest,
            modifier = Modifier.weight(1f).fillMaxSize(),
        )
    }
}

@Composable
private fun ActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsGroup(schedule: RecurringSchedule) {
    val categoryText = if (schedule.categoryPath.isEmpty()) {
        stringResource(R.string.picker_uncategorized)
    } else {
        schedule.categoryPath.joinToString(" › ") { it.name }
    }
    val endsText = schedule.endDate?.let { formatScheduleDate(it) } ?: stringResource(R.string.recurring_detail_no_end)
    val rows = buildList<@Composable () -> Unit> {
        add {
            DetailRowContent(
                AppIcons.Account,
                stringResource(R.string.recurring_label_account),
                "${schedule.account.name} · ${schedule.account.currency.code}",
            )
        }
        add { DetailRowContent(AppIcons.Category, stringResource(R.string.recurring_label_category), categoryText) }
        add { DetailRowContent(AppIcons.Recurring, stringResource(R.string.recurring_label_repeats), ruleText(schedule.rule)) }
        add { DetailRowContent(AppIcons.Calendar, stringResource(R.string.recurring_label_ends), endsText) }
        if (schedule.tags.isNotEmpty()) {
            add {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecurringLeadingIcon(AppIcons.Tag)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.recurring_label_tags),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            schedule.tags.forEach { TagChip(it) }
                        }
                    }
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, content ->
            RecurringRowCard(shape = recurringGroupShape(index, rows.size)) { content() }
        }
    }
}

@Composable
private fun DetailRowContent(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecurringLeadingIcon(icon)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun UpcomingCard(
    schedule: RecurringSchedule,
    upcoming: List<java.time.LocalDate>,
    displayState: ScheduleDisplayState,
) {
    RecurringCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurringSectionHeader(stringResource(R.string.recurring_detail_upcoming))
            if (displayState == ScheduleDisplayState.PAUSED) {
                Text(
                    stringResource(R.string.recurring_detail_upcoming_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (upcoming.isEmpty()) {
                Text(
                    stringResource(R.string.recurring_detail_upcoming_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val amount = RecurringListLogic.signedAmount(schedule.kind, schedule.money)
                val dimmed = displayState != ScheduleDisplayState.ACTIVE
                upcoming.forEach { date ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(formatScheduleDate(date), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            amount,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else recurringSignedAmountColor(schedule.kind),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(history: HistoryView, onOpenTransaction: (Long) -> Unit) {
    RecurringCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecurringSectionHeader(stringResource(R.string.recurring_detail_history))
            if (history.rows.isEmpty()) {
                Text(
                    stringResource(R.string.recurring_detail_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    history.rows.forEachIndexed { index, row ->
                        HistoryItem(row, recurringGroupShape(index, history.rows.size), onOpenTransaction)
                    }
                }
                if (history.hasMore) {
                    Text(
                        stringResource(R.string.recurring_detail_history_more, RecurringDetailLogic.HISTORY_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, shape: androidx.compose.ui.graphics.Shape, onOpenTransaction: (Long) -> Unit) {
    when (row) {
        is HistoryRow.Generated -> RecurringRowCard(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            shape = shape,
            onClick = { onOpenTransaction(row.operationId) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    AppIcons.Recurring,
                    contentDescription = stringResource(R.string.recurring_marker),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatScheduleDate(row.date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                row.money?.let {
                    Text(it.format(), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                }
                Icon(
                    AppIcons.Next,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Default),
                )
            }
        }
        is HistoryRow.Deleted -> HistorySecondaryRow(row, shape, stringResource(R.string.recurring_history_deleted))
        is HistoryRow.Skipped -> HistorySecondaryRow(row, shape, stringResource(R.string.recurring_history_skipped))
    }
}

@Composable
private fun HistorySecondaryRow(row: HistoryRow, shape: androidx.compose.ui.graphics.Shape, label: String) {
    RecurringRowCard(modifier = Modifier.semantics(mergeDescendants = true) {}, shape = shape) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatScheduleDate(row.date), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IssueBanner(
    schedule: RecurringSchedule,
    issue: GenerationIssue,
    state: RecurringDetailUiState,
    viewModel: RecurringDetailViewModel,
) {
    RecurringCard(color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val nextDate = schedule.nextDate
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    when (issue) {
                        GenerationIssue.ACCOUNT_ARCHIVED -> stringResource(R.string.recurring_issue_archived)
                        GenerationIssue.BEFORE_OPENING_BALANCE -> stringResource(
                            R.string.recurring_issue_opening,
                            nextDate?.let(::formatScheduleDate).orEmpty(),
                            schedule.account.name,
                            state.openingDate?.let(::formatScheduleDate).orEmpty(),
                        )
                        GenerationIssue.BALANCE_OVERFLOW -> stringResource(R.string.recurring_issue_overflow, schedule.account.name)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
            }
            if (issue == GenerationIssue.BEFORE_OPENING_BALANCE) {
                Button(onClick = viewModel::onMoveBackRequest) { Text(stringResource(R.string.recurring_issue_move_back)) }
                OutlinedButton(onClick = viewModel::onSkipBlocked) { Text(stringResource(R.string.recurring_issue_skip)) }
            }
        }
    }
}

@Composable
private fun DetailDialogs(dialog: DetailDialog?, viewModel: RecurringDetailViewModel) {
    when (dialog) {
        null -> Unit
        is DetailDialog.SkipNext -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text(stringResource(R.string.recurring_skip_title)) },
            text = { Text(stringResource(R.string.recurring_skip_body, formatScheduleDate(dialog.date))) },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmSkipNext) { Text(stringResource(R.string.recurring_action_skip)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is DetailDialog.Resume -> {
            val info = dialog.info
            AlertDialog(
                onDismissRequest = viewModel::onDismissDialog,
                title = { Text(stringResource(R.string.recurring_resume_title)) },
                text = {
                    Text(pluralStringResource(R.plurals.recurring_resume_body, info.count, info.count, rangeText(info.first, info.last)))
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onResumeCreate() }) { Text(stringResource(R.string.recurring_resume_create)) }
                },
                dismissButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = viewModel::onResumeSkip) {
                            Text(
                                info.continueFrom?.let { stringResource(R.string.recurring_resume_skip, formatScheduleDate(it)) }
                                    ?: stringResource(R.string.recurring_resume_skip_ended),
                            )
                        }
                        TextButton(onClick = viewModel::onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
                    }
                },
            )
        }
        is DetailDialog.Delete -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text(stringResource(R.string.recurring_delete_title)) },
            text = {
                Text(
                    if (dialog.generatedCount == 0) {
                        stringResource(R.string.recurring_delete_body_none)
                    } else {
                        pluralStringResource(R.plurals.recurring_delete_body, dialog.generatedCount, dialog.generatedCount)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onConfirmDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is DetailDialog.MoveBack -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text(stringResource(R.string.recurring_move_back_title)) },
            text = { Text(stringResource(R.string.recurring_move_back_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmMoveBack(dialog.then) }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDialog) { Text(stringResource(R.string.action_cancel)) }
            },
        )
        is DetailDialog.Error -> AlertDialog(
            onDismissRequest = viewModel::onDismissDialog,
            title = { Text(stringResource(R.string.recurring_error_title)) },
            text = {
                Text(
                    stringResource(
                        when (dialog.kind) {
                            DetailError.ACCOUNT_ARCHIVED -> R.string.recurring_error_archived
                            DetailError.BALANCE_OVERFLOW -> R.string.recurring_error_overflow
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissDialog) { Text(stringResource(R.string.recurring_error_ok)) }
            },
        )
    }
}

// ---- Previews ----

@Composable
private fun RecurringHeroPreviewContent(dark: Boolean) {
    PreviewTheme(dark) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RecurringHero(previewSchedule())
                RecurringHero(previewSchedule(2, "Gym", status = ScheduleStatus.PAUSED))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecurringHeroPreview() = RecurringHeroPreviewContent(dark = false)

@Preview(showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun RecurringHeroDarkPreview() = RecurringHeroPreviewContent(dark = true)
