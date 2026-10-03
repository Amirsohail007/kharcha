package com.amir.expense.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.Budget
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.Category
import com.amir.expense.data.formatRupees

/**
 * Every budget in one list: the monthly total on top, then each category and its subcategories.
 * The total is the sum of the category budgets unless you type your own.
 * Changes apply from the month on screen onward.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val month by vm.month.collectAsState()
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var editingTotal by remember { mutableStateOf(false) }

    val custom = budgets[Budget.TOTAL]
    val allocated = BudgetMath.allocated(budgets, categories)
    val parents = categories.filter { it.parentId == null }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text("Budgets", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "From ${monthTitle(month)} onward. Earlier months keep their budgets.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Surface(
                    onClick = { editingTotal = true },
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Savings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Monthly total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                (custom ?: allocated.takeIf { it > 0 })?.let(::formatRupees) ?: "Not set",
                                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                when {
                                    custom != null && allocated > 0 -> "Set by you. Categories add up to ${formatRupees(allocated)}."
                                    custom != null -> "Set by you."
                                    allocated > 0 -> "Sum of your category budgets."
                                    else -> "Give each category a budget and the total adds itself up, or tap to set one total."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (custom != null && allocated > 0) {
                item {
                    TextButton(onClick = { vm.setBudget(Budget.TOTAL, 0) }) {
                        Text("Use the sum of categories (${formatRupees(allocated)}) instead")
                    }
                }
            }
            item { SectionLabel("By category") }
            item {
                AppCard(padding = 8.dp) {
                    parents.forEachIndexed { i, parent ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        val kids = categories.filter { it.parentId == parent.id }
                        val own = budgets[parent.id]
                        val fromKids = kids.sumOf { budgets[it.id] ?: 0L }
                        BudgetRow(
                            category = parent, categories = categories,
                            value = own?.let(::formatRupees) ?: fromKids.takeIf { it > 0 }?.let(::formatRupees),
                            note = when {
                                own != null && fromKids > own -> "Subcategories add up to ${formatRupees(fromKids)}, more than this"
                                own == null && fromKids > 0 -> "Sum of subcategories"
                                else -> null
                            },
                            onClick = { editing = parent },
                        )
                        kids.forEach { kid ->
                            BudgetRow(
                                category = kid, categories = categories,
                                value = budgets[kid.id]?.let(::formatRupees),
                                small = true, modifier = Modifier.padding(start = 20.dp),
                                onClick = { editing = kid },
                            )
                        }
                    }
                }
            }
        }
    }

    if (editingTotal) {
        AmountDialog(
            title = "Monthly total",
            initialPaise = custom,
            note = if (allocated > 0) "Leave empty to use the sum of category budgets (${formatRupees(allocated)})."
            else "Leave empty for no total.",
            onSave = { vm.setBudget(Budget.TOTAL, it) },
            onDismiss = { editingTotal = false },
        )
    }
    editing?.let { c ->
        AmountDialog(
            title = "${c.name} budget",
            initialPaise = budgets[c.id],
            note = "Per month. Leave empty for no budget.",
            onSave = { vm.setBudget(c.id, it) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun BudgetRow(
    category: Category,
    categories: List<Category>,
    value: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    small: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = if (small) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(lookOf(category.id, categories), size = if (small) 32.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        // Unset subcategories stay quiet; an unset category invites you to set one.
        Text(
            value ?: if (small) "–" else "Set",
            style = MaterialTheme.typography.labelLarge,
            color = when {
                value != null -> MaterialTheme.colorScheme.onSurface
                small -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.primary
            },
        )
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp),
        )
    }
}
