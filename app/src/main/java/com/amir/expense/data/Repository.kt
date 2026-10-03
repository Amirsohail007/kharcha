package com.amir.expense.data

import com.amir.expense.alerts.BudgetAlerts
import com.amir.expense.importer.ParsedTxn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

/**
 * What a statement import did. [batchId] is null when nothing was new.
 * [deletedBefore] are payments in the statement that you had deleted: they stay deleted unless you restore them.
 */
data class ImportResult(
    val batchId: Long?,
    val added: Int,
    val duplicates: Int,
    val autoFiled: Int,
    val toFile: Int,
    val deletedBefore: List<Txn>,
)

class Repository(private val dao: ExpenseDao, private val alerts: BudgetAlerts) {
    val categories: Flow<List<Category>> = dao.categories()
    val inbox: Flow<List<Txn>> = dao.inbox()
    val rules: Flow<List<Rule>> = dao.rules()
    val topCategoryIds: Flow<List<Long>> = dao.topCategoryIds()
    val imports: Flow<List<ImportSummary>> = dao.imports()

    fun txns(ym: YearMonth): Flow<List<Txn>> = ym.range().let { (s, e) -> dao.txnsBetween(s, e) }

    fun deletedTxns(ym: YearMonth): Flow<List<Txn>> = ym.range().let { (s, e) -> dao.deletedBetween(s, e) }

    /** Every row an import added, deleted ones included, for the review after importing. */
    fun importedBy(batchId: Long): Flow<List<Txn>> = dao.importedBy(batchId)

    fun budgets(ym: YearMonth): Flow<Map<Long, Long>> =
        dao.budgetsUpTo(ym.key).map { BudgetMath.effectiveBudgets(it, ym.key) }

    /**
     * Inserts new statement rows as one undoable batch, skips ones already imported (deleted ones too),
     * and auto-files known merchants.
     */
    suspend fun import(parsed: List<ParsedTxn>, fileName: String?): ImportResult {
        val rules = dao.rulesOnce().associate { it.merchantKey to it.categoryId }
        val zone = ZoneId.systemDefault()
        val rows = parsed.map { p ->
            val key = merchantKey(p.merchant)
            val expense = p.amountPaise > 0
            Txn(
                externalId = p.transactionId,
                source = Txn.SOURCE_PHONEPE,
                timestamp = p.dateTime.atZone(zone).toInstant().toEpochMilli(),
                amountPaise = p.amountPaise,
                merchant = p.merchant,
                merchantKey = key,
                categoryId = if (expense) rules[key] else null,
                // Money received is not spending until you file it as a refund.
                ignored = !expense,
            )
        }
        if (rows.isEmpty()) return ImportResult(null, 0, 0, 0, 0, emptyList())
        val batch = ImportBatch(
            importedAt = System.currentTimeMillis(),
            fileName = fileName,
            firstTimestamp = rows.minOf { it.timestamp },
            lastTimestamp = rows.maxOf { it.timestamp },
        )
        val (batchId, ids) = dao.insertImport(batch, rows)
        val added = rows.filterIndexed { i, _ -> ids[i] != -1L }
        val skipped = rows.filterIndexed { i, _ -> ids[i] == -1L }.mapNotNull { it.externalId }.distinct()
        val deletedBefore = skipped.chunked(500).flatMap { dao.deletedWithExternalIds(it) }
        added.map { yearMonthOf(it.timestamp) }.distinct().forEach { alerts.check(it) }
        return ImportResult(
            batchId = batchId,
            added = added.size,
            duplicates = rows.size - added.size - deletedBefore.size,
            autoFiled = added.count { it.categoryId != null },
            toFile = added.count { it.categoryId == null && !it.ignored },
            deletedBefore = deletedBefore.sortedByDescending { it.timestamp },
        )
    }

    /** Removes everything an import added, including payments you sorted or deleted since. */
    suspend fun undoImport(batchId: Long) {
        dao.undoImport(batchId)
        // Undoing can take out a refund, which raises spending.
        alerts.check(YearMonth.now())
    }

    /** Files [txn]; with [remember], also files this merchant's other inbox items and future imports. */
    suspend fun categorize(txn: Txn, categoryId: Long, remember: Boolean) {
        dao.updateTxn(txn.copy(categoryId = categoryId, ignored = false))
        if (remember && txn.amountPaise > 0) {
            dao.upsertRule(Rule(txn.merchantKey, categoryId))
            dao.fileUncategorized(txn.merchantKey, categoryId)
        }
        alerts.check(yearMonthOf(txn.timestamp))
    }

    suspend fun setIgnored(txn: Txn, ignored: Boolean) {
        dao.updateTxn(txn.copy(ignored = ignored))
        alerts.check(yearMonthOf(txn.timestamp))
    }

    /** Writes back several edited rows at once (bulk "not an expense", and undoing it). */
    suspend fun update(txns: List<Txn>) {
        dao.updateTxns(txns)
        txns.map { yearMonthOf(it.timestamp) }.distinct().forEach { alerts.check(it) }
    }

    /** Manual add (id == 0) or edit. */
    suspend fun save(txn: Txn) {
        if (txn.id == 0L) dao.insertTxn(txn) else dao.updateTxn(txn)
        alerts.check(yearMonthOf(txn.timestamp))
    }

    /** Deleted payments leave every list and total; they can be restored. */
    suspend fun delete(txns: List<Txn>) {
        dao.setDeleted(txns.map { it.id }, System.currentTimeMillis())
        // Deleting a refund raises spending.
        txns.map { yearMonthOf(it.timestamp) }.distinct().forEach { alerts.check(it) }
    }

    suspend fun restore(txns: List<Txn>) {
        dao.setDeleted(txns.map { it.id }, null)
        txns.map { yearMonthOf(it.timestamp) }.distinct().forEach { alerts.check(it) }
    }

    suspend fun setBudget(ym: YearMonth, categoryId: Long, paise: Long) {
        dao.upsertBudget(Budget(ym.key, categoryId, paise))
        alerts.check(ym)
    }

    suspend fun addCategory(name: String, parentId: Long?) {
        dao.insertCategory(Category(name = name, parentId = parentId, sortOrder = dao.nextSortOrder()))
    }

    suspend fun renameCategory(c: Category, name: String) = dao.updateCategory(c.copy(name = name))

    suspend fun deleteCategory(id: Long) = dao.deleteCategoryTree(id)

    suspend fun deleteRule(key: String) = dao.deleteRule(key)

    // --- backup ---
    suspend fun snapshot(): Snapshot = dao.snapshot()

    /** Replaces all data with a backup. Alerts already sent stay sent: the backup carries them. */
    suspend fun replaceAll(snapshot: Snapshot) = dao.replaceAll(snapshot)
}
