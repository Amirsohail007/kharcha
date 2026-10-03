package com.amir.expense.sync

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amir.expense.App
import com.amir.expense.data.Backup
import com.amir.expense.data.Repository
import com.amir.expense.data.Snapshot
import com.amir.expense.ui.plural
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Google Drive backup: one JSON file ([Backup]) in the app's hidden Drive folder. "Sync now" and a daily
 * background job upload it. After a reinstall, connecting the same Google account restores it on its own
 * while the app is still empty; otherwise you choose between the two (see [SyncPlan]).
 * Access comes from Google Play services' [AuthorizationClient]: one consent screen, then silent tokens.
 *
 * The connection state lives in its own SharedPreferences file. Android's own backup may restore it
 * after a reinstall; that's safe because [SyncPlan] never overwrites a Drive backup it didn't write.
 */
class DriveSync(private val context: Context, private val repo: Repository) {

    /** Drive's backup differs from this phone's data and was written elsewhere (another phone, an earlier install). */
    data class Conflict(val savedAt: Long?, val payments: Int?, val device: String?)

    data class State(
        val connected: Boolean = false,
        /** The Google account's email, when Drive told us. */
        val account: String? = null,
        val autoSync: Boolean = true,
        val lastSyncAt: Long? = null,
        val busy: Boolean = false,
        /** Google wants the consent screen again (access removed, or the consent screen is still in testing). */
        val needsSignIn: Boolean = false,
        val error: String? = null,
        val conflict: Conflict? = null,
    )

    /** For the background job: [Retry] asks WorkManager to try again later. */
    enum class Outcome { Done, Retry, Stopped }

    private class NeedsSignIn : Exception()

    /** A failure with a message fit to show as is. */
    class Problem(message: String) : Exception(message)

