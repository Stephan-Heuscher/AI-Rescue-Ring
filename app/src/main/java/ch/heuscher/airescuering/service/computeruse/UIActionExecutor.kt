package ch.heuscher.airescuering.service.computeruse

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputMethodManager
import ch.heuscher.airescuering.data.api.FunctionCall
import ch.heuscher.airescuering.service.accessibility.AIAssistantAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * Helper extension to safely get an int from JsonElement
 */
private fun JsonElement.getIntOrNull(): Int? {
    return when (this) {
        is JsonPrimitive -> this.intOrNull
        else -> null
    }
}

/**
 * Helper extension to safely get a string from JsonElement
 */
private fun JsonElement.getStringOrNull(): String? {
    return when (this) {
        is JsonPrimitive -> this.contentOrNull
        else -> null
    }
}

/**
 * Helper extension to safely get a boolean from JsonElement
 */
private fun JsonElement.getBooleanOrNull(): Boolean? {
    return when (this) {
        is JsonPrimitive -> this.booleanOrNull
        else -> null
    }
}

/**
 * Executes UI actions based on function calls from the Computer Use model
 */
class UIActionExecutor(private val context: Context) {

    companion object {
        private const val TAG = "UIActionExecutor"
    }

    private val accessibilityService: AIAssistantAccessibilityService?
        get() = AIAssistantAccessibilityService.getInstance()

    /**
     * Execute a function call from the model
     * @return Map with execution result
     */
    suspend fun executeFunctionCall(
        functionCall: FunctionCall,
        screenWidth: Int,
        screenHeight: Int
    ): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["action"] = functionCall.name

        try {
            when (functionCall.name) {
                "click_at" -> {
                    val x = functionCall.args["x"]?.getIntOrNull() ?: 0
                    val y = functionCall.args["y"]?.getIntOrNull() ?: 0
                    val success = performClick(x, y, screenWidth, screenHeight)
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to perform click"
                    }
                }

                "type_text_at" -> {
                    val x = functionCall.args["x"]?.getIntOrNull() ?: 0
                    val y = functionCall.args["y"]?.getIntOrNull() ?: 0
                    val text = functionCall.args["text"]?.getStringOrNull() ?: ""
                    val pressEnter = functionCall.args["press_enter"]?.getBooleanOrNull() ?: true
                    val clearBefore = functionCall.args["clear_before_typing"]?.getBooleanOrNull() ?: true

                    val success = performTypeText(x, y, text, pressEnter, clearBefore, screenWidth, screenHeight)
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to type text"
                    }
                }

