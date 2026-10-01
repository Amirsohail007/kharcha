package com.amir.expense.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.amir.expense.ImportState
import com.amir.expense.MainViewModel

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Rounded.Home),
    Inbox("Inbox", Icons.Rounded.MoveToInbox),
    Spends("Spends", Icons.AutoMirrored.Rounded.ReceiptLong),
    Insights("Insights", Icons.Rounded.BarChart),
    Settings("Settings", Icons.Rounded.Settings),
}

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val inbox by vm.inbox.collectAsState()
    BackHandler(enabled = tab != Tab.Home) { tab = Tab.Home }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            if (t == Tab.Inbox && inbox.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${inbox.size}") } }) { Icon(t.icon, contentDescription = t.label) }
                            } else {
                                Icon(t.icon, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.Home -> HomeScreen(vm, openInbox = { tab = Tab.Inbox })
                Tab.Inbox -> InboxScreen(vm)
                Tab.Spends -> TransactionsScreen(vm)
                Tab.Insights -> InsightsScreen(vm)
                Tab.Settings -> SettingsScreen(vm)
            }
        }
    }
    ImportDialogs(vm, onDone = { tab = Tab.Inbox })
}

/** Opens the system file picker for a PhonePe statement PDF. */
@Composable
fun ImportButton(vm: MainViewModel, modifier: Modifier = Modifier, label: String = "Import PhonePe statement", tonal: Boolean = false) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::startImport) }
    val content: @Composable RowScope.() -> Unit = {
        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
    val onClick = { pick.launch(arrayOf("application/pdf")) }
    val shape = RoundedCornerShape(16.dp)
    if (tonal) {
        FilledTonalButton(onClick, modifier, shape = shape, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, content = content)
    } else {
        Button(onClick, modifier, shape = shape, contentPadding = ButtonDefaults.ButtonWithIconContentPadding, content = content)
    }
}

@Composable
private fun ImportDialogs(vm: MainViewModel, onDone: () -> Unit) {
    when (val s = vm.importState.collectAsState().value) {
        ImportState.Idle -> Unit
        is ImportState.AskPassword -> {
            var password by remember(s) { mutableStateOf(s.password) }
            AlertDialog(
                onDismissRequest = vm::dismissImport,
                icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                title = { Text("Statement password") },
                text = {
                    Column {
                        Text("PhonePe locks statements with your registered mobile number.")
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Mobile number") },
                            singleLine = true,
                            isError = s.error != null,
                            supportingText = s.error?.let { { Text(it) } },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                },
                confirmButton = { Button(onClick = { vm.runImport(s.uri, password) }, shape = RoundedCornerShape(14.dp)) { Text("Import") } },
                dismissButton = { TextButton(onClick = vm::dismissImport) { Text("Cancel") } },
            )
        }
        ImportState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Reading statement…")
                }
            },
        )
        is ImportState.Done -> {
            val r = s.result
            AlertDialog(
                onDismissRequest = vm::dismissImport,
                title = { Text(if (r.added > 0) "${r.added} payments imported" else "Nothing new") },
                text = {
                    Text(
                        listOfNotNull(
                            if (r.autoFiled > 0) "${r.autoFiled} filed automatically" else null,
                            if (r.toFile > 0) "${r.toFile} waiting in your inbox" else null,
                            if (r.duplicates > 0) "${r.duplicates} already imported, skipped" else null,
                        ).joinToString("\n").ifEmpty { "All set." },
                    )
                },
                confirmButton = {
                    Button(onClick = { vm.dismissImport(); if (r.toFile > 0) onDone() }, shape = RoundedCornerShape(14.dp)) {
                        Text(if (r.toFile > 0) "Sort them" else "Done")
                    }
                },
            )
        }
        is ImportState.Failed -> AlertDialog(
            onDismissRequest = vm::dismissImport,
            title = { Text("Couldn't import") },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = vm::dismissImport) { Text("OK") } },
        )
    }
}
