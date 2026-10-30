package com.corpvpn.app.data

import com.corpvpn.core.LatencyProbe
import com.corpvpn.core.PingOutput
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Pings with the system `ping` tool (ICMP works without root on Android). If the
 * server drops ICMP, falls back to timing a TCP connect to SSH or HTTPS, which
 * Amnezia-managed servers usually have open.
 */
class SystemLatencyProbe : LatencyProbe {
    override fun measure(host: String): Long? = icmp(host) ?: tcp(host)

    private fun icmp(host: String): Long? = try {
        val process = ProcessBuilder("ping", "-c", "3", "-i", "0.2", "-W", "2", "-n", host)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroy()
        PingOutput.averageMs(output)
    } catch (e: Exception) {
        null
    }

    private fun tcp(host: String): Long? = FALLBACK_PORTS.firstNotNullOfOrNull { port ->
        try {
            Socket().use { socket ->
                val start = System.nanoTime()
                socket.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS)
                ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            }
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        val FALLBACK_PORTS = listOf(22, 443)
        const val TCP_TIMEOUT_MS = 2000
    }
}
