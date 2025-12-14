package ch.heuscher.airescuering.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import ch.heuscher.airescuering.util.AppConstants

/**
 * Manages the Telestrator indicators (pulsing rings) on the screen.
 * Handles adding/removing views from the WindowManager.
 */
class TelestratorManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    private var indicatorView: TelestratorView? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoRemoveRunnable: Runnable? = null

    /**
     * Show a pulsing indicator at the specified screen coordinates.
     * @param x X screen coordinate
     * @param y Y screen coordinate
     * @param durationMs How long to show the indicator (default 10s). 0 = Indefinite.
     */
    fun showIndicator(x: Int, y: Int, durationMs: Long = 10000) {
        mainHandler.post {
            removeIndicator() // Clear existing

            val view = TelestratorView(context)
            view.setColor(0xFFFF0000.toInt()) // Red by default

            val size = (100 * context.resources.displayMetrics.density).toInt() // 100dp size
            
            // Center the view at x, y
            val paramsX = x - (size / 2)
            val paramsY = y - (size / 2)

            val layoutParams = WindowManager.LayoutParams(
                size,
                size,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
                else 
                    @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // PASS THROUGH TOUCHES
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                this.x = paramsX
                this.y = paramsY
            }

            try {
                windowManager.addView(view, layoutParams)
                indicatorView = view
                
                if (durationMs > 0) {
                    autoRemoveRunnable = Runnable { removeIndicator() }
                    mainHandler.postDelayed(autoRemoveRunnable!!, durationMs)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeIndicator() {
        mainHandler.post {
            autoRemoveRunnable?.let { mainHandler.removeCallbacks(it) }
            autoRemoveRunnable = null
            
            indicatorView?.let { view ->
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            indicatorView = null
        }
    }
}
