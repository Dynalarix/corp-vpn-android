package com.corpvpn.core

import kotlinx.serialization.Serializable

/** One line of the site list. */
sealed interface SiteEntry {
    data class Domain(val name: String) : SiteEntry
    data class Address(val prefix: IpPrefix) : SiteEntry
    data class Invalid(val line: String) : SiteEntry
}

/**
 * Split tunneling by website: domains, IP addresses and ranges (CIDR), one per line,
 * kept as the text the user typed.
 *
 * Android routes by IP address, so domains are resolved when the VPN connects and the
 * peer's AllowedIPs are rewritten. A site that later moves to other addresses (CDNs do)
 * is only followed on the next connection.
 */
@Serializable
data class SiteRules(
    val mode: SplitMode = SplitMode.OFF,
    val text: String = "",
) {
    val entries: List<SiteEntry> get() = parse(text)

    fun domains(): List<String> = entries.filterIsInstance<SiteEntry.Domain>().map { it.name }

    /**
     * Rewrites the AllowedIPs of every [Peer]. [resolved] maps each domain to the addresses
     * it resolved to; a domain missing from it is skipped. In ONLY mode the config's DNS
     * servers and [alwaysViaVpn] (the check sites' addresses) also stay in the VPN, so names
     * resolve and "Connect to verified" still tests the tunnel.
     */
    fun applyTo(config: String, resolved: Map<String, List<String>>, alwaysViaVpn: List<String> = emptyList()): String {
        if (mode == SplitMode.OFF) return config
        val listed = entries.flatMap { entry ->
            when (entry) {
                is SiteEntry.Address -> listOf(entry.prefix)
                is SiteEntry.Domain -> resolved[entry.name].orEmpty().mapNotNull(IpPrefix::parse)
                is SiteEntry.Invalid -> emptyList()
            }
        }
        val sections = splitSections(config.lines())
        val dns = sections.firstOrNull { it.first().trim().equals("[Interface]", ignoreCase = true) }
            .orEmpty().filter { key(it).equals("DNS", ignoreCase = true) }
            .flatMap { values(it) }.mapNotNull(IpPrefix::parse)
        val keep = listed + dns + alwaysViaVpn.mapNotNull(IpPrefix::parse)

        return sections.flatMap { section ->
            if (!section.first().trim().equals("[Peer]", ignoreCase = true)) return@flatMap section
            val allowedLines = section.indices.filter { key(section[it]).equals(ALLOWED_IPS, ignoreCase = true) }
            if (allowedLines.isEmpty()) return@flatMap section
            val current = allowedLines.flatMap { values(section[it]) }.mapNotNull(IpPrefix::parse)
            val routed = when (mode) {
                SplitMode.BYPASS -> IpPrefix.subtract(current, listed)
                else -> IpPrefix.intersect(current, keep)
            }
            // A peer with nothing routed has no AllowedIPs line at all; an empty value doesn't parse.
            val line = routed.takeIf { it.isNotEmpty() }?.let { "$ALLOWED_IPS = ${it.joinToString(", ")}" }
            section.filterIndexed { i, _ -> i !in allowedLines || i == allowedLines.first() }
                .mapNotNull { if (key(it).equals(ALLOWED_IPS, ignoreCase = true)) line else it }
        }.joinToString("\n")
    }

    companion object {
        private val DOMAIN = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+""")

        /**
         * One entry per line; blank lines and "#" comments are ignored. A pasted URL is
         * cut down to its host, and "*.example.com" is read as "example.com".
         */
        fun parse(text: String): List<SiteEntry> =
            text.lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .map { line ->
                    IpPrefix.parse(line)?.let { return@map SiteEntry.Address(it) }
                    val host = line.substringAfter("://").substringBefore('/').substringBefore(':')
                        .removePrefix("*.").trimStart('.').trimEnd('.').lowercase()
                    // A last label of digits only is a mistyped address like 300.1.1.1, not a domain.
                    val valid = DOMAIN.matches(host) && !host.substringAfterLast('.').all { it.isDigit() }
                    if (valid) SiteEntry.Domain(host) else SiteEntry.Invalid(line)
                }
                .distinct()
                .toList()

        private fun splitSections(lines: List<String>): List<List<String>> {
            val sections = mutableListOf<MutableList<String>>()
            for (line in lines) {
                if (line.trim().startsWith("[") || sections.isEmpty()) sections += mutableListOf<String>()
                sections.last() += line
            }
            return sections
        }

        private fun key(line: String) = line.substringBefore('=').trim()

        private fun values(line: String) =
            line.substringAfter('=', "").substringBefore('#').split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

private const val ALLOWED_IPS = "AllowedIPs"
