package com.corpvpn.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CheckListTest {
    @Test
    fun `combines own lines with downloaded ones without duplicates`() {
        val own = "api.telegram.org\nmy.intranet.local"
        val downloaded = "# from the portal\napi.telegram.org\nhttps://web.whatsapp.com/"
        assertEquals(
            listOf("https://api.telegram.org/", "https://my.intranet.local/", "https://web.whatsapp.com/"),
            CheckList.combine(own, downloaded),
        )
        assertEquals(listOf("https://api.telegram.org/"), CheckList.combine("api.telegram.org", null))
    }

    @Test
    fun `accepts a plain text list`() {
        assertEquals(
            listOf("https://api.telegram.org/", "https://example.com/health"),
            CheckList.validateDownload("api.telegram.org\r\nhttps://example.com/health\r\n"),
        )
    }

    @Test
    fun `rejects web pages, empty files and odd schemes`() {
        assertFailsWith<CheckListException> { CheckList.validateDownload("<!DOCTYPE html><html><body>Login</body></html>") }
        assertFailsWith<CheckListException> { CheckList.validateDownload("# nothing here\n\n") }
        assertFailsWith<CheckListException> { CheckList.validateDownload("ftp://files.example.com/") }
        assertFailsWith<CheckListException> { CheckList.validateDownload("a.example.com\n".repeat(10_000)) }
    }
}
