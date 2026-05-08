package com.morfoboard.app.ai

import com.google.gson.annotations.SerializedName

/**
 * Request/response models for the Morfoboard AI API.
 */

data class AIProcessRequest(
    val action: String,
    val text: String,
    @SerializedName("source_language")
    val sourceLanguage: String = "auto",
    @SerializedName("target_language")
    val targetLanguage: String = "en",
    val tone: String = "natural"
)

data class AIProcessResponse(
    val success: Boolean,
    val original: String? = null,
    val result: String? = null,
    val action: String? = null,
    val error: String? = null,
    val message: String? = null,
    @SerializedName("model_used")
    val modelUsed: String? = null,
    @SerializedName("processing_time_ms")
    val processingTimeMs: Long? = null
)

enum class AIAction(val apiValue: String) {
    TRANSLATE("translate"),
    FIX_TEXT("fix_text")
}

sealed class AIException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NetworkError : AIException("No internet connection")
    class RateLimited : AIException("Too many requests. Please wait.")
    class AiUnavailable : AIException("AI service is temporarily unavailable")
    class Timeout : AIException("Request timed out. Please try again.")
    class ServerError(msg: String) : AIException(msg)
    class Unknown(msg: String) : AIException(msg)
}
