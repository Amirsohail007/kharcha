package com.amir.expense

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amir.expense.data.Category
import com.amir.expense.data.ImportResult
import com.amir.expense.data.ImportSummary
import com.amir.expense.data.Rule
import com.amir.expense.data.Txn
import com.amir.expense.importer.PdfText
import com.amir.expense.importer.PhonePeParser
import com.amir.expense.sync.DriveSync
import com.amir.expense.ui.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

sealed interface ImportState {
    data object Idle : ImportState
    data class AskPassword(val uri: Uri, val password: String, val error: String?) : ImportState
    data object Working : ImportState
    /** Shown as the review sheet: what was added, with undo, and payments you had deleted before. */
    data class Done(val result: ImportResult) : ImportState
    data class Failed(val message: String) : ImportState
}

/** A snackbar message; [undo] adds an Undo button. */
class Notice(val text: String, val undo: (() -> Unit)? = null)

/** One ViewModel for the whole app: it's five small screens over the same data. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repo
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val month = MutableStateFlow(YearMonth.now())

    val categories: StateFlow<List<Category>> = repo.categories.state(emptyList())
    val inbox: StateFlow<List<Txn>> = repo.inbox.state(emptyList())
    val rules: StateFlow<List<Rule>> = repo.rules.state(emptyList())
    val topCategoryIds: StateFlow<List<Long>> = repo.topCategoryIds.state(emptyList())
    val txns: StateFlow<List<Txn>> = month.flatMapLatest { repo.txns(it) }.state(emptyList())
    val prevTxns: StateFlow<List<Txn>> = month.flatMapLatest { repo.txns(it.minusMonths(1)) }.state(emptyList())
    val budgets: StateFlow<Map<Long, Long>> = month.flatMapLatest { repo.budgets(it) }.state(emptyMap())
    /** Deleted payments in the month on screen, for the Deleted filter in Spends. */
    val deletedTxns: StateFlow<List<Txn>> = month.flatMapLatest { repo.deletedTxns(it) }.state(emptyList())
    val imports: StateFlow<List<ImportSummary>> = repo.imports.state(emptyList())

    val importState = MutableStateFlow<ImportState>(ImportState.Idle)

    /** Snackbars, shown by AppRoot. */
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)

    private val drive: DriveSync = (app as App).drive
    val driveState: StateFlow<DriveSync.State> = drive.state
    /** Google's consent screen, launched by AppRoot. */
    val driveConsent = MutableSharedFlow<PendingIntent>(extraBufferCapacity = 1)

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    init {
        viewModelScope.launch { drive.messages.collect { notices.emit(Notice(it)) } }
    }

    fun shiftMonth(by: Long) { month.value = month.value.plusMonths(by) }

    private var thisMonth = YearMonth.now()

    /**
     * Called on resume: if the app sat open into a new month, follow it, unless you were browsing another month.
     * ponytail: resume only; staying on screen across midnight on the 31st waits for the next resume.
     */
    fun refreshMonth() {
        val now = YearMonth.now()
        if (now == thisMonth) return
        if (month.value == thisMonth) month.value = now
        thisMonth = now
    }

    // --- import ---
    fun startImport(uri: Uri) {
        importState.value = ImportState.AskPassword(uri, prefs.getString(KEY_PASSWORD, "").orEmpty(), null)
    }

    fun runImport(uri: Uri, password: String) = viewModelScope.launch {
        importState.value = ImportState.Working
        importState.value = try {
            val text = withContext(Dispatchers.IO) { PdfText.extract(getApplication(), uri, password) }
            val parsed = PhonePeParser.parse(text)
            if (parsed.isEmpty()) {
                ImportState.Failed("No PhonePe transactions found in this PDF.")
            } else {
                prefs.edit { putString(KEY_PASSWORD, password) }
                ImportState.Done(repo.import(parsed, withContext(Dispatchers.IO) { displayName(uri) }))
            }
        } catch (_: PdfText.WrongPassword) {
            ImportState.AskPassword(uri, password, "Wrong password. It's your PhonePe mobile number.")
        } catch (e: Exception) {
            ImportState.Failed(e.message ?: "Couldn't read this PDF.")
        }
    }

    fun dismissImport() { importState.value = ImportState.Idle }

    /** Every row an import added, live, for the review sheet. */
    fun importedBy(batchId: Long): Flow<List<Txn>> = repo.importedBy(batchId)

    fun undoImport(batchId: Long, payments: Int) = viewModelScope.launch {
        repo.undoImport(batchId)
        if ((importState.value as? ImportState.Done)?.result?.batchId == batchId) importState.value = ImportState.Idle
        notices.tryEmit(Notice("Import undone: ${plural(payments, "payment")} removed"))
    }

    /** The statement's file name, for the import history. */
    private fun displayName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    // --- transactions ---
    fun categorize(txn: Txn, categoryId: Long, remember: Boolean) =
        viewModelScope.launch { repo.categorize(txn, categoryId, remember) }

    fun setIgnored(txn: Txn, ignored: Boolean) = viewModelScope.launch { repo.setIgnored(txn, ignored) }
    fun save(txn: Txn) = viewModelScope.launch { repo.save(txn) }

    /** Marks payments "not an expense"; the snackbar's Undo writes the rows back as they were. */
    fun markNotExpense(txns: List<Txn>) = viewModelScope.launch {
        repo.update(txns.map { it.copy(ignored = true) })
        notices.tryEmit(Notice("${plural(txns.size, "payment")} marked not an expense") { viewModelScope.launch { repo.update(txns) } })
    }

    /** Soft delete, with an Undo snackbar unless [quiet] (the import review shows the change in place). */
    fun delete(txns: List<Txn>, quiet: Boolean = false): Job = viewModelScope.launch {
        repo.delete(txns)
        if (!quiet) notices.tryEmit(Notice("Deleted ${plural(txns.size, "payment")}") { restore(txns, quiet = true) })
    }

    fun restore(txns: List<Txn>, quiet: Boolean = false): Job = viewModelScope.launch {
        repo.restore(txns)
        if (!quiet) notices.tryEmit(Notice("Restored ${plural(txns.size, "payment")}") { delete(txns, quiet = true) })
    }

    // --- budgets, categories, rules ---
    /** Applies from the month on screen onward. */
    fun setBudget(categoryId: Long, paise: Long) =
        viewModelScope.launch { repo.setBudget(month.value, categoryId, paise) }

    fun addCategory(name: String, parentId: Long?) = viewModelScope.launch { repo.addCategory(name, parentId) }
    fun renameCategory(c: Category, name: String) = viewModelScope.launch { repo.renameCategory(c, name) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repo.deleteCategory(id) }
    fun deleteRule(key: String) = viewModelScope.launch { repo.deleteRule(key) }

    // --- Google Drive backup ---
    fun connectDrive() = viewModelScope.launch {
        try {
            drive.connect()?.let { driveConsent.tryEmit(it) }
        } catch (e: Exception) {
            notices.tryEmit(Notice(DriveSync.describe(e)))
        }
    }

    /** The consent screen closed: [data] carries Google's answer unless it was cancelled. */
    fun finishDriveConnect(ok: Boolean, data: Intent?) = viewModelScope.launch {
        if (!ok) return@launch
        try {
            drive.onConsent(data)
        } catch (e: Exception) {
            notices.tryEmit(Notice(DriveSync.describe(e)))
        }
    }

    fun syncNow() = viewModelScope.launch { drive.sync() }
    fun restoreFromDrive() = viewModelScope.launch { drive.restore() }
    fun overwriteDrive() = viewModelScope.launch { drive.overwriteDrive() }
    fun setAutoSync(on: Boolean) = drive.setAutoSync(on)
    fun disconnectDrive() = viewModelScope.launch { drive.disconnect() }

    private companion object {
        const val KEY_PASSWORD = "pdfPassword"
    }
}
