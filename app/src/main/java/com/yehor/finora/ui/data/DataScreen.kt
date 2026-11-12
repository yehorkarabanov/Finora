package com.yehor.finora.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.dataio.BackupFileNames
import com.yehor.finora.domain.dataio.ImportFailure
import com.yehor.finora.domain.dataio.ImportReportFormatter
import java.time.ZoneId

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


    // Restore state overlays
    when (val res = state.restoreState) {
        is RestoreState.Validating -> RestoreProgressDialog(
            title = stringResource(
                when (res.step) {
                    "Copying" -> R.string.data_restore_step_copying
                    "Checking structure" -> R.string.data_restore_step_checking_structure
                    "Upgrading" -> R.string.data_restore_step_upgrading
                    else -> R.string.data_restore_step_checking_data
                },
            ),
            note = null,
            onCancel = { viewModel.cancelRestore() },
        )
        is RestoreState.Rejected -> RestoreRejectedDialog(
            headline = stringResource(
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
            ),
            problems = if (res.problems.isEmpty()) emptyList() else ImportReportFormatter.format(res.problems, res.totalProblems),
            onChooseAnother = {
                viewModel.cancelRestore()
                restoreLauncher.launch(
                    arrayOf(
                        "application/vnd.sqlite3",
                        "application/x-sqlite3",
                        "application/octet-stream",
                    ),
                )
            },
            onClose = { viewModel.cancelRestore() },
        )
        is RestoreState.Preview -> RestorePreviewDialog(
            current = res.currentSummary,
            incoming = res.ready.summary,
            upgradedFrom = res.ready.upgradedFrom,
            backedUpFirst = res.backedUpFirst,
            onBackUpFirst = {
                val fileName = BackupFileNames.backup(System.currentTimeMillis(), ZoneId.systemDefault())
                backupFirstLauncher.launch(fileName)
            },
            onReplace = { viewModel.confirmRestore() },
            onCancel = { viewModel.cancelRestore() },
        )
        is RestoreState.Restarting -> RestoreProgressDialog(
            title = stringResource(R.string.data_restore_restarting),
            note = stringResource(R.string.data_restore_restarting_note),
            onCancel = null,
        )
        RestoreState.Idle -> Unit
    }
}
