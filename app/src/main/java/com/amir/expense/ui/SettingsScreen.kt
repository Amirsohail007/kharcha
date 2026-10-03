package com.amir.expense.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amir.expense.MainViewModel
import com.amir.expense.data.Budget
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.Category
import com.amir.expense.data.ImportSummary
import com.amir.expense.data.formatRupees
import com.amir.expense.data.parseRupees
import com.amir.expense.sync.DriveSync
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private sealed interface SettingsDialog {
    data object Budgets : SettingsDialog
    data class Edit(val category: Category) : SettingsDialog
    data class Add(val parent: Category?) : SettingsDialog
    data class UndoImport(val batch: ImportSummary) : SettingsDialog
    data object RestoreDrive : SettingsDialog
    data object OverwriteDrive : SettingsDialog
    data object DisconnectDrive : SettingsDialog
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val month by vm.month.collectAsState()
    val categories by vm.categories.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val rules by vm.rules.collectAsState()
    val imports by vm.imports.collectAsState()
    val drive by vm.driveState.collectAsState()
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    val parents = categories.filter { it.parentId == null }
    val overall = BudgetMath.overall(budgets, categories)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Settings") }

        item {
            AppCard(padding = 20.dp) {
                Text("Import statement", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "In PhonePe open History → Download Statement, then share the PDF to Expenses. Or pick the file here.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                ImportButton(vm)
            }
        }

        item {
            DriveCard(
                drive,
                onConnect = vm::connectDrive,
                onSync = vm::syncNow,
                onRestore = { dialog = SettingsDialog.RestoreDrive },
                onOverwrite = { dialog = SettingsDialog.OverwriteDrive },
                onAutoSync = vm::setAutoSync,
                onDisconnect = { dialog = SettingsDialog.DisconnectDrive },
            )
        }

        item {
            AppCard(padding = 8.dp) {
                SettingRow(
                    icon = { IconBox(Icons.Rounded.Savings) },
                    title = "Budgets",
                    subtitle = when {
                        budgets[Budget.TOTAL] != null -> "Monthly total set by you"
                        overall != null -> "Monthly total is the sum of category budgets"
                        else -> "Give Food, Travel and the rest a monthly budget"
                    },
                    value = overall?.let(::formatRupees),
                    onClick = { dialog = SettingsDialog.Budgets },
                )
            }
        }

        item {
            SectionLabel("Categories and budgets") {
                Text("From ${monthTitle(month)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(parents.size) { i ->
            val p = parents[i]
            AppCard(padding = 8.dp) {
                SettingRow(
                    icon = { CategoryBadge(lookOf(p.id, categories)) },
                    title = p.name, value = budgets[p.id]?.let(::formatRupees) ?: "No budget",
                    onClick = { dialog = SettingsDialog.Edit(p) },
                )
                categories.filter { it.parentId == p.id }.forEach { kid ->
                    SettingRow(
                        icon = { CategoryBadge(lookOf(kid.id, categories), size = 32.dp) },
                        title = kid.name, value = budgets[kid.id]?.let(::formatRupees), small = true,
                        modifier = Modifier.padding(start = 20.dp),
                        onClick = { dialog = SettingsDialog.Edit(kid) },
                    )
                }
                TextButton(onClick = { dialog = SettingsDialog.Add(p) }, modifier = Modifier.padding(start = 12.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add to ${p.name}")
                }
            }
        }
        item {
            TextButton(onClick = { dialog = SettingsDialog.Add(null) }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("New category")
            }
        }

        item { SectionLabel("Auto-file rules") }
        item {
            AppCard(padding = 8.dp) {
                if (rules.isEmpty()) {
                    SettingRow(
                        icon = { IconBox(Icons.Rounded.AutoAwesome) },
                        title = "No rules yet",
                        subtitle = "Tick “Always file here” when sorting a payment and that merchant gets filed automatically.",
                    )
                }
                rules.forEachIndexed { i, rule ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(lookOf(rule.categoryId, categories), size = 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rule.merchantKey.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(categoryLabel(rule.categoryId, categories) ?: "?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.deleteRule(rule.merchantKey) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove rule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (imports.isNotEmpty()) {
            item { SectionLabel("Imports") }
            item {
                AppCard(padding = 8.dp) {
                    imports.take(10).forEachIndexed { i, batch ->
                        if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBox(Icons.Rounded.Description)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(batch.fileName ?: "PhonePe statement", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${plural(batch.payments, "payment")} · ${formatDay(batch.firstTimestamp)} – ${formatDay(batch.lastTimestamp)}\n" +
                                        "Imported ${formatWhen(batch.importedAt)}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            TextButton(onClick = { dialog = SettingsDialog.UndoImport(batch) }) { Text("Undo") }
                        }
                    }
                }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Your data stays on this phone. Google Drive backup, when you turn it on, copies it to a private folder " +
                        "in your own Drive that only this app can read. If Android backup is on, it's also in your phone's backup.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        SettingsDialog.Budgets -> BudgetsSheet(vm, onDismiss = { dialog = null })
        is SettingsDialog.UndoImport -> ConfirmDialog(
            title = "Undo this import?",
            text = "Removes the ${plural(d.batch.payments, "payment")} it added, including any you've sorted or deleted since. " +
                "Importing the statement again adds them back.",
            confirm = "Undo import",
            onConfirm = { vm.undoImport(d.batch.id, d.batch.payments) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.RestoreDrive -> ConfirmDialog(
            title = "Restore from Google Drive?",
            text = "Everything on this phone (payments, categories, budgets and rules) is replaced with the backup in Drive.",
            confirm = "Restore",
            onConfirm = vm::restoreFromDrive,
            onDismiss = { dialog = null },
        )
        SettingsDialog.OverwriteDrive -> ConfirmDialog(
            title = "Replace the Drive backup?",
            text = "The backup in Drive is replaced with this phone's data. The backup that's there now can't be brought back.",
            confirm = "Replace",
            onConfirm = vm::overwriteDrive,
            onDismiss = { dialog = null },
        )
        SettingsDialog.DisconnectDrive -> ConfirmDialog(
            title = "Disconnect Google Drive?",
            text = "Daily backup stops and the app gives up access to your Drive. The backup stays in Drive; connect again to use it.",
            confirm = "Disconnect",
            onConfirm = vm::disconnectDrive,
            onDismiss = { dialog = null },
        )
        is SettingsDialog.Add -> TextDialog(
            title = if (d.parent == null) "New category" else "Add to ${d.parent.name}",
            initial = "",
            onSave = { vm.addCategory(it, d.parent?.id) },
            onDismiss = { dialog = null },
        )
        is SettingsDialog.Edit -> CategoryDialog(
            category = d.category,
            budget = budgets[d.category.id],
            hasChildren = categories.any { it.parentId == d.category.id },
            onSave = { name, paise ->
                if (name != d.category.name) vm.renameCategory(d.category, name)
                if (paise != (budgets[d.category.id] ?: 0L)) vm.setBudget(d.category.id, paise)
            },
            onDelete = { vm.deleteCategory(d.category.id) },
            onDismiss = { dialog = null },
        )
    }
}

/** Connect, sync now, daily sync, and the restore-or-replace choice when Drive holds a different backup. */
@Composable
private fun DriveCard(
    s: DriveSync.State,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onRestore: () -> Unit,
    onOverwrite: () -> Unit,
    onAutoSync: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
) {
    AppCard(padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBox(Icons.Rounded.CloudSync)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Google Drive backup", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (s.connected) s.account ?: "Connected" else "Off",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (s.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(12.dp))
        if (!s.connected) {
            Text(
                "Copies your payments, categories and budgets to a private folder in your Google Drive, now and every day. " +
                    "After reinstalling, connect the same Google account to get everything back.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onConnect, enabled = !s.busy, shape = RoundedCornerShape(14.dp)) { Text("Connect Google Drive") }
            return@AppCard
        }
        val conflict = s.conflict
        val problem = when {
            s.needsSignIn -> "Google needs you to allow access to Drive again."
            conflict != null -> "Drive has a different backup" +
                (conflict.device?.let { " from $it" } ?: "") +
                (conflict.savedAt?.let { ", saved ${formatWhen(it)}" } ?: "") +
                (conflict.payments?.let { ", with ${plural(it, "payment")}" } ?: "") +
                ". Restore it, or replace it with this phone's data?"
            else -> s.error
        }
        Text(
            problem ?: s.lastSyncAt?.let { "Last synced ${syncedWhen(it)}" } ?: "Not synced yet",
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                s.needsSignIn -> Button(onClick = onConnect, enabled = !s.busy, shape = RoundedCornerShape(14.dp)) { Text("Allow access") }
                conflict != null -> {
                    Button(onClick = onRestore, enabled = !s.busy, shape = RoundedCornerShape(14.dp)) { Text("Restore it") }
                    OutlinedButton(onClick = onOverwrite, enabled = !s.busy, shape = RoundedCornerShape(14.dp)) { Text("Replace it") }
                }
                else -> {
                    Button(onClick = onSync, enabled = !s.busy, shape = RoundedCornerShape(14.dp)) { Text("Sync now") }
                    OutlinedButton(onClick = onRestore, enabled = !s.busy, shape = RoundedCornerShape(14.dp)) { Text("Restore") }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sync every day", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = s.autoSync, onCheckedChange = onAutoSync)
        }
        TextButton(onClick = onDisconnect, enabled = !s.busy) {
            Text("Disconnect", color = MaterialTheme.colorScheme.error)
        }
    }
}

/** "today, 10:42 am" for today, else "3 Oct, 10:42 am". */
private fun syncedWhen(timestamp: Long): String {
    val at = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    return if (at.toLocalDate() == LocalDate.now()) "today, ${formatTime(timestamp)}" else formatWhen(timestamp)
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun SettingRow(
    icon: @Composable () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    small: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = if (small) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CategoryDialog(
    category: Category,
    budget: Long?,
    hasChildren: Boolean,
    onSave: (name: String, budgetPaise: Long) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category.name) }
    var amount by remember { mutableStateOf(paiseToInput(budget)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val paise = if (amount.isBlank()) 0L else parseRupees(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, shape = RoundedCornerShape(16.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("Monthly budget (blank = none)") }, prefix = { Text("₹") }, singleLine = true,
                    isError = paise == null || paise < 0, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (confirmDelete) {
                    Text(
                        "Delete “${category.name}”" + (if (hasChildren) " and its subcategories" else "") + "? Its payments go back to the inbox.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            if (confirmDelete) {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(enabled = name.isNotBlank() && paise != null && paise >= 0, onClick = { onSave(name.trim(), paise!!); onDismiss() }) { Text("Save") }
            }
        },
        dismissButton = {
            Row {
                if (!confirmDelete) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
