package ch.heuscher.airescuering.service.screenshot

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
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer

/**
 * Manages screenshot capture using MediaProjection API
 */
class ScreenshotCaptureManager(private val context: Context) {

    private val TAG = "ScreenshotCapture"
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Start the media projection with the result from permission activity
     */
    fun startProjection(resultCode: Int, data: Intent) {
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        Log.d(TAG, "MediaProjection started")
    }

    /**
     * Capture a screenshot
     * @param callback Called with the captured bitmap or null if failed
     */
    fun captureScreenshot(callback: (Bitmap?) -> Unit) {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection not initialized. Call startProjection first.")
            callback(null)
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        Log.d(TAG, "Capturing screenshot: ${width}x${height} @ ${density}dpi")

        // Create ImageReader to receive screen capture
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        // Create virtual display
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        // Wait a bit for the display to render, then capture
        handler.postDelayed({
            try {
                val image: Image? = imageReader?.acquireLatestImage()
                if (image != null) {
                    val bitmap = imageToBitmap(image)
                    image.close()
                    callback(bitmap)
                    Log.d(TAG, "Screenshot captured successfully")
                } else {
                    Log.e(TAG, "Failed to acquire image")
                    callback(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing screenshot", e)
                callback(null)
            } finally {
                cleanup()
            }
        }, 100) // Small delay to ensure display is rendered
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
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } else {
            bitmap
        }
    }

    /**
     * Clean up resources after capture
     */
    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /**
     * Stop the media projection and release all resources
     */
    fun stopProjection() {
        cleanup()
        mediaProjection?.stop()
        mediaProjection = null
        Log.d(TAG, "MediaProjection stopped")
    }

    /**
     * Check if projection is active
     */
    fun isProjectionActive(): Boolean {
        return mediaProjection != null
    }

    companion object {
        const val REQUEST_MEDIA_PROJECTION = 1001

        /**
         * Create an intent to request screen capture permission
         */
        fun createPermissionIntent(context: Context): Intent {
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            return mediaProjectionManager.createScreenCaptureIntent()
        }
    }
}
