package com.amir.expense.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

/** The backup file as Drive sees it. [md5] is Drive's checksum of the file content. */
data class RemoteBackup(val id: String, val md5: String?, val modifiedTime: String?, val properties: Map<String, String>)

/** Drive answered with an error status. 401 means the access token is no longer valid. */
class DriveError(val code: Int, message: String) : IOException("Google Drive error $code: $message")

/**
 * The few Drive v3 REST calls the backup needs, over plain HttpURLConnection (no Google API client
 * library: it's large and leans on reflection that R8 strips). Everything lives in the app's hidden
 * appDataFolder, which only this app can see and which survives uninstalling it.
 * Blocking: call from a background thread.
 */
class DriveApi(private val token: String) {

    /** The newest backup file, or null when this Google account has none yet. */
    fun find(): RemoteBackup? {
        val q = URLEncoder.encode("name = '$FILE_NAME' and trashed = false", "UTF-8")
        val json = JSONObject(
            String(request("GET", "$API/files?spaces=appDataFolder&q=$q&orderBy=modifiedTime%20desc&pageSize=1&fields=files($FIELDS)")),
        )
        val files = json.optJSONArray("files") ?: return null
        return if (files.length() == 0) null else remote(files.getJSONObject(0))
    }

    fun create(content: ByteArray, properties: Map<String, String>): RemoteBackup {
        val metadata = JSONObject()
            .put("name", FILE_NAME)
            .put("mimeType", MIME)
            .put("parents", JSONArray().put("appDataFolder"))
            .put("appProperties", JSONObject(properties))
        return remote(JSONObject(String(upload("POST", "$UPLOAD/files?uploadType=multipart&fields=$FIELDS", metadata, content))))
    }

    fun update(id: String, content: ByteArray, properties: Map<String, String>): RemoteBackup {
        val metadata = JSONObject().put("appProperties", JSONObject(properties))
        return remote(JSONObject(String(upload("PATCH", "$UPLOAD/files/$id?uploadType=multipart&fields=$FIELDS", metadata, content))))
    }

    fun download(id: String): ByteArray = request("GET", "$API/files/$id?alt=media")

    /** The signed-in account's email, to show which Drive the backup goes to. */
    fun email(): String? =
        JSONObject(String(request("GET", "$API/about?fields=user(emailAddress)")))
            .optJSONObject("user")?.optString("emailAddress")?.takeIf { it.isNotEmpty() }

    private fun upload(method: String, url: String, metadata: JSONObject, content: ByteArray): ByteArray {
        val boundary = "kharcha-" + UUID.randomUUID()
        val body = multipartBody(boundary, metadata.toString(), content)
        return request(method, url, body, "multipart/related; boundary=$boundary")
    }

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method // Android's HttpURLConnection accepts PATCH (desktop Java's doesn't)
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.use { String(it.readBytes()) }.orEmpty()
                throw DriveError(code, errorMessage(detail) ?: conn.responseMessage.orEmpty())
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_NAME = "kharcha-backup.json"
        private const val MIME = "application/json"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FIELDS = "id,md5Checksum,modifiedTime,appProperties"

        /** multipart/related body for a Drive upload: JSON metadata, then the file content. */
        internal fun multipartBody(boundary: String, metadata: String, content: ByteArray): ByteArray {
            val head = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                "--$boundary\r\nContent-Type: $MIME\r\n\r\n"
            val tail = "\r\n--$boundary--\r\n"
            return head.toByteArray() + content + tail.toByteArray()
        }

        internal fun remote(json: JSONObject): RemoteBackup {
            val props = json.optJSONObject("appProperties")
            return RemoteBackup(
                id = json.getString("id"),
                md5 = json.optString("md5Checksum").takeIf { it.isNotEmpty() },
                modifiedTime = json.optString("modifiedTime").takeIf { it.isNotEmpty() },
                properties = props?.keys()?.asSequence()?.associateWith { props.getString(it) }.orEmpty(),
            )
        }

        /** Drive's error body is {"error": {"message": "..."}}. */
        internal fun errorMessage(body: String): String? =
            runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
    }
}
