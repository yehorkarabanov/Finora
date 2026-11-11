package com.yehor.finora.designsystem.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.chart.SplitBar
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.component.button.RoundIconButton
import com.yehor.finora.designsystem.component.display.AmountText
import com.yehor.finora.designsystem.component.input.FilterPill
import com.yehor.finora.designsystem.component.input.SegmentedPill
import com.yehor.finora.designsystem.component.list.GroupedCard
import com.yehor.finora.designsystem.component.list.ListRow
import com.yehor.finora.designsystem.component.list.RoundIcon
import com.yehor.finora.designsystem.component.list.SectionHeader
import com.yehor.finora.designsystem.component.navigation.AddFab
import com.yehor.finora.designsystem.component.navigation.FabMode
import com.yehor.finora.designsystem.component.navigation.FinoraNavBar
import com.yehor.finora.designsystem.component.navigation.NavBarItem
import com.yehor.finora.designsystem.component.navigation.SpeedDialItem
import com.yehor.finora.designsystem.component.sheet.RangeWheelsContent
import com.yehor.finora.designsystem.icon.AppIcons
import java.time.LocalDate
import java.time.YearMonth
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.grainyBackground

/**
 * Gallery of every design-system component, light and dark. Later tasks append their
 * components here; this starts with the color swatches and the type scale.
 */
@Composable
fun ComponentCatalog(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ColorSwatches()
        TypeScale()
        ChartsSection()
        ControlsSection()
        GrainSection()
        ListSection()
        NavigationSection()
    }
}

@Composable
private fun Swatch(name: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .background(color, RoundedCornerShape(12.dp)),
        )
        Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColorSwatches(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val finance = MaterialTheme.finance
    val roles = listOf(
        "surface" to scheme.surface,
        "container" to scheme.surfaceContainer,
        "primary" to scheme.primary,
        "onSurface" to scheme.onSurface,
        "variant" to scheme.onSurfaceVariant,
        "outline" to scheme.outlineVariant,
        "income" to finance.income,
        "expense" to finance.expense,
        "negative" to finance.negative,
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Colors", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        roles.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (name, color) -> Swatch(name, color) }
            }
        }
    }
}

@Composable
private fun TypeScale(modifier: Modifier = Modifier) {
    val type = MaterialTheme.typography
    val onSurface = MaterialTheme.colorScheme.onSurface
    val styles = listOf(
        "displayLarge" to type.displayLarge,
        "headlineMedium" to type.headlineMedium,
        "titleLarge" to type.titleLarge,
        "titleMedium" to type.titleMedium,
        "bodyLarge" to type.bodyLarge,
        "bodyMedium" to type.bodyMedium,
        "labelLarge" to type.labelLarge,
        "labelSmall" to type.labelSmall,
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Type scale", style = type.titleMedium, color = onSurface)
        styles.forEach { (name, style) -> Text("$name 1 234,56", style = style, color = onSurface) }
    }
}

@Composable
private fun ChartsSection(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val finance = MaterialTheme.finance
    val runningNet = listOf(120L, 300L, 180L, -150L, -320L, -40L, 260L, 540L, 480L, 760L)
    val compare = listOf(80L, 150L, 220L, 90L, 40L, 120L, 200L, 310L, 380L, 420L)
    var selected by remember { mutableStateOf<Int?>(7) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Wave chart", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        WaveChart(
            values = runningNet,
            zeroLine = true,
            selectedIndex = selected,
            onSelect = { selected = it },
            label = { "Day ${it + 1}: ${runningNet[it]}" },
            modifier = Modifier.fillMaxWidth().height(160.dp),
        )
        WaveChart(listOf(5L, 5L, 5L, 5L), label = { "" }, modifier = Modifier.fillMaxWidth().height(80.dp))
        WaveChart(
            values = runningNet,
            compare = compare,
            label = { "" },
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        WaveChart(
            values = runningNet,
            sparkline = true,
            color = finance.expense,
            label = { "" },
            modifier = Modifier.fillMaxWidth().height(36.dp),
        )
        Text("Split bar", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        SplitBar(listOf(5f to finance.income, 3f to finance.expense, 2f to scheme.primary))
    }
}

@Composable
private fun ControlsSection(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    var segment by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf(true) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Controls", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        SegmentedPill(listOf("Expense", "Income", "Transfer"), segment, { segment = it })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterPill("Account", active = false, onClick = { filter = true })
            FilterPill("Groceries", active = filter, onClick = {}, onClear = { filter = false })
        }
        PrimaryButton("Save", onClick = {})
        PrimaryButton("Disabled", onClick = {}, enabled = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoundIconButton(AppIcons.Search, "Search", {})
            RoundIconButton(AppIcons.Sort, "Sort", {})
        }
        AmountText("1 234,56", MaterialTheme.typography.displayLarge, scheme.onSurface)
        AmountText("-48.20", MaterialTheme.typography.titleLarge, MaterialTheme.finance.negative)
    }
}

