package ch.heuscher.airescuering.data.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
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
                    listOf(Tool(computerUse = ComputerUse(
                        environment = Environment.BROWSER,
                        excludedPredefinedFunctions = ExcludedFunctions.MOBILE_EXCLUDED
                    )))
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
                val firstPart = geminiResponse.candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()

                val text = firstPart?.text
                if (text != null) {
                    Result.success(text)
                } else if (firstPart?.functionCall != null) {
                    val functionCall = firstPart.functionCall
                    val actionDescription = "I want to perform action: ${functionCall.name}. However, I can only provide text responses in this mode. Please ask me to describe what you should do instead."
                    Result.success(actionDescription)
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
     * @param screenshot Optional screenshot of the current screen
     * @param context Additional context about the device state
     * @return A detailed plan as text
     */
    suspend fun generateSolutionPlan(
        userRequest: String,
        screenshot: Bitmap? = null,
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
            if (screenshot != null) {
                append("I'm looking at my Android screen. ")
            }
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
<<<<<<< HEAD
     * Generate suggestions for device assistance using Gemini's computer use model.
     *
     * @param userRequest The user's request description
     * @param screenshot Optional screenshot of the current screen
     * @param context Additional context about the device state
     * @return Suggested actions as text
     */
    suspend fun generateAssistanceSuggestion(
        userRequest: String,
        screenshot: Bitmap? = null,
        context: String = ""
    ): Result<String> {
        val systemPrompt = """
You are an AI assistant with computer use capabilities helping users with their Android device.

When provided with a screenshot and user request:
1. Analyze the screenshot to understand what's on screen
2. Understand what the user wants to do
3. Provide clear, actionable steps they can take

Your response should be concise and focus on what can be done right now on their device.
Be helpful and specific about which UI elements to interact with if visible in the screenshot.
        """.trimIndent()

        val userMessage = buildString {
            if (screenshot != null) {
                append("I'm looking at my Android screen. ")
            }
            append("User request: $userRequest")
            if (context.isNotEmpty()) {
                append("\n\nDevice context: $context")
            }
        }

        return if (screenshot != null) {
            generateContentWithImage(
                model = "gemini-2.5-computer-use-preview-10-2025",
                text = userMessage,
                image = screenshot,
                systemPrompt = systemPrompt
            )
        } else {
            generateContent(
                model = "gemini-2.5-computer-use-preview-10-2025",
                messages = listOf("user" to userMessage),
                systemPrompt = systemPrompt
            )
        }
    }

    /**
     * Generate content with both text and image (multimodal).
     *
     * @param model The model to use
     * @param text The text prompt
     * @param image The image (screenshot) to analyze
     * @param systemPrompt Optional system instruction
     * @return Response from the API
     */
    private suspend fun generateContentWithImage(
        model: String,
        text: String,
        image: Bitmap,
        systemPrompt: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Convert bitmap to base64
            val base64Image = bitmapToBase64(image)

            // Create parts with both text and image
            val parts = listOf(
                Part(text = text),
                Part(inlineData = InlineData(
                    mimeType = "image/jpeg",
                    data = base64Image
                ))
            )

            val contents = listOf(
                Content(
                    role = "user",
                    parts = parts
=======
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
>>>>>>> origin/claude/chat-overlay-rescue-button-011hUxVMjXfqKWBg9xBUB5uy
                )
            )

            val request = GeminiRequest(
                contents = contents,
                generationConfig = GenerationConfig(
<<<<<<< HEAD
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
                    listOf(Tool(computerUse = ComputerUse(
                        environment = Environment.BROWSER,
                        excludedPredefinedFunctions = ExcludedFunctions.MOBILE_EXCLUDED
                    )))
                } else {
                    null
                }
            )

            val url = "$BASE_URL/$model:generateContent?key=$apiKey"
=======
                    temperature = 0.7,  // Lower temperature for more focused responses
                    maxOutputTokens = 8192
                ),
                systemInstruction = Content(
                    role = "system",
                    parts = listOf(Part(text = systemPrompt))
                )
            )

            val url = "$BASE_URL/gemini-2.5-pro:generateContent?key=$apiKey"
>>>>>>> origin/claude/chat-overlay-rescue-button-011hUxVMjXfqKWBg9xBUB5uy
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            if (debug) {
<<<<<<< HEAD
                Log.d(TAG, "Request with image: text=$text, imageSize=${image.width}x${image.height}")
=======
                Log.d(TAG, "Request with image: model=gemini-2.5-pro")
>>>>>>> origin/claude/chat-overlay-rescue-button-011hUxVMjXfqKWBg9xBUB5uy
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
<<<<<<< HEAD
                val firstPart = geminiResponse.candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()

                val text = firstPart?.text
                if (text != null) {
                    Result.success(text)
                } else if (firstPart?.functionCall != null) {
                    val functionCall = firstPart.functionCall
                    val actionDescription = when (functionCall.name) {
                        "click_at" -> {
                            val x = functionCall.args["x"]?.toString()?.toIntOrNull() ?: 0
                            val y = functionCall.args["y"]?.toString()?.toIntOrNull() ?: 0
                            "📱 I see you want to interact with the screen. I suggest tapping at position ($x, $y) on your screen. Would you like me to describe what's at that location?"
                        }
                        "scroll" -> "📜 I suggest scrolling the screen to see more content."
                        "type_text" -> {
                            val textToType = functionCall.args["text"]?.toString() ?: ""
                            "⌨️ I suggest typing: \"$textToType\""
                        }
                        else -> "🔧 I detected an action: ${functionCall.name}. Let me know if you'd like me to explain what to do."
                    }
                    Result.success(actionDescription)
=======
                val text = geminiResponse.candidates.firstOrNull()
                    ?.content
                    ?.parts
                    ?.firstOrNull()
                    ?.text

                if (text != null) {
                    Result.success(text)
>>>>>>> origin/claude/chat-overlay-rescue-button-011hUxVMjXfqKWBg9xBUB5uy
                } else {
                    Result.failure(Exception("No text in response"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content with image", e)
            Result.failure(e)
        }
    }
<<<<<<< HEAD

    /**
     * Convert Bitmap to Base64 encoded JPEG
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Compress to JPEG with 85% quality to reduce size
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
=======
>>>>>>> origin/claude/chat-overlay-rescue-button-011hUxVMjXfqKWBg9xBUB5uy
}
