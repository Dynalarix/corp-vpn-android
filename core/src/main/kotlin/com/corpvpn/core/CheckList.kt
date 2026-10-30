package com.corpvpn.core

/** The sites "Connect to verified" must be able to open through the VPN. */
object CheckList {
    val DEFAULT: List<String> = listOf("https://api.telegram.org/")

    /** A downloaded list larger than this is not a check list. */
    const val MAX_DOWNLOAD_BYTES = 64 * 1024

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

    /** The sites actually checked: the user's own lines plus the ones loaded from the URL. */
    fun combine(manualText: String, downloadedText: String?): List<String> =
        (parse(manualText) + parse(downloadedText.orEmpty())).distinct()

    /**
     * Validates a downloaded list before it replaces the cached one, so a login
     * page or an error page from the server doesn't wipe the list.
     */
    fun validateDownload(body: String): List<String> {
        if (body.length > MAX_DOWNLOAD_BYTES) throw CheckListException("The file is too large for a check list.")
        if (body.contains("<html", ignoreCase = true) || body.contains("<!doctype", ignoreCase = true)) {
            throw CheckListException("The link returned a web page, not a text file with one site per line.")
        }
        val sites = parse(body)
        if (sites.isEmpty()) throw CheckListException("The file has no sites in it.")
        sites.firstOrNull { !it.startsWith("http://") && !it.startsWith("https://") }?.let {
            throw CheckListException("\"$it\" is not an http or https address.")
        }
        return sites
    }

    /** "https://api.telegram.org/" -> "api.telegram.org" */
    fun displayName(url: String): String =
        url.substringAfter("://").substringBefore('/').ifEmpty { url }
}

class CheckListException(message: String) : Exception(message)
