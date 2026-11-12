package com.yehor.finora.ui.data

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconSize
import com.yehor.finora.designsystem.theme.AmountStyle
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import com.yehor.finora.domain.dataio.DataSummary
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val DialogShape = RoundedCornerShape(28.dp)

/** Shared Obsidian dialog frame for every restore step: 28dp card, hairline, pinned actions. */
@Composable
private fun RestoreDialog(
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        RestoreCard(Modifier.padding(horizontal = 20.dp, vertical = 24.dp), content)
    }
}

@Composable
private fun RestoreCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, DialogShape)
            .border(hairline(MaterialTheme.finance.isDark), DialogShape)
            .padding(24.dp),
        content = content,
    )
}

/** Icon + title header used by every restore dialog. */
@Composable
private fun DialogHeader(icon: ImageVector, tint: Color, title: String) {
    RoundIcon(icon, tint, size = 44.dp)
    Spacer(Modifier.height(16.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
internal fun RestoreProgressDialog(title: String, note: String?, onCancel: (() -> Unit)?) {
    RestoreDialog(onDismiss = { onCancel?.invoke() }, dismissible = onCancel != null) {
        RestoreProgressBody(title, note, onCancel)
    }
}

@Composable
private fun ColumnScope.RestoreProgressBody(title: String, note: String?, onCancel: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (note != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (onCancel != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onCancel, shape = PillShape, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
internal fun RestoreRejectedDialog(
    headline: String,
    problems: List<String>,
    onChooseAnother: () -> Unit,
    onClose: () -> Unit,
) {
    RestoreDialog(onDismiss = onClose) {
        RestoreRejectedBody(headline, problems, onChooseAnother, onClose)
    }
}

@Composable
private fun ColumnScope.RestoreRejectedBody(
    headline: String,
    problems: List<String>,
    onChooseAnother: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        DialogHeader(AppIcons.Error, MaterialTheme.colorScheme.error, stringResource(R.string.data_restore_reject_title))
        Spacer(Modifier.height(8.dp))
        Text(headline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (problems.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            GroupedCard(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        problems.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
    PrimaryButton(stringResource(R.string.data_restore_reject_choose_another), onChooseAnother)
    Spacer(Modifier.height(4.dp))
    DialogTextButton(stringResource(R.string.data_restore_reject_close), onClose)
}

@Composable
internal fun RestorePreviewDialog(
    current: DataSummary?,
    incoming: DataSummary,
    upgradedFrom: Int?,
    backedUpFirst: Boolean,
    onBackUpFirst: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    RestoreDialog(onDismiss = onCancel) {
        RestorePreviewBody(current, incoming, upgradedFrom, backedUpFirst, onBackUpFirst, onReplace, onCancel)
    }
}

@Composable
private fun ColumnScope.RestorePreviewBody(
    current: DataSummary?,
    incoming: DataSummary,
    upgradedFrom: Int?,
    backedUpFirst: Boolean,
    onBackUpFirst: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState()),
    ) {
        DialogHeader(AppIcons.Warning, MaterialTheme.colorScheme.error, stringResource(R.string.data_restore_preview_title))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.data_restore_preview_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (upgradedFrom != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(IconSize.Small))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.data_restore_preview_upgrade_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        ComparisonTable(current, incoming, upgradedFrom)
    }

    Spacer(Modifier.height(24.dp))
    if (backedUpFirst) {
        BackedUpChip()
    } else {
        OutlinedButton(
            onClick = onBackUpFirst,
            shape = PillShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(AppIcons.Export, null, modifier = Modifier.size(IconSize.Small), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.data_restore_action_backup_first),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton(stringResource(R.string.data_restore_action_replace), onReplace, destructive = true)
    Spacer(Modifier.height(4.dp))
    DialogTextButton(stringResource(R.string.action_cancel), onCancel)
}

/** Soft mint chip (primary on primary @ 12%) confirming the safety backup was saved. */
@Composable
private fun BackedUpChip() {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(primary.copy(alpha = 0.12f), PillShape),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Success, null, tint = primary, modifier = Modifier.size(IconSize.Small))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.data_restore_action_backed_up), style = MaterialTheme.typography.labelLarge, color = primary)
    }
}

@Composable
private fun DialogTextButton(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, shape = PillShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ComparisonTable(current: DataSummary?, incoming: DataSummary, upgradedFrom: Int?) {
    val numbers = NumberFormat.getIntegerInstance()
    fun count(value: Int?) = value?.let(numbers::format) ?: "—"
    val version = if (upgradedFrom != null) "$upgradedFrom → ${incoming.schemaVersion}" else incoming.schemaVersion.toString()

    val rows = listOf(
        Triple(stringResource(R.string.data_accounts), count(current?.accounts), count(incoming.accounts)),
        Triple(stringResource(R.string.data_transactions), count(current?.transactions), count(incoming.transactions)),
        Triple(stringResource(R.string.data_transfers), count(current?.transfers), count(incoming.transfers)),
        Triple(stringResource(R.string.data_categories), count(current?.categories), count(incoming.categories)),
        Triple(stringResource(R.string.data_tags), count(current?.tags), count(incoming.tags)),
        Triple(stringResource(R.string.data_schedules), count(current?.schedules), count(incoming.schedules)),
        Triple(stringResource(R.string.data_period), period(current), period(incoming)),
        Triple(stringResource(R.string.data_schema_version), current?.schemaVersion?.toString() ?: "—", version),
    )

    GroupedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Spacer(Modifier.weight(LabelWeight))
            ColumnLabel(stringResource(R.string.data_restore_preview_current_col), Modifier.weight(ValueWeight))
            ColumnLabel(stringResource(R.string.data_restore_preview_backup_col), Modifier.weight(ValueWeight))
        }
        rows.forEach { (label, now, backup) ->
            HorizontalDivider(
                Modifier.padding(horizontal = 14.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            ComparisonRow(label, now, backup)
        }
    }
}

private const val LabelWeight = 1.3f
private const val ValueWeight = 1f

@Composable
private fun ColumnLabel(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun ComparisonRow(label: String, current: String, incoming: String) {
    val valueStyle = AmountStyle.copy(fontSize = 14.sp, lineHeight = 18.sp, textAlign = TextAlign.End)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(LabelWeight),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(current, Modifier.weight(ValueWeight), style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            incoming,
            Modifier.weight(ValueWeight),
            style = valueStyle,
            // What changes stands out; identical values stay quiet.
            color = if (incoming != current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** First and last operation dates on two lines, so the range never wraps mid-date. */
private fun period(summary: DataSummary?): String {
    val first = summary?.firstOperationAt ?: return "—"
    val last = summary.lastOperationAt ?: return "—"
    val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
    val zone = ZoneId.systemDefault()
    fun date(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(fmt)
    return "${date(first)} –\n${date(last)}"
}

// --- Previews ---

private val PreviewCurrent = DataSummary(
    accounts = 9, activeAccounts = 8, categories = 31, tags = 12, transactions = 4874, transfers = 124,
    schedules = 8, firstOperationAt = 1_698_883_200_000, lastOperationAt = 1_794_441_600_000,
    databaseBytes = 2_400_000, schemaVersion = 3,
)
private val PreviewIncoming = PreviewCurrent.copy(transactions = 4612, tags = 10, lastOperationAt = 1_788_000_000_000)

@Composable
private fun RestorePreviewSample(dark: Boolean, backedUp: Boolean) {
    PreviewTheme(dark = dark) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            RestoreCard {
                RestorePreviewBody(PreviewCurrent, PreviewIncoming, upgradedFrom = if (backedUp) null else 2, backedUp, {}, {}, {})
            }
        }
    }
}

@Preview(name = "Restore preview light", heightDp = 900)
@Composable
private fun RestorePreviewLight() = RestorePreviewSample(dark = false, backedUp = false)

@Preview(name = "Restore preview dark", heightDp = 900)
@Composable
private fun RestorePreviewDark() = RestorePreviewSample(dark = true, backedUp = true)

@Composable
private fun RestoreRejectedSample(dark: Boolean) {
    PreviewTheme(dark = dark) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            RestoreCard {
                RestoreRejectedBody(
                    "This file is not a Finora backup.",
                    listOf("transactions #12: account missing", "transactions #40: account missing"),
                    {},
                    {},
                )
            }
        }
    }
}

@Preview(name = "Restore rejected light")
@Composable
private fun RestoreRejectedLight() = RestoreRejectedSample(dark = false)

@Preview(name = "Restore rejected dark")
@Composable
private fun RestoreRejectedDark() = RestoreRejectedSample(dark = true)

@Composable
private fun RestoreProgressSample(dark: Boolean) {
    PreviewTheme(dark = dark) {
        Column(Modifier.background(MaterialTheme.colorScheme.background).padding(20.dp)) {
            RestoreCard { RestoreProgressBody("Checking structure", null, onCancel = {}) }
        }
    }
}

@Preview(name = "Restore progress light")
@Composable
private fun RestoreProgressLight() = RestoreProgressSample(dark = false)

@Preview(name = "Restore progress dark")
@Composable
private fun RestoreProgressDark() = RestoreProgressSample(dark = true)
