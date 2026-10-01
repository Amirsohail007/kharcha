package com.amir.expense.data

/** Pure budget arithmetic, shared by the UI and alerts. All amounts in paise. */
object BudgetMath {
    val ALERT_LEVELS = listOf(80, 100)

    /** categoryId -> budget for month [ym]: latest row at or before [ym]; zero rows mean "removed". */
    fun effectiveBudgets(rows: List<Budget>, ym: Int): Map<Long, Long> =
        rows.filter { it.yearMonth <= ym }
            .groupBy { it.categoryId }
            .mapValues { (_, v) -> v.maxBy { it.yearMonth }.amountPaise }
            .filterValues { it > 0 }

    /** Money out this month: every non-ignored transaction, filed or not yet filed. */
    fun totalSpend(txns: List<Txn>): Long = txns.filterNot { it.ignored }.sumOf { it.amountPaise }

    /** categoryId -> spend; a parent's figure includes its subcategories. */
    fun spendByCategory(txns: List<Txn>, categories: List<Category>): Map<Long, Long> {
        val parentOf = categories.associate { it.id to it.parentId }
        val out = HashMap<Long, Long>()
        for (t in txns) {
            if (t.ignored) continue
            val c = t.categoryId ?: continue
            out[c] = (out[c] ?: 0L) + t.amountPaise
            parentOf[c]?.let { p -> out[p] = (out[p] ?: 0L) + t.amountPaise }
        }
        return out
    }

    /** Alert levels (percent) that [spent] has reached against [budget]. */
    fun crossedLevels(spent: Long, budget: Long): List<Int> =
        if (budget <= 0) emptyList() else ALERT_LEVELS.filter { spent * 100 >= budget * it }
}
