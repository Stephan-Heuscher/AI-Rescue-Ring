package ch.heuscher.airescuering.util

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import ch.heuscher.airescuering.BackHomeAccessibilityService
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

/**
 * Manager for capturing screenshots using the Accessibility Service
 * This provides a simple, permission-free way to capture the screen for AI assistance
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"
    private const val JPEG_QUALITY = 75 // Balance between quality and size
    private const val MAX_DIMENSION = 1080 // Max width/height to reduce API payload
    private const val MIN_SCREENSHOT_INTERVAL_MS = 1000L // Minimum 1 second between screenshots
    
    private var lastScreenshotTime = 0L

    /**
     * Capture the current screen as a base64-encoded JPEG
     * Returns null if capture fails or is not supported
     */
    suspend fun captureScreenAsBase64(): String? {
        Log.d(TAG, "captureScreenAsBase64: ENTER - Starting capture process")
        
        // Check if we need to wait before capturing
        val timeSinceLastCapture = System.currentTimeMillis() - lastScreenshotTime
        if (timeSinceLastCapture < MIN_SCREENSHOT_INTERVAL_MS) {
            val waitTime = MIN_SCREENSHOT_INTERVAL_MS - timeSinceLastCapture
            Log.d(TAG, "captureScreenAsBase64: Waiting ${waitTime}ms before capture (rate limiting)")
            kotlinx.coroutines.delay(waitTime)
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.w(TAG, "Screen capture requires Android 11 (API 30) or higher, current: ${Build.VERSION.SDK_INT}")
            return null
        }
        Log.d(TAG, "captureScreenAsBase64: Android version OK (${Build.VERSION.SDK_INT})")

        val service = BackHomeAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "captureScreenAsBase64: FAIL - Accessibility service is NULL")
            return null
        }
        Log.d(TAG, "captureScreenAsBase64: Accessibility service available: $service")

        return try {
            val bitmap = captureScreenshot(service)
            if (bitmap == null) {
                Log.e(TAG, "Screenshot capture returned null")
                return null
            }

            // Resize if needed to reduce payload size
            val resizedBitmap = resizeBitmap(bitmap)

            // Convert to JPEG base64
            val base64 = bitmapToBase64(resizedBitmap)

            // Clean up
            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }
            bitmap.recycle()

            lastScreenshotTime = System.currentTimeMillis()
            Log.d(TAG, "Screenshot captured successfully, size: ${base64.length} chars")
            base64
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screenshot", e)
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreenshot(service: AccessibilityService): Bitmap? =
        suspendCancellableCoroutine { continuation ->
            // Use 1 for TAKE_SCREENSHOT_FULLSCREEN (constant only available in API 34+)
            service.takeScreenshot(
                1,
                { it.run() },
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hardwareBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace

                            // Convert HardwareBuffer to Bitmap
                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)

                            // Create a software copy that we can use after HardwareBuffer is closed
                            val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)

                            // Clean up
                            hardwareBuffer.close()

                            continuation.resume(softwareBitmap)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot", e)
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        val errorMessage = when (errorCode) {
                            1 -> "ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR (1): Internal error"
                            2 -> "ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY (2): Invalid display"
                            3 -> "ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT (3): Too soon after last screenshot"
                            4 -> "ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR (4): System busy or transitioning (common during activity launch)"
                            else -> "Unknown error code: $errorCode"
                        }
                        Log.e(TAG, "Screenshot failed: $errorMessage")
                        continuation.resume(null)
                    }
                }
            )
        }

    /**
     * Resize bitmap if it exceeds maximum dimensions
     */
    private fun resizeBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return bitmap
        }

        val scale = if (width > height) {
            MAX_DIMENSION.toFloat() / width
        } else {
            MAX_DIMENSION.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        Log.d(TAG, "Resizing bitmap from ${width}x${height} to ${newWidth}x${newHeight}")
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convert bitmap to base64-encoded JPEG string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
