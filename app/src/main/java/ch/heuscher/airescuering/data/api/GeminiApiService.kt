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
        isLenient = true
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
     * @param model The model to use (e.g., "gemini-2.0-flash-exp")
     * @param messages List of conversation messages
     * @param systemPrompt Optional system instruction
     * @return Response from the API or null if error
     */
    suspend fun generateContent(
        model: String,
        messages: List<Pair<String, String>>, // role to text
        systemPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contents = messages.map { (role, text) ->
                Content(
                    role = role,
                    parts = listOf(Part(text = text))
                )
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
                tools = if (model.contains("computer-use")) {
                    listOf(Tool(computerUse = ComputerUse(environment = Environment.BROWSER)))
                } else {
                    null
                }
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
                val text = geminiResponse.candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

                if (text != null) {
                    Result.success(text)
                } else {
                    Result.failure(Exception("No text in response"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            Result.failure(e)
        }
    }

    /**
     * Generate content with full conversation support (including function calls/responses)
     * @param model The model to use
     * @param contents Full conversation history
     * @param systemPrompt Optional system instruction
     * @param tools Optional tools to provide to the model
     * @return Full GeminiResponse with function calls if present
     */
    suspend fun generateContentFull(
        model: String,
        contents: List<Content>,
        systemPrompt: String? = null,
        tools: List<Tool>? = null
    ): Result<GeminiResponse> = withContext(Dispatchers.IO) {
        try {
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
                tools = tools ?: if (model.contains("computer-use")) {
                    listOf(Tool(computerUse = ComputerUse(environment = Environment.BROWSER)))
                } else {
                    null
                }
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
                Result.success(geminiResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            Result.failure(e)
        }
    }

    /**
     * Generate suggestions for device assistance using Gemini's thinking mode.
     *
     * @param userRequest The user's request description
     * @param context Additional context about the device state
     * @return Suggested actions as text
     */
    suspend fun generateAssistanceSuggestion(
        userRequest: String,
        context: String = ""
    ): Result<String> {
        val systemPrompt = """
You are an AI assistant helping users with their Android device. You have the ability to suggest actions the user can perform on their device.

When the user asks for help, analyze their request and suggest specific actions they can take.

Your response should be structured as follows:
1. A brief summary of what you understand they want to do
2. Suggested steps or actions
3. Any important notes or warnings

Be concise and actionable. Focus on what can be done right now on their device.
        """.trimIndent()

        val userMessage = buildString {
            append("User request: $userRequest")
            if (context.isNotEmpty()) {
                append("\n\nDevice context: $context")
            }
        }

        return generateContent(
            model = "gemini-2.0-flash-exp",
            messages = listOf("user" to userMessage),
            systemPrompt = systemPrompt
        )
    }
}
