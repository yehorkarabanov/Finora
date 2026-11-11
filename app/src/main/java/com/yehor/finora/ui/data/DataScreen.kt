package com.yehor.finora.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.domain.dataio.BackupFileNames
import com.yehor.finora.domain.dataio.DataSummary
import com.yehor.finora.domain.dataio.ImportFailure
import com.yehor.finora.domain.dataio.ImportReportFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun DataScreen(
    onBack: () -> Unit,
    onOpenCsvExport: () -> Unit,
    viewModel: DataViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.sqlite3"),
    ) { uri ->
        if (uri != null) {
            val fileName = BackupFileNames.backup(System.currentTimeMillis(), ZoneId.systemDefault())
            viewModel.exportBackup(uri, fileName)
        }
    }

    val backupFirstLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.sqlite3"),
    ) { uri ->
        if (uri != null) {
            val fileName = BackupFileNames.backup(System.currentTimeMillis(), ZoneId.systemDefault())
            viewModel.backUpCurrentFirst(uri, fileName)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.stageRestore(uri)
        }
    }

    DataContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onBackup = {
            val fileName = BackupFileNames.backup(System.currentTimeMillis(), ZoneId.systemDefault())
            backupLauncher.launch(fileName)
        },
        onRestore = {
            restoreLauncher.launch(
                arrayOf(
                    "application/vnd.sqlite3",
                    "application/x-sqlite3",
                    "application/octet-stream",
                ),
            )
        },
        onOpenCsvExport = onOpenCsvExport,
    )

    // Restore State Overlays / Dialogs
    when (val res = state.restoreState) {
        is RestoreState.Validating -> {
            Dialog(
                onDismissRequest = { viewModel.cancelRestore() },
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
            ) {
                GroupedCard(Modifier.padding(16.dp)) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            stringResource(
                                when (res.step) {
                                    "Copying" -> R.string.data_restore_step_copying
                                    "Checking structure" -> R.string.data_restore_step_checking_structure
                                    "Upgrading" -> R.string.data_restore_step_upgrading
                                    else -> R.string.data_restore_step_checking_data
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        OutlinedButton(onClick = { viewModel.cancelRestore() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
        is RestoreState.Rejected -> {
            val failureHeadline = stringResource(
                when (res.failure) {
                    ImportFailure.UNREADABLE -> R.string.import_failure_unreadable
                    ImportFailure.TOO_LARGE -> R.string.import_failure_too_large
                    ImportFailure.NOT_ENOUGH_SPACE -> R.string.import_failure_not_enough_space
                    ImportFailure.NOT_SQLITE -> R.string.import_failure_not_sqlite
                    ImportFailure.NOT_FINORA -> R.string.import_failure_not_finora
                    ImportFailure.NEWER_VERSION -> R.string.import_failure_newer_version
                    ImportFailure.UNKNOWN_SCHEMA -> R.string.import_failure_unknown_schema
                    ImportFailure.UNSAFE_OBJECTS -> R.string.import_failure_unsafe_objects
                    ImportFailure.CORRUPT -> R.string.import_failure_corrupt
                    ImportFailure.FOREIGN_KEY_VIOLATIONS -> R.string.import_failure_foreign_key_violations
                    ImportFailure.MIGRATION_FAILED -> R.string.import_failure_migration_failed
                    ImportFailure.UNEXPECTED_OBJECTS -> R.string.import_failure_unexpected_objects
                    ImportFailure.CURRENCY_MISMATCH -> R.string.import_failure_currency_mismatch
                    ImportFailure.BALANCE_OVERFLOW -> R.string.import_failure_balance_overflow
                    ImportFailure.INVARIANT_VIOLATIONS -> R.string.import_failure_invariant_violations
                },
            )
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                onDismissRequest = { viewModel.cancelRestore() },
                title = { Text(stringResource(R.string.data_restore_reject_title)) },
                text = {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(failureHeadline, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        if (res.problems.isNotEmpty()) {
                            val formattedProblems = ImportReportFormatter.format(res.problems, res.totalProblems)
                            SelectionContainer {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    formattedProblems.forEach { line ->
                                        Text(line, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelRestore()
                            restoreLauncher.launch(
                                arrayOf(
                                    "application/vnd.sqlite3",
                                    "application/x-sqlite3",
                                    "application/octet-stream",
                                ),
                            )
                        },
                    ) {
                        Text(stringResource(R.string.data_restore_reject_choose_another))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelRestore() }) {
                        Text(stringResource(R.string.data_restore_reject_close))
                    }
                },
            )
        }
        is RestoreState.Preview -> {
            AlertDialog(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(28.dp),
                onDismissRequest = { viewModel.cancelRestore() },
                title = { Text(stringResource(R.string.data_restore_preview_title)) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.data_restore_preview_warning),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )

                        if (res.ready.upgradedFrom != null) {
                            Text(
                                stringResource(R.string.data_restore_preview_upgrade_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }

                        // Comparison Table
                        ComparisonTable(current = res.currentSummary, incoming = res.ready.summary, upgradedFrom = res.ready.upgradedFrom)

                        if (res.backedUpFirst) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Success, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.data_restore_action_backed_up),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    PrimaryButton(
                        text = stringResource(R.string.data_restore_action_replace),
                        onClick = { viewModel.confirmRestore() },
                        destructive = true,
                        fillWidth = false,
                    )
                },
                dismissButton = {
                    Column {
                        OutlinedButton(
                            onClick = {
                                val fileName = BackupFileNames.backup(System.currentTimeMillis(), ZoneId.systemDefault())
                                backupFirstLauncher.launch(fileName)
                            },
                        ) {
                            Text(stringResource(R.string.data_restore_action_backup_first))
                        }
                        TextButton(onClick = { viewModel.cancelRestore() }) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                },
            )
        }
        is RestoreState.Restarting -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            ) {
                GroupedCard(Modifier.padding(16.dp)) {
                    Column(
                        Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.data_restore_restarting), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.data_restore_restarting_note), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        RestoreState.Idle -> Unit
    }
}

@Composable
private fun ComparisonTable(current: DataSummary?, incoming: DataSummary, upgradedFrom: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("", Modifier.weight(1.2f))
            Text(stringResource(R.string.data_restore_preview_current_col), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.data_restore_preview_backup_col), Modifier.weight(1.2f), fontWeight = FontWeight.Bold)
        }
        HorizontalDivider()

        TableRow(stringResource(R.string.data_accounts), current?.accounts?.toString() ?: "—", incoming.accounts.toString())
        TableRow(stringResource(R.string.data_transactions), current?.transactions?.toString() ?: "—", incoming.transactions.toString())
        TableRow(stringResource(R.string.data_transfers), current?.transfers?.toString() ?: "—", incoming.transfers.toString())
        TableRow(stringResource(R.string.data_categories), current?.categories?.toString() ?: "—", incoming.categories.toString())
        TableRow(stringResource(R.string.data_tags), current?.tags?.toString() ?: "—", incoming.tags.toString())
        TableRow(stringResource(R.string.data_schedules), current?.schedules?.toString() ?: "—", incoming.schedules.toString())

        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
        val curPeriod = if (current?.firstOperationAt != null && current.lastOperationAt != null) {
            val f = Instant.ofEpochMilli(current.firstOperationAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val l = Instant.ofEpochMilli(current.lastOperationAt).atZone(ZoneId.systemDefault()).toLocalDate()
            "${f.format(fmt)} – ${l.format(fmt)}"
        } else "—"

        val incPeriod = if (incoming.firstOperationAt != null && incoming.lastOperationAt != null) {
            val f = Instant.ofEpochMilli(incoming.firstOperationAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val l = Instant.ofEpochMilli(incoming.lastOperationAt).atZone(ZoneId.systemDefault()).toLocalDate()
            "${f.format(fmt)} – ${l.format(fmt)}"
        } else "—"

        TableRow(stringResource(R.string.data_period), curPeriod, incPeriod)

        val versionText = if (upgradedFrom != null) {
            "$upgradedFrom (upgrades to ${incoming.schemaVersion})"
        } else {
            incoming.schemaVersion.toString()
        }
        TableRow(stringResource(R.string.data_schema_version), current?.schemaVersion?.toString() ?: "—", versionText)
    }
}

@Composable
private fun TableRow(label: String, current: String, incoming: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall)
        Text(current, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(incoming, Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
