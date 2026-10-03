package com.amir.expense

import com.amir.expense.sync.DriveApi
import com.amir.expense.sync.RemoteBackup
import com.amir.expense.sync.SyncPlan
import com.amir.expense.sync.SyncPlan.Action
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveSyncTest {
    private fun remote(md5: String?) = RemoteBackup("file", md5, null, emptyMap())

    @Test fun firstBackupUploads() {
        assertEquals(Action.Upload, SyncPlan.decide(base = null, local = "a", remote = null))
    }

    @Test fun uploadsWhenDriveStillHasOurLastBackup() {
        assertEquals(Action.Upload, SyncPlan.decide(base = "a", local = "b", remote = remote("a")))
    }

    @Test fun nothingToDoWhenBothSidesMatch() {
        assertEquals(Action.InSync, SyncPlan.decide(base = "a", local = "a", remote = remote("a")))
        // Another phone uploaded exactly this data: nothing to resolve.
        assertEquals(Action.InSync, SyncPlan.decide(base = "a", local = "c", remote = remote("c")))
    }

    @Test fun neverOverwritesABackupWrittenElsewhere() {
        // Reinstalled app (no base) finding an older install's backup.
        assertEquals(Action.Conflict, SyncPlan.decide(base = null, local = "fresh", remote = remote("old")))
        // Another phone uploaded after our last sync.
        assertEquals(Action.Conflict, SyncPlan.decide(base = "a", local = "b", remote = remote("c")))
        // Drive gave no checksum: don't guess.
        assertEquals(Action.Conflict, SyncPlan.decide(base = "a", local = "b", remote = remote(null)))
    }

    @Test fun md5MatchesDriveFormat() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", SyncPlan.md5("abc".toByteArray()))
    }

    @Test fun multipartBodyHasMetadataThenContent() {
        val body = String(DriveApi.multipartBody("B", """{"name":"x"}""", "[1]".toByteArray()))
        assertEquals(
            "--B\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{\"name\":\"x\"}\r\n" +
                "--B\r\nContent-Type: application/json\r\n\r\n[1]\r\n--B--\r\n",
            body,
        )
    }

    @Test fun readsDriveFileAndErrors() {
        val file = DriveApi.remote(
            JSONObject("""{"id":"abc","md5Checksum":"ff","modifiedTime":"2026-10-03T10:15:30.123Z","appProperties":{"payments":"12"}}"""),
        )
        assertEquals(RemoteBackup("abc", "ff", "2026-10-03T10:15:30.123Z", mapOf("payments" to "12")), file)
        assertEquals(RemoteBackup("abc", null, null, emptyMap()), DriveApi.remote(JSONObject("""{"id":"abc"}""")))

        assertEquals("Invalid Credentials", DriveApi.errorMessage("""{"error":{"code":401,"message":"Invalid Credentials"}}"""))
        assertNull(DriveApi.errorMessage("<html>Bad gateway</html>"))
    }
}
