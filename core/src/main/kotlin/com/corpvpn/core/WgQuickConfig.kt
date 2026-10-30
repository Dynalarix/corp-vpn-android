package com.corpvpn.core

/**
 * Minimal reader for wg-quick style configs ("[Interface]" / "[Peer]" sections
 * of "Key = Value" lines). AmneziaWG uses the same format with extra
 * Interface fields (Jc, Jmin, Jmax, S1, S2, H1-H4).
 */
class WgQuickConfig private constructor(
    val interfaceFields: Map<String, String>,
    val peers: List<Map<String, String>>,
) {
    val isAmnezia: Boolean
        get() = AMNEZIA_FIELDS.any { field ->
            val value = interfaceFields[field]?.toLongOrNull() ?: return@any false
            // H1-H4 = 1-4 and all junk params = 0 is what plain WireGuard looks like.
            value != 0L && !(field.startsWith("H") && value == field.drop(1).toLong())
        }

    /** The first peer's endpoint as (host, port). */
    fun endpoint(): Pair<String, Int> {
        val raw = peers.firstNotNullOfOrNull { it["Endpoint"] }
            ?: throw KeyImportException("The config has no server address (Endpoint).")
        return parseEndpoint(raw)
    }

    companion object {
        private val AMNEZIA_FIELDS = listOf("Jc", "Jmin", "Jmax", "S1", "S2", "H1", "H2", "H3", "H4")

        fun parse(text: String): WgQuickConfig {
            val interfaceFields = mutableMapOf<String, String>()
            val peers = mutableListOf<MutableMap<String, String>>()
            var current: MutableMap<String, String>? = null

            for (rawLine in text.lineSequence()) {
                val line = rawLine.substringBefore('#').trim()
                if (line.isEmpty()) continue
                when {
                    line.equals("[Interface]", ignoreCase = true) -> current = interfaceFields
                    line.equals("[Peer]", ignoreCase = true) -> current = mutableMapOf<String, String>().also(peers::add)
                    '=' in line -> {
                        val section = current ?: throw KeyImportException("The config has a setting outside any section.")
                        section[line.substringBefore('=').trim()] = line.substringAfter('=').trim()
                    }
                }
            }
            if (interfaceFields["PrivateKey"].isNullOrEmpty()) {
                throw KeyImportException("The config has no PrivateKey.")
            }
            if (peers.isEmpty()) throw KeyImportException("The config has no [Peer] section.")
            return WgQuickConfig(interfaceFields, peers)
        }

        fun looksLikeConfig(text: String): Boolean =
            text.contains("[Interface]", ignoreCase = true) && text.contains("[Peer]", ignoreCase = true)

        internal fun parseEndpoint(raw: String): Pair<String, Int> {
            val host: String
            val portText: String
            if (raw.startsWith("[")) {
                host = raw.substringAfter('[').substringBefore(']')
                portText = raw.substringAfter("]:", "")
            } else {
                host = raw.substringBeforeLast(':')
                portText = raw.substringAfterLast(':', "")
            }
            val port = portText.toIntOrNull()
            if (host.isEmpty() || port == null || port !in 1..65535) {
                throw KeyImportException("The server address \"$raw\" is not valid.")
            }
            return host to port
        }
    }
}