    private val prefs = context.getSharedPreferences("drive_sync", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DriveApi.SCOPE))).build()
    private val client: AuthorizationClient get() = Identity.getAuthorizationClient(context)

    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off news for a snackbar, like "Restored 240 payments". Dropped when no screen is listening. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     * Connecting, step 1. Returns Google's consent screen to show, or null when access was granted
     * before (after a reinstall, say) and the connection is already made.
     */
    suspend fun connect(): PendingIntent? {
        val result = client.authorize(request).await()
        if (result.hasResolution()) return result.pendingIntent
        finishConnect(result.accessToken ?: throw Problem("Google returned no access token."))
        return null
    }

    /** Connecting, step 2: the consent screen's result. Throws [ApiException] if access wasn't granted. */
    suspend fun onConsent(data: Intent?) {
        val result = client.getAuthorizationResultFromIntent(data)
        finishConnect(result.accessToken ?: throw Problem("Google returned no access token."))
    }

    private suspend fun finishConnect(token: String) {
        val email = withContext(Dispatchers.IO) { runCatching { DriveApi(token).email() }.getOrNull() }
        update { it.copy(connected = true, account = email ?: it.account, needsSignIn = false, error = null) }
        schedule()
        sync(token)
    }

    /**
     * Uploads this phone's data if Drive still holds this phone's last backup. When Drive holds a different
     * backup: restores it if this phone has nothing of its own yet (a reinstall), else reports a [Conflict].
     */
    suspend fun sync(token: String? = null): Outcome = locked {
        val snapshot = repo.snapshot()
        val bytes = withContext(Dispatchers.Default) { Backup.encode(snapshot) }
        val local = SyncPlan.md5(bytes)
        val base = prefs.getString(KEY_BASE, null)
        val other = drive(token) { api ->
            val remote = api.find()
            when (SyncPlan.decide(base, local, remote)) {
                SyncPlan.Action.Upload -> {
                    if (remote == null) api.create(bytes, properties(snapshot)) else api.update(remote.id, bytes, properties(snapshot))
                    null
                }
                SyncPlan.Action.InSync -> null
                SyncPlan.Action.Conflict -> remote
            }
        }
        when {
            other == null -> synced(local)
            snapshot.isFresh() -> restoreNow(token)
            else -> update { it.copy(conflict = conflictOf(other), error = null) }
        }
    }

    /** Replaces everything on this phone with the backup on Drive. */
    suspend fun restore(): Outcome = locked { restoreNow(null) }

    private suspend fun restoreNow(token: String?) {
        val bytes = drive(token) { api -> api.find()?.let { api.download(it.id) } }
            ?: throw Problem("There's no backup in this Google account's Drive yet.")
        val snapshot = withContext(Dispatchers.Default) { Backup.decode(bytes) }
        repo.replaceAll(snapshot)
        synced(SyncPlan.md5(bytes))
        _messages.tryEmit("Restored ${plural(snapshot.txns.count { it.deletedAt == null }, "payment")} from Google Drive")
    }

    /** Nothing entered yet: only the seeded categories. What a reinstalled app looks like. */
    private fun Snapshot.isFresh() = txns.isEmpty() && budgets.isEmpty() && rules.isEmpty() && imports.isEmpty()

    /** Replaces the backup on Drive with this phone's data. */
    suspend fun overwriteDrive(): Outcome = locked {
        val snapshot = repo.snapshot()
        val bytes = withContext(Dispatchers.Default) { Backup.encode(snapshot) }
        drive(null) { api ->
            val remote = api.find()
            if (remote == null) api.create(bytes, properties(snapshot)) else api.update(remote.id, bytes, properties(snapshot))
        }
        synced(SyncPlan.md5(bytes))
    }

    fun setAutoSync(on: Boolean) {
        update { it.copy(autoSync = on) }
        schedule()
    }

    /** Stops syncing and gives up Drive access. The backup itself stays in Drive. */
    suspend fun disconnect() = mutex.withLock {
        val account = _state.value.account
        if (account != null) {
            runCatching {
                client.revokeAccess(
                    RevokeAccessRequest.builder()
                        .setAccount(Account(account, "com.google"))
                        .setScopes(listOf(Scope(DriveApi.SCOPE)))
                        .build(),
                ).await()
            }
        }
        prefs.edit { clear() }
        _state.value = State()
        schedule()
    }

    /** Keeps the daily background sync in step with [State.connected] and [State.autoSync]. Safe to call often. */
    fun schedule() {
        val work = WorkManager.getInstance(context)
        val s = _state.value
        if (s.connected && s.autoSync) {
            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(24, TimeUnit.HOURS) // connecting just synced
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        } else {
            work.cancelUniqueWork(WORK_NAME)
        }
    }

    /** Runs [block] with the busy flag set, one operation at a time, turning failures into state. */
    private suspend fun locked(block: suspend () -> Unit): Outcome = mutex.withLock {
        if (!_state.value.connected) return@withLock Outcome.Stopped
        _state.update { it.copy(busy = true) }
        try {
            block()
            Outcome.Done
        } catch (e: CancellationException) {
            throw e
        } catch (_: NeedsSignIn) {
            update { it.copy(needsSignIn = true, error = null) }
            Outcome.Stopped
        } catch (e: DriveError) {
            update { it.copy(error = e.message) }
            if (e.code == 429 || e.code >= 500) Outcome.Retry else Outcome.Stopped
        } catch (e: Backup.Unreadable) {
            update { it.copy(error = e.message) }
            Outcome.Stopped
        } catch (_: IOException) {
            update { it.copy(error = OFFLINE) }
            Outcome.Retry
        } catch (e: Exception) {
            update { it.copy(error = describe(e)) }
            Outcome.Stopped
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    /** Calls Drive with a fresh access token, retrying once with a new token if Drive rejects the cached one. */
    private suspend fun <T> drive(token: String?, call: (DriveApi) -> T): T {
        val first = token ?: silentToken()
        return try {
            withContext(Dispatchers.IO) { call(DriveApi(first)) }
        } catch (e: DriveError) {
            if (e.code != 401) throw e
            runCatching { client.clearToken(ClearTokenRequest.builder().setToken(first).build()).await() }
            val fresh = silentToken()
            withContext(Dispatchers.IO) { call(DriveApi(fresh)) }
        }
    }

    private suspend fun silentToken(): String {
        val result = client.authorize(request).await()
        if (result.hasResolution()) throw NeedsSignIn()
        return result.accessToken ?: throw NeedsSignIn()
    }

    private fun synced(md5: String) {
        prefs.edit { putString(KEY_BASE, md5) }
        update { it.copy(lastSyncAt = System.currentTimeMillis(), conflict = null, error = null, needsSignIn = false) }
    }

    /** Shown before restoring, so you can tell which backup it is without downloading it. */
    private fun properties(s: Snapshot) = mapOf(
        PROP_PAYMENTS to s.txns.count { it.deletedAt == null }.toString(),
        PROP_DEVICE to "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(60),
    )

    private fun conflictOf(remote: RemoteBackup) = Conflict(
        savedAt = remote.modifiedTime?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
        payments = remote.properties[PROP_PAYMENTS]?.toIntOrNull(),
        device = remote.properties[PROP_DEVICE],
    )

    private fun update(change: (State) -> State) {
        _state.update(change)
        save(_state.value)
    }

    private fun load() = State(
        connected = prefs.getBoolean(KEY_CONNECTED, false),
        account = prefs.getString(KEY_ACCOUNT, null),
        autoSync = prefs.getBoolean(KEY_AUTO, true),
        lastSyncAt = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0 },
        needsSignIn = prefs.getBoolean(KEY_NEEDS_SIGN_IN, false),
        error = prefs.getString(KEY_ERROR, null),
        conflict = if (!prefs.getBoolean(KEY_CONFLICT, false)) null else Conflict(
            savedAt = prefs.getLong(KEY_CONFLICT_AT, 0L).takeIf { it > 0 },
            payments = prefs.getInt(KEY_CONFLICT_PAYMENTS, -1).takeIf { it >= 0 },
            device = prefs.getString(KEY_CONFLICT_DEVICE, null),
        ),
    )

    private fun save(s: State) = prefs.edit {
        putBoolean(KEY_CONNECTED, s.connected)
        putString(KEY_ACCOUNT, s.account)
        putBoolean(KEY_AUTO, s.autoSync)
        putLong(KEY_LAST_SYNC, s.lastSyncAt ?: 0L)
        putBoolean(KEY_NEEDS_SIGN_IN, s.needsSignIn)
        putString(KEY_ERROR, s.error)
        putBoolean(KEY_CONFLICT, s.conflict != null)
        putLong(KEY_CONFLICT_AT, s.conflict?.savedAt ?: 0L)
        putInt(KEY_CONFLICT_PAYMENTS, s.conflict?.payments ?: -1)
        putString(KEY_CONFLICT_DEVICE, s.conflict?.device)
    }

    companion object {
        private const val WORK_NAME = "drive-sync"
        private const val OFFLINE = "Couldn't reach Google Drive. Check your internet connection."
        private const val PROP_PAYMENTS = "payments"
        private const val PROP_DEVICE = "device"
        private const val KEY_BASE = "baseMd5"
        private const val KEY_CONNECTED = "connected"
        private const val KEY_ACCOUNT = "account"
        private const val KEY_AUTO = "autoSync"
        private const val KEY_LAST_SYNC = "lastSyncAt"
        private const val KEY_NEEDS_SIGN_IN = "needsSignIn"
        private const val KEY_ERROR = "error"
        private const val KEY_CONFLICT = "conflict"
        private const val KEY_CONFLICT_AT = "conflictSavedAt"
        private const val KEY_CONFLICT_PAYMENTS = "conflictPayments"
        private const val KEY_CONFLICT_DEVICE = "conflictDevice"

        /** Plain-language reason for a failed Google sign-in or sync. */
        fun describe(e: Throwable): String = when {
            e is ApiException && e.statusCode == CommonStatusCodes.DEVELOPER_ERROR ->
                "This copy of the app isn't registered with Google Cloud yet (see docs/google-drive-setup.md)."
            e is ApiException && e.statusCode == CommonStatusCodes.NETWORK_ERROR -> OFFLINE
            e is ApiException -> "Google sign-in failed (code ${e.statusCode})."
            else -> e.message ?: "Something went wrong."
        }
    }
}

/** The daily background sync. */
class DriveSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when ((applicationContext as App).drive.sync()) {
            DriveSync.Outcome.Retry -> Result.retry()
            else -> Result.success()
        }
}
