package com.corpvpn.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.corpvpn.core.AppRules
import kotlinx.serialization.json.Json

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

/** Split tunneling by app: the saved rules and the apps the user can pick from. */
class AppRulesRepository(context: Context) {
    private val packageManager = context.packageManager
    private val ownPackage = context.packageName
    private val prefs = context.getSharedPreferences("app_rules", Context.MODE_PRIVATE)

    fun load(): AppRules = try {
        prefs.getString(KEY, null)?.let { Json.decodeFromString(AppRules.serializer(), it) } ?: AppRules()
    } catch (e: Exception) {
        AppRules()
    }

    fun save(rules: AppRules) {
        prefs.edit().putString(KEY, Json.encodeToString(AppRules.serializer(), rules)).apply()
    }

    /** Adds the saved per-app rules to a key's config right before connecting. */
    fun applyTo(config: String): String {
        val rules = load()
        val installed = rules.packages.filterTo(mutableSetOf()) { isInstalled(it) }
        return rules.applyTo(config, ownPackage, installed)
    }

    /** Apps with a launcher icon, sorted by name. This app is left out: it always uses the VPN. */
    fun launchableApps(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { InstalledApp(it.packageName, it.loadLabel(packageManager).toString(), it.loadIcon(packageManager)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val KEY = "rules"
    }
}
