package com.corpvpn.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Inflater

class KeyImportException(message: String) : Exception(message)

/**
 * Turns what the user pastes or opens into [VpnKey]s. Accepts:
 *  - Amnezia `vpn://` keys: base64url of a Qt qCompress'ed (or plain) JSON server
 *    description with one or more protocol "containers".
 *  - Plain WireGuard / AmneziaWG `.conf` text.
 */
object KeyDecoder {
    private const val SCHEME = "vpn://"
    private val json = Json { ignoreUnknownKeys = true }

    fun decode(input: String): List<VpnKey> {
        val text = input.trim()
        return when {
            text.startsWith(SCHEME, ignoreCase = true) -> decodeAmneziaKey(text.substring(SCHEME.length))
            WgQuickConfig.looksLikeConfig(text) -> listOf(keyFromConfig(text, name = null))
            else -> throw KeyImportException("This doesn't look like a vpn:// key or a WireGuard config.")
        }
    }

    private fun decodeAmneziaKey(payload: String): List<VpnKey> {
        val bytes = try {
            Base64.getUrlDecoder().decode(payload.trim().trimEnd('='))
        } catch (e: IllegalArgumentException) {
            throw KeyImportException("The key is damaged (not valid base64). Copy it again from the portal.")
        }
        val jsonText = (qUncompress(bytes) ?: bytes).toString(Charsets.UTF_8)
        val root = try {
            json.parseToJsonElement(jsonText).jsonObject
        } catch (e: Exception) {
            throw KeyImportException("The key is damaged (unreadable contents). Copy it again from the portal.")
        }

        val hostName = root.string("hostName")
        val serverName = root.string("description")?.takeIf { it.isNotBlank() } ?: hostName
        val dns = listOfNotNull(root.string("dns1"), root.string("dns2"))

        val containers = root["containers"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        val keys = mutableListOf<VpnKey>()
        val unsupported = mutableListOf<String>()
        for (container in containers) {
            val containerName = container.string("container") ?: continue
            val config = container.values
                .filterIsInstance<JsonObject>()
                .firstNotNullOfOrNull { protocolConfig(it) }
            if (config == null) {
                unsupported += containerName.removePrefix("amnezia-")
                continue
            }
            keys += keyFromConfig(substituteDns(config, dns), serverName)
        }

        if (keys.isEmpty()) {
            throw KeyImportException(
                if (unsupported.isNotEmpty()) {
                    "This key only has protocols the app doesn't support yet: ${unsupported.joinToString()}."
                } else {
                    "This key has no connection settings in it."
                },
            )
        }
        return keys
    }

    /** Amnezia stores the ready-made wg-quick text in the container's `last_config` JSON string. */
    private fun protocolConfig(protocolObject: JsonObject): String? {
        val lastConfig = protocolObject.string("last_config") ?: return null
        val config = try {
            json.parseToJsonElement(lastConfig).jsonObject.string("config")
        } catch (e: Exception) {
            null
        }
        return config?.takeIf { WgQuickConfig.looksLikeConfig(it) }
    }

    private fun substituteDns(config: String, dns: List<String>): String =
        config.replace("\$PRIMARY_DNS", dns.getOrElse(0) { "1.1.1.1" })
            .replace("\$SECONDARY_DNS", dns.getOrElse(1) { dns.getOrElse(0) { "1.0.0.1" } })

    private fun keyFromConfig(config: String, name: String?): VpnKey {
        val parsed = WgQuickConfig.parse(config)
        val (host, port) = parsed.endpoint()
        return VpnKey(
            id = sha256(config).take(16),
            name = name ?: host,
            protocol = if (parsed.isAmnezia) Protocol.AMNEZIAWG else Protocol.WIREGUARD,
            endpointHost = host,
            endpointPort = port,
            config = config.trim(),
        )
    }

    /** Qt's qUncompress: 4-byte big-endian length followed by a zlib stream. Null if not compressed. */
    internal fun qUncompress(data: ByteArray): ByteArray? {
        if (data.size < 6) return null
        val expected = ((data[0].toInt() and 0xff) shl 24) or ((data[1].toInt() and 0xff) shl 16) or
            ((data[2].toInt() and 0xff) shl 8) or (data[3].toInt() and 0xff)
        if (expected <= 0 || expected > 10_000_000) return null
        val inflater = Inflater()
        return try {
            inflater.setInput(data, 4, data.size - 4)
            val out = ByteArrayOutputStream(expected)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                out.write(buffer, 0, n)
            }
            out.toByteArray()
        } catch (e: DataFormatException) {
            null
        } finally {
            inflater.end()
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.trim().toByteArray())
            .joinToString("") { "%02x".format(it) }
}
