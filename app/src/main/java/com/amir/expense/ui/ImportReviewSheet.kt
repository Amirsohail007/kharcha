package com.amir.expense.ui

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.ImportResult
import com.amir.expense.data.Txn
import kotlinx.coroutines.flow.flowOf

/**
 * Shown after every import. Undo the whole import, pick payments you don't want and delete them,
 * and restore any that were in the statement but deleted before (they are not added back on their own).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportReviewSheet(vm: MainViewModel, result: ImportResult, onSort: () -> Unit) {
    val categories by vm.categories.collectAsState()
    val batchId = result.batchId
    val added by remember(batchId) { if (batchId == null) flowOf(emptyList<Txn>()) else vm.importedBy(batchId) }.collectAsState(emptyList())
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var restored by remember { mutableStateOf(emptySet<Long>()) }
    var confirmUndo by remember { mutableStateOf(false) }

    val live = added.filter { it.deletedAt == null }
    val selected = live.filter { it.id in selectedIds }
    val toSort = live.count { it.categoryId == null && !it.ignored }
    val waiting = result.deletedBefore.filter { it.id !in restored }
    fun toggle(txn: Txn) { selectedIds = if (txn.id in selectedIds) selectedIds - txn.id else selectedIds + txn.id }

    ModalBottomSheet(onDismissRequest = vm::dismissImport, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (result.added > 0) "${plural(result.added, "payment")} imported" else "Nothing new", style = MaterialTheme.typography.titleLarge)
            Text(
                listOfNotNull(
                    if (result.autoFiled > 0) "${result.autoFiled} filed automatically" else null,
                    if (result.toFile > 0) "${result.toFile} waiting in your inbox" else null,
                    if (result.duplicates > 0) "${result.duplicates} already in the app, skipped" else null,
                    if (result.deletedBefore.isNotEmpty()) "${plural(result.deletedBefore.size, "payment")} you deleted before, left out" else null,
                ).joinToString("\n").ifEmpty { "All set." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (confirmUndo) {
                Text(
                    "Undo removes all ${plural(result.added, "payment")} this import added. Tap Undo import again to confirm.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
            Row(Modifier.padding(top = 8.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (batchId != null) {
                    OutlinedButton(
                        onClick = { if (confirmUndo) vm.undoImport(batchId, result.added) else confirmUndo = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Undo import")
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.dismissImport(); if (toSort > 0) onSort() }, shape = RoundedCornerShape(14.dp)) {
                    Text(if (toSort > 0) "Sort $toSort in inbox" else "Done")
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            if (added.isNotEmpty()) {
                item {
                    ReviewSection(
                        "Added from this statement",
                        "Tap payments you don't want, then delete them.",
                    )
                }
                items(added, key = { it.id }) { txn ->
                    if (txn.deletedAt != null) {
                        TxnRow(txn, categories, trailing = { TextButton(onClick = { vm.restore(listOf(txn), quiet = true) }) { Text("Restore") } })
                    } else {
                        TxnRow(
                            txn, categories,
                            selected = txn.id in selectedIds,
                            onLongClick = { toggle(txn) },
                            onClick = { toggle(txn) },
                        )
                    }
                }
            }
            if (result.deletedBefore.isNotEmpty()) {
                item {
                    ReviewSection(
                        "Deleted earlier",
                        "These are in the statement, but you had deleted them, so they stay deleted. Restore any you want back.",
                    ) {
                        if (waiting.size > 1) {
                            TextButton(onClick = { vm.restore(waiting, quiet = true); restored = restored + waiting.map { it.id } }) {
                                Text("Restore all")
                            }
                        }
                    }
                }
                items(result.deletedBefore, key = { "deleted-${it.id}" }) { txn ->
                    if (txn.id in restored) {
                        TxnRow(txn.copy(deletedAt = null), categories, trailing = {
                            Text(
                                "Restored", style = MaterialTheme.typography.labelLarge, color = successColor,
                                modifier = Modifier.padding(horizontal = 12.dp),
                            )
                        })
                    } else {
                        TxnRow(txn, categories, trailing = {
                            TextButton(onClick = { vm.restore(listOf(txn), quiet = true); restored = restored + txn.id }) { Text("Restore") }
                        })
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            SelectionBar(
                count = selected.size,
                onClose = { selectedIds = emptySet() },
                onSelectAll = if (selected.size < live.size) { { selectedIds = live.map { it.id }.toSet() } } else null,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            ) {
                SelectionAction(Icons.Rounded.Delete, "Delete ${selected.size}", danger = true) {
                    vm.delete(selected, quiet = true)
                    selectedIds = emptySet()
                }
            }
        }
    }
}

@Composable
private fun ReviewSection(title: String, hint: String, action: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            action()
        }
        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
    }
}
