package com.corpvpn.core

/** Result of probing one server. */
sealed interface Latency {
    data object Measuring : Latency
    data object Unreachable : Latency
    data class Ms(val value: Long) : Latency
}

/**
 * Measures round-trip time to a host. WireGuard itself is UDP and stays silent to
 * strangers, so implementations ping the host (ICMP) and may fall back to a TCP
 * connect on a port the server usually has open.
 */
fun interface LatencyProbe {
    /** Blocking. Returns milliseconds, or null if the host did not answer. */
    fun measure(host: String): Long?
}

object PingOutput {
    private val summary = Regex("""=\s*[\d.]+/([\d.]+)/[\d.]+""")
    private val single = Regex("""time[=<]\s*([\d.]+)\s*ms""")

    /**
     * Parses the output of the system `ping` tool: the average from the
     * "rtt min/avg/max/mdev = a/b/c/d ms" summary, or else the average of the
     * per-reply "time=X ms" values. Null when no reply came back.
     */
    fun averageMs(output: String): Long? {
        summary.find(output)?.let { return it.groupValues[1].toDouble().toRoundedMs() }
        val times = single.findAll(output).map { it.groupValues[1].toDouble() }.toList()
        return if (times.isEmpty()) null else times.average().toRoundedMs()
    }

    private fun Double.toRoundedMs(): Long = Math.round(this).coerceAtLeast(1)
}

/**
 * Orders servers best first: reachable servers by ping, then servers still being
 * measured, then unreachable ones. Ties keep the list order.
 */
fun rankServers(servers: List<Server>, latencies: Map<String, Latency>): List<Server> =
    servers.sortedBy { server ->
        when (val latency = latencies[server.host]) {
            is Latency.Ms -> latency.value
            Latency.Unreachable -> Long.MAX_VALUE
            Latency.Measuring, null -> Long.MAX_VALUE - 1
        }
    }

/** The server to connect to automatically: the lowest ping that answered, or null if none did. */
fun bestServer(servers: List<Server>, latencies: Map<String, Latency>): Server? =
    rankServers(servers, latencies).firstOrNull { latencies[it.host] is Latency.Ms }
