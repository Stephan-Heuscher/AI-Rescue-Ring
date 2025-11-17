package ch.heuscher.airescuering

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import ch.heuscher.airescuering.di.ServiceLocator
import ch.heuscher.airescuering.domain.repository.SettingsRepository
import ch.heuscher.airescuering.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Unified accessibility service for system navigation and AI automation
 * Provides:
 * - System navigation (back, home, recents)
 * - Screenshot capture
 * - Keyboard detection
 * - AI automation (tap, swipe, text typing, gestures)
 */
class AIRescueRingAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Cache settings values for synchronous access
    private var recentsTimeout: Long = AppConstants.RECENTS_TIMEOUT_DEFAULT_MS

    // Track current foreground package for home screen detection
    private var currentPackageName: String? = null
    private var launcherPackageName: String? = null

    // Screenshot callback
    var onScreenshotCaptured: ((Bitmap) -> Unit)? = null

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Initialize repository through ServiceLocator
        repository = ServiceLocator.getRepository(this)

        // Observe recentsTimeout changes
        repository.getRecentsTimeout()
            .onEach { timeout ->
                recentsTimeout = timeout
                Log.d(TAG, "Recents timeout updated: $timeout ms")
            }
            .launchIn(serviceScope)

        // Configure service info
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        // Detect launcher package
        launcherPackageName = getLauncherPackageName()
        Log.d(TAG, "Launcher package detected: $launcherPackageName")

        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // Track current foreground package
                    val packageName = it.packageName?.toString()
                    if (packageName != null && packageName != currentPackageName) {
                        currentPackageName = packageName
                        Log.d(TAG, "Window changed to package: $packageName")
                    }
                    detectKeyboardState(it)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Service interrupted
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    /**
     * Detect keyboard state changes and broadcast to overlay service
     */
    private fun detectKeyboardState(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: ""
        val packageName = event.packageName?.toString() ?: ""

        // Check if this is an input method (keyboard) window
        val isKeyboard = className.contains("InputMethod") ||
                        packageName.contains("inputmethod") ||
                        event.isFullScreen // IME windows are often full screen in terms of accessibility

        Log.d(TAG, "Window state changed: class=$className, package=$packageName, isKeyboard=$isKeyboard")

        if (isKeyboard) {
            // Try to estimate keyboard height - this is approximate
            val rootNode = rootInActiveWindow
            val keyboardHeight = if (rootNode != null) {
                // Get screen height and estimate keyboard takes bottom portion
                val displayMetrics = resources.displayMetrics
                val screenHeight = displayMetrics.heightPixels
                (screenHeight * 0.4f).toInt() // Estimate 40% of screen height
            } else {
                800 // Fallback height
            }

            Log.d(TAG, "Keyboard detected, broadcasting height=$keyboardHeight")

            // Send broadcast to overlay service
            val intent = Intent(AppConstants.ACTION_UPDATE_KEYBOARD)
            intent.putExtra("keyboard_visible", true)
            intent.putExtra("keyboard_height", keyboardHeight)
            sendBroadcast(intent)
        }
    }

    /**
     * Perform back navigation action
     */
    fun performBackAction() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Perform home action
     */
    fun performHomeAction() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * Switch to previous app using double-tap recents
     * Uses configurable timeout from settings
     */
    fun performRecentsAction() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }, recentsTimeout)
    }

    /**
     * Open recent apps overview
     */
    fun performRecentsOverviewAction() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    /**
     * Get launcher package name
     */
    private fun getLauncherPackageName(): String? {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
            }
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error getting launcher package", e)
            null
        }
    }

    /**
     * Check if currently on home screen
     */
    fun isOnHomeScreen(): Boolean {
        val onHomeScreen = currentPackageName != null &&
                          launcherPackageName != null &&
                          currentPackageName == launcherPackageName
        Log.d(TAG, "isOnHomeScreen: current=$currentPackageName, launcher=$launcherPackageName, result=$onHomeScreen")
        return onHomeScreen
    }

    /**
     * Take a screenshot using AccessibilityService (Android 11+)
     * Falls back to showing a message on older versions
     */
    fun takeScreenshot() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            takeScreenshotAPI30()
        } else {
            Log.w(TAG, "Screenshot not supported on Android versions below 11")
            // Broadcast a message that screenshots are not supported
            val intent = Intent(AppConstants.ACTION_SCREENSHOT_FAILED)
            intent.putExtra("error", "Screenshot requires Android 11 or higher")
            sendBroadcast(intent)
        }
    }

    /**
     * Take screenshot using Android 11+ API
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotAPI30() {
        try {
            val executor = Executors.newSingleThreadExecutor()

            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )

                            if (bitmap != null) {
                                Log.d(TAG, "Screenshot captured successfully: ${bitmap.width}x${bitmap.height}")

                                // Convert hardware bitmap to software bitmap for processing
                                val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)

                                // Invoke callback
                                Handler(Looper.getMainLooper()).post {
                                    onScreenshotCaptured?.invoke(softwareBitmap)
                                }

                                // Broadcast success
                                val intent = Intent(AppConstants.ACTION_SCREENSHOT_CAPTURED)
                                sendBroadcast(intent)
                            } else {
                                Log.e(TAG, "Failed to create bitmap from hardware buffer")
                                broadcastScreenshotError("Failed to create bitmap")
                            }

                            // Clean up
                            screenshot.hardwareBuffer.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot", e)
                            broadcastScreenshotError(e.message ?: "Unknown error")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                        broadcastScreenshotError("Screenshot failed with error code: $errorCode")
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot", e)
            broadcastScreenshotError(e.message ?: "Unknown error")
        }
    }

    /**
     * Broadcast screenshot error
     */
    private fun broadcastScreenshotError(error: String) {
        val intent = Intent(AppConstants.ACTION_SCREENSHOT_FAILED)
        intent.putExtra("error", error)
        sendBroadcast(intent)
    }

    // ==================== AI AUTOMATION METHODS ====================

    /**
     * Perform a tap at the specified coordinates
     * @param x X coordinate (normalized 0-1000)
     * @param y Y coordinate (normalized 0-1000)
     * @param screenWidth Actual screen width in pixels
     * @param screenHeight Actual screen height in pixels
     */
    suspend fun performTap(x: Int, y: Int, screenWidth: Int, screenHeight: Int): Boolean {
        // Validate coordinates
        if (x < 0 || x > 1000 || y < 0 || y > 1000 || screenWidth <= 0 || screenHeight <= 0) {
            Log.e(TAG, "Invalid tap coordinates: x=$x, y=$y, screen=${screenWidth}x${screenHeight}")
            return false
        }

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
        // Validate coordinates
        if (startX < 0 || startX > 1000 || startY < 0 || startY > 1000 ||
            endX < 0 || endX > 1000 || endY < 0 || endY > 1000 ||
            screenWidth <= 0 || screenHeight <= 0 || durationMs <= 0) {
            Log.e(TAG, "Invalid swipe parameters: start=($startX,$startY), end=($endX,$endY), screen=${screenWidth}x${screenHeight}, duration=${durationMs}ms")
            return false
        }

        // If start and end coordinates are the same, this is a long press
        if (startX == endX && startY == endY) {
            return performLongPress(startX, startY, screenWidth, screenHeight, durationMs)
        }

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
     * Perform a long press at the specified coordinates
     * @param x X coordinate (normalized 0-1000)
     * @param y Y coordinate (normalized 0-1000)
     * @param screenWidth Actual screen width in pixels
     * @param screenHeight Actual screen height in pixels
     * @param durationMs Duration of the long press in milliseconds
     */
    suspend fun performLongPress(
        x: Int, y: Int,
        screenWidth: Int, screenHeight: Int,
        durationMs: Long = 1000
    ): Boolean {
        // Validate coordinates
        if (x < 0 || x > 1000 || y < 0 || y > 1000 ||
            screenWidth <= 0 || screenHeight <= 0 || durationMs <= 0) {
            Log.e(TAG, "Invalid long press parameters: x=$x, y=$y, screen=${screenWidth}x${screenHeight}, duration=${durationMs}ms")
            return false
        }

        val actualX = (x / 1000f) * screenWidth
        val actualY = (y / 1000f) * screenHeight

        Log.d(TAG, "Performing long press at normalized ($x, $y) -> actual ($actualX, $actualY) for ${durationMs}ms")

        val path = Path().apply {
            moveTo(actualX, actualY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return dispatchGestureAsync(gesture)
    }

    /**
     * Type text by sending key events
     * Note: This requires an input field to be focused
     * @param text The text to type
     */
    fun performTypeText(text: String): Boolean {
        if (text.isEmpty()) {
            Log.w(TAG, "Cannot type empty text")
            return false
        }

        Log.d(TAG, "Typing text: $text")

        // Find the currently focused node
        val focusedNode = rootInActiveWindow?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode == null) {
            Log.w(TAG, "No input field is focused. Cannot type text.")
            return false
        }

        // Use AccessibilityNodeInfo.ACTION_SET_TEXT to input text
        val arguments = android.os.Bundle()
        arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)

        val success = focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        focusedNode.recycle()

        if (!success) {
            Log.w(TAG, "Failed to set text on focused node")
        }

        return success
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

            try {
                val dispatched = dispatchGesture(gesture, callback, null)
                if (!dispatched) {
                    Log.e(TAG, "Failed to dispatch gesture - dispatchGesture returned false")
                    continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during gesture dispatch", e)
                continuation.resume(false)
            }
        }

    companion object {
        private const val TAG = "AIRescueRingAccessibilityService"
        
        var instance: AIRescueRingAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null

        fun isEnabled(): Boolean = instance != null
    }
}
