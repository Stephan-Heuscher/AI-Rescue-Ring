package ch.heuscher.airescuering.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for Gemini API requests and responses
 */

@Serializable
data class GeminiRequest(
    val contents: List<Content>,
    @SerialName("generationConfig")
    val generationConfig: GenerationConfig? = null,
    @SerialName("systemInstruction")
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val role: String,
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null,
    @SerialName("inline_data")
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    @SerialName("mime_type")
    val mimeType: String,
    val data: String  // Base64-encoded image data
)

@Serializable
data class GenerationConfig(
    val temperature: Double = 1.0,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxOutputTokens: Int = 8192,
    @SerialName("responseMimeType")
    val responseMimeType: String = "text/plain"
)

@Serializable
data class GeminiResponse(
    val candidates: List<Candidate>,
    @SerialName("usageMetadata")
    val usageMetadata: UsageMetadata? = null,
    @SerialName("modelVersion")
    val modelVersion: String? = null
)

@Serializable
data class Candidate(
    val content: Content,
    @SerialName("finishReason")
    val finishReason: String? = null,
    @SerialName("avgLogprobs")
    val avgLogprobs: Double? = null
)

@Serializable
data class UsageMetadata(
    @SerialName("promptTokenCount")
    val promptTokenCount: Int,
    @SerialName("candidatesTokenCount")
    val candidatesTokenCount: Int,
    @SerialName("totalTokenCount")
    val totalTokenCount: Int
)

@Serializable
data class GeminiErrorResponse(
    val error: GeminiError
)

@Serializable
data class GeminiError(
    val code: Int,
    val message: String,
    val status: String
)
