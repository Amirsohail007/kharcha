package com.amir.expense

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amir.expense.data.Category
import com.amir.expense.data.ImportResult
import com.amir.expense.data.Rule
import com.amir.expense.data.Txn
import com.amir.expense.importer.PdfText
import com.amir.expense.importer.PhonePeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    data class Done(val result: ImportResult) : ImportState
    data class Failed(val message: String) : ImportState
}

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

    val importState = MutableStateFlow<ImportState>(ImportState.Idle)

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.Eagerly, initial)

    fun shiftMonth(by: Long) { month.value = month.value.plusMonths(by) }

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
                ImportState.Done(repo.import(parsed))
            }
        } catch (_: PdfText.WrongPassword) {
            ImportState.AskPassword(uri, password, "Wrong password. It's your PhonePe mobile number.")
        } catch (e: Exception) {
            ImportState.Failed(e.message ?: "Couldn't read this PDF.")
        }
    }

    fun dismissImport() { importState.value = ImportState.Idle }

    // --- transactions ---
    fun categorize(txn: Txn, categoryId: Long, remember: Boolean) =
        viewModelScope.launch { repo.categorize(txn, categoryId, remember) }

    fun setIgnored(txn: Txn, ignored: Boolean) = viewModelScope.launch { repo.setIgnored(txn, ignored) }
    fun save(txn: Txn) = viewModelScope.launch { repo.save(txn) }
    fun delete(txn: Txn) = viewModelScope.launch { repo.delete(txn) }

    // --- budgets, categories, rules ---
    /** Applies from the month on screen onward. */
    fun setBudget(categoryId: Long, paise: Long) =
        viewModelScope.launch { repo.setBudget(month.value, categoryId, paise) }

    fun addCategory(name: String, parentId: Long?) = viewModelScope.launch { repo.addCategory(name, parentId) }
    fun renameCategory(c: Category, name: String) = viewModelScope.launch { repo.renameCategory(c, name) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repo.deleteCategory(id) }
    fun deleteRule(key: String) = viewModelScope.launch { repo.deleteRule(key) }

    private companion object {
        const val KEY_PASSWORD = "pdfPassword"
    }
}
