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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/** Ids per `IN (...)` query: SQLite before 3.32 (Android 10 and older) allows 999 parameters. */
private const val CHUNK = 500

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

    // --- transactions (every list and total skips deleted rows) ---
    @Query("SELECT * FROM txn WHERE timestamp >= :start AND timestamp < :end AND deletedAt IS NULL ORDER BY timestamp DESC")
    abstract fun txnsBetween(start: Long, end: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txn WHERE timestamp >= :start AND timestamp < :end AND deletedAt IS NULL")
    abstract suspend fun txnsBetweenOnce(start: Long, end: Long): List<Txn>

    @Query("SELECT * FROM txn WHERE timestamp >= :start AND timestamp < :end AND deletedAt IS NOT NULL ORDER BY timestamp DESC")
    abstract fun deletedBetween(start: Long, end: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txn WHERE categoryId IS NULL AND ignored = 0 AND deletedAt IS NULL ORDER BY timestamp DESC")
    abstract fun inbox(): Flow<List<Txn>>

    /** Most-used categories, for one-tap filing in the inbox. */
    @Query("SELECT categoryId FROM txn WHERE categoryId IS NOT NULL AND ignored = 0 AND deletedAt IS NULL GROUP BY categoryId ORDER BY COUNT(*) DESC LIMIT 3")
    abstract fun topCategoryIds(): Flow<List<Long>>

    /** Returns -1 for rows skipped because their externalId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertTxns(txns: List<Txn>): List<Long>

    @Insert
    abstract suspend fun insertTxn(txn: Txn): Long

    @Update
    abstract suspend fun updateTxn(txn: Txn)

    @Update
    abstract suspend fun updateTxns(txns: List<Txn>)

    /** [at] null brings deleted rows back. Callers keep [ids] under SQLite's 999-parameter limit. */
    @Query("UPDATE txn SET deletedAt = :at WHERE id IN (:ids)")
    abstract suspend fun setDeletedAt(ids: List<Long>, at: Long?)

    @Transaction
    open suspend fun setDeleted(ids: List<Long>, at: Long?) {
        ids.chunked(CHUNK).forEach { setDeletedAt(it, at) }
    }

    /** Files every still-uncategorized expense from this merchant. */
    @Query("UPDATE txn SET categoryId = :categoryId WHERE categoryId IS NULL AND ignored = 0 AND amountPaise > 0 AND merchantKey = :key AND deletedAt IS NULL")
    abstract suspend fun fileUncategorized(key: String, categoryId: Long)

    // --- imports ---
    @Insert
    abstract suspend fun insertImportBatch(batch: ImportBatch): Long

    @Query("DELETE FROM import_batch WHERE id = :id")
    abstract suspend fun deleteImportBatch(id: Long)

    /**
     * Adds a statement's rows under a new [ImportBatch]; rows already in the app are skipped.
     * Returns the batch id (null when nothing was new) and, per row, its new id or -1.
     */
    @Transaction
    open suspend fun insertImport(batch: ImportBatch, rows: List<Txn>): Pair<Long?, List<Long>> {
        val batchId = insertImportBatch(batch)
        val ids = insertTxns(rows.map { it.copy(importId = batchId) })
        if (ids.all { it == -1L }) {
            deleteImportBatch(batchId)
            return null to ids
        }
        return batchId to ids
    }

    @Query("SELECT * FROM txn WHERE importId = :importId ORDER BY timestamp DESC")
    abstract fun importedBy(importId: Long): Flow<List<Txn>>

    @Query("SELECT * FROM txn WHERE externalId IN (:externalIds) AND deletedAt IS NOT NULL")
    abstract suspend fun deletedWithExternalIds(externalIds: List<String>): List<Txn>

    @Query("DELETE FROM txn WHERE importId = :importId")
    abstract suspend fun deleteImported(importId: Long)

    /** Removes an import and every row it added, deleted ones included, as if it never happened. */
    @Transaction
    open suspend fun undoImport(importId: Long) {
        deleteImported(importId)
        deleteImportBatch(importId)
    }

    @Query(
        "SELECT b.id AS id, b.importedAt AS importedAt, b.fileName AS fileName, b.firstTimestamp AS firstTimestamp, " +
            "b.lastTimestamp AS lastTimestamp, (SELECT COUNT(*) FROM txn t WHERE t.importId = b.id) AS payments " +
            "FROM import_batch b ORDER BY b.importedAt DESC",
    )
    abstract fun imports(): Flow<List<ImportSummary>>

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

    // --- backup: whole-database snapshot and restore ---
    @Query("SELECT * FROM category ORDER BY id")
    abstract suspend fun allCategories(): List<Category>

    @Query("SELECT * FROM txn ORDER BY id")
    abstract suspend fun allTxns(): List<Txn>

    @Query("SELECT * FROM rule ORDER BY merchantKey")
    abstract suspend fun allRules(): List<Rule>

    @Query("SELECT * FROM budget ORDER BY yearMonth, categoryId")
    abstract suspend fun allBudgets(): List<Budget>

    @Query("SELECT * FROM alert_fired ORDER BY yearMonth, categoryId, level")
    abstract suspend fun allAlerts(): List<AlertFired>

    @Query("SELECT * FROM import_batch ORDER BY id")
    abstract suspend fun allImports(): List<ImportBatch>

    @Transaction
    open suspend fun snapshot(): Snapshot =
        Snapshot(allCategories(), allTxns(), allRules(), allBudgets(), allAlerts(), allImports())

    @Query("DELETE FROM category") abstract suspend fun clearCategories()
    @Query("DELETE FROM txn") abstract suspend fun clearTxns()
    @Query("DELETE FROM rule") abstract suspend fun clearRules()
    @Query("DELETE FROM budget") abstract suspend fun clearBudgets()
    @Query("DELETE FROM alert_fired") abstract suspend fun clearAlerts()
    @Query("DELETE FROM import_batch") abstract suspend fun clearImports()

    @Insert abstract suspend fun insertCategories(rows: List<Category>)
    @Insert abstract suspend fun insertAllTxns(rows: List<Txn>)
    @Insert abstract suspend fun insertRules(rows: List<Rule>)
    @Insert abstract suspend fun insertBudgets(rows: List<Budget>)
    @Insert abstract suspend fun insertAlerts(rows: List<AlertFired>)
    @Insert abstract suspend fun insertImports(rows: List<ImportBatch>)

    /** Replaces everything with a backup, in one transaction: a failed restore leaves the data untouched. */
    @Transaction
    open suspend fun replaceAll(s: Snapshot) {
        clearTxns(); clearRules(); clearBudgets(); clearAlerts(); clearImports(); clearCategories()
        insertCategories(s.categories)
        insertImports(s.imports)
        insertAllTxns(s.txns)
        insertRules(s.rules)
        insertBudgets(s.budgets)
        insertAlerts(s.alerts)
    }
}

@Database(
    entities = [Category::class, Txn::class, Rule::class, Budget::class, AlertFired::class, ImportBatch::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) = seed(db)
                })
                .build()

        /**
         * v2: import batches (undo an import) and soft delete (deleted payments stay out of later imports).
         * The SQL matches what Room generates for [Txn] and [ImportBatch]; Room checks it when the database opens.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `importId` INTEGER")
                db.execSQL("ALTER TABLE `txn` ADD COLUMN `deletedAt` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `import_batch` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`importedAt` INTEGER NOT NULL, `fileName` TEXT, `firstTimestamp` INTEGER NOT NULL, " +
                        "`lastTimestamp` INTEGER NOT NULL)",
                )
            }
        }

        /** Inserted in this order into a fresh database, so these get ids 1, 2, 3… (Food = 1, Delivery = 2, …). */
        internal val SEED = linkedMapOf(
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
