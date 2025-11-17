package ch.heuscher.airescuering.service.computeruse

import android.content.Context
import android.util.Log
import ch.heuscher.airescuering.AIRescueRingAccessibilityService
import ch.heuscher.airescuering.data.api.*
import ch.heuscher.airescuering.service.screencapture.ScreenCaptureManager
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * Agent that manages the Computer Use agent loop:
 * 1. Capture screen
 * 2. Send to Gemini Computer Use model
 * 3. Parse function calls
 * 4. Execute UI actions
 * 5. Capture new screen and repeat
 */
class ComputerUseAgent(
    private val context: Context,
    private val geminiService: GeminiApiService,
    private val screenCaptureManager: ScreenCaptureManager
) {
    companion object {
        private const val TAG = "ComputerUseAgent"
        private const val MAX_TURNS = 10
        private const val MODEL = "gemini-2.5-computer-use-preview-10-2025"
    }

    private val uiActionExecutor = UIActionExecutor(context)
    private val conversationHistory = mutableListOf<Content>()

    /**
     * Callback for receiving updates during agent execution
     */
    interface AgentCallback {
        fun onThinking(message: String)
        fun onActionExecuted(action: String, success: Boolean)
        fun onCompleted(finalMessage: String)
        fun onError(error: String)
        fun onConfirmationRequired(message: String, onConfirm: () -> Unit, onDeny: () -> Unit)
    }

    /**
     * Start the Computer Use agent loop
     * @param userGoal The user's goal/request
     * @param callback Callback for updates
     */
    suspend fun startAgentLoop(
        userGoal: String,
        callback: AgentCallback
    ) {
        // Check prerequisites
        if (!AIRescueRingAccessibilityService.isEnabled()) {
            callback.onError("Accessibility service is not enabled. Please enable it in Settings.")
            return
        }

        try {
            // Initial setup
            conversationHistory.clear()

            // Capture initial screenshot
            val initialScreenshot = screenCaptureManager.captureScreen()
            if (initialScreenshot == null) {
                callback.onError("Failed to capture screen. Please grant screen capture permission.")
                return
            }

            // Create initial user message with screenshot
            val (screenWidth, screenHeight) = screenCaptureManager.getScreenDimensions()
            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")

            val systemPrompt = buildSystemPrompt()

            conversationHistory.add(
                Content(
                    role = "user",
                    parts = listOf(
                        Part(text = "Screen size: ${screenWidth}x${screenHeight}\n\nUser goal: $userGoal"),
                        Part(inlineData = InlineData(mimeType = "image/png", data = initialScreenshot))
                    )
                )
            )

            callback.onThinking("Analyzing screen and planning actions...")

            // Agent loop
            var turn = 0
            while (turn < MAX_TURNS) {
                turn++
                Log.d(TAG, "--- Turn $turn ---")

                // Generate response from model
                val response = geminiService.generateContentFull(
                    model = MODEL,
                    contents = conversationHistory,
                    systemPrompt = systemPrompt
                )

                if (response.isFailure) {
                    callback.onError("API Error: ${response.exceptionOrNull()?.message}")
                    return
                }

                val geminiResponse = response.getOrNull() ?: return
                val candidate = geminiResponse.candidates.firstOrNull()

                if (candidate == null) {
                    callback.onError("No response from model")
                    return
                }

                // Add model response to history
                conversationHistory.add(candidate.content)

                // Check if there are function calls
                val functionCalls = candidate.content.parts.mapNotNull { it.functionCall }

                if (functionCalls.isEmpty()) {
                    // No more actions - model is done
                    val finalText = candidate.content.parts.mapNotNull { it.text }.joinToString("\n")
                    Log.d(TAG, "Agent completed: $finalText")
                    callback.onCompleted(finalText)
                    return
                }

                // Execute function calls
                callback.onThinking("Executing ${functionCalls.size} action(s)...")

                val functionResponses = mutableListOf<Part>()

                for (functionCall in functionCalls) {
                    Log.d(TAG, "Executing: ${functionCall.name}")

                    // Check for safety decision
                    val safetyDecisionJson = functionCall.args["safety_decision"]
                    val safetyDecision = safetyDecisionJson?.jsonObject
                    if (safetyDecision != null) {
                        val decision = safetyDecision["decision"]?.jsonPrimitive?.contentOrNull
                        val explanation = safetyDecision["explanation"]?.jsonPrimitive?.contentOrNull

                        if (decision == "require_confirmation") {
                            // Pause and ask user for confirmation
                            var userConfirmed = false
                            var userResponded = false

                            callback.onConfirmationRequired(
                                explanation ?: "The AI wants to perform: ${functionCall.name}",
                                onConfirm = {
                                    userConfirmed = true
                                    userResponded = true
                                },
                                onDeny = {
                                    userConfirmed = false
                                    userResponded = true
                                }
                            )

                            // Wait for user response
                            while (!userResponded) {
                                delay(100)
                            }

                            if (!userConfirmed) {
                                callback.onError("User denied action: ${functionCall.name}")
                                return
                            }
                        }
                    }

                    // Execute the action
                    val executionResult = uiActionExecutor.executeFunctionCall(
                        functionCall,
                        screenWidth,
                        screenHeight
                    )

                    val success = executionResult["success"] as? Boolean ?: false
                    callback.onActionExecuted(functionCall.name, success)

                    // Convert execution result to JsonElement map
                    val responseData = buildMap<String, JsonElement> {
                        executionResult.forEach { (key, value) ->
                            put(key, when (value) {
                                is String -> JsonPrimitive(value)
                                is Boolean -> JsonPrimitive(value)
                                is Number -> JsonPrimitive(value)
                                else -> JsonPrimitive(value.toString())
                            })
                        }
                        // Add safety acknowledgement if needed
                        if (safetyDecision != null) {
                            put("safety_acknowledgement", JsonPrimitive("true"))
                        }
                    }

                    // Wait a bit for UI to settle
                    delay(500)

                    // Capture new screenshot
                    val newScreenshot = screenCaptureManager.captureScreen()
                    if (newScreenshot == null) {
                        callback.onError("Failed to capture screen after action")
                        return
                    }

                    // Create function response
                    functionResponses.add(
                        Part(
                            functionResponse = FunctionResponse(
                                name = functionCall.name,
                                response = responseData
                            ),
                            inlineData = InlineData(mimeType = "image/png", data = newScreenshot)
                        )
                    )
                }

                // Add function responses to history
                conversationHistory.add(
                    Content(
                        role = "user",
                        parts = functionResponses
                    )
                )

                callback.onThinking("Processing results...")
            }

            // Max turns reached
            callback.onCompleted("Maximum number of steps reached. Task may be incomplete.")

        } catch (e: Exception) {
            Log.e(TAG, "Error in agent loop", e)
            callback.onError("Error: ${e.message}")
        }
    }

    private fun buildSystemPrompt(): String {
        return """
You are an AI assistant that can see and control an Android device. You are helping the user accomplish a task.

IMPORTANT INSTRUCTIONS:
1. You can see the device screen through screenshots
2. You have UI control functions available: click_at, type_text_at, scroll_at, scroll_document, go_back, go_home, wait_5_seconds, long_press_at, drag_and_drop
3. Coordinates are normalized 0-1000 for both x and y axes
4. When you've completed the user's goal, respond with plain text (no function calls) to indicate completion
5. Be precise with your actions - analyze the screen carefully before acting
6. If you encounter an error, try alternative approaches
7. Always explain what you're about to do before taking action (in text responses alongside function calls)
8. Complete the task EXACTLY as stated - don't make assumptions

COORDINATE SYSTEM:
- X axis: 0 (left) to 1000 (right)
- Y axis: 0 (top) to 1000 (bottom)
- Center of screen: (500, 500)

When providing final answers or when the task is complete, output ONLY text with no function calls.
        """.trimIndent()
    }
}
