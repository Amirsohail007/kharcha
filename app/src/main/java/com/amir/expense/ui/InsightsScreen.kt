package com.amir.expense.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.Category
import com.amir.expense.data.Txn
import com.amir.expense.data.formatRupees
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Category color = identity (same as everywhere else in the app), always paired with an icon and a printed
 * value, so nothing is read from color alone. Magnitude-only charts use the single brand hue.
 */
@Composable
fun InsightsScreen(vm: MainViewModel) {
    val month by vm.month.collectAsState()
    val txns by vm.txns.collectAsState()
    val prevTxns by vm.prevTxns.collectAsState()
    val categories by vm.categories.collectAsState()

    val parents = categories.filter { it.parentId == null }
    val now = BudgetMath.spendByCategory(txns, categories)
    val before = BudgetMath.spendByCategory(prevTxns, categories)
    val total = BudgetMath.totalSpend(txns)
    val prevTotal = BudgetMath.totalSpend(prevTxns)
    val unfiled = txns.filter { !it.ignored && it.categoryId == null }.sumOf { it.amountPaise }
    val prevName = month.minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM"))

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Insights", vm) }
        if (total <= 0L) {
            item { AppCard { EmptyState(Icons.Rounded.BarChart, "Nothing to show yet", "Charts appear once this month has payments.") } }
            return@LazyColumn
        }
        item {
            AppCard(padding = 20.dp) {
                Text("Where it went", style = MaterialTheme.typography.titleMedium)
                Text(formatRupees(total), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(14.dp))
                val rows = parents.mapNotNull { p -> (now[p.id] ?: 0L).takeIf { it > 0 }?.let { p to it } }.sortedByDescending { it.second }
                ShareBreakdown(rows, unfiled, total, categories)
            }
        }
        item { AppCard(padding = 20.dp) { DailyBars(txns, month) } }
        item {
            AppCard(padding = 20.dp) {
                CompareSection(
                    prevName, total, prevTotal, categories,
                    parents.map { Triple(it, now[it.id] ?: 0L, before[it.id] ?: 0L) }
                        .filter { it.second > 0 || it.third > 0 }
                        .sortedByDescending { maxOf(it.second, it.third) },
                )
            }
        }
    }
}

/** Part-to-whole: one stacked bar (2dp gaps between segments) plus a legend with icon, amount and share. */
@Composable
private fun ShareBreakdown(rows: List<Pair<Category, Long>>, unfiled: Long, total: Long, categories: List<Category>) {
    val gray = MaterialTheme.colorScheme.outline
    val segments = rows.map { (c, v) -> lookOf(c.id, categories).color to v } + listOfNotNull(if (unfiled > 0) gray to unfiled else null)
    Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        segments.forEach { (color, value) -> Box(Modifier.weight(value.toFloat()).height(14.dp).background(color)) }
    }
    Spacer(Modifier.height(16.dp))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { (c, v) -> LegendRow({ CategoryBadge(lookOf(c.id, categories), size = 34.dp) }, c.name, v, total) }
        if (unfiled > 0) LegendRow({ LetterAvatar("?", size = 34.dp) }, "Uncategorized", unfiled, total)
    }
}

@Composable
private fun LegendRow(badge: @Composable () -> Unit, name: String, value: Long, total: Long) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        badge()
        Spacer(Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(formatRupees(value), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(10.dp))
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "${value * 100 / total}%", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/** Column per day, baseline-anchored with rounded tops. Tap a column to read its value. */
@Composable
private fun DailyBars(txns: List<Txn>, month: YearMonth) {
    val zone = ZoneId.systemDefault()
    val days = month.lengthOfMonth()
    val perDay = LongArray(days)
    txns.filterNot { it.ignored }.forEach { perDay[Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfMonth - 1] += it.amountPaise }
    val max = perDay.max().coerceAtLeast(1)
    val today = LocalDate.now()
    val todayIndex = if (YearMonth.from(today) == month) today.dayOfMonth - 1 else -1
    // Keyed on txns too: the month flips a moment before its transactions load.
    var selected by remember(month, txns) { mutableIntStateOf(if (todayIndex >= 0) todayIndex else perDay.indices.maxBy { perDay[it] }) }

    val primary = MaterialTheme.colorScheme.primary
    val dim = primary.copy(alpha = 0.3f).compositeOver(MaterialTheme.colorScheme.surfaceContainerLowest)
    val baseline = MaterialTheme.colorScheme.outlineVariant

    Text("Day by day", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(formatRupees(perDay[selected].coerceAtLeast(0)), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(8.dp))
        Text(
            month.atDay(selected + 1).format(DateTimeFormatter.ofPattern("EEE, d MMM")),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Spacer(Modifier.height(14.dp))
    Canvas(
        Modifier.fillMaxWidth().height(140.dp).pointerInput(days) {
            detectTapGestures { pos -> selected = (pos.x / size.width * days).toInt().coerceIn(0, days - 1) }
        },
    ) {
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (days - 1)) / days
        val r = minOf(4.dp.toPx(), w / 2)
        for (i in 0 until days) {
            val x = i * (w + gap)
            val h = size.height * perDay[i].coerceAtLeast(0) / max
            val color = if (i == selected) primary else dim
            if (h <= 0f) {
                drawCircle(baseline, radius = 1.5.dp.toPx(), center = Offset(x + w / 2, size.height - 1.5.dp.toPx()))
                continue
            }
            drawRoundRect(color, Offset(x, size.height - h), Size(w, h), CornerRadius(r, r))
            if (h > r) drawRect(color, Offset(x, size.height - r), Size(w, r)) // square foot on the baseline
        }
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        listOf("1", "8", "15", "22").forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        Text("$days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatPill("Highest day", formatRupees(perDay.max()), Modifier.weight(1f))
        StatPill("Daily average", formatRupees(perDay.sum() / days / 100 * 100), Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** This month vs last: category color for now, gray for before, change written as ▲/▼ text. */
@Composable
private fun CompareSection(prevName: String, total: Long, prevTotal: Long, categories: List<Category>, rows: List<Triple<Category, Long, Long>>) {
    val lastColor = MaterialTheme.colorScheme.outline
    Text("Compared with $prevName", style = MaterialTheme.typography.titleMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatRupees(total), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.width(10.dp))
        ChangePill(total, prevTotal)
    }
    Text("${formatRupees(prevTotal)} in $prevName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))
    Text(
        "Colored bar is this month, gray bar is $prevName.",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    val max = rows.maxOfOrNull { maxOf(it.second, it.third) }?.coerceAtLeast(1) ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { (c, nowValue, prevValue) ->
            val look = lookOf(c.id, categories)
            Row(verticalAlignment = Alignment.Top) {
                CategoryBadge(look, size = 34.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row {
                        Text(c.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(formatRupees(nowValue), style = MaterialTheme.typography.titleSmall)
                    }
                    Meter(nowValue.toFloat() / max, look.color, height = 6.dp, track = Color.Transparent)
                    Meter(prevValue.toFloat() / max, lastColor, height = 6.dp, track = Color.Transparent)
                    Text(change(nowValue, prevValue), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChangePill(now: Long, before: Long) {
    val color = if (now > before) MaterialTheme.colorScheme.error else successColor
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.14f)) {
        Text(
            change(now, before), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            color = color, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private fun change(now: Long, before: Long): String = when {
    now == before -> "No change"
    before == 0L -> "▲ ${formatRupees(now)} new"
    now > before -> "▲ ${formatRupees(now - before)} more"
    else -> "▼ ${formatRupees(abs(before - now))} less"
}
