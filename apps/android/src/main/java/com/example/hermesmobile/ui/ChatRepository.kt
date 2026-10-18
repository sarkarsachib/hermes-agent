package com.example.hermesmobile.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ChatRepository(context: Context) {
    private val apiBase = "http://127.0.0.1:8642"
    private val client = OkHttpClient()
    private val sessionId by lazy { UUID.randomUUID().toString() }

    sealed interface Result {
        data class Success(val text: String) : Result
        data class Failure(val error: Throwable) : Result
    }

    suspend fun sendMessage(text: String): Result = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
            .put("stream", false)
            .toString()
        val body = payload.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$apiBase/v1/chat/completions")
            .post(body)
            .header("X-Hermes-Session-Id", sessionId)
            .build()
        execute(request, "Gateway error")
    }

    suspend fun getStatus(): Result = withContext(Dispatchers.IO) {
        execute(Request.Builder().url("$apiBase/health").get().build(), "Gateway health check failed")
    }

    suspend fun listSessions(): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$apiBase/api/sessions")
            .header("X-Hermes-Session-Id", sessionId)
            .get()
            .build()
        execute(request, "List sessions failed")
    }

    private fun execute(request: Request, errorPrefix: String): Result = try {
        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                Result.Success(responseText.ifBlank { "No response body" })
            } else {
                Result.Failure(IllegalStateException("$errorPrefix ${response.code}: $responseText"))
            }
        }
    } catch (error: Exception) {
        Result.Failure(error)
    }
}
