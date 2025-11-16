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
     * Generate suggestions for device assistance using Gemini Pro.
     * Optimized for guiding technically challenged users step-by-step.
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
You are a patient, friendly AI rescue assistant helping technically challenged users with their Android device. Your goal is to guide them step-by-step through tasks in the simplest way possible.

IMPORTANT GUIDELINES:
- Use very simple, non-technical language
- Break down tasks into small, clear steps (one action at a time)
- Number each step clearly
- Use emojis to make instructions friendly and visual (📱 👆 ⚙️ etc.)
- Assume the user has little technical knowledge
- Be encouraging and supportive
- After giving steps, ask if they need help with any specific step
- Keep responses concise but complete
- If you need to see their screen to help better, remind them they can take a screenshot with the 📸 button

RESPONSE FORMAT:
1. Brief acknowledgment of what they want to do
2. Step-by-step instructions (numbered, one clear action per step)
3. Encouraging message or question to check if they need more help

EXAMPLE:
"I'll help you connect to WiFi! Here's what to do:

1. 👆 Swipe down from the top of your screen with two fingers
2. 🔍 Look for the WiFi icon (looks like a fan or radio waves)
3. 👆 Tap on the WiFi icon to turn it on
4. 👆 Tap and hold the WiFi icon to see available networks
5. 👆 Select your network and enter the password

Did that work? If you're stuck on any step, just tell me which one! 😊"
        """.trimIndent()

        val userMessage = buildString {
            append("User request: $userRequest")
            if (context.isNotEmpty()) {
                append("\n\nDevice context: $context")
            }
        }

        return generateContent(
            model = "gemini-2.5-pro",
            messages = listOf("user" to userMessage),
            systemPrompt = systemPrompt
        )
    }

    /**
     * Generate assistance with a screenshot for visual context.
     * Uses Gemini Pro's vision capabilities to analyze the screen.
     *
     * @param userRequest The user's request description
     * @param imageBase64 Base64 encoded screenshot image (JPEG)
     * @param context Additional context about the device state
     * @return Suggested actions as text
     */
    suspend fun generateAssistanceWithImage(
        userRequest: String,
        imageBase64: String,
        context: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
You are a patient, friendly AI rescue assistant helping technically challenged users with their Android device. The user has shared a screenshot of their screen.

IMPORTANT GUIDELINES:
- Analyze the screenshot carefully to understand what the user sees
- Use very simple, non-technical language
- Give specific instructions based on what you see in the image
- Point out exact buttons, icons, or text they should look for
- Break down tasks into small, clear steps
- Use emojis to make instructions friendly and visual
- Be encouraging and supportive
- Reference specific things you see on their screen (e.g., "I can see the Settings icon in the top right")

RESPONSE FORMAT:
1. Acknowledge what you see on their screen
2. Specific step-by-step instructions based on the screenshot
3. Encouraging message
        """.trimIndent()

            val userMessage = buildString {
                append("User request: $userRequest")
                if (context.isNotEmpty()) {
                    append("\n\nDevice context: $context")
                }
            }

            // Create content with both text and image
            val contents = listOf(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(text = userMessage),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = imageBase64))
                    )
                )
            )

            val request = GeminiRequest(
                contents = contents,
                generationConfig = GenerationConfig(
                    temperature = 0.7,  // Lower temperature for more focused responses
                    maxOutputTokens = 8192
                ),
                systemInstruction = Content(
                    role = "system",
                    parts = listOf(Part(text = systemPrompt))
                )
            )

            val url = "$BASE_URL/gemini-2.5-pro:generateContent?key=$apiKey"
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            if (debug) {
                Log.d(TAG, "Request with image: model=gemini-2.5-pro")
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
            Log.e(TAG, "Error generating content with image", e)
            Result.failure(e)
        }
    }
}
