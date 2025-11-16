package ch.heuscher.airescuering.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import ch.heuscher.airescuering.BackHomeAccessibilityService

/**
 * Helper class for taking screenshots using the accessibility service.
 * Provides a simple interface to capture screenshots with automatic fallback behavior.
 */
object ScreenshotHelper {

    private const val TAG = "ScreenshotHelper"

    /**
     * Take a screenshot using the accessibility service.
     * Automatically uses the best method available for the device's Android version:
     * - Android 11+: Uses AccessibilityService.takeScreenshot() with fallback to performGlobalAction
     * - Android 9-10: Uses performGlobalAction (simulates pressing screenshot button)
     *
     * @param context Application context for showing toast messages
     * @param onSuccess Callback invoked when screenshot is captured successfully with file path
     * @param onFailure Callback invoked when screenshot fails with error message
     * @param showToast Whether to show toast messages for success/failure (default: true)
     */
    fun takeScreenshot(
        context: Context,
        onSuccess: (String) -> Unit = {},
        onFailure: (String) -> Unit = {},
        showToast: Boolean = true
    ) {
        val service = BackHomeAccessibilityService.instance

        if (service == null) {
            val errorMsg = "Accessibility service is not enabled. Please enable it in Settings."
            Log.e(TAG, errorMsg)
            if (showToast) {
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            }
            onFailure(errorMsg)
            return
        }

        service.takeScreenshot(
            onSuccess = { path ->
                Log.d(TAG, "Screenshot captured: $path")
                if (showToast) {
                    Toast.makeText(context, "Screenshot saved!", Toast.LENGTH_SHORT).show()
                }
                onSuccess(path)
            },
            onFailure = { error ->
                Log.e(TAG, "Screenshot failed: $error")
                if (showToast) {
                    Toast.makeText(context, "Screenshot failed: $error", Toast.LENGTH_LONG).show()
                }
                onFailure(error)
            }
        )
    }

    /**
     * Check if screenshot functionality is available (i.e., accessibility service is enabled)
     */
    fun isAvailable(): Boolean {
        return BackHomeAccessibilityService.isServiceEnabled()
    }

    /**
     * Get a user-friendly message explaining how to enable screenshot functionality
     */
    fun getEnableInstructions(): String {
        return """
            To enable screenshot functionality:
            1. Go to Settings > Accessibility
            2. Find "AI Rescue Ring" service
            3. Enable the service
            4. Grant the requested permissions
        """.trimIndent()
    }
}
