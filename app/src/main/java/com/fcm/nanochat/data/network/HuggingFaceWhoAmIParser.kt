package com.fcm.nanochat.data.network

import org.json.JSONObject

data class HuggingFaceWhoAmI(
    val name: String,
    val fullName: String,
    val email: String?,
    val emailVerified: Boolean,
    val avatarUrl: String?,
    val profileUrl: String?,
    val isPro: Boolean,
    val tokenName: String?,
    val tokenRole: String?
)

object HuggingFaceWhoAmIParser {
    fun parseAccount(body: String): HuggingFaceWhoAmI {
        val root = JSONObject(body)
        val fullName = root.optString("fullname").trim()
        val userName = root.optString("name").trim()
        val resolvedFullName = fullName.ifBlank { userName }.ifBlank { "Unknown account" }
        val email = root.optString("email").trim().takeIf { it.isNotBlank() }
        val emailVerified = root.optBoolean("email_verified", false)
        val avatarUrl = root.optString("avatar_url").trim().takeIf { it.isNotBlank() }
        val profileUrl = root.optString("profile").trim().takeIf { it.isNotBlank() }
        val isPro = root.optBoolean("is_pro", false)

        val auth = root.optJSONObject("auth")
        val accessToken = auth?.optJSONObject("accessToken")
        val tokenName = accessToken?.optString("displayName")?.trim().takeIf { !it.isNullOrBlank() }
        val tokenRole = accessToken?.optString("role")?.trim().takeIf { !it.isNullOrBlank() }

        return HuggingFaceWhoAmI(
            name = userName.ifBlank { "unknown" },
            fullName = resolvedFullName,
            email = email,
            emailVerified = emailVerified,
            avatarUrl = avatarUrl,
            profileUrl = profileUrl,
            isPro = isPro,
            tokenName = tokenName,
            tokenRole = tokenRole
        )
    }

    fun parseError(body: String): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val error = root.optString("error").trim()
        if (error.isNotBlank()) return error

        val message = root.optString("message").trim()
        if (message.isNotBlank()) return message

        return null
    }
}
