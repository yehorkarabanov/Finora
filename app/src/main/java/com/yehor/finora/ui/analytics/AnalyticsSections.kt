package com.yehor.finora.ui.analytics

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yehor.finora.R
import com.yehor.finora.designsystem.chart.SplitBar
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.icon.AppIcons
import com.yehor.finora.designsystem.icon.IconCatalog
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.PillShape
import com.yehor.finora.designsystem.theme.SpaceGrotesk
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.domain.analytics.Cell
import com.yehor.finora.domain.analytics.DrillRow
import com.yehor.finora.domain.analytics.Measure
import com.yehor.finora.domain.analytics.Share
import com.yehor.finora.domain.category.Category
import com.yehor.finora.domain.model.CategoryType
import com.yehor.finora.domain.operation.TransactionFilter
import com.yehor.finora.ui.common.ColorPalette
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Sections of the Statistics screen: the category drill-down and the by-account / by-tag breakdowns.

/** "Food", "Directly in Food" or "Uncategorized". */
@Composable
fun rowLabel(row: DrillRow): String {
    val name = row.category?.name ?: return stringResource(R.string.picker_uncategorized)
    return if (row.isDirect) stringResource(R.string.stats_directly_in, name) else name
}

private const val UNCATEGORIZED_COLOR = 0xFF52525BL

/** Palette colors for rows that have none of their own, distinct from one row to the next. */
internal fun fallbackColor(index: Int): Long = ColorPalette.colors[(index * 11) % ColorPalette.colors.size]

/**
 * A category's own color, else its nearest ancestor's, else the type default for the first row
 * and distinct palette colors after it.
 */
private fun categoryColor(category: Category, byId: Map<Long, Category>, index: Int, type: CategoryType): Long {
    var current: Category? = category
    var depth = 0
    while (current != null && depth < MAX_DEPTH) {
        current.color?.let { return it }
        current = current.parentId?.let(byId::get)
        depth++
    }
    return if (index == 0) ColorPalette.defaultFor(type) else fallbackColor(index)
}

private const val MAX_DEPTH = 4

/** The color of a drill row: muted for a "Directly in" row and for Uncategorized. */
@Composable
private fun rowTint(row: DrillRow, index: Int, type: CategoryType, byId: Map<Long, Category>): Color {
    val category = row.category
    return when {
        row.isDirect -> MaterialTheme.colorScheme.outline
        category == null -> MaterialTheme.finance.accent(UNCATEGORIZED_COLOR)
        else -> MaterialTheme.finance.accent(categoryColor(category, byId, index, type))
    }
}

// ---- Split bar ----

/** One colored segment of a split bar; [key] keeps a segment's animation across data changes. */
data class SplitPart(val key: Any, val weight: Float, val color: Color)

/** The weight of an amount in a split bar: nothing for a missing, negative or overflowed amount. */
internal fun splitWeight(cell: Cell): Float =
    if (cell.overflowed) 0f else (cell.estimate?.amountMinor ?: 0L).coerceAtLeast(0L).toFloat()

/**
 * [SplitBar] whose segments grow and shrink to new weights: a new segment grows from nothing, a
 * segment that is gone shrinks away. Instant under reduced motion.
 */
@Composable
fun AnimatedSplitBar(parts: List<SplitPart>, modifier: Modifier = Modifier) {
    val instant = rememberReduceMotion() || LocalInspectionMode.current
    val weights = remember { mutableStateMapOf<Any, Animatable<Float, AnimationVector1D>>() }
    val colors = remember { mutableStateMapOf<Any, Color>() }
    val order = remember { mutableStateListOf<Any>() }
    if (weights.isEmpty() && parts.isNotEmpty()) {
        // First composition: register the segments now (formed when instant, else growing from nothing).
        parts.forEach { part ->
            weights[part.key] = Animatable(if (instant) part.weight else 0f)
            colors[part.key] = part.color
            order.add(part.key)
        }
    }
    LaunchedEffect(parts, instant) {
        val targets = parts.associate { it.key to it.weight }
        parts.forEach { part ->
            colors[part.key] = part.color
            if (part.key !in weights) weights[part.key] = Animatable(0f)
        }
        val stale = order.filter { it !in targets }
        order.clear()
        order.addAll(parts.map { it.key } + stale)
        coroutineScope {
            order.toList().forEach { key ->
                val animatable = weights[key] ?: return@forEach
                val target = targets[key] ?: 0f
                launch { if (instant) animatable.snapTo(target) else animatable.animateTo(target, tween(SPLIT_MS)) }
            }
        }
        stale.forEach { key ->
            weights.remove(key)
            colors.remove(key)
            order.remove(key)
        }
    }
    SplitBar(
        parts = order.mapNotNull { key ->
            val weight = weights[key]?.value ?: return@mapNotNull null
            val color = colors[key] ?: return@mapNotNull null
            weight to color
        },
        modifier = modifier,
    )
}

