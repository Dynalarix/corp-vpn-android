package com.corpvpn.app.data

import android.content.Context
import com.corpvpn.core.CheckList

/** The sites "Connect to verified" checks, kept as the text the user edits (one site per line). */
class CheckListRepository(context: Context) {
    private val prefs = context.getSharedPreferences("check_list", Context.MODE_PRIVATE)

    fun text(): String = prefs.getString(KEY_TEXT, null) ?: DEFAULT_TEXT

    fun sites(): List<String> = CheckList.parse(text())

    fun save(text: String) {
        prefs.edit().putString(KEY_TEXT, text).apply()
    }

    private companion object {
        const val KEY_TEXT = "text"
        val DEFAULT_TEXT = CheckList.DEFAULT.joinToString("\n") { CheckList.displayName(it) }
    }
}
