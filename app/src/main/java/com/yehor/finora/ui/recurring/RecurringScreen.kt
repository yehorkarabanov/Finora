package com.yehor.finora.ui.recurring

import com.yehor.finora.ui.common.AppIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.operation.TransactionKind

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
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.recurring_empty_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.recurring_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                TextButton(onClick = onAdd) { Text(stringResource(R.string.recurring_add)) }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
                section(R.string.recurring_section_active, sections.active, onOpen)
                section(R.string.recurring_section_paused, sections.paused, onOpen)
                section(R.string.recurring_section_ended, sections.ended, onOpen)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    titleRes: Int,
    rows: List<RecurringRow>,
    onOpen: (Long) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$titleRes") {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(rows, key = { it.schedule.id }) { RecurringListRow(it, onOpen) }
}

@Composable
private fun RecurringListRow(row: RecurringRow, onOpen: (Long) -> Unit) {
    val schedule = row.schedule
    val line = RecurringListLogic.statusLine(row)
    val isBlocked = line is StatusLine.Blocked
    ListItem(
        modifier = Modifier.clickable { onOpen(schedule.id) },
        leadingContent = {
            Icon(
                if (schedule.kind == TransactionKind.INCOME) AppIcons.Income else AppIcons.Expense,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        headlineContent = { Text(schedule.name, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Column {
                Text(
                    stringResource(R.string.recurring_row_subtitle, schedule.account.name, ruleText(schedule.rule)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    statusLineText(line),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Text(
                RecurringListLogic.signedAmount(schedule.kind, schedule.money),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
    )
}
