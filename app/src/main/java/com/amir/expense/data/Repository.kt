package com.amir.expense.data

import com.amir.expense.alerts.BudgetAlerts
import com.amir.expense.importer.ParsedTxn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

data class ImportResult(val added: Int, val duplicates: Int, val autoFiled: Int, val toFile: Int)

class Repository(private val dao: ExpenseDao, private val alerts: BudgetAlerts) {
    val categories: Flow<List<Category>> = dao.categories()
    val inbox: Flow<List<Txn>> = dao.inbox()
    val rules: Flow<List<Rule>> = dao.rules()
    val topCategoryIds: Flow<List<Long>> = dao.topCategoryIds()

    fun txns(ym: YearMonth): Flow<List<Txn>> = ym.range().let { (s, e) -> dao.txnsBetween(s, e) }

    fun budgets(ym: YearMonth): Flow<Map<Long, Long>> =
        dao.budgetsUpTo(ym.key).map { BudgetMath.effectiveBudgets(it, ym.key) }

    /** Inserts new statement rows, skips ones already imported, auto-files known merchants. */
    suspend fun import(parsed: List<ParsedTxn>): ImportResult {
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
        val ids = dao.insertTxns(rows)
        val added = rows.filterIndexed { i, _ -> ids[i] != -1L }
        added.map { yearMonthOf(it.timestamp) }.distinct().forEach { alerts.check(it) }
        return ImportResult(
            added = added.size,
            duplicates = rows.size - added.size,
            autoFiled = added.count { it.categoryId != null },
            toFile = added.count { it.categoryId == null && !it.ignored },
        )
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

    /** Manual add (id == 0) or edit. */
    suspend fun save(txn: Txn) {
        if (txn.id == 0L) dao.insertTxn(txn) else dao.updateTxn(txn)
        alerts.check(yearMonthOf(txn.timestamp))
    }

    suspend fun delete(txn: Txn) = dao.deleteTxn(txn.id)

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
}
