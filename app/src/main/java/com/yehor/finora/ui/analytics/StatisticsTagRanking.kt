package com.yehor.finora.ui.analytics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.SpaceGrotesk
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.tag.Tag

// The Tags tab before any tag is picked: the ranking of the period's tags.

/** The tint of a tag: its own color, else the accent. */
@Composable
private fun tagTint(tag: Tag): Color = tag.color?.let { MaterialTheme.finance.accent(it) } ?: MaterialTheme.colorScheme.primary

internal fun LazyListScope.tagRankingItems(
    content: StatsContent.TagRanking,
    state: StatisticsUiState,
    vm: StatisticsViewModel,
) {
    val env = state.env ?: return
    item(key = "tag_ranking") {
        TagRankingSection(
            rows = content.rows,
            measure = content.measure,
            measureEnabled = state.controls.tagFilter.types.size != 1,
            title = stringResource(R.string.stats_tags_ranking_title, periodShortName(env.period, env.today)),
            onMeasure = vm::setTagMeasure,
            onOpen = vm::toggleTag,
            onCombine = vm::combineTags,
        )
    }
}

/**
 * "Tags in November" with the Spent / Earned switch over the ranked tags. Tapping a tag opens its
 * stats; a long press starts picking several, which "Combine" opens together (any of them).
 */
@Composable
internal fun TagRankingSection(
    rows: List<TagRankRow>,
    measure: Measure,
    measureEnabled: Boolean,
    title: String,
    onMeasure: (Measure) -> Unit,
    onOpen: (Long) -> Unit,
    onCombine: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picked by rememberSaveable { mutableStateOf(longArrayOf()) }
    // Picks of tags that left the ranking (another period, a deleted tag) do not count.
    val pickedIds = picked.filter { id -> rows.any { it.tag.id == id } }.toSet()
    val picking = pickedIds.isNotEmpty()
    fun toggle(id: Long) {
        picked = if (id in pickedIds) (pickedIds - id).toLongArray() else (pickedIds + id).toLongArray()
    }
    BackHandler(enabled = picking) { picked = longArrayOf() }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            val flows = listOf(Measure.EXPENSE, Measure.INCOME)
            SegmentedPill(
                options = listOf(stringResource(R.string.stats_measure_spent), stringResource(R.string.stats_measure_earned)),
                selected = flows.indexOf(measure),
                onSelect = { onMeasure(flows[it]) },
                enabled = measureEnabled,
                modifier = Modifier.width(184.dp),
            )
        }
        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.stats_tags_ranking_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
            return@Column
        }
        GroupedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            rows.forEach { row ->
                val id = row.tag.id
                TagRankItem(
                    row = row,
                    selected = id in pickedIds,
                    onClick = { if (picking) toggle(id) else onOpen(id) },
                    onLongClick = { toggle(id) },
                )
            }
        }
        if (picking) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.heightIn(min = 48.dp).clip(PillShape).clickable(role = Role.Button) { picked = longArrayOf() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrimaryButton(
                    text = stringResource(R.string.stats_tags_combine, pickedIds.size),
                    onClick = {
                        onCombine(pickedIds)
                        picked = longArrayOf()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Text(
                stringResource(R.string.stats_tags_ranking_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}

/** "#" tile (a check while picked), name and amount, a bar relative to the largest tag, and the count. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TagRankItem(row: TagRankRow, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint = tagTint(row.tag)
    val reduceMotion = rememberReduceMotion()
    // Read only while drawing the bar: the 450ms grow never recomposes the row's text and icons.
    val fraction = animateFloatAsState(
        row.fraction,
        if (reduceMotion) snap() else tween(BAR_MS),
        label = "tagBar",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) scheme.primary.copy(alpha = 0.08f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.stats_tags_pick),
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(if (selected) scheme.primary.copy(alpha = 0.16f) else scheme.surfaceContainerHighest, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(AppIcons.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            } else {
                Text("#", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.tag.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                )
                AmountText(row.cell, color = scheme.onSurface)
            }
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(PillShape)
                    .background(scheme.surfaceContainerHighest)
                    .drawBehind {
                        val f = fraction.value.coerceIn(0f, 1f)
                        if (f > 0f) {
                            drawRoundRect(tint, size = Size(size.width * f, size.height), cornerRadius = CornerRadius(size.height / 2f))
                        }
                    },
            )
            Text(
                pluralStringResource(R.plurals.analytics_operations, row.cell.operationCount, row.cell.operationCount),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val BAR_MS = 450