@Composable
private fun GrainSection(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val gradient = Brush.verticalGradient(listOf(scheme.primary, scheme.background))
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Grain", style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(96.dp).background(gradient, shape))
                Text("Plain", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(96.dp).grainyBackground(gradient, shape))
                Text("Grain", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ListSection(modifier: Modifier = Modifier) {
    val finance = MaterialTheme.finance
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionHeader("Today") { Text("-62,40", style = MaterialTheme.typography.labelMedium, color = finance.expense) }
        GroupedCard {
            ListRow(
                icon = { RoundIcon(AppIcons.Receipt, finance.expense) },
                title = "Groceries",
                subtitle = "Cash · Supermarket",
                trailing = { AmountText("-48,20", MaterialTheme.typography.titleSmall, finance.expense) },
                onClick = {},
            )
            ListRow(
                icon = { RoundIcon(AppIcons.Income, finance.income) },
                title = "Salary",
                subtitle = null,
                trailing = { AmountText("+2 400,00", MaterialTheme.typography.titleSmall, finance.income) },
                onClick = {},
            )
        }
    }
}

@Composable
private fun NavigationSection(modifier: Modifier = Modifier) {
    val items = listOf(
        NavBarItem("Home", AppIcons.NavDashboard, AppIcons.NavDashboardSelected),
        NavBarItem("List", AppIcons.NavTransactions, AppIcons.NavTransactionsSelected),
        NavBarItem("Accounts", AppIcons.NavAccounts, AppIcons.NavAccountsSelected),
        NavBarItem("Stats", AppIcons.NavStatistics, AppIcons.NavStatisticsSelected),
    )
    val dial = listOf(
        SpeedDialItem("Transfer", AppIcons.Transfer, {}, primary = false),
        SpeedDialItem("Income", AppIcons.Add, {}, primary = false),
        SpeedDialItem("Expense", AppIcons.Remove, {}, primary = true),
    )
    var selected by remember { mutableStateOf(1) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Navigation", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        FinoraNavBar(items, selected, { selected = it }, Modifier.padding(horizontal = 0.dp))
        Box(Modifier.fillMaxWidth().height(80.dp)) {
            AddFab(FabMode.EXTENDED, expanded = false, onToggle = {}, label = "Add", speedDial = dial)
        }
        Box(Modifier.fillMaxWidth().height(80.dp)) {
            AddFab(FabMode.COLLAPSED, expanded = false, onToggle = {}, label = "Add", speedDial = dial)
        }
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            AddFab(FabMode.COLLAPSED, expanded = true, onToggle = {}, label = "Add", speedDial = dial)
        }
        Text("Range wheels", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        RangeWheelsContent(
            initialFrom = YearMonth.of(2025, 11),
            initialTo = YearMonth.of(2026, 2),
            today = LocalDate.of(2026, 11, 10),
            earliest = null,
            quickOptions = listOf("This month" to {}, "Last 3 months" to {}),
            onConfirm = { _, _ -> },
        )
    }
}

@Preview(name = "Catalog light", showBackground = true, heightDp = 2400)
@Composable
private fun ComponentCatalogLightPreview() = PreviewTheme(dark = false) { ComponentCatalog() }

@Preview(name = "Catalog dark", showBackground = true, heightDp = 2400)
@Composable
private fun ComponentCatalogDarkPreview() = PreviewTheme(dark = true) { ComponentCatalog() }
