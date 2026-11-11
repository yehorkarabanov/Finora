package com.yehor.finora.ui.data

import com.yehor.finora.designsystem.icon.AppIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.tooling.preview.Preview
import com.yehor.finora.R
import com.yehor.finora.data.dataio.CsvExportResult
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.ui.common.OrganizeTopBar
import com.yehor.finora.domain.dataio.BackupFileNames
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.ui.operations.FilterSection
import com.yehor.finora.ui.operations.TransactionFilterSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun CsvExportScreen(
    onBack: () -> Unit,
    viewModel: CsvExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv(uri, BackupFileNames.csv(System.currentTimeMillis(), zone), zone)
        }
    }

    CsvExportContent(
        state = state,
        zone = zone,
        onBack = onBack,
        onFilterChange = viewModel::onFilterChange,
        onExport = { launcher.launch(BackupFileNames.csv(System.currentTimeMillis(), zone)) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CsvExportContent(
    state: CsvExportUiState,
    zone: ZoneId,
    onBack: () -> Unit,
    onFilterChange: (TransactionFilter) -> Unit,
    onExport: () -> Unit,
) {
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { OrganizeTopBar(stringResource(R.string.csv_export_title), onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(stringResource(R.string.csv_export_filter_section), Modifier.semantics { heading() })

            val filter = state.filter
            val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
            fun dateText(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(dateFormat)

            GroupedCard(Modifier.fillMaxWidth()) {
            FlowRow(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                @Composable
                fun Chip(label: String, onRemove: () -> Unit) {
                    FilterPill(label = label, active = true, onClick = onRemove, onClear = onRemove)
                }
                filter.types.forEach { type ->
                    Chip(
                        stringResource(
                            when (type) {
                                OperationType.INCOME -> R.string.transaction_kind_income
                                OperationType.TRANSFER -> R.string.transaction_kind_transfer
                                else -> R.string.transaction_kind_expense
                            },
                        ),
                    ) { onFilterChange(filter.copy(types = filter.types - type)) }
                }
                filter.accountIds.forEach { id ->
                    Chip(state.accounts.find { it.id == id }?.name.orEmpty()) {
                        onFilterChange(filter.copy(accountIds = filter.accountIds - id))
                    }
                }
                filter.categoryIds.forEach { id ->
                    val name = state.categories.find { it.id == id }?.name.orEmpty()
                    val isDirectOnly = filter.categoryExact || id in filter.exactCategoryIds
                    Chip(if (isDirectOnly) stringResource(R.string.filter_category_direct_only, name) else name) {
                        onFilterChange(
                            filter.copy(
                                categoryIds = filter.categoryIds - id,
                                exactCategoryIds = filter.exactCategoryIds - id,
                            ),
                        )
                    }
                }
                if (filter.includeUncategorized) {
                    Chip(stringResource(R.string.picker_uncategorized)) {
                        onFilterChange(filter.copy(includeUncategorized = false))
                    }
                }
                filter.tagIds.forEach { id ->
                    Chip("#" + state.tags.find { it.id == id }?.name.orEmpty()) {
                        onFilterChange(filter.copy(tagIds = filter.tagIds - id))
                    }
                }
                if (filter.from != null || filter.toExclusive != null) {
                    val from = filter.from?.let(::dateText)
                    val to = filter.toExclusive?.let { dateText(it - 1) }
                    val label = when {
                        from != null && to != null -> stringResource(R.string.filter_chip_date_range, from, to)
                        from != null -> stringResource(R.string.filter_chip_date_from, from)
                        else -> stringResource(R.string.filter_chip_date_to, to.orEmpty())
                    }
                    Chip(label) { onFilterChange(filter.copy(from = null, toExclusive = null)) }
                }
                FilterPill(
                    label = stringResource(R.string.filter_title),
                    active = false,
                    onClick = { if (!state.isBusy) filterSheetOpen = true },
                )
            }
            }

            Text(
                if (state.matchingCount > 0) {
                    pluralStringResource(R.plurals.csv_export_count_preview, state.matchingCount, state.matchingCount)
                } else {
                    stringResource(R.string.csv_export_count_zero)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.csv_export_initial_balance_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrimaryButton(
                text = stringResource(R.string.csv_export_button),
                onClick = onExport,
                enabled = !state.isBusy && state.matchingCount > 0,
            )

            if (state.isBusy) {
                val exporting = stringResource(R.string.csv_export_exporting)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { contentDescription = exporting },
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(exporting)
                }
            }
            state.exportResult?.let { result ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Success, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(pluralStringResource(R.plurals.csv_export_success, result.count, result.count))
                        Text(result.fileName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.errorMessage?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Error, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.csv_export_failed, message), color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (filterSheetOpen) {
            TransactionFilterSheet(
                filter = state.filter,
                accounts = state.accounts,
                categories = state.categories,
                tags = state.tags,
                onApply = onFilterChange,
                onDismiss = { filterSheetOpen = false },
                sections = setOf(
                    FilterSection.TYPE,
                    FilterSection.ACCOUNT,
                    FilterSection.CATEGORY,
                    FilterSection.TAG,
                    FilterSection.DATE,
                ),
            )
        }
    }
}

@Composable
private fun CsvPreviewBody(dark: Boolean) {
    PreviewTheme(dark = dark) {
        CsvExportContent(
            state = CsvExportUiState(
                filter = TransactionFilter(types = setOf(OperationType.EXPENSE), includeUncategorized = true),
                matchingCount = 42,
                exportResult = CsvExportResult("finora-2026-11-11.csv", 42),
            ),
            zone = ZoneId.of("UTC"),
            onBack = {},
            onFilterChange = {},
            onExport = {},
        )
    }
}

@Preview(name = "CSV export light", showBackground = true)
@Composable
private fun CsvExportLightPreview() = CsvPreviewBody(dark = false)

@Preview(name = "CSV export dark", showBackground = true, backgroundColor = 0xFF0F1412)
@Composable
private fun CsvExportDarkPreview() = CsvPreviewBody(dark = true)
