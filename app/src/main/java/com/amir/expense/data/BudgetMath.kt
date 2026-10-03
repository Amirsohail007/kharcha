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

    /** What the category budgets add up to: each top-level category's own budget, else the sum of its subcategories'. */
    fun allocated(budgets: Map<Long, Long>, categories: List<Category>): Long =
        categories.filter { it.parentId == null }.sumOf { parent ->
            budgets[parent.id] ?: categories.filter { it.parentId == parent.id }.sumOf { budgets[it.id] ?: 0L }
        }

    /** The month's overall budget: the total you set yourself, else the sum of category budgets; null when neither exists. */
    fun overall(budgets: Map<Long, Long>, categories: List<Category>): Long? =
        budgets[Budget.TOTAL] ?: allocated(budgets, categories).takeIf { it > 0 }

    /** [budgets] with [Budget.TOTAL] filled in from the category budgets when you haven't set your own. */
    fun withOverall(budgets: Map<Long, Long>, categories: List<Category>): Map<Long, Long> =
        overall(budgets, categories)?.let { budgets + (Budget.TOTAL to it) } ?: budgets

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
