package ch.heuscher.airescuering.service.screencapture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.coroutines.resume

/**
 * Manages screen capture using MediaProjection API for Computer Use
 */
class ScreenCaptureManager(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureManager"
        const val REQUEST_MEDIA_PROJECTION = 1002
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val displayMetrics = DisplayMetrics()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.display
            if (display != null) {
                display.getRealMetrics(displayMetrics)
            } else {
                // Fallback to window manager if display is not available
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        }

        // Ensure we have valid dimensions
        if (displayMetrics.widthPixels == 0 || displayMetrics.heightPixels == 0) {
            Log.w(TAG, "Display metrics not properly initialized, using fallback")
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
        }
    }

    /**
     * Create an intent to request screen capture permission
     */
    fun createScreenCaptureIntent(): Intent {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return projectionManager.createScreenCaptureIntent()
    }

    /**
     * Initialize media projection with the result from the permission request
     */
    fun initializeMediaProjection(resultCode: Int, data: Intent) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        Log.d(TAG, "Media projection initialized")
    }

    /**
     * Capture a screenshot of the device screen
     * @return Base64 encoded PNG image
     */
    suspend fun captureScreen(): String? = suspendCancellableCoroutine { continuation ->
        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "Media projection not initialized")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val density = displayMetrics.densityDpi

        // Validate dimensions
        if (width <= 0 || height <= 0) {
            Log.e(TAG, "Invalid screen dimensions: ${width}x${height}")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        // Create image reader
        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader

        // Create virtual display
        virtualDisplay = projection.createVirtualDisplay(
            "AIAssistantScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null, null
        )

        // Capture image after a short delay to ensure display is ready
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()

                    // Convert to base64 PNG
                    val base64 = bitmapToBase64(bitmap)

                    // Cleanup
                    cleanupCapture()

                    continuation.resume(base64)
                } else {
                    Log.e(TAG, "Failed to acquire image")
                    cleanupCapture()
                    continuation.resume(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screen", e)
                cleanupCapture()
                continuation.resume(null)
            }
        }, 100)

        continuation.invokeOnCancellation {
            cleanupCapture()
        }
    }

    /**
     * Get screen dimensions
     */
    fun getScreenDimensions(): Pair<Int, Int> {
        return Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)
    }

    /**
     * Convert Image to Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // Crop if there's padding
        return if (rowPadding != 0) {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } else {
            bitmap
        }
    }

    /**
     * Convert Bitmap to Base64 encoded PNG
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val bytes = outputStream.toByteArray()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Base64.getEncoder().encodeToString(bytes)
        } else {
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        }
    }

    /**
     * Cleanup capture resources
     */
    private fun cleanupCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /**
     * Release all resources
     */
    fun release() {
        cleanupCapture()
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "Screen capture manager released")
    }
}
