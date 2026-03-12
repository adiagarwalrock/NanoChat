package com.fcm.nanochat.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuggingFaceWhoAmIParserTest {

    @Test
    fun `parseAccount prefers fullname and reads email`() {
        val body = """
            {
              "name": "nano_user",
              "fullname": "Nano User",
              "email": "nano@example.com",
              "email_verified": true,
              "avatar_url": "https://avatars/foo.png",
              "profile": "https://huggingface.co/nano_user",
              "is_pro": true,
              "auth": {
                "accessToken": {
                  "displayName": "read key",
                  "role": "read"
                }
              }
            }
        """.trimIndent()

        val account = HuggingFaceWhoAmIParser.parseAccount(body)

        assertEquals("nano_user", account.name)
        assertEquals("Nano User", account.fullName)
        assertEquals("nano@example.com", account.email)
        assertEquals(true, account.emailVerified)
        assertEquals("https://avatars/foo.png", account.avatarUrl)
        assertEquals("https://huggingface.co/nano_user", account.profileUrl)
        assertEquals(true, account.isPro)
        assertEquals("read key", account.tokenName)
        assertEquals("read", account.tokenRole)
    }

    @Test
    fun `parseAccount falls back to username when fullname missing`() {
        val body = """
            {
              "name": "nano_user"
            }
        """.trimIndent()

        val account = HuggingFaceWhoAmIParser.parseAccount(body)

        assertEquals("nano_user", account.name)
        assertEquals("nano_user", account.fullName)
        assertNull(account.email)
        assertEquals(false, account.emailVerified)
        assertNull(account.avatarUrl)
        assertNull(account.profileUrl)
        assertEquals(false, account.isPro)
        assertNull(account.tokenName)
        assertNull(account.tokenRole)
    }

    @Test
    fun `parseError returns error value when present`() {
        val body = """
            {
              "error": "Invalid username or password."
            }
        """.trimIndent()

        val message = HuggingFaceWhoAmIParser.parseError(body)

        assertEquals("Invalid username or password.", message)
    }
}
