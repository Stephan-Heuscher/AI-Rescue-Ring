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
     * @param conversationHistory Previous conversation messages for context
     * @return A detailed plan as text
     */
    suspend fun generateSolutionPlan(
        userRequest: String,
        screenshot: Bitmap? = null,
        context: String = "",
        conversationHistory: List<Content> = emptyList()
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val systemPrompt = """
You are a patient, friendly AI rescue assistant helping technically challenged users with their Android device. Your goal is to guide them step-by-step through tasks in the simplest way possible.

RESCUE HELPER APP CAPABILITIES:
- You can see the user's screen through screenshots
- After completing steps, users can take a new screenshot with the 📸 button to show you their progress
- Users navigate through your steps one at a time using forward ▶ and back ◀ buttons
- The app has a compact floating window that can be positioned at the top or bottom of the screen

IMPORTANT LIMITATIONS:
- You CANNOT help with tasks inside Android System Settings due to Android security restrictions
- If the user needs to change system settings (WiFi, Bluetooth, Display, etc.), you must guide them to open Settings themselves
- Once they're in Settings, they can take a screenshot and you can guide them visually
- Be clear about this limitation upfront if they ask for system-level changes

IMPORTANT GUIDELINES:
- Use very simple, non-technical language
- Break down tasks into small, clear steps (one action at a time)
- Start each step with ### followed by a brief title
- Use emojis to make instructions friendly and visual (📱 👆 ⚙️ etc.)
- Assume the user has little technical knowledge
- Be encouraging and supportive
- Keep each step concise and focused on one action
- After complex steps, remind users they can take a new screenshot with 📸 to show their progress
- If a task requires System Settings, explain you can't access Settings but can guide them visually

RESPONSE FORMAT (VERY IMPORTANT):
- Start each step with ### followed by a brief title
- Follow with the detailed instructions for that step
- Keep each step separate and focused
- Users will navigate through steps one at a time using forward/back buttons
- At the end of multi-step processes, remind them to take a screenshot to confirm completion

EXAMPLE:
"I'll help you connect to WiFi! Here are the steps:

### Open Quick Settings
👆 Swipe down from the top of your screen with two fingers to open Quick Settings.

### Find WiFi Icon
🔍 Look for the WiFi icon (looks like a fan or radio waves). It's usually in the first row of icons.

### Turn on WiFi
👆 Tap on the WiFi icon to turn it on. It should turn blue when active.

### Open WiFi Settings
👆 Tap and hold the WiFi icon for 2 seconds to see available networks.

### Connect to Network
👆 Select your network from the list and enter the password when asked.

### Confirm Connection
✅ Once connected, take a screenshot 📸 so I can confirm everything worked!

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

            // Build contents list with conversation history
            val contents = mutableListOf<Content>()

            // Add conversation history (excluding the last user message which we'll re-add with potential screenshot)
            if (conversationHistory.isNotEmpty()) {
                // Take all but the last message (which is the current user request)
                contents.addAll(conversationHistory.dropLast(1))
            }

            // Add current user message with optional screenshot
            val userParts = mutableListOf<Part>()
            userParts.add(Part(text = userMessage))

            if (screenshot != null) {
                val base64Image = bitmapToBase64(screenshot)
                userParts.add(Part(inlineData = InlineData(
                    mimeType = "image/jpeg",
                    data = base64Image
                )))
            }

            contents.add(Content(
                role = "user",
                parts = userParts
            ))

            val request = GeminiRequest(
                contents = contents,
                generationConfig = GenerationConfig(
                    temperature = 1.0,
                    maxOutputTokens = 8192
                ),
                systemInstruction = Content(
                    role = "system",
                    parts = listOf(Part(text = systemPrompt))
                )
            )

            val url = "$BASE_URL/gemini-flash-latest:generateContent?key=$apiKey"
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            if (debug) {
                Log.d(TAG, "Solution plan request: ${contents.size} messages in conversation")
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
            Log.e(TAG, "Error generating solution plan", e)
            Result.failure(e)
        }
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
- Start each step with ### followed by a brief title
- Use emojis to make instructions friendly and visual
- Be encouraging and supportive
- Reference specific things you see on their screen (e.g., "I can see the Settings icon in the top right")

RESPONSE FORMAT:
- Start each step with ### followed by a brief title
- Follow with the detailed instructions for that step
- Keep each step separate and focused
- Users will navigate through steps one at a time using forward/back buttons
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

            val url = "$BASE_URL/gemini-flash-latest:generateContent?key=$apiKey"
            val requestBody = json.encodeToString(GeminiRequest.serializer(), request)
                .toRequestBody("application/json".toMediaType())

            val httpRequest = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            if (debug) {
                Log.d(TAG, "Request with image: model=gemini-flash-latest")
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

RESPONSE FORMAT:
- Start each step with ### followed by a brief title
- Follow with the detailed instructions for that step
- Keep each step separate and focused
- Users will navigate through steps one at a time using forward/back buttons

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

    /**
     * Generate content using the specified Gemini model with full Content objects.
     * Used for advanced scenarios like computer use with conversation history.
     *
     * @param model The model to use (e.g., "gemini-2.5-computer-use-preview-10-2025")
     * @param contents List of Content objects representing the conversation
     * @param systemPrompt Optional system instruction
     * @return Response from the API
     */
    suspend fun generateContentFull(
        model: String,
        contents: List<Content>,
        systemPrompt: String? = null
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
                Log.d(TAG, "Full content request: model=$model, contents=${contents.size}")
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
            Log.e(TAG, "Error generating full content", e)
            Result.failure(e)
        }
    }

    /**
     * Execute computer use functionality with conversation history and function responses.
     * This is a high-level method that handles the computer use agent loop.
     *
     * @param userRequest The user's request
     * @param approvedPlan Optional approved plan text
     * @param screenshot Optional screenshot bitmap
     * @param conversationHistory Previous conversation contents
     * @param functionResponse Optional function response from previous action
     * @return Response from the API
     */
    suspend fun executeWithComputerUse(
        userRequest: String,
        approvedPlan: String? = null,
        screenshot: Bitmap? = null,
        conversationHistory: List<Content> = emptyList(),
        functionResponse: FunctionResponse? = null
    ): Result<GeminiResponse> = withContext(Dispatchers.IO) {
        try {
            val contents = mutableListOf<Content>()

            // Add conversation history
            contents.addAll(conversationHistory)

            // Create user message with request and optional screenshot
            val userParts = mutableListOf<Part>()

            val requestText = buildString {
                append(userRequest)
                if (approvedPlan != null) {
                    append("\n\nApproved plan: $approvedPlan")
                }
            }
            userParts.add(Part(text = requestText))

            // Add screenshot if provided
            if (screenshot != null) {
                val base64Image = bitmapToBase64(screenshot)
                userParts.add(Part(inlineData = InlineData(
                    mimeType = "image/jpeg",
                    data = base64Image
                )))
            }

            // Add function response if provided
            if (functionResponse != null) {
                contents.add(Content(
                    role = "user",
                    parts = listOf(Part(functionResponse = functionResponse))
                ))
            } else {
                // Add the user message
                contents.add(Content(
                    role = "user",
                    parts = userParts
                ))
            }

            val systemPrompt = """
You are an AI assistant with computer use capabilities helping users with their Android device.

You can perform actions like:
- click_at: Click at specific coordinates (x,y) on screen
- scroll: Scroll in a direction (up, down, left, right)
- type_text: Type text into the focused field
- go_back: Press the back button
- go_home: Press the home button

When you need to perform an action, use the appropriate function call.
When you've completed the task or want to communicate with the user, provide text responses.

Always be helpful and precise in your actions.
            """.trimIndent()

            return@withContext generateContentFull(
                model = "gemini-2.5-computer-use-preview-10-2025",
                contents = contents,
                systemPrompt = systemPrompt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error executing with computer use", e)
            Result.failure(e)
        }
    }
}
