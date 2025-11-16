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
                Result.success(geminiResponse)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content", e)
            Result.failure(e)
        }
    }

    /**
     * Generate a solution plan using Gemini 2.5 Pro (planning stage).
     * This is the first stage where we analyze the screenshot and user request,
     * then generate a step-by-step plan for the user to approve.
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
You are an AI assistant analyzing an Android screen to help the user accomplish their task.

Your job is to:
1. Carefully analyze the screenshot to understand what's currently on screen
2. Understand what the user wants to accomplish
3. Create a clear, step-by-step plan to accomplish their goal

Format your response as a numbered plan with:
- Brief summary of what you see on screen
- Clear steps to accomplish the user's goal
- Any important notes or warnings

Be specific about UI elements visible in the screenshot.
Keep the plan concise but actionable.
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
                model = "gemini-2.0-flash-thinking-exp-01-21",
                text = userMessage,
                image = screenshot,
                systemPrompt = systemPrompt
            )
        } else {
            generateContent(
                model = "gemini-2.0-flash-thinking-exp-01-21",
                messages = listOf("user" to userMessage),
                systemPrompt = systemPrompt
            )
        }
    }

    /**
     * Execute the approved plan using Gemini's computer use model (execution stage).
     * This is the second stage where the computer use model interacts with the screen
     * based on the approved plan.
     *
     * @param userRequest The original user request
     * @param approvedPlan The plan that was approved by the user
     * @param screenshot The screenshot of the current screen
     * @param conversationHistory Previous interactions for multi-round execution
     * @param functionResponse Optional function response to include in the same turn as screenshot
     * @return GeminiResponse with potential function calls
     */
    suspend fun executeWithComputerUse(
        userRequest: String,
        approvedPlan: String,
        screenshot: Bitmap,
        conversationHistory: List<Content> = emptyList(),
        functionResponse: FunctionResponse? = null
    ): Result<GeminiResponse> {
        val systemPrompt = """
You are an AI assistant with computer use capabilities to help users interact with their Android device.

You have been given an approved plan. Your job is to execute this plan by:
1. Analyzing the current screenshot
2. Following the approved plan step-by-step
3. Using the available computer use functions to interact with the screen

Available functions:
- click_at(x, y): Click at specific coordinates
- scroll(direction): Scroll in a direction (up/down/left/right)
- type_text(text): Type text into the current input field

Be careful and precise with your actions. Only perform actions specified in the approved plan.
        """.trimIndent()

        // Build conversation with history
        val contents = conversationHistory.toMutableList()

        // Build parts for the user turn
        val parts = mutableListOf<Part>()

        // Add function response if this is a continuation (round 2+)
        if (functionResponse != null) {
            parts.add(Part(functionResponse = functionResponse))
        } else {
            // First round - add the plan and request
            val userMessage = buildString {
                append("Original request: $userRequest\n\n")
                append("Approved plan:\n$approvedPlan\n\n")
                append("Please execute this plan step by step. I'm looking at my Android screen now.")
            }
            parts.add(Part(text = userMessage))
        }

        // Always add screenshot
        val base64Image = bitmapToBase64(screenshot)
        parts.add(Part(inlineData = InlineData(
            mimeType = "image/jpeg",
            data = base64Image
        )))

        // Add user turn with function response (if any) + screenshot
        contents.add(Content(role = "user", parts = parts))

        return generateContentFull(
            model = "gemini-2.5-computer-use-preview-10-2025",
            contents = contents,
            systemPrompt = systemPrompt,
            tools = listOf(Tool(computerUse = ComputerUse(
                environment = Environment.BROWSER,
                excludedPredefinedFunctions = ExcludedFunctions.MOBILE_EXCLUDED
            )))
        )
    }

    /**
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
                )
            )

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
                Log.d(TAG, "Request with image: text=$text, imageSize=${image.width}x${image.height}")
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
                } else {
                    Result.failure(Exception("No text in response"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating content with image", e)
            Result.failure(e)
        }
    }

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
}