private const val SPLIT_MS = 450

// ---- Rows ----

/** A section title ("Categories") with an optional trailing link ("Show transactions"). */
@Composable
internal fun StatsSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    link: String? = null,
    onLink: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = SpaceGrotesk,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (link != null && onLink != null) {
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onLink).padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    link,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        trailing?.invoke()
    }
}

/** "57%" in the row's color; "<1%" for a sliver. */
@Composable
internal fun PercentBadge(share: Share, tint: Color, modifier: Modifier = Modifier) {
    val text = if (share.permille in 1..4) "<1%" else "${(share.permille + 5) / 10}%"
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier = modifier.background(tint.copy(alpha = 0.15f), PillShape).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** The icon of a "Directly in <parent>" row: a dashed ring instead of a tinted disc. */
@Composable
internal fun DashedRoundIcon(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val ring = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            drawCircle(
                color = ring,
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
            )
        }
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(size * 0.5f))
    }
}

/**
 * One row of a statistics list: icon, name and details, the amount and its share as a badge in
 * [tint]. [dimTitle] greys the name (a "Directly in" row); [muted] fades the whole row.
 */
@Composable
internal fun StatRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    cell: Cell?,
    share: Share?,
    tint: Color,
    modifier: Modifier = Modifier,
    dimTitle: Boolean = false,
    muted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (muted) 0.6f else 1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (dimTitle) scheme.onSurfaceVariant else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AmountText(cell, color = scheme.onSurface)
            if (share != null) PercentBadge(share, if (dimTitle) scheme.onSurfaceVariant else tint)
        }
    }
}

// ---- Categories ----

/**
 * Categories of the period: the trail ("All › Food › Restaurants"), the level total, a split bar and
 * the rows of the current level. Tapping a parent drills in (the list slides left, the bar re-forms);
 * the trail jumps back. Leaf rows and "Show transactions" open filtered Transactions.
 */
@Composable
internal fun CategoryCard(
    report: CategoryReport,
    measure: Measure,
    base: TransactionFilter,
    compare: Boolean,
    categories: List<Category>,
    onEnter: (Long) -> Unit,
    onBack: (Int) -> Unit,
    onOpen: (TransactionFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val byId = remember(categories) { categories.associateBy { it.id } }
    val childCount = remember(categories) { categories.groupingBy { it.parentId }.eachCount() }
    val type = measure.categoryType
    val reduceMotion = rememberReduceMotion()
    Column(modifier.fillMaxWidth()) {
        StatsSectionTitle(
            stringResource(R.string.stats_categories),
            link = stringResource(R.string.stats_show_transactions),
            onLink = {
                val current = report.breadcrumb.lastOrNull()
                onOpen(
                    if (current == null) {
                        base.copy(types = setOf(measure.operationType()))
                    } else {
                        AnalyticsFilters.drill(base, measure, DrillRow(current.id, current, report.total, isDirect = false, hasChildren = true))
                    },
                )
            },
        )
        if (report.breadcrumb.isNotEmpty()) {
            Breadcrumb(report.breadcrumb, onBack)
            AmountText(report.levelTotal, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = SpaceGrotesk))
        }
        val parts = report.lines.mapIndexedNotNull { index, line ->
            val row = line.row
            if (row.cell.isEmpty) null else SplitPart(row.key(), splitWeight(row.cell), rowTint(row, index, type, byId))
        }
        AnimatedSplitBar(parts, Modifier.padding(top = 12.dp))
        AnimatedContent(
            targetState = report,
            contentKey = { level -> level.breadcrumb.map { it.id } },
            transitionSpec = {
                if (reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // Deeper: the new level comes in from the right; back: from the left.
                    val dir = if (targetState.breadcrumb.size > initialState.breadcrumb.size) 1 else -1
                    (slideInHorizontally(tween(DRILL_MS)) { it * dir } + fadeIn(tween(DRILL_MS))) togetherWith
                        (slideOutHorizontally(tween(DRILL_MS)) { -it * dir } + fadeOut(tween(DRILL_MS / 2))) using
                        SizeTransform(clip = false)
                }
            },
            label = "categoryLevel",
        ) { level ->
            if (level.lines.isEmpty()) {
                Text(
                    stringResource(R.string.stats_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                )
            } else {
                GroupedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    level.lines.forEachIndexed { index, line ->
                        CategoryRow(
                            line = line,
                            tint = rowTint(line.row, index, type, byId),
                            children = line.row.categoryId?.let { childCount[it] } ?: 0,
                            compare = compare,
                            onOpen = when {
                                compare && line.previous != null && line.row.cell.isEmpty -> null
                                line.row.categoryId != null && line.row.hasChildren && !line.row.isDirect ->
                                    ({ onEnter(line.row.categoryId) })
                                else -> ({ onOpen(AnalyticsFilters.drill(base, measure, line.row)) })
                            },
                        )
                    }
                }
            }
        }
    }
}

