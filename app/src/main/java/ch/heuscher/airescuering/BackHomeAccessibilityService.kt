package ch.heuscher.airescuering

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Accessibility service for performing system navigation actions
 */
class BackHomeAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var repository: SettingsRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Cache settings values for synchronous access
    private var recentsTimeout: Long = AppConstants.RECENTS_TIMEOUT_DEFAULT_MS

    // Track current foreground package for home screen detection
    private var currentPackageName: String? = null
    private var launcherPackageName: String? = null

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
     * Take a screenshot using the best available method for this Android version.
     * For Android 11+, uses the modern takeScreenshot API with fallback to performGlobalAction.
     * For older versions, uses performGlobalAction directly.
     *
     * @param onSuccess Callback with the screenshot file path
     * @param onFailure Callback with error message
     */
    fun takeScreenshot(
        onSuccess: (String) -> Unit = {},
        onFailure: (String) -> Unit = {}
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use modern API for Android 11+
            takeScreenshotModern(onSuccess, onFailure)
        } else {
            // Fall back to system default for older versions
            takeScreenshotLegacy(onSuccess, onFailure)
        }
    }

    /**
     * Modern screenshot method using AccessibilityService.takeScreenshot() API (Android 11+)
     * Falls back to performGlobalAction if this fails
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun takeScreenshotModern(
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Log.d(TAG, "Taking screenshot using modern API (Android 11+)")

        try {
            super.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                { r -> Thread(r).start() },
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshot: ScreenshotResult) {
                        try {
                            Log.d(TAG, "Screenshot captured successfully via modern API")

                            // Convert HardwareBuffer to Bitmap
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshot.colorSpace)
                                ?: throw IllegalStateException("Failed to wrap hardware buffer")

                            // Save bitmap to file
                            val filePath = saveBitmapToFile(bitmap)

                            // Clean up
                            bitmap.recycle()
                            hardwareBuffer.close()

                            onSuccess(filePath)
                            Log.d(TAG, "Screenshot saved to: $filePath")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot from modern API", e)
                            onFailure("Failed to process screenshot: ${e.message}")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "Modern API failed with code $errorCode, falling back to system default")
                        // Fall back to the legacy method
                        takeScreenshotLegacy(onSuccess, onFailure)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling modern screenshot API, falling back", e)
            takeScreenshotLegacy(onSuccess, onFailure)
        }
    }

    /**
     * Legacy screenshot method that simulates pressing the physical screenshot button.
     * This method works on Android 9+ and uses the device's native screenshot functionality.
     */
    private fun takeScreenshotLegacy(
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Log.d(TAG, "Taking screenshot using legacy method (performGlobalAction)")

        try {
            val success = performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)

            if (success) {
                Log.d(TAG, "Screenshot action triggered successfully")
                // Note: With performGlobalAction, we can't get the file path directly
                // The system handles the screenshot and saves it to the default location
                onSuccess("Screenshot saved to system default location")
            } else {
                Log.e(TAG, "Failed to trigger screenshot action")
                onFailure("Failed to trigger screenshot. Ensure accessibility service is properly enabled.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception triggering screenshot action", e)
            onFailure("Exception: ${e.message}")
        }
    }

    /**
     * Save a bitmap to the screenshots directory
     * @return The file path where the screenshot was saved
     */
    private fun saveBitmapToFile(bitmap: Bitmap): String {
        // Create screenshots directory in app's external files directory
        val screenshotsDir = File(getExternalFilesDir(null), "screenshots").apply {
            if (!exists()) {
                mkdirs()
            }
        }

        // Generate filename with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "screenshot_$timestamp.png"
        val file = File(screenshotsDir, filename)

        // Save bitmap to file
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file.absolutePath
    }

    companion object {
        private const val TAG = "BackHomeAccessibilityService"
        var instance: BackHomeAccessibilityService? = null
            private set

        fun isServiceEnabled(): Boolean = instance != null
    }
}
