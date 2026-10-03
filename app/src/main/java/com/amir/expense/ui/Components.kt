package com.amir.expense.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.Category
import com.amir.expense.data.Txn
import com.amir.expense.data.formatRupees
import com.amir.expense.data.parseRupees
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val TIME = DateTimeFormatter.ofPattern("h:mm a")
private val ROW_DATE = DateTimeFormatter.ofPattern("d MMM, h:mm a")
private val DAY = DateTimeFormatter.ofPattern("d MMM")
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

fun formatWhen(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(ROW_DATE)
fun formatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(TIME)
fun formatDay(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DAY)

/** "Food › Delivery" for leaf categories. */
fun categoryLabel(id: Long?, categories: List<Category>): String? {
    val c = categories.firstOrNull { it.id == id } ?: return null
    val parent = categories.firstOrNull { it.id == c.parentId } ?: return c.name
    return "${parent.name} › ${c.name}"
}

/** plural(1, "payment") -> "1 payment", plural(3, "payment") -> "3 payments". */
fun plural(n: Int, noun: String): String = "$n $noun" + if (n == 1) "" else "s"

fun paiseToInput(paise: Long?): String =
    if (paise == null || paise == 0L) "" else java.math.BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()

/** White rounded card on the gray canvas. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = color) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Big page title with the month switcher on the right. */
@Composable
fun ScreenHeader(title: String, vm: MainViewModel? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (vm != null) MonthSwitcher(vm)
    }
}

@Composable
fun MonthSwitcher(vm: MainViewModel) {
    val month by vm.month.collectAsState()
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftMonth(-1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous month")
            }
            Text(month.format(DateTimeFormatter.ofPattern("MMM yyyy")), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { vm.shiftMonth(1) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next month")
            }
        }
    }
}

fun monthTitle(month: java.time.YearMonth): String = month.format(MONTH)

/** Rounded progress track. [fraction] is clamped; colour carries identity, the text beside it carries the number. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, track: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        if (fraction > 0f) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(height).clip(CircleShape).background(color))
        }
    }
}

/**
 * One payment: category badge (or merchant initial), name, when/category, amount.
 * Long-press starts selecting; a selected row shows a check instead of its badge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TxnRow(
    txn: Txn,
    categories: List<Category>,
    showDate: Boolean = true,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val credit = txn.amountPaise < 0
    val struck = txn.ignored || txn.deletedAt != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            selected -> SelectedBadge()
            txn.categoryId == null -> LetterAvatar(txn.merchant)
            else -> CategoryBadge(lookOf(txn.categoryId, categories))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val status = when {
                txn.deletedAt != null -> "Deleted"
                txn.ignored -> "Not counted"
                txn.categoryId == null -> "Uncategorized"
                else -> categories.firstOrNull { it.id == txn.categoryId }?.name ?: "Uncategorized"
            }
            Text(
                listOfNotNull(if (showDate) formatWhen(txn.timestamp) else formatTime(txn.timestamp), status, txn.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (if (credit) "+" else "−") + formatRupees(abs(txn.amountPaise)),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                struck -> MaterialTheme.colorScheme.onSurfaceVariant
                credit -> successColor
                else -> MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (struck) TextDecoration.LineThrough else null,
        )
        trailing?.invoke()
    }
}

/** Stands in for a row's badge while it's selected. */
@Composable
fun SelectedBadge(size: Dp = 40.dp) {
    Box(
        Modifier.size(size).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.55f))
    }
}

/** Pinned at the bottom while picking payments: how many, select all, and [actions] (see [SelectionAction]). */
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Stop selecting") }
                Text("$count selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onSelectAll != null) TextButton(onClick = onSelectAll) { Text("Select all") }
            }
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/** One labeled button in a [SelectionBar]; [danger] colors it as destructive. */
@Composable
fun RowScope.SelectionAction(icon: ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.weight(1f),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        colors = if (danger) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Centered illustration-style empty state. */
@Composable
fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        action()
    }
}

/**
 * Bottom sheet with every category as an icon tile, grouped by parent; one tap picks a leaf.
 * [header] goes above the grid (payment summary, "always" checkbox, ignore button).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: Long?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            header()
            val parents = categories.filter { it.parentId == null }
            val leafParents = parents.filter { p -> categories.none { it.parentId == p.id } }
            parents.filter { it !in leafParents }.forEach { p ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        categories.filter { it.parentId == p.id }.forEach { c -> CategoryTile(c, categories, c.id == selectedId) { onPick(c.id) } }
                    }
                }
            }
            if (leafParents.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    leafParents.forEach { c -> CategoryTile(c, categories, c.id == selectedId) { onPick(c.id) } }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(c: Category, categories: List<Category>, selected: Boolean, onClick: () -> Unit) {
    val look = lookOf(c.id, categories)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) look.container else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, look.color) else null,
    ) {
        Row(Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(look, size = 30.dp)
            Spacer(Modifier.width(8.dp))
            Text(c.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Rupee amount entry. Saving an empty field passes 0 (= remove budget). */
@Composable
fun AmountDialog(title: String, initialPaise: Long?, note: String? = null, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(paiseToInput(initialPaise)) }
    val parsed = if (text.isBlank()) 0L else parseRupees(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = parsed == null || parsed < 0,
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.headlineSmall,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = parsed != null && parsed >= 0, onClick = { onSave(parsed!!); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, shape = RoundedCornerShape(16.dp)) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onSave(text.trim()); onDismiss() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Small uppercase-free section label above a card. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing()
    }
}
