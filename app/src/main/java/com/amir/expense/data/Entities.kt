package com.amir.expense.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Two levels: parentId == null is a top-level category. Only leaves get transactions. */
@Entity(tableName = "category")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val sortOrder: Int = 0,
)

/**
 * One money movement. amountPaise > 0 is money out (expense), < 0 is money in (refund/credit).
 * externalId is the PhonePe Transaction ID; the unique index makes re-imports a no-op.
 */
@Entity(
    tableName = "txn",
    indices = [Index(value = ["externalId"], unique = true), Index("timestamp")],
)
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String? = null,
    val source: String = SOURCE_MANUAL,
    val timestamp: Long,
    val amountPaise: Long,
    val merchant: String,
    val merchantKey: String = merchantKey(merchant),
    val note: String? = null,
    val categoryId: Long? = null,
    /** Not an expense (self-transfer, money received...). Excluded from every total. */
    val ignored: Boolean = false,
) {
    companion object {
        const val SOURCE_MANUAL = "MANUAL"
        const val SOURCE_PHONEPE = "PHONEPE"
    }
}

/** Learned "this merchant always goes to this category". */
@Entity(tableName = "rule")
data class Rule(@PrimaryKey val merchantKey: String, val categoryId: Long)

/**
 * Effective-dated budget: applies from [yearMonth] until a later row for the same category.
 * categoryId == [TOTAL] is the overall monthly budget. amountPaise == 0 means "no budget".
 */
@Entity(tableName = "budget", primaryKeys = ["yearMonth", "categoryId"])
data class Budget(val yearMonth: Int, val categoryId: Long, val amountPaise: Long) {
    companion object {
        const val TOTAL = 0L
    }
}

/** Remembers which 80% / 100% alerts already fired, so each fires once per month. */
@Entity(tableName = "alert_fired", primaryKeys = ["yearMonth", "categoryId", "level"])
data class AlertFired(val yearMonth: Int, val categoryId: Long, val level: Int)
