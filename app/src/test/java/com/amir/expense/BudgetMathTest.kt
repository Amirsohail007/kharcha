package com.amir.expense

import com.amir.expense.data.Budget
import com.amir.expense.data.BudgetMath
import com.amir.expense.data.Category
import com.amir.expense.data.Txn
import com.amir.expense.data.merchantKey
import com.amir.expense.data.parseRupees
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetMathTest {
    private val food = Category(id = 1, name = "Food")
    private val delivery = Category(id = 2, name = "Delivery", parentId = 1)
    private val restaurant = Category(id = 3, name = "Restaurant", parentId = 1)
    private val cats = listOf(food, delivery, restaurant)

    private fun txn(paise: Long, cat: Long?, ignored: Boolean = false) =
        Txn(timestamp = 0, amountPaise = paise, merchant = "x", categoryId = cat, ignored = ignored)

    @Test fun budgetCarriesForwardUntilChanged() {
        val rows = listOf(
            Budget(202608, Budget.TOTAL, 20_000_00),
            Budget(202610, Budget.TOTAL, 25_000_00),
            Budget(202609, 2, 4_000_00),
            Budget(202610, 2, 0), // Delivery budget removed in October
        )
        assertEquals(mapOf(Budget.TOTAL to 20_000_00L, 2L to 4_000_00L), BudgetMath.effectiveBudgets(rows, 202609))
        assertEquals(mapOf(Budget.TOTAL to 25_000_00L), BudgetMath.effectiveBudgets(rows, 202612))
        assertEquals(emptyMap<Long, Long>(), BudgetMath.effectiveBudgets(rows, 202607))
    }

    @Test fun spendRollsUpToParentAndSkipsIgnored() {
        val txns = listOf(
            txn(300_00, 2), txn(200_00, 3),
            txn(-50_00, 2), // refund
            txn(999_00, 2, ignored = true),
            txn(100_00, null), // not filed yet
        )
        val spend = BudgetMath.spendByCategory(txns, cats)
        assertEquals(250_00L, spend[2])
        assertEquals(200_00L, spend[3])
        assertEquals(450_00L, spend[1])
        // Unfiled money still counts against the monthly total.
        assertEquals(550_00L, BudgetMath.totalSpend(txns))
    }

    @Test fun alertLevels() {
        assertEquals(emptyList<Int>(), BudgetMath.crossedLevels(79_99, 100_00))
        assertEquals(listOf(80), BudgetMath.crossedLevels(80_00, 100_00))
        assertEquals(listOf(80, 100), BudgetMath.crossedLevels(150_00, 100_00))
        assertEquals(emptyList<Int>(), BudgetMath.crossedLevels(150_00, 0))
    }

    @Test fun moneyHelpers() {
        assertEquals(125050L, parseRupees("1,250.50"))
        assertEquals(null, parseRupees("abc"))
        assertEquals("swiggy limited", merchantKey("SWIGGY  Limited."))
    }
}
