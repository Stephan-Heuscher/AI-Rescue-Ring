package ch.heuscher.back_home_dot.service.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import ch.heuscher.back_home_dot.R
import ch.heuscher.back_home_dot.domain.model.DotPosition
import ch.heuscher.back_home_dot.domain.model.OverlayMode
import ch.heuscher.back_home_dot.domain.model.OverlaySettings
import ch.heuscher.back_home_dot.util.AppConstants

/**
 * Manages the overlay view creation, positioning, and appearance.
 * Handles the floating dot display.
 */
class OverlayViewManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    companion object {
        private const val TAG = "OverlayViewManager"
    }

    private var floatingView: View? = null
    private var floatingDot: View? = null
    private var floatingDotHalo: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: View.OnTouchListener? = null
    private var fadeAnimator: ValueAnimator? = null
    private var haloAnimator: ValueAnimator? = null
    private val fadeHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    /**
     * Creates and adds the overlay view to the window.
     */
    fun createOverlayView(): View {
        if (floatingView != null) return floatingView!!

        floatingView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null)
        floatingDot = floatingView?.findViewById<View>(R.id.floating_dot)
        floatingDotHalo = floatingView?.findViewById<View>(R.id.floating_dot_halo)

        setupLayoutParams()
        windowManager.addView(floatingView, layoutParams)

        touchListener?.let { listener ->
            floatingView?.setOnTouchListener(listener)
            floatingDot?.setOnTouchListener(listener)
        }

        return floatingView!!
    }

    /**
     * Removes the overlay view from the window.
     */
    fun removeOverlayView() {
        fadeAnimator?.cancel()
        fadeAnimator = null
        haloAnimator?.cancel()
        haloAnimator = null
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }
        fadeRunnable = null
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        floatingDot = null
        floatingDotHalo = null
        layoutParams = null
    }

    /**
     * Updates the overlay appearance based on settings.
     */
    fun updateAppearance(settings: OverlaySettings) {
        showNormalDot(settings)
    }

    /**
     * Updates the position of the overlay view.
     */
    fun updatePosition(position: DotPosition) {
        layoutParams?.let { params ->
            val oldX = params.x
            val oldY = params.y
            params.x = position.x
            params.y = position.y
            floatingView?.let { windowManager.updateViewLayout(it, params) }

            // Log significant position changes
            if (Math.abs(oldX - position.x) > 10 || Math.abs(oldY - position.y) > 10) {
                Log.d(TAG, "updatePosition: LARGE MOVE from ($oldX, $oldY) to (${position.x}, ${position.y})")
            }
        }
    }

    /**
     * Gets the current position of the overlay.
     */
    fun getCurrentPosition(): DotPosition? {
        return layoutParams?.let { params ->
            DotPosition(params.x, params.y)
        }
    }

    /**
     * Sets the visibility of the overlay view.
     */
    fun setVisibility(visibility: Int) {
        floatingView?.visibility = visibility
    }

    /**
     * Fades in the overlay view over the specified duration.
     * Uses manual Handler-based animation to bypass system animator settings.
     */
    fun fadeIn(duration: Long = 300L) {
        floatingView?.apply {
            // Check animator duration scale
            val animatorScale = try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
            } catch (e: Exception) {
                1.0f
            }

            Log.d(TAG, "fadeIn: Starting fade-in, duration=${duration}ms, system animator scale=$animatorScale")

            // Cancel any ongoing animations
            fadeRunnable?.let { fadeHandler.removeCallbacks(it) }

            // Set initial alpha
            alpha = 0f
            visibility = View.VISIBLE

            // Manual animation using Handler - bypasses system animator settings
            val frameIntervalMs = 16L // ~60fps
            val totalFrames = (duration / frameIntervalMs).toInt()
            var currentFrame = 0
            val startTime = System.currentTimeMillis()

            Log.d(TAG, "fadeIn: Starting manual animation, totalFrames=$totalFrames, frameInterval=${frameIntervalMs}ms")

            fadeRunnable = object : Runnable {
                override fun run() {
                    currentFrame++
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                    floatingView?.alpha = progress

                    if (currentFrame % 30 == 0 || progress >= 1f) {
                        Log.d(TAG, "fadeIn: frame=$currentFrame, elapsed=${elapsed}ms, progress=$progress, alpha=${floatingView?.alpha}")
                    }

                    if (progress < 1f) {
                        fadeHandler.postDelayed(this, frameIntervalMs)
                    } else {
                        floatingView?.alpha = 1f
                        Log.d(TAG, "fadeIn: Manual animation completed, total elapsed=${elapsed}ms, final alpha=${floatingView?.alpha}")
                    }
                }
            }

            fadeHandler.post(fadeRunnable!!)
        } ?: Log.w(TAG, "fadeIn: floatingView is null, cannot animate")
    }

    /**
     * Registers a touch listener for gesture detection on overlay elements.
     */
    fun setTouchListener(listener: View.OnTouchListener) {
        touchListener = listener
        floatingView?.setOnTouchListener(listener)
        floatingDot?.setOnTouchListener(listener)
    }

    /**
     * Calculates constrained position within screen bounds.
     * Accounts for button being centered in larger layout.
     */
    fun constrainPositionToBounds(x: Int, y: Int): Pair<Int, Int> {
        val screenSize = getScreenSize()
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val buttonSize = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2

        // Allow layout to position partially off-screen so button can reach edges
        val constrainedX = x.coerceIn(-offset, screenSize.x - buttonSize - offset)
        val constrainedY = y.coerceIn(-offset, screenSize.y - buttonSize - offset)

        // Log constraint details for debugging
        if (x != constrainedX || y != constrainedY) {
            Log.d(TAG, "constrainPositionToBounds: screenSize=${screenSize.x}x${screenSize.y}, layoutSize=$layoutSize, buttonSize=$buttonSize, offset=$offset")
            Log.d(TAG, "constrainPositionToBounds: input=($x,$y) -> output=($constrainedX,$constrainedY)")
            Log.d(TAG, "constrainPositionToBounds: maxX=${screenSize.x - buttonSize - offset}, maxY=${screenSize.y - buttonSize - offset}")
        }

        return Pair(constrainedX, constrainedY)
    }

    private fun setupLayoutParams() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Use fixed layout size to prevent button jump when halo appears/disappears
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()

        layoutParams = WindowManager.LayoutParams(
            layoutSize,
            layoutSize,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun showNormalDot(settings: OverlaySettings) {
        floatingDot?.visibility = View.VISIBLE

        floatingDot?.let { dotView ->
            val drawable = GradientDrawable().apply {
                // Square shape only for SAFE_HOME mode, circle for others
                if (settings.tapBehavior == "SAFE_HOME") {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8f * context.resources.displayMetrics.density
                } else {
                    shape = GradientDrawable.OVAL
                }
                setColor(settings.getColorWithAlpha())
                setStroke(
                    (AppConstants.DOT_STROKE_WIDTH_DP * context.resources.displayMetrics.density).toInt(),
                    android.graphics.Color.WHITE
                )
            }
            dotView.background = drawable
        }
    }

    private fun getScreenSize(): Point {
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            size.x = bounds.width()
            size.y = bounds.height()
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(size)
        }
        return size
    }

    private fun getDotSize(): Int {
        // Return the actual layout size to account for the halo
        return (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }

    /**
     * Shows or hides the halo effect for drag mode.
     */
    fun setDragMode(enabled: Boolean) {
        floatingDotHalo?.let { haloView ->
            if (enabled) {
                // Create halo drawable (72dp, 1.5x button size)
                val drawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 14f * context.resources.displayMetrics.density
                    setColor(android.graphics.Color.argb(80, 255, 255, 255))
                }
                haloView.background = drawable
                haloView.visibility = View.VISIBLE

                // Animate the halo (pulsing effect)
                haloAnimator?.cancel()
                haloAnimator = ValueAnimator.ofFloat(0.3f, 0.7f).apply {
                    duration = 800
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { animator ->
                        haloView.alpha = animator.animatedValue as Float
                    }
                    start()
                }
            } else {
                // Hide halo
                haloAnimator?.cancel()
                haloAnimator = null
                haloView.visibility = View.GONE
            }
        }
    }
}