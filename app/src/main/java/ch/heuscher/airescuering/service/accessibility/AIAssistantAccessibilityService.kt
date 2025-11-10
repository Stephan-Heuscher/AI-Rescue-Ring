package ch.heuscher.airescuering.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Accessibility service that enables UI automation for the AI assistant.
 * Allows the AI to perform touch gestures, type text, and navigate the device.
 */
class AIAssistantAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AIAccessibilityService"
        private var instance: AIAssistantAccessibilityService? = null

        /**
         * Get the current instance of the accessibility service
         */
        fun getInstance(): AIAssistantAccessibilityService? = instance

        /**
         * Check if the accessibility service is enabled
         */
        fun isEnabled(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "AI Assistant Accessibility Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for automation
    }

    override fun onInterrupt() {
        Log.d(TAG, "AI Assistant Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "AI Assistant Accessibility Service destroyed")
    }

    /**
     * Perform a tap at the specified coordinates
     * @param x X coordinate (normalized 0-1000)
     * @param y Y coordinate (normalized 0-1000)
     * @param screenWidth Actual screen width in pixels
     * @param screenHeight Actual screen height in pixels
     */
    suspend fun performTap(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Boolean {
        val actualX = (x / 1000f) * screenWidth
        val actualY = (y / 1000f) * screenHeight

        Log.d(TAG, "Performing tap at normalized ($x, $y) -> actual ($actualX, $actualY)")

        val path = Path().apply {
            moveTo(actualX, actualY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        return dispatchGestureAsync(gesture)
    }

    /**
     * Perform a swipe gesture
     * @param startX Start X coordinate (normalized 0-1000)
     * @param startY Start Y coordinate (normalized 0-1000)
     * @param endX End X coordinate (normalized 0-1000)
     * @param endY End Y coordinate (normalized 0-1000)
     * @param durationMs Duration of the swipe in milliseconds
     */
    suspend fun performSwipe(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        screenWidth: Int, screenHeight: Int,
        durationMs: Long = 300
    ): Boolean {
        val actualStartX = (startX / 1000f) * screenWidth
        val actualStartY = (startY / 1000f) * screenHeight
        val actualEndX = (endX / 1000f) * screenWidth
        val actualEndY = (endY / 1000f) * screenHeight

        Log.d(TAG, "Performing swipe from ($actualStartX, $actualStartY) to ($actualEndX, $actualEndY)")

        val path = Path().apply {
            moveTo(actualStartX, actualStartY)
            lineTo(actualEndX, actualEndY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return dispatchGestureAsync(gesture)
    }

    /**
     * Perform the back button action
     */
    fun performBack(): Boolean {
        Log.d(TAG, "Performing back action")
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Perform the home button action
     */
    fun performHome(): Boolean {
        Log.d(TAG, "Performing home action")
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Perform the recent apps action
     */
    fun performRecents(): Boolean {
        Log.d(TAG, "Performing recents action")
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Dispatch a gesture and wait for completion
     */
    private suspend fun dispatchGestureAsync(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Gesture completed successfully")
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Gesture cancelled")
                    continuation.resume(false)
                }
            }

            val dispatched = dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                Log.e(TAG, "Failed to dispatch gesture")
                continuation.resume(false)
            }
        }
}
