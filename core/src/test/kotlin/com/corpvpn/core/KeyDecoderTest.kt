package com.corpvpn.core

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeyDecoderTest {
    private val wgConfig = """
        [Interface]
        PrivateKey = cGFzc3dvcmRwYXNzd29yZHBhc3N3b3JkcGFzc3dvcmQ=
        Address = 10.8.1.2/32
        DNS = ${'$'}PRIMARY_DNS, ${'$'}SECONDARY_DNS

        [Peer]
        PublicKey = c2VydmVyc2VydmVyc2VydmVyc2VydmVyc2VydmVyc2U=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = 203.0.113.10:51820
    """.trimIndent()

    private val awgConfig = wgConfig.replace(
        "Address = 10.8.1.2/32",
        "Address = 10.8.1.2/32\nJc = 4\nJmin = 10\nJmax = 50\nS1 = 86\nS2 = 23\nH1 = 1234567\nH2 = 2345678\nH3 = 3456789\nH4 = 4567890",
    ).replace("51820", "34567")

    private fun amneziaKey(vararg containers: Pair<String, String?>, compress: Boolean = true): String {
        val json = buildJsonObject {
            put("hostName", "203.0.113.10")
            put("description", "Frankfurt")
            put("dns1", "9.9.9.9")
            put("dns2", "149.112.112.112")
            put("containers", buildJsonArray {
                for ((name, config) in containers) {
                    add(buildJsonObject {
                        put("container", name)
                        put(name.removePrefix("amnezia-"), buildJsonObject {
                            if (config != null) {
                                put("last_config", buildJsonObject { put("config", config) }.toString())
                            }
                        })
                    })
                }
            })
        }.toString().toByteArray()

        val payload = if (compress) {
            val zlib = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(json) } }.toByteArray()
            val size = json.size
            byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()) + zlib
        } else {
            json
        }
        return "vpn://" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
    }

    @Test
    fun `decodes compressed amnezia key with both protocols`() {
        val keys = KeyDecoder.decode(amneziaKey("amnezia-awg" to awgConfig, "amnezia-wireguard" to wgConfig))

        assertEquals(2, keys.size)
        assertEquals(Protocol.AMNEZIAWG, keys[0].protocol)
        assertEquals(34567, keys[0].endpointPort)
        assertEquals(Protocol.WIREGUARD, keys[1].protocol)
        assertEquals(51820, keys[1].endpointPort)
        keys.forEach {
            assertEquals("Frankfurt", it.name)
            assertEquals("203.0.113.10", it.endpointHost)
            assertTrue("DNS = 9.9.9.9, 149.112.112.112" in it.config)
        }
    }

    @Test
    fun `decodes uncompressed amnezia key`() {
        val keys = KeyDecoder.decode(amneziaKey("amnezia-wireguard" to wgConfig, compress = false))
        assertEquals(Protocol.WIREGUARD, keys.single().protocol)
    }

    @Test
    fun `skips unsupported containers but reports when nothing is usable`() {
        val keys = KeyDecoder.decode(amneziaKey("amnezia-openvpn" to null, "amnezia-awg" to awgConfig))
        assertEquals(Protocol.AMNEZIAWG, keys.single().protocol)

        val error = assertFailsWith<KeyImportException> {
            KeyDecoder.decode(amneziaKey("amnezia-openvpn" to null, "amnezia-xray" to null))
        }
        assertTrue("openvpn" in error.message!!)
    }

    @Test
    fun `same key imported twice gets the same id`() {
        val key = amneziaKey("amnezia-awg" to awgConfig)
        assertEquals(KeyDecoder.decode(key).single().id, KeyDecoder.decode(key).single().id)
    }

    @Test
    fun `decodes plain wireguard config text`() {
        val key = KeyDecoder.decode(wgConfig).single()
        assertEquals(Protocol.WIREGUARD, key.protocol)
        assertEquals("203.0.113.10", key.name)
    }

    @Test
    fun `rejects garbage with a readable message`() {
        assertFailsWith<KeyImportException> { KeyDecoder.decode("hello") }
        assertFailsWith<KeyImportException> { KeyDecoder.decode("vpn://!!!not-base64!!!") }
        assertFailsWith<KeyImportException> { KeyDecoder.decode("vpn://aGVsbG8gd29ybGQ") }
    }

    @Test
    fun `groups keys for the same server into one row`() {
        val first = KeyDecoder.decode(amneziaKey("amnezia-awg" to awgConfig, "amnezia-wireguard" to wgConfig))
        val other = KeyDecoder.decode(wgConfig.replace("203.0.113.10", "198.51.100.7"))
        val servers = (first + other).groupByServer()

        assertEquals(listOf("203.0.113.10", "198.51.100.7"), servers.map { it.host })
        assertEquals(2, servers[0].keys.size)
    }

    @Test
    fun `parses ipv6 endpoints`() {
        assertEquals("2001:db8::1" to 51820, WgQuickConfig.parseEndpoint("[2001:db8::1]:51820"))
        assertFailsWith<KeyImportException> { WgQuickConfig.parseEndpoint("203.0.113.10") }
    }

    @Test
    fun `plain wireguard header values are not treated as amnezia`() {
        val plain = wgConfig.replace("Address = 10.8.1.2/32", "Address = 10.8.1.2/32\nJc = 0\nH1 = 1\nH2 = 2\nH3 = 3\nH4 = 4")
        assertEquals(Protocol.WIREGUARD, KeyDecoder.decode(plain).single().protocol)
    }
}
