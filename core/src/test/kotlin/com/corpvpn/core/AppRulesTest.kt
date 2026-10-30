package com.corpvpn.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppRulesTest {
    private val config = """
        [Interface]
        PrivateKey = abc
        Address = 10.8.1.2/32
        ExcludedApplications = com.old.rule

        [Peer]
        PublicKey = def
        Endpoint = 203.0.113.10:51820
    """.trimIndent()
    private val own = "com.corpvpn.app"
    private val installed = setOf("org.telegram.messenger", "com.bank.app", own)

    private fun interfaceLines(result: String) =
        result.substringAfter("[Interface]").substringBefore("[Peer]").lines().map { it.trim() }.filter { it.isNotEmpty() }

    @Test
    fun `off keeps the config but drops rules that came with the key`() {
        val result = AppRules(SplitMode.OFF, setOf("com.bank.app")).applyTo(config, own, installed)
        assertFalse("Applications" in result)
        assertTrue("Endpoint = 203.0.113.10:51820" in result)
    }

    @Test
    fun `bypass excludes installed apps but never this app`() {
        val rules = AppRules(SplitMode.BYPASS, setOf("com.bank.app", "not.installed", own))
        assertEquals(
            listOf("ExcludedApplications = com.bank.app", "PrivateKey = abc", "Address = 10.8.1.2/32"),
            interfaceLines(rules.applyTo(config, own, installed)),
        )
    }

    @Test
    fun `bypass with nothing installed adds no rule`() {
        val result = AppRules(SplitMode.BYPASS, setOf("not.installed")).applyTo(config, own, installed)
        assertFalse("Applications" in result)
    }

    @Test
    fun `only always includes this app so site checks go through the tunnel`() {
        val rules = AppRules(SplitMode.ONLY, setOf("org.telegram.messenger"))
        assertEquals(
            "IncludedApplications = org.telegram.messenger, com.corpvpn.app",
            interfaceLines(rules.applyTo(config, own, installed)).first(),
        )
        assertEquals(
            "IncludedApplications = com.corpvpn.app",
            interfaceLines(AppRules(SplitMode.ONLY).applyTo(config, own, installed)).first(),
        )
    }
}
