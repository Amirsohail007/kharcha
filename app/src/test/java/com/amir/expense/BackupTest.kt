package com.amir.expense

import com.amir.expense.data.AlertFired
import com.amir.expense.data.Backup
import com.amir.expense.data.Budget
import com.amir.expense.data.Category
import com.amir.expense.data.ImportBatch
import com.amir.expense.data.Rule
import com.amir.expense.data.Snapshot
import com.amir.expense.data.Txn
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupTest {
    private val snapshot = Snapshot(
        categories = listOf(Category(1, "Food", null, 0), Category(2, "Delivery", 1, 1)),
        txns = listOf(
            // Every nullable field null…
            Txn(id = 1, timestamp = 1_790_000_000_000, amountPaise = 25_050, merchant = "Chai \"stall\" / café", merchantKey = "chai stall caf"),
            // …and every one set.
            Txn(
                id = 2, externalId = "T2609282242123456789", source = Txn.SOURCE_PHONEPE, timestamp = 1_790_000_100_000,
                amountPaise = -50_000, merchant = "Rahul", note = "split dinner", categoryId = 2, ignored = true,
                importId = 7, deletedAt = 1_790_000_200_000,
            ),
        ),
        rules = listOf(Rule("swiggy limited", 2)),
        budgets = listOf(Budget(202610, Budget.TOTAL, 25_000_00), Budget(202610, 1, 10_000_00)),
        alerts = listOf(AlertFired(202610, 1, 80)),
        imports = listOf(ImportBatch(7, 1_790_000_300_000, null, 1_790_000_000_000, 1_790_000_100_000)),
    )

    @Test fun roundTripsEveryTable() {
        assertEquals(snapshot, Backup.decode(Backup.encode(snapshot)))
    }

    @Test fun sameDataSameBytes() {
        // Drive sync compares checksums, so encoding must not depend on time or randomness.
        assertArrayEquals(Backup.encode(snapshot), Backup.encode(snapshot.copy()))
    }

    @Test fun missingTablesReadAsEmpty() {
        val s = Backup.decode("""{"app":"kharcha","format":1,"txns":[]}""".toByteArray())
        assertEquals(Snapshot(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()), s)
    }

    @Test fun rejectsOtherFiles() {
        assertThrows(Backup.Unreadable::class.java) { Backup.decode("not json".toByteArray()) }
        assertThrows(Backup.Unreadable::class.java) { Backup.decode("""{"hello":1}""".toByteArray()) }
        assertThrows(Backup.Unreadable::class.java) { Backup.decode("""{"app":"kharcha","format":99}""".toByteArray()) }
        assertThrows(Backup.Unreadable::class.java) {
            Backup.decode("""{"app":"kharcha","format":1,"txns":[{"id":1}]}""".toByteArray())
        }
    }
}