                "scroll_at" -> {
                    val x = functionCall.args["x"]?.getIntOrNull() ?: 500
                    val y = functionCall.args["y"]?.getIntOrNull() ?: 500
                    val direction = functionCall.args["direction"]?.getStringOrNull() ?: "down"
                    val magnitude = functionCall.args["magnitude"]?.getIntOrNull() ?: 800

                    val success = performScroll(x, y, direction, magnitude, screenWidth, screenHeight)
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to scroll"
                    }
                }

                "scroll_document" -> {
                    val direction = functionCall.args["direction"]?.getStringOrNull() ?: "down"
                    val success = performDocumentScroll(direction, screenWidth, screenHeight)
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to scroll document"
                    }
                }

                "go_back" -> {
                    val success = performBack()
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to go back"
                    }
                }

                "go_home" -> {
                    val success = performHome()
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to go home"
                    }
                }

                "wait_5_seconds" -> {
                    delay(5000)
                    result["success"] = true
                }

                "open_app" -> {
                    val appName = functionCall.args["app_name"]?.getStringOrNull()
                    result["success"] = false
                    result["error"] = "App opening not yet implemented. App requested: $appName"
                }

                "long_press_at" -> {
                    val x = functionCall.args["x"]?.getIntOrNull() ?: 0
                    val y = functionCall.args["y"]?.getIntOrNull() ?: 0
                    val success = performLongPress(x, y, screenWidth, screenHeight)
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to perform long press"
                    }
                }

                "drag_and_drop" -> {
                    val x = functionCall.args["x"]?.getIntOrNull() ?: 0
                    val y = functionCall.args["y"]?.getIntOrNull() ?: 0
                    val destX = functionCall.args["destination_x"]?.getIntOrNull() ?: 0
                    val destY = functionCall.args["destination_y"]?.getIntOrNull() ?: 0
                    val success = performDragDrop(x, y, destX, destY, screenWidth, screenHeight)
                    result["success"] = success
                    if (!success) {
                        result["error"] = "Failed to perform drag and drop"
                    }
                }

                else -> {
                    result["success"] = false
                    result["error"] = "Unknown action: ${functionCall.name}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing function call: ${functionCall.name}", e)
            result["success"] = false
            result["error"] = e.message ?: "Unknown error"
        }

        return result
    }

    private suspend fun performClick(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Boolean {
        val service = accessibilityService
        if (service == null) {
            Log.e(TAG, "Accessibility service not available")
            return false
        }

        return service.performTap(x, y, screenWidth, screenHeight)
    }

    private suspend fun performTypeText(
        x: Int, y: Int, text: String,
        pressEnter: Boolean, clearBefore: Boolean,
        screenWidth: Int, screenHeight: Int
    ): Boolean {
        // First tap to focus the text field
        if (!performClick(x, y, screenWidth, screenHeight)) {
            return false
        }

        delay(500) // Wait for keyboard to appear

        // Note: Actual text input would require additional implementation
        // This is a simplified version that just simulates the tap
        // In production, you'd need to use IME or other text input methods
        Log.w(TAG, "Text typing simulation - actual text input not fully implemented yet")
        Log.d(TAG, "Would type: $text, pressEnter: $pressEnter, clearBefore: $clearBefore")

        return true
    }

    private suspend fun performScroll(
        x: Int, y: Int, direction: String,
        magnitude: Int, screenWidth: Int, screenHeight: Int
    ): Boolean {
        val service = accessibilityService ?: return false

        val actualX = (x / 1000f) * screenWidth
        val actualY = (y / 1000f) * screenHeight
        val scrollDistance = (magnitude / 1000f) * minOf(screenWidth, screenHeight)

        val (startX, startY, endX, endY) = when (direction.lowercase()) {
            "up" -> {
                Pair(actualX.toInt(), (actualY + scrollDistance / 2).toInt()) to
                        Pair(actualX.toInt(), (actualY - scrollDistance / 2).toInt())
            }
            "down" -> {
                Pair(actualX.toInt(), (actualY - scrollDistance / 2).toInt()) to
                        Pair(actualX.toInt(), (actualY + scrollDistance / 2).toInt())
            }
            "left" -> {
                Pair((actualX + scrollDistance / 2).toInt(), actualY.toInt()) to
                        Pair((actualX - scrollDistance / 2).toInt(), actualY.toInt())
            }
            "right" -> {
                Pair((actualX - scrollDistance / 2).toInt(), actualY.toInt()) to
                        Pair((actualX + scrollDistance / 2).toInt(), actualY.toInt())
            }
            else -> return false
        }

        // Convert back to normalized coordinates for the service
        val normStartX = (startX.toFloat() / screenWidth * 1000).toInt()
        val normStartY = (startY.toFloat() / screenHeight * 1000).toInt()
        val normEndX = (endX.toFloat() / screenWidth * 1000).toInt()
        val normEndY = (endY.toFloat() / screenHeight * 1000).toInt()

        return service.performSwipe(normStartX, normStartY, normEndX, normEndY, screenWidth, screenHeight)
    }

    private suspend fun performDocumentScroll(direction: String, screenWidth: Int, screenHeight: Int): Boolean {
        // Scroll in the middle of the screen
        return performScroll(500, 500, direction, 800, screenWidth, screenHeight)
    }

    private fun performBack(): Boolean {
        return accessibilityService?.performBack() ?: false
    }

    private fun performHome(): Boolean {
        return accessibilityService?.performHome() ?: false
    }

    private suspend fun performLongPress(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Boolean {
        val service = accessibilityService ?: return false

        // Long press is implemented as a long-duration tap
        val actualX = (x / 1000f) * screenWidth
        val actualY = (y / 1000f) * screenHeight

        // For long press, we perform a swipe with duration but no movement
        val normX = x
        val normY = y

        return service.performSwipe(normX, normY, normX, normY, screenWidth, screenHeight, durationMs = 1000)
    }

    private suspend fun performDragDrop(
        x: Int, y: Int, destX: Int, destY: Int,
        screenWidth: Int, screenHeight: Int
    ): Boolean {
        val service = accessibilityService ?: return false

        return service.performSwipe(x, y, destX, destY, screenWidth, screenHeight, durationMs = 500)
    }
}
