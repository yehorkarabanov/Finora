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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yehor.finora.designsystem.chart.SplitBar
import com.yehor.finora.designsystem.chart.WaveChart
import com.yehor.finora.designsystem.theme.PreviewTheme
import com.yehor.finora.designsystem.theme.finance

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

@Preview(name = "Catalog light", showBackground = true, heightDp = 1200)
@Composable
private fun ComponentCatalogLightPreview() = PreviewTheme(dark = false) { ComponentCatalog() }

@Preview(name = "Catalog dark", showBackground = true, heightDp = 1200)
@Composable
private fun ComponentCatalogDarkPreview() = PreviewTheme(dark = true) { ComponentCatalog() }
