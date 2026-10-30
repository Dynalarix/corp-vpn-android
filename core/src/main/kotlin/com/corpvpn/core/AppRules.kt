package com.corpvpn.core

import kotlinx.serialization.Serializable

@Serializable
enum class SplitMode {
    /** Every app uses the VPN. */
    OFF,

    /** The listed apps go straight to the internet; everything else uses the VPN. */
    BYPASS,

    /** Only the listed apps use the VPN. */
    ONLY,
}

/** Split tunneling by app, as package names. */
@Serializable
data class AppRules(
    val mode: SplitMode = SplitMode.OFF,
    val packages: Set<String> = emptySet(),
) {
    /**
     * Adds the per-app lines to a wg-quick config's [Interface] section.
     *
     * Packages that aren't installed are dropped (Android refuses to start the VPN
     * for unknown packages). [ownPackage] always stays inside the VPN, because the
     * site checks of "Connect to verified" must run through the tunnel.
     */
    fun applyTo(config: String, ownPackage: String, installed: Set<String>): String {
        val lines = config.lines().filterNot { line ->
            val key = line.substringBefore('=').trim()
            key.equals(EXCLUDED, ignoreCase = true) || key.equals(INCLUDED, ignoreCase = true)
        }
        val chosen = packages.filter { it in installed && it != ownPackage }
        val rule = when (mode) {
            SplitMode.OFF -> null
            SplitMode.BYPASS -> chosen.takeIf { it.isNotEmpty() }?.let { "$EXCLUDED = ${it.joinToString(", ")}" }
            SplitMode.ONLY -> "$INCLUDED = ${(chosen + ownPackage).joinToString(", ")}"
        } ?: return lines.joinToString("\n")

        val header = lines.indexOfFirst { it.trim().equals("[Interface]", ignoreCase = true) }
        if (header < 0) throw KeyImportException("The config has no [Interface] section.")
        return (lines.take(header + 1) + rule + lines.drop(header + 1)).joinToString("\n")
    }

    private companion object {
        const val EXCLUDED = "ExcludedApplications"
        const val INCLUDED = "IncludedApplications"
    }
}
