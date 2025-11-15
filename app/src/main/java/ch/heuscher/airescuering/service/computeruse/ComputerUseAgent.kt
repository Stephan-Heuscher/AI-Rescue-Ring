package ch.heuscher.airescuering.service.computeruse

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import ch.heuscher.airescuering.data.api.*
import ch.heuscher.airescuering.service.accessibility.AIAssistantAccessibilityService
import ch.heuscher.airescuering.util.ScreenCaptureManager
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * Agent that manages the Computer Use agent loop:
 * 1. Capture screen via AccessibilityService
 * 2. Send to Gemini Computer Use model
 * 3. Parse function calls
 * 4. Execute UI actions via AccessibilityService
 * 5. Capture new screen and repeat
 */
class ComputerUseAgent(
    private val context: Context,
    private val geminiService: GeminiApiService
) {
    companion object {
        private const val TAG = "ComputerUseAgent"
        private const val MAX_TURNS = 10
        private const val MODEL = "gemini-2.5-computer-use-preview-10-2025"
    }

    private val uiActionExecutor = UIActionExecutor(context)
    private val conversationHistory = mutableListOf<Pair<String, String>>() // role to text
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

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
        if (!AIAssistantAccessibilityService.isEnabled()) {
            callback.onError("Accessibility service is not enabled. Please enable it in Settings.")
            return
        }

        try {
            // Initial setup
            conversationHistory.clear()

            // Get screen dimensions
            val (screenWidth, screenHeight) = getScreenDimensions()
            Log.d(TAG, "Screen dimensions: ${screenWidth}x${screenHeight}")

            // Capture initial screenshot using AccessibilityService
            val initialScreenshot = ScreenCaptureManager.captureScreenAsBase64()
            if (initialScreenshot == null) {
                callback.onError("Failed to capture screen. AccessibilityService may not be enabled.")
                return
            }

            val systemPrompt = buildSystemPrompt()

            // Add initial user message
            conversationHistory.add("user" to "Screen size: ${screenWidth}x${screenHeight}\n\nUser goal: $userGoal")

            callback.onThinking("Analyzing screen and planning actions...")

            // Agent loop
            var turn = 0
            while (turn < MAX_TURNS) {
                turn++
                Log.d(TAG, "--- Turn $turn ---")

                // Generate response from model
                val response = geminiService.generateContent(
                    model = MODEL,
                    messages = conversationHistory,
                    systemPrompt = systemPrompt,
                    useComputerUse = true
                )

                if (response.isFailure) {
                    callback.onError("API Error: ${response.exceptionOrNull()?.message}")
                    return
                }

                val geminiResult = response.getOrNull() ?: return

                // Handle the response
                when {
                    geminiResult.hasText -> {
                        // Model provided text response - task is complete
                        val finalText = geminiResult.text!!
                        Log.d(TAG, "Agent completed: $finalText")
                        callback.onCompleted(finalText)
                        return
                    }
                    geminiResult.hasFunctionCall -> {
                        // Model wants to perform an action
                        val functionCall = geminiResult.functionCall!!
                        Log.d(TAG, "Executing: ${functionCall.name}")

                        // Check for safety decision
                        val safetyDecisionJson = functionCall.args?.get("safety_decision")
                        val safetyDecision = (safetyDecisionJson as? JsonObject)
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

                        // Add model response to conversation
                        conversationHistory.add("model" to "Executed: ${functionCall.name}")

                        // Wait a bit for UI to settle
                        delay(500)

                        // Continue the loop for next action
                        callback.onThinking("Processing results...")
                    }
                    else -> {
                        callback.onError("Unexpected response from model")
                        return
                    }
                }
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

    /**
     * Get the current screen dimensions
     */
    private fun getScreenDimensions(): Pair<Int, Int> {
        val displayMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
}
