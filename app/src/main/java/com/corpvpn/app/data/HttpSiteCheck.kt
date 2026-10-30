package com.corpvpn.app.data

import com.corpvpn.core.SiteCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A site counts as open when it sends back any HTTP response, even an error page:
 * that proves DNS, TCP and TLS all got through. Blocked sites time out or reset.
 */
class HttpSiteCheck : SiteCheck {
    override suspend fun reachable(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.responseCode > 0
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val TIMEOUT_MS = 4000
    }
}