private const val DRILL_MS = 280

private fun DrillRow.key(): Any = (categoryId ?: -1L) to isDirect

@Composable
private fun CategoryRow(line: CategoryLine, tint: Color, children: Int, compare: Boolean, onOpen: (() -> Unit)?) {
    val row = line.row
    val previousOnly = compare && line.previous != null && row.cell.isEmpty
    val subtitle = buildList {
        if (row.hasChildren && !row.isDirect && children > 0) {
            add(pluralStringResource(R.plurals.stats_subcategories, children, children) + " ›")
        } else if (row.cell.operationCount > 0) {
            add(pluralStringResource(R.plurals.analytics_operations, row.cell.operationCount, row.cell.operationCount))
        }
        deltaText(line.delta).takeIf { it.isNotEmpty() }?.let(::add)
    }.joinToString(" · ")
    val category = row.category
    StatRow(
        icon = {
            when {
                row.isDirect -> DashedRoundIcon(IconCatalog.get(category?.icon))
                category == null -> RoundIcon(AppIcons.Help, tint)
                else -> RoundIcon(IconCatalog.get(category.icon), tint)
            }
        },
        title = rowLabel(row),
        subtitle = subtitle,
        cell = if (previousOnly) null else row.cell,
        share = line.share,
        tint = tint,
        dimTitle = row.isDirect,
        muted = previousOnly,
        onClick = onOpen,
    )
}

/** "All › Food & Dining › Restaurants": every step but the current one jumps back to it. */
@Composable
private fun Breadcrumb(trail: List<Category>, onBack: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CrumbChip(stringResource(R.string.stats_crumb_all), current = false) { onBack(0) }
        trail.forEachIndexed { index, category ->
            Text("›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CrumbChip(category.name, current = index == trail.lastIndex) { onBack(index + 1) }
        }
    }
}

@Composable
private fun CrumbChip(text: String, current: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.heightIn(min = 48.dp).clickable(enabled = !current, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (current) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .background(if (current) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainer, PillShape)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

// ---- Breakdowns ----

/** One row of a by-account or by-tag breakdown. */
data class BreakdownItem(
    val key: Any,
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val cell: Cell,
    val share: Share?,
    val onClick: (() -> Unit)?,
)

/** A titled breakdown: split bar over a grouped card of rows with amounts and shares. */
@Composable
internal fun BreakdownCard(title: String, items: List<BreakdownItem>, modifier: Modifier = Modifier, note: String? = null) {
    Column(modifier.fillMaxWidth()) {
        StatsSectionTitle(title)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedSplitBar(items.map { SplitPart(it.key, splitWeight(it.cell), it.tint) }, Modifier.padding(top = 12.dp))
        GroupedCard(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            items.forEach { item ->
                StatRow(
                    icon = { RoundIcon(item.icon, item.tint) },
                    title = item.label,
                    subtitle = item.cell.operationCount.takeIf { it > 0 }
                        ?.let { pluralStringResource(R.plurals.analytics_operations, it, it) },
                    cell = item.cell,
                    share = item.share,
                    tint = item.tint,
                    onClick = item.onClick,
                )
            }
        }
    }
}
