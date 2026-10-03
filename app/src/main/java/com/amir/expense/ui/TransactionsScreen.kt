package com.amir.expense.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.Category
import com.amir.expense.data.Txn
import com.amir.expense.data.formatRupees
import com.amir.expense.data.merchantKey
import com.amir.expense.data.parseRupees
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/** Filter value for the "Deleted" chip; real filters are category ids (>= 1). */
private const val DELETED = -1L

@Composable
fun TransactionsScreen(vm: MainViewModel) {
    val txns by vm.txns.collectAsState()
    val deletedTxns by vm.deletedTxns.collectAsState()
    val categories by vm.categories.collectAsState()
    var filter by remember { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

    val parentOf = categories.associate { it.id to it.parentId }
    val parents = categories.filter { it.parentId == null }
    val showingDeleted = filter == DELETED
    val shown = when (filter) {
        null -> txns
        DELETED -> deletedTxns
        else -> txns.filter { t -> t.categoryId == filter || t.categoryId?.let { parentOf[it] } == filter }
    }
    // Rows can leave the list while selected (filed, deleted, month switched): only count what's on screen.
    val selected = shown.filter { it.id in selectedIds }
    val selecting = selected.isNotEmpty()
    val zone = ZoneId.systemDefault()
    val byDay = shown.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }

    fun toggle(txn: Txn) { selectedIds = if (txn.id in selectedIds) selectedIds - txn.id else selectedIds + txn.id }
    BackHandler(enabled = selecting) { selectedIds = emptySet() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (selecting) 160.dp else 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ScreenHeader("Spends", vm) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filter == null, onClick = { filter = null; selectedIds = emptySet() }, label = { Text("All") },
                            shape = RoundedCornerShape(12.dp), colors = chipColors(),
                        )
                    }
                    items(parents, key = { it.id }) { p ->
                        val look = lookOf(p.id, categories)
                        FilterChip(
                            selected = filter == p.id, onClick = { filter = if (filter == p.id) null else p.id; selectedIds = emptySet() },
                            label = { Text(p.name) },
                            leadingIcon = { look.icon?.let { Icon(it, contentDescription = null, tint = look.color, modifier = Modifier.size(18.dp)) } },
                            shape = RoundedCornerShape(12.dp), colors = chipColors(),
                        )
                    }
                    // Only there when this month has deleted payments (or you're looking at them).
                    if (deletedTxns.isNotEmpty() || showingDeleted) {
                        item(key = "deleted") {
                            FilterChip(
                                selected = showingDeleted, onClick = { filter = if (showingDeleted) null else DELETED; selectedIds = emptySet() },
                                label = { Text("Deleted (${deletedTxns.size})") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                shape = RoundedCornerShape(12.dp), colors = chipColors(),
                            )
                        }
                    }
                }
            }
            item {
                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (showingDeleted) {
                                Text("Deleted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                                Text(
                                    "Not counted anywhere. Tap payments to pick them, then restore.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                Text("Spent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                                Text(formatRupees(BudgetMath.totalSpend(shown)), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(plural(shown.size, "payment"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            if (shown.isEmpty()) {
                item {
                    AppCard {
                        if (showingDeleted) {
                            EmptyState(Icons.Rounded.DeleteOutline, "Nothing deleted", "Payments you delete this month show up here, so you can bring them back.")
                        } else {
                            EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "No payments yet", "Import a PhonePe statement, or add a cash expense with the button below.")
                        }
                    }
                }
            }
            byDay.forEach { (day, list) ->
                item(key = day.toString()) {
                    Column {
                        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                            Text(dayLabel(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            BudgetMath.totalSpend(list).takeIf { it != 0L && !showingDeleted }?.let {
                                Text(formatRupees(it), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        AppCard(padding = 8.dp) {
                            list.forEach { txn ->
                                TxnRow(
                                    txn, categories, showDate = false,
                                    selected = txn.id in selectedIds,
                                    onLongClick = { toggle(txn) },
                                    // Deleted payments have nothing to edit: a tap picks them for restoring.
                                    onClick = { if (selecting || showingDeleted) toggle(txn) else editing = txn },
                                )
                            }
                        }
                    }
                }
            }
        }
        when {
            selecting -> SelectionBar(
                count = selected.size,
                onClose = { selectedIds = emptySet() },
                onSelectAll = if (selected.size < shown.size) { { selectedIds = shown.map { it.id }.toSet() } } else null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            ) {
                if (showingDeleted) {
                    SelectionAction(Icons.Rounded.RestoreFromTrash, "Restore") { vm.restore(selected); selectedIds = emptySet() }
                } else {
                    SelectionAction(Icons.Rounded.Block, "Not expense") { vm.markNotExpense(selected); selectedIds = emptySet() }
                    SelectionAction(Icons.Rounded.Delete, "Delete", danger = true) { vm.delete(selected); selectedIds = emptySet() }
                }
            }
            !showingDeleted -> ExtendedFloatingActionButton(
                onClick = { editing = blankTxn(vm.month.value) },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add expense") },
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    editing?.let { TxnEditor(vm, it, categories, onClose = { editing = null }) }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
)

/** Digits only, up to ₹9,99,999.99: keeps the big centered number on one line. */
private val AMOUNT_INPUT = Regex("""\d{0,6}(\.\d{0,2})?""")

private val RupeePrefix = VisualTransformation { t ->
    if (t.text.isEmpty()) {
        TransformedText(t, OffsetMapping.Identity)
    } else {
        TransformedText(
            AnnotatedString("₹") + t,
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + 1
                override fun transformedToOriginal(offset: Int) = (offset - 1).coerceIn(0, t.text.length)
            },
        )
    }
}

private fun dayLabel(day: LocalDate): String {
    val today = LocalDate.now()
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
    }
}

/** New manual expense dated now, or the 1st of the month being viewed. */
private fun blankTxn(month: YearMonth): Txn {
    val now = LocalDateTime.now()
    val at = if (YearMonth.from(now) == month) now else month.atDay(1).atTime(12, 0)
    return Txn(timestamp = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), amountPaise = 0, merchant = "")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxnEditor(vm: MainViewModel, txn: Txn, categories: List<Category>, onClose: () -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    // The field edits the size of the amount; money received keeps its minus sign on save.
    val credit = txn.amountPaise < 0
    var amount by remember { mutableStateOf(paiseToInput(abs(txn.amountPaise))) }
    var merchant by remember { mutableStateOf(txn.merchant) }
    var note by remember { mutableStateOf(txn.note.orEmpty()) }
    var timestamp by remember { mutableLongStateOf(txn.timestamp) }
    var categoryId by remember { mutableStateOf(txn.categoryId) }
    var counted by remember { mutableStateOf(!txn.ignored) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val paise = parseRupees(amount)
    val valid = paise != null && paise != 0L && merchant.isNotBlank()
    val fieldShape = RoundedCornerShape(16.dp)

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(when { txn.id == 0L -> "Add expense"; credit -> "Money received"; else -> "Edit payment" }, style = MaterialTheme.typography.titleLarge)
            // Big centered "₹250": the ₹ is drawn by a visual transformation so it stays glued to the digits.
            BasicTextField(
                value = amount, onValueChange = { if (it.matches(AMOUNT_INPUT)) amount = it },
                textStyle = MaterialTheme.typography.displayMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                singleLine = true,
                visualTransformation = RupeePrefix,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.Center) {
                        if (amount.isEmpty()) {
                            Text("₹0", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outline, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        inner()
                    }
                },
            )
            OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("Paid to") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, singleLine = true, shape = fieldShape, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    onClick = {
                        val at = Instant.ofEpochMilli(timestamp).atZone(zone)
                        DatePickerDialog(context, { _, y, m, d ->
                            timestamp = LocalDate.of(y, m + 1, d).atTime(at.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        }, at.year, at.monthValue - 1, at.dayOfMonth).show()
                    },
                    shape = fieldShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM yyyy")), style = MaterialTheme.typography.labelLarge)
                    }
                }
                Surface(onClick = { picking = true }, shape = fieldShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.weight(1f)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(lookOf(categoryId, categories), size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(categories.firstOrNull { it.id == categoryId }?.name ?: "Category", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (credit) "Count as refund" else "Count as expense", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (txn.source == Txn.SOURCE_PHONEPE) "From PhonePe statement" else "Turn off for transfers or money lent",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = counted, onCheckedChange = { counted = it })
            }
            if (confirmDelete) {
                Text(
                    if (txn.source == Txn.SOURCE_PHONEPE) "Delete? It moves to Deleted in Spends. Importing this statement again won't add it back; it shows as deleted with an option to restore."
                    else "Delete this expense? It moves to Deleted in Spends, where you can restore it.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (txn.id != 0L) {
                    TextButton(onClick = { if (confirmDelete) { vm.delete(listOf(txn)); onClose() } else confirmDelete = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (confirmDelete) "Confirm delete" else "Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = valid, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(48.dp),
                    onClick = {
                        val name = merchant.trim()
                        vm.save(
                            txn.copy(
                                amountPaise = if (credit) -paise!! else paise!!, merchant = name, merchantKey = merchantKey(name),
                                note = note.trim().ifEmpty { null }, timestamp = timestamp,
                                categoryId = categoryId, ignored = !counted,
                            ),
                        )
                        onClose()
                    },
                ) { Text("Save", modifier = Modifier.padding(horizontal = 16.dp)) }
            }
        }
    }

    if (picking) {
        CategoryPickerSheet(
            categories = categories,
            selectedId = categoryId,
            onPick = { categoryId = it; counted = true; picking = false },
            onDismiss = { picking = false },
        )
    }
}
