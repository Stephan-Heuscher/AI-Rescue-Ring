package ch.heuscher.airescuering.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with the Gemini API.
 * Handles requests to Gemini models with thinking mode enabled.
 */
class GeminiApiService(
    private val apiKey: String,
    private val debug: Boolean = false
) {
    companion object {
        private const val TAG = "GeminiApiService"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val TIMEOUT_SECONDS = 60L
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = debug
    }

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (debug) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        builder.build()
    }

    /**
     * Generate content using the specified Gemini model.
     *
     * @param model The model to use (e.g., "gemini-2.5-computer-use-preview-10-2025")
     * @param messages List of conversation messages
     * @param systemPrompt Optional system instruction
     * @param useComputerUse Whether to enable Computer Use tools
     * @return Response from the API (contains text or function call)
     */
    suspend fun generateContent(
        model: String,
        messages: List<Pair<String, String>>, // role to text
        systemPrompt: String? = null,
        useComputerUse: Boolean = false
    ): Result<GeminiContentResult> = withContext(Dispatchers.IO) {
        try {
            val contents = messages.map { (role, text) ->
                Content(
                    role = role,
                    parts = listOf(Part(text = text))
                )
            }

            val tools = if (useComputerUse) {
                listOf(Tool(computerUse = ComputerUse()))
            } else {
                null
            }

            val request = GeminiRequest(
                contents = contents,
                generationConfig = GenerationConfig(
                    temperature = 1.0,
                    maxOutputTokens = 8192
                ),
                systemInstruction = systemPrompt?.let {
                    Content(
                        role = "system",
                        parts = listOf(Part(text = it))
                    )
                },
                tools = tools
            )

            val url = "$BASE_URL/$model:generateContent?key=$apiKey"
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            if (debug) {
                Log.d(TAG, "Request: $requestBody")
            }

            client.newCall(httpRequest).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (debug) {
                    Log.d(TAG, "Response code: ${response.code}")
                    Log.d(TAG, "Response body: $responseBody")
                }

                if (!response.isSuccessful) {
                    val errorResponse = try {
                        json.decodeFromString<GeminiErrorResponse>(responseBody)
                    } catch (e: Exception) {
                        null
                    }

                    val errorMessage = errorResponse?.error?.message
                        ?: "API request failed with code ${response.code}"

                    Log.e(TAG, "API error: $errorMessage")
                    return@withContext Result.failure(Exception(errorMessage))
                }

                val geminiResponse = json.decodeFromString<GeminiResponse>(responseBody)
                val firstPart = geminiResponse.candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()

                val result = when {
                    firstPart?.text != null -> GeminiContentResult(text = firstPart.text)
                    firstPart?.functionCall != null -> GeminiContentResult(functionCall = firstPart.functionCall)
                    else -> null
                }

                if (result != null) {
                    Result.success(result)
                } else {
                    Result.failure(Exception("No text or function call in response"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            Result.failure(e)
        }
    }

    /**
     * Generate suggestions for device assistance using Gemini's Computer Use model.
     *
     * @param userRequest The user's request description
     * @param context Additional context about the device state
     * @return Response with text or function call for tool use
     */
    suspend fun generateAssistanceSuggestion(
        userRequest: String,
        context: String = ""
    ): Result<GeminiContentResult> {
        val systemPrompt = """
You are an AI assistant with computer use capabilities helping users with their Android device.

When the user asks for help, you can:
1. Provide text responses with guidance and information
2. Suggest specific UI actions using the computer_use tool

When suggesting actions, describe what you want to do and why. The user will approve or deny your suggestions before they are executed.

Be helpful, clear, and explain your reasoning.
        """.trimIndent()

        val userMessage = buildString {
            append(userRequest)
            if (context.isNotEmpty()) {
                append("\n\nContext: $context")
            }
        }

        return generateContent(
            model = "gemini-2.5-computer-use-preview-10-2025",
            messages = listOf("user" to userMessage),
            systemPrompt = systemPrompt,
            useComputerUse = true
        )
    }
}
