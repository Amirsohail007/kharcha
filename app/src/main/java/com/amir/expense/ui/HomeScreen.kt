package com.amir.expense.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.Budget
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.Category
import com.amir.expense.data.formatRupees
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(vm: MainViewModel, openInbox: () -> Unit) {
    val month by vm.month.collectAsState()
    val txns by vm.txns.collectAsState()
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val inbox by vm.inbox.collectAsState()
    var editTotal by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(emptySet<Long>()) }

    val spent = BudgetMath.totalSpend(txns)
    val byCategory = BudgetMath.spendByCategory(txns, categories)
    val parents = categories.filter { it.parentId == null }
        .sortedWith(compareByDescending<Category> { byCategory[it.id] ?: 0L }.thenByDescending { budgets[it.id] ?: 0L })

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader(month.month.name.lowercase().replaceFirstChar { it.uppercase() }, vm) }
        item { HeroCard(spent, budgets[Budget.TOTAL], month, onEdit = { editTotal = true }) }
        if (inbox.isNotEmpty()) item { SortBanner(inbox.size, openInbox) }
        item { SectionLabel("Categories") }
        item {
            AppCard(padding = 8.dp) {
                parents.forEachIndexed { i, parent ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    val kids = categories.filter { it.parentId == parent.id }
                    val open = parent.id in expanded
                    Column(Modifier.animateContentSize()) {
                        CategoryRow(
                            category = parent, categories = categories,
                            spent = byCategory[parent.id] ?: 0L, budget = budgets[parent.id],
                            trailingIcon = if (kids.isEmpty()) null else if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            onClick = if (kids.isEmpty()) null else { { expanded = if (open) expanded - parent.id else expanded + parent.id } },
                        )
                        if (open) {
                            kids.forEach { kid ->
                                CategoryRow(
                                    category = kid, categories = categories,
                                    spent = byCategory[kid.id] ?: 0L, budget = budgets[kid.id],
                                    small = true, modifier = Modifier.padding(start = 20.dp),
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    if (editTotal) {
        AmountDialog(
            title = "Monthly budget",
            initialPaise = budgets[Budget.TOTAL],
            note = "Applies from this month onward.",
            onSave = { vm.setBudget(Budget.TOTAL, it) },
            onDismiss = { editTotal = false },
        )
    }
}

/** The headline: money left this month on the brand color, with a slim meter and daily allowance. */
@Composable
private fun HeroCard(spent: Long, budget: Long?, month: YearMonth, onEdit: () -> Unit) {
    val over = budget != null && spent > budget
    val onHero = Color.White
    val soft = Color.White.copy(alpha = 0.72f)
    val progress by animateFloatAsState(if (budget == null || budget == 0L) 0f else (spent.toFloat() / budget).coerceIn(0f, 1f), label = "spent")

    Surface(shape = RoundedCornerShape(28.dp), color = if (over) HeroOver else HeroIndigo, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when { budget == null -> "Spent this month"; over -> "Over budget by"; else -> "Left to spend" },
                    style = MaterialTheme.typography.labelLarge, color = soft, modifier = Modifier.weight(1f),
                )
                if (budget != null) {
                    // IconButton keeps the 48dp touch target while the pencil stays small.
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit monthly budget", tint = soft, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(
                formatRupees(when { budget == null -> spent; over -> spent - budget; else -> budget - spent }),
                style = MaterialTheme.typography.displaySmall, color = onHero,
            )
            if (budget == null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onEdit, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeroIndigo),
                ) { Text("Set a monthly budget") }
                return@Column
            }
            Spacer(Modifier.height(16.dp))
            Meter(progress, Color.White, height = 8.dp, track = Color.White.copy(alpha = 0.22f))
            Spacer(Modifier.height(12.dp))
            Row {
                Text("${formatRupees(spent)} of ${formatRupees(budget)}", style = MaterialTheme.typography.bodyMedium, color = soft, modifier = Modifier.weight(1f))
                val today = LocalDate.now()
                if (!over && YearMonth.from(today) == month) {
                    val daysLeft = month.lengthOfMonth() - today.dayOfMonth + 1
                    Text(
                        "${formatRupees((budget - spent) / daysLeft / 100 * 100)}/day",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = onHero,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortBanner(count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MoveToInbox, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${plural(count, "payment")} to sort", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("Pick a category so budgets stay accurate", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/** Badge, name, amount, and (when budgeted) a meter with "left" / "over by" in words. */
@Composable
private fun CategoryRow(
    category: Category,
    categories: List<Category>,
    spent: Long,
    budget: Long?,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val look = lookOf(category.id, categories)
    val over = budget != null && spent > budget
    val muted = spent == 0L && budget == null
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = if (small) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(look, size = if (small) 32.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category.name,
                    style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatRupees(spent),
                    style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                    color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (budget != null) {
                Spacer(Modifier.height(6.dp))
                Meter(spent.toFloat() / budget, if (over) MaterialTheme.colorScheme.error else look.color)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (over) "Over by ${formatRupees(spent - budget)}" else "${formatRupees(budget - spent)} left of ${formatRupees(budget)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(trailingIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
