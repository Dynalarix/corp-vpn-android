package com.corpvpn.core

/** The sites "Connect to verified" must be able to open through the VPN. */
object CheckList {
    val DEFAULT: List<String> = listOf("https://api.telegram.org/")

    /**
     * One site per line: a bare host ("api.telegram.org") or a full URL.
     * Blank lines and "#" comments are ignored. Bare hosts are checked over HTTPS.
     */
    fun parse(text: String): List<String> =
        text.lineSequence()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { if ("://" in it) it else "https://$it/" }
            .distinct()
            .toList()

    /** "https://api.telegram.org/" -> "api.telegram.org" */
    fun displayName(url: String): String =
        url.substringAfter("://").substringBefore('/').ifEmpty { url }
}
