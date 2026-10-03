package com.amir.expense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.Category
import com.amir.expense.data.Txn
import com.amir.expense.data.formatRupees
import kotlin.math.abs

@Composable
fun InboxScreen(vm: MainViewModel) {
    val inbox by vm.inbox.collectAsState()
    val categories by vm.categories.collectAsState()
    val top by vm.topCategoryIds.collectAsState()
    var picking by remember { mutableStateOf<Txn?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    // Filed payments leave the inbox: only count what's still here.
    val selected = inbox.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    fun toggle(txn: Txn) { selectedIds = if (txn.id in selectedIds) selectedIds - txn.id else selectedIds + txn.id }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    // One-tap suggestions: your most-used categories, else the first leaf of each top-level category.
    val leaves = categories.filter { c -> categories.none { it.parentId == c.id } }
    val firstPerParent = categories.filter { it.parentId == null }.mapNotNull { p -> leaves.firstOrNull { it.parentId == p.id || it.id == p.id } }
    val quick = (top.mapNotNull { id -> leaves.firstOrNull { it.id == id } } + firstPerParent).distinct().take(2)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (selecting) 160.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ScreenHeader("Inbox")
                Text(
                    if (inbox.isEmpty()) "Imported payments land here until you file them."
                    else "${plural(inbox.size, "payment")} to sort. Tap a category to file one, or More to auto-file that merchant too. " +
                        "Press and hold to pick several to delete.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (inbox.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            Icons.Rounded.TaskAlt,
                            "All caught up",
                            "In PhonePe open History → Download Statement, then share the PDF to Expenses.",
                        ) { ImportButton(vm) }
                    }
                }
            } else {
                item { ImportButton(vm, label = "Import another statement", tonal = true) }
                items(inbox, key = { it.id }) { txn ->
                    InboxCard(
                        txn, quick, categories,
                        selected = txn.id in selectedIds,
                        selecting = selecting,
                        onQuick = { vm.categorize(txn, it, remember = false) },
                        onMore = { if (selecting) toggle(txn) else picking = txn },
                        onLongClick = { toggle(txn) },
                    )
                }
            }
        }
        if (selecting) {
            SelectionBar(
                count = selected.size,
                onClose = { selectedIds = emptySet() },
                onSelectAll = if (selected.size < inbox.size) { { selectedIds = inbox.map { it.id }.toSet() } } else null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) {
                SelectionAction(Icons.Rounded.Block, "Not expense") { vm.markNotExpense(selected); selectedIds = emptySet() }
                SelectionAction(Icons.Rounded.Delete, "Delete", danger = true) { vm.delete(selected); selectedIds = emptySet() }
            }
        }
    }

    picking?.let { txn -> CategorizeSheet(vm, txn, categories, onClose = { picking = null }) }
}

/** While [selecting], a tap picks the card and the category chips are hidden. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InboxCard(
    txn: Txn,
    quick: List<Category>,
    categories: List<Category>,
    selected: Boolean,
    selecting: Boolean,
    onQuick: (Long) -> Unit,
    onMore: () -> Unit,
    onLongClick: () -> Unit,
) {
    AppCard(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onMore, onLongClick = onLongClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) SelectedBadge(size = 44.dp) else LetterAvatar(txn.merchant, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(txn.merchant, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatWhen(txn.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatRupees(abs(txn.amountPaise)), style = MaterialTheme.typography.titleMedium)
        }
        if (selecting) return@AppCard
        Spacer(Modifier.height(12.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            quick.forEach { c ->
                val look = lookOf(c.id, categories)
                AssistChip(
                    onClick = { onQuick(c.id) },
                    label = { Text(c.name) },
                    leadingIcon = { look.icon?.let { Icon(it, contentDescription = null, tint = look.color, modifier = Modifier.size(18.dp)) } },
                    shape = RoundedCornerShape(12.dp),
                    border = null,
                    colors = AssistChipDefaults.assistChipColors(containerColor = look.container),
                )
            }
            AssistChip(
                onClick = onMore,
                label = { Text("More") },
                leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null, modifier = Modifier.size(18.dp)) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

/** Pick a category for one payment, optionally remembering the merchant, or mark it not an expense. */
@Composable
fun CategorizeSheet(vm: MainViewModel, txn: Txn, categories: List<Category>, onClose: () -> Unit) {
    val expense = txn.amountPaise > 0
    var alwaysFile by remember { mutableStateOf(expense) }
    CategoryPickerSheet(
        categories = categories,
        selectedId = txn.categoryId,
        onPick = { vm.categorize(txn, it, alwaysFile); onClose() },
        onDismiss = onClose,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (txn.categoryId == null) LetterAvatar(txn.merchant, size = 48.dp) else CategoryBadge(lookOf(txn.categoryId, categories), size = 48.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(txn.merchant, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatWhen(txn.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    (if (expense) "" else "+") + formatRupees(abs(txn.amountPaise)),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (expense) MaterialTheme.colorScheme.onSurface else successColor,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (expense) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { alwaysFile = !alwaysFile },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = alwaysFile, onCheckedChange = { alwaysFile = it })
                    Text("Always file “${txn.merchant}” here", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    "Money received. Pick a category to count it as a refund there.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            if (!txn.ignored) {
                OutlinedButton(onClick = { vm.setIgnored(txn, true); onClose() }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Not an expense")
                }
            }
        }
    }
}
