package com.yehor.finora.ui.data

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.domain.dataio.DataSummary
import com.yehor.finora.ui.common.OrganizeTopBar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DataContent(
    state: DataUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onOpenCsvExport: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { OrganizeTopBar(stringResource(R.string.data_title), onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DataSummaryCard(state.summary, state.appVersion)

            DataSection(R.string.data_backup_title) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    DataActionRow(
                        icon = AppIcons.Data,
                        title = stringResource(R.string.data_backup_button),
                        enabled = !state.isBusy,
                        onClick = onBackup,
                    )
                    when (val exp = state.exportState) {
                        is ExportState.Exporting -> DataStatus {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.data_backup_exporting))
                        }
                        is ExportState.Done -> DataStatus {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(AppIcons.Success, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.data_backup_done_title) + " (${exp.fileName})",
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    stringResource(R.string.data_backup_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        is ExportState.Failed -> DataStatus {
                            Icon(AppIcons.Error, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.data_backup_failed, exp.message),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        ExportState.Idle -> Unit
                    }
                }
            }

            DataSection(R.string.data_restore_title) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    DataActionRow(
                        icon = AppIcons.Archive,
                        title = stringResource(R.string.data_restore_button),
                        enabled = !state.isBusy,
                        onClick = onRestore,
                    )
                }
            }

            DataSection(R.string.data_export_csv_title) {
                GroupedCard(Modifier.fillMaxWidth()) {
                    DataActionRow(
                        icon = AppIcons.Export,
                        title = stringResource(R.string.data_export_csv_button),
                        enabled = !state.isBusy,
                        onClick = onOpenCsvExport,
                    )
                }
            }
        }
    }
}

@Composable
private fun DataSection(titleRes: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader(stringResource(titleRes), Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun DataActionRow(icon: ImageVector, title: String, enabled: Boolean, onClick: () -> Unit) {
    ListRow(
        icon = { RoundIcon(icon, MaterialTheme.colorScheme.primary) },
        title = title,
        subtitle = null,
        trailing = { Icon(AppIcons.Next, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.heightIn(min = 64.dp).alpha(if (enabled) 1f else 0.38f),
        onClick = if (enabled) onClick else null,
    )
}

@Composable
private fun DataStatus(content: @Composable RowScope.() -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun DataSummaryCard(summary: DataSummary?, appVersion: String) {
    val context = LocalContext.current
    DataSection(R.string.data_summary_title) {
        GroupedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (summary != null) {
                    SummaryRow(stringResource(R.string.data_accounts), "${summary.accounts} (${summary.activeAccounts})")
                    SummaryRow(stringResource(R.string.data_transactions), summary.transactions.toString())
                    SummaryRow(stringResource(R.string.data_transfers), summary.transfers.toString())
                    SummaryRow(stringResource(R.string.data_categories), summary.categories.toString())
                    SummaryRow(stringResource(R.string.data_tags), summary.tags.toString())
                    SummaryRow(stringResource(R.string.data_schedules), summary.schedules.toString())

                    val periodText = if (summary.firstOperationAt != null && summary.lastOperationAt != null) {
                        val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        val first = Instant.ofEpochMilli(summary.firstOperationAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        val last = Instant.ofEpochMilli(summary.lastOperationAt).atZone(ZoneId.systemDefault()).toLocalDate()
                        "${first.format(fmt)} to ${last.format(fmt)}"
                    } else {
                        stringResource(R.string.data_period_empty)
                    }
                    SummaryRow(stringResource(R.string.data_period), periodText)
                    SummaryRow(stringResource(R.string.data_database_size), Formatter.formatFileSize(context, summary.databaseBytes))
                    SummaryRow(stringResource(R.string.data_schema_version), summary.schemaVersion.toString())
                }
                SummaryRow(stringResource(R.string.data_app_version), appVersion)
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Preview(name = "Data light", showBackground = true)
@Composable
private fun DataContentLightPreview() = DataPreviewBody(dark = false)

@Preview(name = "Data dark", showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun DataContentDarkPreview() = DataPreviewBody(dark = true)

@Composable
private fun DataPreviewBody(dark: Boolean) {
    PreviewTheme(dark = dark) {
        DataContent(
            state = DataUiState(
                summary = DataSummary(
                    accounts = 4, activeAccounts = 3, transactions = 128, transfers = 12, categories = 24, tags = 6, schedules = 3,
                    firstOperationAt = null, lastOperationAt = null, databaseBytes = 120_000L, schemaVersion = 7,
                ),
                appVersion = "1.0",
                exportState = ExportState.Done("finora-backup.db", 0L),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onBackup = {},
            onRestore = {},
            onOpenCsvExport = {},
        )
    }
}
