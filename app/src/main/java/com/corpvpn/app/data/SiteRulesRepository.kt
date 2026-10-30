package com.corpvpn.app.data

import android.content.Context
import com.corpvpn.core.CheckList
import com.corpvpn.core.SiteRules
import com.corpvpn.core.SplitMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.net.InetAddress

/** Split tunneling by website: the saved list, and turning it into routes at connect time. */
class SiteRulesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("site_rules", Context.MODE_PRIVATE)

    fun load(): SiteRules = try {
        prefs.getString(KEY, null)?.let { Json.decodeFromString(SiteRules.serializer(), it) } ?: SiteRules()
    } catch (e: Exception) {
        SiteRules()
    }

    fun save(rules: SiteRules) {
        prefs.edit().putString(KEY, Json.encodeToString(SiteRules.serializer(), rules)).apply()
    }

    /**
     * Resolves the listed domains (and the check sites) once, before the first server is
     * tried, and returns the step that rewrites a key's config. Domains that don't resolve
     * are skipped.
     */
    suspend fun prepare(checkSites: List<String>): (String) -> String {
        val rules = load()
        if (rules.mode == SplitMode.OFF) return { it }
        val checkHosts = checkSites.map(CheckList::displayName)
        val resolved = resolveAll((rules.domains() + checkHosts).distinct())
        val checkAddresses = checkHosts.flatMap { resolved[it].orEmpty() }
        return { config -> rules.applyTo(config, resolved, checkAddresses) }
    }

    private suspend fun resolveAll(hosts: List<String>): Map<String, List<String>> = coroutineScope {
        hosts.map { host ->
            async(Dispatchers.IO) {
                val addresses = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                    runCatching { InetAddress.getAllByName(host).mapNotNull { it.hostAddress } }.getOrNull()
                }
                host to addresses.orEmpty()
            }
        }.awaitAll().toMap()
    }

    private companion object {
        const val KEY = "rules"
        const val RESOLVE_TIMEOUT_MS = 5_000L
    }
}
