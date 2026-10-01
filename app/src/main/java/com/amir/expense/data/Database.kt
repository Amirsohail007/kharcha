package com.amir.expense.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ExpenseDao {
    // --- categories ---
    @Query("SELECT * FROM category ORDER BY sortOrder, name")
    abstract fun categories(): Flow<List<Category>>

    @Query("SELECT * FROM category")
    abstract suspend fun categoriesOnce(): List<Category>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) + 1 FROM category")
    abstract suspend fun nextSortOrder(): Int

    @Insert
    abstract suspend fun insertCategory(c: Category): Long

    @Update
    abstract suspend fun updateCategory(c: Category)

    @Query("SELECT id FROM category WHERE parentId = :id")
    abstract suspend fun childIds(id: Long): List<Long>

    @Query("UPDATE txn SET categoryId = NULL WHERE categoryId IN (:ids)")
    abstract suspend fun uncategorize(ids: List<Long>)

    @Query("DELETE FROM rule WHERE categoryId IN (:ids)")
    abstract suspend fun deleteRulesFor(ids: List<Long>)

    @Query("DELETE FROM budget WHERE categoryId IN (:ids)")
    abstract suspend fun deleteBudgetsFor(ids: List<Long>)

    @Query("DELETE FROM category WHERE id IN (:ids)")
    abstract suspend fun deleteCategories(ids: List<Long>)

    /** Removes a category and its children. Their transactions go back to the inbox. */
    @Transaction
    open suspend fun deleteCategoryTree(id: Long) {
        val ids = childIds(id) + id
        uncategorize(ids)
        deleteRulesFor(ids)
        deleteBudgetsFor(ids)
        deleteCategories(ids)
    }

    // --- transactions ---
    @Query("SELECT * FROM txn WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC")
    abstract fun txnsBetween(start: Long, end: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txn WHERE timestamp >= :start AND timestamp < :end")
    abstract suspend fun txnsBetweenOnce(start: Long, end: Long): List<Txn>

    @Query("SELECT * FROM txn WHERE categoryId IS NULL AND ignored = 0 ORDER BY timestamp DESC")
    abstract fun inbox(): Flow<List<Txn>>

    /** Most-used categories, for one-tap filing in the inbox. */
    @Query("SELECT categoryId FROM txn WHERE categoryId IS NOT NULL AND ignored = 0 GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT 3")
    abstract fun topCategoryIds(): Flow<List<Long>>

    /** Returns -1 for rows skipped because their externalId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTxns(txns: List<Txn>): List<Long>

    @Insert
    abstract suspend fun insertTxn(txn: Txn): Long

    @Update
    abstract suspend fun updateTxn(txn: Txn)

    @Query("DELETE FROM txn WHERE id = :id")
    abstract suspend fun deleteTxn(id: Long)

    /** Files every still-uncategorized expense from this merchant. */
    @Query("UPDATE txn SET categoryId = :categoryId WHERE categoryId IS NULL AND ignored = 0 AND amountPaise > 0 AND merchantKey = :key")
    abstract suspend fun fileUncategorized(key: String, categoryId: Long)

    // --- rules ---
    @Query("SELECT * FROM rule ORDER BY merchantKey")
    abstract fun rules(): Flow<List<Rule>>

    @Query("SELECT * FROM rule")
    abstract suspend fun rulesOnce(): List<Rule>

    @Upsert
    abstract suspend fun upsertRule(rule: Rule)

    @Query("DELETE FROM rule WHERE merchantKey = :key")
    abstract suspend fun deleteRule(key: String)

    // --- budgets & alerts ---
    @Query("SELECT * FROM budget WHERE yearMonth <= :ym")
    abstract fun budgetsUpTo(ym: Int): Flow<List<Budget>>

    @Query("SELECT * FROM budget WHERE yearMonth <= :ym")
    abstract suspend fun budgetsUpToOnce(ym: Int): List<Budget>

    @Upsert
    abstract suspend fun upsertBudget(budget: Budget)

    /** Returns -1 if this alert already fired. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun markAlert(alert: AlertFired): Long
}

@Database(
    entities = [Category::class, Txn::class, Rule::class, Budget::class, AlertFired::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "expenses.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()

        private val SEED = linkedMapOf(
            "Food" to listOf("Delivery", "Restaurant", "Groceries"),
            "Travel" to listOf("Office", "Explore", "Fuel"),
            "Bills" to listOf("Rent", "Utilities", "Mobile & Internet", "Subscriptions"),
            "Shopping" to listOf("Clothes", "Electronics", "Household"),
            "Health" to listOf("Medicine", "Doctor"),
            "Fun" to listOf("Movies", "Outings"),
            "Personal" to listOf("Grooming", "Gifts"),
            "Other" to emptyList(),
        )

        private fun seed(db: SupportSQLiteDatabase) {
            var order = 0
            fun insert(name: String, parentId: Long?): Long =
                db.insert("category", SQLiteDatabase.CONFLICT_NONE, ContentValues().apply {
                    put("name", name)
                    put("parentId", parentId)
                    put("sortOrder", order++)
                })
            SEED.forEach { (parent, kids) ->
                val pid = insert(parent, null)
                kids.forEach { insert(it, pid) }
            }
        }
    }
}
