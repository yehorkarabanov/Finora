package com.yehor.finora.ui.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yehor.finora.R
import com.yehor.finora.domain.dataio.BackupFileNames
import com.yehor.finora.domain.model.OperationType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.ui.operations.FilterSection
import com.yehor.finora.ui.operations.TransactionFilterSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CsvExportScreen(
    onBack: () -> Unit,
    viewModel: CsvExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filterSheetOpen by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsv(uri, BackupFileNames.csv(System.currentTimeMillis(), zone), zone)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.csv_export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.csv_export_filter_section), style = MaterialTheme.typography.titleMedium)

            val filter = state.filter
            val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
            fun dateText(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(dateFormat)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                @Composable
                fun Chip(label: String, onRemove: () -> Unit) {
                    InputChip(
                        selected = true,
                        onClick = onRemove,
                        label = { Text(label) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_remove),
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
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
                    ) { viewModel.onFilterChange(filter.copy(types = filter.types - type)) }
                }
                filter.accountIds.forEach { id ->
                    Chip(state.accounts.find { it.id == id }?.name.orEmpty()) {
                        viewModel.onFilterChange(filter.copy(accountIds = filter.accountIds - id))
                    }
                }
                filter.categoryIds.forEach { id ->
                    val name = state.categories.find { it.id == id }?.name.orEmpty()
                    val isDirectOnly = filter.categoryExact || id in filter.exactCategoryIds
                    Chip(if (isDirectOnly) stringResource(R.string.filter_category_direct_only, name) else name) {
                        viewModel.onFilterChange(
                            filter.copy(
                                categoryIds = filter.categoryIds - id,
                                exactCategoryIds = filter.exactCategoryIds - id,
                            ),
                        )
                    }
                }
                if (filter.includeUncategorized) {
                    Chip(stringResource(R.string.picker_uncategorized)) {
                        viewModel.onFilterChange(filter.copy(includeUncategorized = false))
                    }
                }
                filter.tagIds.forEach { id ->
                    Chip("#" + state.tags.find { it.id == id }?.name.orEmpty()) {
                        viewModel.onFilterChange(filter.copy(tagIds = filter.tagIds - id))
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
                    Chip(label) { viewModel.onFilterChange(filter.copy(from = null, toExclusive = null)) }
                }
            }

            OutlinedButton(onClick = { filterSheetOpen = true }, enabled = !state.isBusy) {
                Text(stringResource(R.string.filter_title))
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

            Button(
                onClick = { launcher.launch(BackupFileNames.csv(System.currentTimeMillis(), zone)) },
                enabled = !state.isBusy && state.matchingCount > 0,
            ) {
                Text(stringResource(R.string.csv_export_button))
            }

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
                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(pluralStringResource(R.plurals.csv_export_success, result.count, result.count))
                        Text(result.fileName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            state.errorMessage?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error)
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
                onApply = viewModel::onFilterChange,
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
