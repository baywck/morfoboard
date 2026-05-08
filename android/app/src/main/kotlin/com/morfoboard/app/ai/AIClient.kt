package com.morfoboard.app.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for AI processing.
 * Phase 1: Direct connection to 9router (no auth required).
 */
class AIClient(
    private val baseUrl: String,
    private val tokenProvider: () -> String? = { null }
) {

    companion object {
        private const val TAG = "AIClient"
        private const val TIMEOUT_SECONDS = 35L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Process text with AI (translate, fix, or custom action).
     */
    suspend fun process(
        action: String,
        text: String,
        targetLanguage: String = "en",
        tone: String = "natural"
    ): Result<AIProcessResponse> = withContext(Dispatchers.IO) {
        try {
            val requestBody = AIProcessRequest(
                action = action,
                text = text,
                targetLanguage = targetLanguage,
                tone = tone
            )

            val jsonBody = gson.toJson(requestBody)
            Log.d(TAG, "Request: action=$action, text=${text.take(30)}...")

            val builder = Request.Builder()
                .url("$baseUrl/api/v1/ai/process")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .addHeader("Content-Type", "application/json")

            // Add Auth token
            tokenProvider()?.let { token ->
                builder.addHeader("Authorization", "Bearer $token")
            }

            val response = client.newCall(builder.build()).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "AI error: ${response.code} - $responseBody")
                return@withContext when (response.code) {
                    401 -> Result.failure(AIException.ServerError("Unauthorized. Please sign in again."))
                    502, 503 -> Result.failure(AIException.AiUnavailable())
                    504 -> Result.failure(AIException.Timeout())
                    else -> Result.failure(AIException.ServerError("AI error (${response.code})"))
                }
            }

            val aiResponse = gson.fromJson(responseBody, AIProcessResponse::class.java)
            
            if (!aiResponse.success) {
                return@withContext Result.failure(AIException.ServerError(aiResponse.message ?: "Server error"))
            }

            Log.d(TAG, "Success: model=${aiResponse.modelUsed}")
            Result.success(aiResponse)

        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            Result.failure(AIException.NetworkError())
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "JSON parse error", e)
            Result.failure(AIException.ServerError("Invalid response from AI"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            Result.failure(AIException.Unknown(e.message ?: "Unknown error"))
        }
    }

    // Overload for AIAction enum
    suspend fun process(
        action: AIAction,
        text: String,
        targetLanguage: String = "en",
        tone: String = "natural"
    ): Result<AIProcessResponse> = process(action.apiValue, text, targetLanguage, tone)

    private fun buildTranslationPrompt(targetLanguage: String, tone: String): String {
        val langName = when (targetLanguage) {
            "id" -> "Indonesian"
            "jv" -> "Javanese"
            "en" -> "English"
            "es" -> "Spanish"
            else -> targetLanguage
        }
        return """You are a translator. Translate the user's text to $langName.

Rules:
- Write in a $tone tone
- Preserve the original meaning
- Use natural, idiomatic expressions
- Return ONLY the translated text, no explanations"""
    }

    private fun buildFixTextPrompt(): String {
        return """You are a text correction assistant. Fix all spelling and grammar errors.

Rules:
- Preserve the original language and tone
- Fix typos and grammar mistakes
- If no errors, return text unchanged
- Return ONLY the corrected text, no explanations"""
    }
}

// 9router response model
data class NineRouterResponse(
    val choices: List<Choice>?,
    val model: String?
)

data class Choice(
    val message: Message?
)

data class Message(
    val content: String?
)
