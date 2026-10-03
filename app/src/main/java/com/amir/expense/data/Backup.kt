package com.amir.expense.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Everything the app stores: what a Google Drive backup holds. */
data class Snapshot(
    val categories: List<Category>,
    val txns: List<Txn>,
    val rules: List<Rule>,
    val budgets: List<Budget>,
    val alerts: List<AlertFired>,
    val imports: List<ImportBatch>,
)

/**
 * The backup file: plain JSON. The same data always encodes to the same bytes, because rows come
 * sorted from [ExpenseDao.snapshot] and Android's JSONObject keeps insertion order. Drive sync
 * relies on that to tell "changed" from "unchanged" by checksum. Null fields are left out.
 */
object Backup {
    /** Bump when the layout changes in a way older apps can't read. */
    const val FORMAT = 1

    class Unreadable(message: String) : Exception(message)

    fun encode(s: Snapshot): ByteArray = JSONObject()
        .put("app", "kharcha")
        .put("format", FORMAT)
        .put("categories", s.categories.toJson {
            put("id", it.id).put("name", it.name).putOpt("parentId", it.parentId).put("sortOrder", it.sortOrder)
        })
        .put("txns", s.txns.toJson {
            put("id", it.id).putOpt("externalId", it.externalId).put("source", it.source)
                .put("timestamp", it.timestamp).put("amountPaise", it.amountPaise)
                .put("merchant", it.merchant).put("merchantKey", it.merchantKey).putOpt("note", it.note)
                .putOpt("categoryId", it.categoryId).put("ignored", it.ignored)
                .putOpt("importId", it.importId).putOpt("deletedAt", it.deletedAt)
        })
        .put("rules", s.rules.toJson { put("merchantKey", it.merchantKey).put("categoryId", it.categoryId) })
        .put("budgets", s.budgets.toJson {
            put("yearMonth", it.yearMonth).put("categoryId", it.categoryId).put("amountPaise", it.amountPaise)
        })
        .put("alerts", s.alerts.toJson { put("yearMonth", it.yearMonth).put("categoryId", it.categoryId).put("level", it.level) })
        .put("imports", s.imports.toJson {
            put("id", it.id).put("importedAt", it.importedAt).putOpt("fileName", it.fileName)
                .put("firstTimestamp", it.firstTimestamp).put("lastTimestamp", it.lastTimestamp)
        })
        .toString()
        .toByteArray(Charsets.UTF_8)

    fun decode(bytes: ByteArray): Snapshot {
        val root = try {
            JSONObject(String(bytes, Charsets.UTF_8))
        } catch (e: JSONException) {
            throw Unreadable("The backup file is damaged.")
        }
        if (root.optString("app") != "kharcha") throw Unreadable("This isn't a Kharcha backup.")
        if (root.optInt("format", Int.MAX_VALUE) > FORMAT) {
            throw Unreadable("This backup is from a newer version of the app. Update the app, then restore.")
        }
        return try {
            Snapshot(
                categories = root.rows("categories") {
                    Category(getLong("id"), getString("name"), longOrNull("parentId"), getInt("sortOrder"))
                },
                txns = root.rows("txns") {
                    Txn(
                        id = getLong("id"),
                        externalId = stringOrNull("externalId"),
                        source = getString("source"),
                        timestamp = getLong("timestamp"),
                        amountPaise = getLong("amountPaise"),
                        merchant = getString("merchant"),
                        merchantKey = getString("merchantKey"),
                        note = stringOrNull("note"),
                        categoryId = longOrNull("categoryId"),
                        ignored = getBoolean("ignored"),
                        importId = longOrNull("importId"),
                        deletedAt = longOrNull("deletedAt"),
                    )
                },
                rules = root.rows("rules") { Rule(getString("merchantKey"), getLong("categoryId")) },
                budgets = root.rows("budgets") { Budget(getInt("yearMonth"), getLong("categoryId"), getLong("amountPaise")) },
                alerts = root.rows("alerts") { AlertFired(getInt("yearMonth"), getLong("categoryId"), getInt("level")) },
                imports = root.rows("imports") {
                    ImportBatch(getLong("id"), getLong("importedAt"), stringOrNull("fileName"), getLong("firstTimestamp"), getLong("lastTimestamp"))
                },
            )
        } catch (e: JSONException) {
            throw Unreadable("The backup file is damaged.")
        }
    }

    private fun <T> List<T>.toJson(fill: JSONObject.(T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(JSONObject().fill(it)) } }

    /** A missing array reads as empty, so a backup can leave out a table it has no rows for. */
    private fun <T> JSONObject.rows(name: String, read: JSONObject.() -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it).read() }
    }

    // optString/optLong turn an explicit null into "null" / 0 on Android, so check isNull first.
    private fun JSONObject.stringOrNull(name: String): String? = if (isNull(name)) null else getString(name)
    private fun JSONObject.longOrNull(name: String): Long? = if (isNull(name)) null else getLong(name)
}
