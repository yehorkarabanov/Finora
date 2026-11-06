package com.yehor.finora.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.recurring.GenerationIssue
import com.yehor.finora.domain.recurring.RecurringSchedule
import com.yehor.finora.domain.recurring.ScheduleDisplayState
import com.yehor.finora.domain.recurring.displayState
import com.yehor.finora.ui.common.TagChip

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
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
            Header(schedule)
            Actions(schedule, displayState, onEdit, viewModel)
            HorizontalDivider()

            Text(stringResource(R.string.recurring_detail_upcoming), style = MaterialTheme.typography.titleMedium)
            if (displayState == ScheduleDisplayState.PAUSED) {
                Text(
                    stringResource(R.string.recurring_detail_upcoming_paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.upcoming.isEmpty()) {
                Text(stringResource(R.string.recurring_detail_upcoming_none), style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    state.upcoming.forEach { date ->
                        Text(formatScheduleDate(date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
            HorizontalDivider()

            Text(stringResource(R.string.recurring_detail_history), style = MaterialTheme.typography.titleMedium)
            if (state.history.rows.isEmpty()) {
                Text(stringResource(R.string.recurring_detail_history_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                Column {
                    state.history.rows.forEach { row -> HistoryItem(row, onOpenTransaction) }
                }
                if (state.history.hasMore) {
                    Text(
                        stringResource(R.string.recurring_detail_history_more, RecurringDetailLogic.HISTORY_LIMIT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        DetailDialogs(state.dialog, viewModel)
    }
}

@Composable
private fun Header(schedule: RecurringSchedule) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(schedule.name, style = MaterialTheme.typography.titleLarge)
        Text(
            RecurringListLogic.signedAmount(schedule.kind, schedule.money),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            stringResource(R.string.recurring_detail_summary, ruleText(schedule.rule), statusText(schedule)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.recurring_detail_account, schedule.account.name), style = MaterialTheme.typography.bodyMedium)
        val categoryText = if (schedule.categoryPath.isEmpty()) {
            stringResource(R.string.picker_uncategorized)
        } else {
            schedule.categoryPath.joinToString(" › ") { it.name }
        }
        Text(stringResource(R.string.recurring_detail_category, categoryText), style = MaterialTheme.typography.bodyMedium)
        Text(
            schedule.endDate?.let { stringResource(R.string.recurring_detail_ends, formatScheduleDate(it)) }
                ?: stringResource(R.string.recurring_detail_no_end),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (schedule.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                schedule.tags.forEach { TagChip(it) }
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
private fun Actions(
    schedule: RecurringSchedule,
    displayState: ScheduleDisplayState,
    onEdit: (Long) -> Unit,
    viewModel: RecurringDetailViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onEdit(schedule.id) }) { Text(stringResource(R.string.action_edit)) }
            when (displayState) {
                ScheduleDisplayState.ACTIVE ->
                    OutlinedButton(onClick = viewModel::onPause) { Text(stringResource(R.string.recurring_action_pause)) }
                ScheduleDisplayState.PAUSED ->
                    OutlinedButton(onClick = viewModel::onResumeRequest) { Text(stringResource(R.string.recurring_action_resume)) }
                ScheduleDisplayState.ENDED -> Unit
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (displayState != ScheduleDisplayState.ENDED) {
                OutlinedButton(onClick = viewModel::onSkipNextRequest) {
                    Text(stringResource(R.string.recurring_action_skip_next))
                }
            }
            TextButton(onClick = viewModel::onDeleteRequest) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
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
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val nextDate = schedule.nextDate
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
            )
            if (issue == GenerationIssue.BEFORE_OPENING_BALANCE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::onMoveBackRequest) { Text(stringResource(R.string.recurring_issue_move_back)) }
                }
                OutlinedButton(onClick = viewModel::onSkipBlocked) { Text(stringResource(R.string.recurring_issue_skip)) }
            }
        }
    }
}

@Composable
private fun HistoryItem(row: HistoryRow, onOpenTransaction: (Long) -> Unit) {
    when (row) {
        is HistoryRow.Generated -> ListItem(
            modifier = Modifier.fillMaxWidth().clickable { onOpenTransaction(row.operationId) },
            leadingContent = { Icon(Icons.Filled.Repeat, contentDescription = stringResource(R.string.recurring_marker)) },
            headlineContent = { Text(formatScheduleDate(row.date)) },
            trailingContent = { row.money?.let { Text(it.format(), style = MaterialTheme.typography.bodyLarge) } },
        )
        is HistoryRow.Deleted -> ListItem(
            headlineContent = { Text(formatScheduleDate(row.date)) },
            supportingContent = { Text(stringResource(R.string.recurring_history_deleted)) },
        )
        is HistoryRow.Skipped -> ListItem(
            headlineContent = { Text(formatScheduleDate(row.date)) },
            supportingContent = { Text(stringResource(R.string.recurring_history_skipped)) },
        )
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
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
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
                TextButton(onClick = viewModel::onConfirmDelete) { Text(stringResource(R.string.action_delete)) }
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
