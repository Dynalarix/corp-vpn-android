package com.corpvpn.app.data

import android.content.Context
import com.corpvpn.core.CheckList
import com.corpvpn.core.CheckListException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class CheckListSettings(
    /** The user's own lines, exactly as typed. */
    val manualText: String,
    /** Optional link to a text file with one site per line. */
    val url: String,
    /** Last successfully downloaded file, used when the link can't be reached. */
    val downloadedText: String?,
    val downloadedAt: Long?,
    val lastError: String?,
) {
    /** What "Connect to verified" checks. */
    val sites: List<String> get() = CheckList.combine(manualText, downloadedText)
}

/** The sites "Connect to verified" checks: the user's own lines plus an optional downloaded list. */
class CheckListRepository(context: Context) {
    private val prefs = context.getSharedPreferences("check_list", Context.MODE_PRIVATE)

    fun load(): CheckListSettings = CheckListSettings(
        manualText = prefs.getString(KEY_TEXT, null) ?: DEFAULT_TEXT,
        url = prefs.getString(KEY_URL, null).orEmpty(),
        downloadedText = prefs.getString(KEY_DOWNLOADED, null),
        downloadedAt = prefs.getLong(KEY_DOWNLOADED_AT, 0).takeIf { it > 0 },
        lastError = prefs.getString(KEY_ERROR, null),
    )

    fun saveManual(text: String) {
        prefs.edit().putString(KEY_TEXT, text).apply()
    }

    /** A new link drops the list downloaded from the old one. */
    fun saveUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed == prefs.getString(KEY_URL, null).orEmpty()) return
        prefs.edit()
            .putString(KEY_URL, trimmed)
            .remove(KEY_DOWNLOADED)
            .remove(KEY_DOWNLOADED_AT)
            .remove(KEY_ERROR)
            .apply()
    }

    /**
     * Downloads the list from the saved link. On failure the previous download is
     * kept and the error is remembered for the settings screen.
     */
    suspend fun refresh(): CheckListSettings = withContext(Dispatchers.IO) {
        val url = prefs.getString(KEY_URL, null).orEmpty()
        if (url.isEmpty()) return@withContext load()
        try {
            val body = download(url)
            CheckList.validateDownload(body)
            prefs.edit()
                .putString(KEY_DOWNLOADED, body)
                .putLong(KEY_DOWNLOADED_AT, System.currentTimeMillis())
                .remove(KEY_ERROR)
                .apply()
        } catch (e: CheckListException) {
            prefs.edit().putString(KEY_ERROR, e.message).apply()
        } catch (e: Exception) {
            prefs.edit().putString(KEY_ERROR, "Couldn't download the list: ${e.message ?: e.javaClass.simpleName}").apply()
        }
        load()
    }

    private fun download(url: String): String {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw CheckListException("The link must start with https://")
        }
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.useCaches = false
            val code = connection.responseCode
            if (code !in 200..299) throw CheckListException("The server answered with error $code.")
            val buffer = CharArray(CheckList.MAX_DOWNLOAD_BYTES + 1)
            val text = StringBuilder()
            InputStreamReader(connection.inputStream, Charsets.UTF_8).use { reader ->
                while (text.length <= CheckList.MAX_DOWNLOAD_BYTES) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    text.append(buffer, 0, n)
                }
            }
            return text.toString()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY_TEXT = "text"
        const val KEY_URL = "url"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_DOWNLOADED_AT = "downloaded_at"
        const val KEY_ERROR = "error"
        const val TIMEOUT_MS = 8000
        val DEFAULT_TEXT = CheckList.DEFAULT.joinToString("\n") { CheckList.displayName(it) }
    }
}
