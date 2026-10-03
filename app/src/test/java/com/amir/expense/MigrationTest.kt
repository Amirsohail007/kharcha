package com.amir.expense

import androidx.sqlite.db.SupportSQLiteDatabase
import com.amir.expense.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs [AppDatabase.MIGRATION_1_2] on a real SQLite database holding a v1.0.0 schema and data, and checks the
 * result is identical to a fresh v2 install. Room compares exactly this (columns, types, nullability, keys,
 * indices) when the app opens an upgraded database, and crashes if they differ.
 * The CREATE statements are the ones Room generates for the entities (AppDatabase_Impl.createAllTables).
 */
class MigrationTest {
    private val category = "CREATE TABLE IF NOT EXISTS `category` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `parentId` INTEGER, `sortOrder` INTEGER NOT NULL)"
    private val rule = "CREATE TABLE IF NOT EXISTS `rule` (`merchantKey` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`merchantKey`))"
    private val budget = "CREATE TABLE IF NOT EXISTS `budget` (`yearMonth` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `amountPaise` INTEGER NOT NULL, PRIMARY KEY(`yearMonth`, `categoryId`))"
    private val alert = "CREATE TABLE IF NOT EXISTS `alert_fired` (`yearMonth` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `level` INTEGER NOT NULL, PRIMARY KEY(`yearMonth`, `categoryId`, `level`))"
    private val txnIndices = listOf(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_txn_externalId` ON `txn` (`externalId`)",
        "CREATE INDEX IF NOT EXISTS `index_txn_timestamp` ON `txn` (`timestamp`)",
    )
    private val txnColumnsV1 = "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `externalId` TEXT, `source` TEXT NOT NULL, " +
        "`timestamp` INTEGER NOT NULL, `amountPaise` INTEGER NOT NULL, `merchant` TEXT NOT NULL, `merchantKey` TEXT NOT NULL, " +
        "`note` TEXT, `categoryId` INTEGER, `ignored` INTEGER NOT NULL"

    private val v1 = listOf(category, "CREATE TABLE IF NOT EXISTS `txn` ($txnColumnsV1)") + txnIndices + listOf(rule, budget, alert)
    private val v2 = listOf(category, "CREATE TABLE IF NOT EXISTS `txn` ($txnColumnsV1, `importId` INTEGER, `deletedAt` INTEGER)") +
        txnIndices + listOf(
            rule, budget, alert,
            "CREATE TABLE IF NOT EXISTS `import_batch` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `importedAt` INTEGER NOT NULL, " +
                "`fileName` TEXT, `firstTimestamp` INTEGER NOT NULL, `lastTimestamp` INTEGER NOT NULL)",
        )

    @Test fun upgradedDatabaseMatchesFreshInstallAndKeepsData() {
        val upgraded = open(v1)
        upgraded.exec("INSERT INTO category (id, name, parentId, sortOrder) VALUES (1, 'Food', NULL, 0)")
        upgraded.exec(
            "INSERT INTO txn (externalId, source, timestamp, amountPaise, merchant, merchantKey, note, categoryId, ignored) " +
                "VALUES ('T1', 'PHONEPE', 1790000000000, 25050, 'Swiggy', 'swiggy', NULL, 1, 0)",
        )
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
        AppDatabase.MIGRATION_1_2.migrate(supportDb(upgraded))

        val fresh = open(v2)
        for (table in listOf("category", "txn", "rule", "budget", "alert_fired", "import_batch")) {
            assertEquals("columns of $table", fresh.rows("PRAGMA table_info(`$table`)"), upgraded.rows("PRAGMA table_info(`$table`)"))
            assertEquals("indices of $table", fresh.rows("PRAGMA index_list(`$table`)"), upgraded.rows("PRAGMA index_list(`$table`)"))
        }

        // The v1 payment is intact, not deleted and not part of any import.
        assertEquals(
            listOf(listOf("T1", "25050", "1", null, null)),
            upgraded.rows("SELECT externalId, amountPaise, categoryId, importId, deletedAt FROM txn"),
        )
        upgraded.exec("INSERT INTO import_batch (importedAt, fileName, firstTimestamp, lastTimestamp) VALUES (1, NULL, 1, 2)")
        assertEquals(listOf(listOf("1")), upgraded.rows("SELECT id FROM import_batch"))
    }

    private fun open(schema: List<String>): Connection =
        DriverManager.getConnection("jdbc:sqlite::memory:").apply { schema.forEach { exec(it) } }

    private fun Connection.exec(sql: String) {
        createStatement().use { it.execute(sql) }
    }

    private fun Connection.rows(sql: String): List<List<String?>> = createStatement().use { st ->
        st.executeQuery(sql).use { rs ->
            buildList { while (rs.next()) add((1..rs.metaData.columnCount).map { rs.getString(it) }) }
        }
    }

    /** The migration only calls execSQL; route that to JDBC. */
    private fun supportDb(conn: Connection): SupportSQLiteDatabase =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupportSQLiteDatabase::class.java)) { _, method, args ->
            if (method.name == "execSQL" && args?.size == 1) conn.exec(args[0] as String) else throw UnsupportedOperationException(method.name)
            null
        } as SupportSQLiteDatabase
}
