package com.amir.expense.sync

import java.security.MessageDigest

/**
 * What a sync should do, decided from three checksums of the backup file:
 * [base] what this phone last uploaded or restored, [local] this phone's data now, and Drive's copy.
 * Drive is only overwritten when it still holds what this phone last synced, so a backup written by
 * another phone or by an earlier install is never lost without asking.
 */
object SyncPlan {
    enum class Action {
        /** Drive has no backup, or still has this phone's last one: upload. */
        Upload,
        /** Drive already holds exactly this data. */
        InSync,
        /** Drive changed somewhere else: ask whether to restore it or replace it. */
        Conflict,
    }

    fun decide(base: String?, local: String, remote: RemoteBackup?): Action = when {
        remote == null -> Action.Upload
        remote.md5 == local -> Action.InSync
        remote.md5 != null && remote.md5 == base -> Action.Upload
        else -> Action.Conflict
    }

    /** Lowercase hex MD5, the same form as Drive's md5Checksum. */
    fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
}
