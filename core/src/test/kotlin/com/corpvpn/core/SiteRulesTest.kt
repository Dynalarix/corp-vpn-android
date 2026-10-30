package com.corpvpn.core

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SiteRulesTest {
    private val config = """
        [Interface]
        PrivateKey = abc
        Address = 10.8.1.2/32
        DNS = 1.1.1.1, 1.0.0.1

        [Peer]
        PublicKey = def
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = 203.0.113.10:51820
    """.trimIndent()

    private fun allowed(result: String) =
        result.lines().single { it.startsWith("AllowedIPs") }.substringAfter('=').split(',').map { it.trim() }

    @Test
    fun `parses domains, urls, addresses and ranges`() {
        val entries = SiteRules.parse(
            """
            # work sites
            Example.com
            https://mail.example.org/inbox
            *.corp.local.test
            1.2.3.4
            10.0.0.0/8
            2001:db8::/32
            not a site
            300.1.1.1
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                SiteEntry.Domain("example.com"),
                SiteEntry.Domain("mail.example.org"),
                SiteEntry.Domain("corp.local.test"),
                SiteEntry.Address(IpPrefix.parse("1.2.3.4/32")!!),
                SiteEntry.Address(IpPrefix.parse("10.0.0.0/8")!!),
                SiteEntry.Address(IpPrefix.parse("2001:db8::/32")!!),
                SiteEntry.Invalid("not a site"),
                SiteEntry.Invalid("300.1.1.1"),
            ),
            entries,
        )
        assertNull(IpPrefix.parse("1.2.3.4/33"))
    }

    @Test
    fun `off leaves the config alone`() {
        assertEquals(config, SiteRules(SplitMode.OFF, "10.0.0.0/8").applyTo(config, emptyMap()))
    }

    @Test
    fun `bypass takes the listed addresses out of the tunnel`() {
        val rules = SiteRules(SplitMode.BYPASS, "10.0.0.0/8\nbank.example")
        val result = allowed(rules.applyTo(config, mapOf("bank.example" to listOf("198.51.100.7"))))
        val v4 = result.mapNotNull(IpPrefix::parse).filter { !it.isV6 }
        val excluded = listOf("10.0.0.0/8", "198.51.100.7/32").map { IpPrefix.parse(it)!! }

        // Everything except the two ranges, nothing overlapping them.
        val total = v4.fold(BigInteger.ZERO) { sum, p -> sum + p.size }
        assertEquals(BigInteger.ONE.shiftLeft(32) - excluded.fold(BigInteger.ZERO) { s, p -> s + p.size }, total)
        assertFalse(v4.any { p -> excluded.any { p.contains(it) || it.contains(p) } })
        assertEquals(IpPrefix.parse("::/0"), IpPrefix.parse(result.last()))
    }

    @Test
    fun `only routes the listed sites, dns servers and check sites`() {
        val rules = SiteRules(SplitMode.ONLY, "intranet.example\n10.20.0.0/16\n2001:db8::1")
        val result = rules.applyTo(
            config,
            resolved = mapOf("intranet.example" to listOf("192.0.2.5", "192.0.2.5")),
            alwaysViaVpn = listOf("149.154.167.220"),
        )
        assertEquals(
            listOf(
                "10.20.0.0/16", "2001:db8:0:0:0:0:0:1/128", "192.0.2.5/32",
                "1.1.1.1/32", "1.0.0.1/32", "149.154.167.220/32",
            ).sorted(),
            allowed(result).sorted(),
        )
        assert("Endpoint = 203.0.113.10:51820" in result)
    }

    @Test
    fun `only keeps addresses inside what the key already routes`() {
        val split = config.replace("0.0.0.0/0, ::/0", "10.0.0.0/8")
        val result = SiteRules(SplitMode.ONLY, "10.1.0.0/16\n8.8.8.8\n0.0.0.0/0").applyTo(split, emptyMap())
        assertEquals(listOf("10.0.0.0/8"), allowed(result))
    }
}
