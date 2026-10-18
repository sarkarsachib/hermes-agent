package com.example.hermesmobile.ui

import android.content.Context
import com.squareup.okhttp.*
import com.example.hermesmobile.R

class ChatRepository(context: Context) {
    private val apiBase = "http://127.0.0.1:8642"
    private val client = OkHttpClient()
    private val sessionId by lazy { java.util.UUID.randomUUID().toString() }

    // Send a chat message through the gateway
    suspend fun sendMessage(text: String): Result {
        val body = """{
            "model": "gpt-4o-mini",
            "messages": [{"role": "user", "content": "$text"}],
            "stream": true
        }"""

        val request = Request.Builder()
            .url("$apiBase/v1/chat/completions")
            .post(
                RequestBody.create(
                    mediaType = com.squareup.okhttp.MediaType.get("application/json"),
                    content = body
                )
            )
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Hermes-Session-Id", sessionId)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body()?.string() ?: "No response body")
                } else {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Result.failure(Exception("Gateway error $${response.code}: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Get the gateway status
    suspend fun getStatus(): Result {
        val request = Request.Builder()
            .url("$apiBase/health")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Gateway OK")
                } else {
                    Result.failure(Exception("Gateway health check failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // List active sessions
    suspend fun listSessions(): Result {
        val request = Request.Builder()
            .url("$apiBase/api/sessions")
            .get()
            .addHeader("X-Hermes-Session-Id", sessionId)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(response.body()?.string() ?: "No sessions")
                } else {
                    Result.failure(Exception("List sessions failed: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// Result type for suspend functions
enum class Result {
    success(String),
    failure(Exception)
}