package ch.heuscher.airescuering.service.overlay

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
import ch.heuscher.airescuering.R
import ch.heuscher.airescuering.domain.model.DotPosition
import ch.heuscher.airescuering.domain.model.OverlayMode
import ch.heuscher.airescuering.domain.model.OverlaySettings
import ch.heuscher.airescuering.util.AppConstants

/**
 * Navigation bar position on screen
 */
enum class NavBarPosition {
    BOTTOM, LEFT, RIGHT, NONE
}

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
        private const val NAV_TAG = "NavBarDebug"  // Easy to filter: adb logcat -s NavBarDebug:D
    }

    private var floatingView: View? = null
    private var floatingDot: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: View.OnTouchListener? = null
    private var fadeAnimator: ValueAnimator? = null
    private val fadeHandler = Handler(Looper.getMainLooper())
    private var fadeRunnable: Runnable? = null

    // Cache nav bar height, position, and log only once
    private var cachedNavBarHeight: Int? = null
    private var cachedNavBarPosition: NavBarPosition = NavBarPosition.NONE
    private var hasLoggedNavBar = false

    /**
     * Creates and adds the overlay view to the window.
     */
    fun createOverlayView(): View {
        if (floatingView != null) return floatingView!!

        floatingView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null)
        floatingDot = floatingView?.findViewById<View>(R.id.floating_dot)

        // Listen for insets to get accurate nav bar height
        floatingView?.setOnApplyWindowInsetsListener { view, insets ->
            // Clear cache so next call to getNavigationBarHeight recalculates
            cachedNavBarHeight = null
            cachedNavBarPosition = NavBarPosition.NONE
            hasLoggedNavBar = false
            // Trigger recalculation by calling getNavigationBarMargin
            getNavigationBarMargin()
            insets
        }

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
        fadeRunnable?.let { fadeHandler.removeCallbacks(it) }
        fadeRunnable = null
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        floatingDot = null
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
     * Get the navigation bar margin (actual nav bar height + safety margin)
     */
    fun getNavigationBarMargin(): Int {
        val density = context.resources.displayMetrics.density
        val detectedHeightPx = getNavigationBarHeight()

        // If WindowInsets returns 0 (transparent/gesture nav), use safe minimum from constants
        val minNavBarHeightPx = (AppConstants.NAV_BAR_MIN_HEIGHT_DP * density).toInt()
        val navBarHeightPx = if (detectedHeightPx == 0) minNavBarHeightPx else detectedHeightPx

        val safetyMarginPx = (AppConstants.NAV_BAR_SAFETY_MARGIN_DP * density).toInt()
        val totalMarginPx = navBarHeightPx + safetyMarginPx

        // Log only once (in dp for readability)
        if (!hasLoggedNavBar) {
            val navBarHeightDp = (navBarHeightPx / density).toInt()
            if (detectedHeightPx == 0) {
                Log.d(NAV_TAG, "NavBar: 0dp detected (transparent/gesture) → Using safe minimum: ${AppConstants.NAV_BAR_MIN_HEIGHT_DP}dp at ${cachedNavBarPosition.name.lowercase()}")
            }
            Log.d(NAV_TAG, "NavBar: ${navBarHeightDp}dp + Safety: ${AppConstants.NAV_BAR_SAFETY_MARGIN_DP}dp = Total: ${(totalMarginPx / density).toInt()}dp (position: ${cachedNavBarPosition.name.lowercase()})")
            hasLoggedNavBar = true
        }

        return totalMarginPx
    }

    /**
     * Calculates constrained position within screen bounds.
     * Accounts for button being centered in larger layout.
     * Adds virtual border at navigation bar to prevent overlap.
     */
    fun constrainPositionToBounds(x: Int, y: Int): Pair<Int, Int> {
        val screenSize = getScreenSize()
        val layoutSize = (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val buttonSize = (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density).toInt()
        val offset = (layoutSize - buttonSize) / 2

        // Get navigation bar margin (actual height + safety margin)
        val navBarMargin = getNavigationBarMargin()

        // Apply margin based on nav bar position
        val minX: Int
        val maxX: Int
        val minY: Int
        val maxY: Int

        when (cachedNavBarPosition) {
            NavBarPosition.BOTTOM -> {
                // Nav bar at bottom - constrain bottom edge
                minX = -offset
                maxX = screenSize.x - buttonSize - offset
                minY = -offset
                maxY = screenSize.y - buttonSize - offset - navBarMargin
            }
            NavBarPosition.LEFT -> {
                // Nav bar on left - constrain left edge
                minX = -offset + navBarMargin
                maxX = screenSize.x - buttonSize - offset
                minY = -offset
                maxY = screenSize.y - buttonSize - offset
            }
            NavBarPosition.RIGHT -> {
                // Nav bar on right - constrain right edge
                minX = -offset
                maxX = screenSize.x - buttonSize - offset - navBarMargin
                minY = -offset
                maxY = screenSize.y - buttonSize - offset
            }
            NavBarPosition.NONE -> {
                // Should not happen (we guess from rotation now), but fallback to bottom
                minX = -offset
                maxX = screenSize.x - buttonSize - offset
                minY = -offset
                maxY = screenSize.y - buttonSize - offset - navBarMargin
            }
        }

        val constrainedX = x.coerceIn(minX, maxX)
        val constrainedY = y.coerceIn(minY, maxY)

        // Log only when position was actually constrained (changed)
        if (x != constrainedX || y != constrainedY) {
            Log.d(NAV_TAG, "constrainPositionToBounds: Position constrained! input=($x,$y) → output=($constrainedX,$constrainedY) | NavBar at ${cachedNavBarPosition.name.lowercase()} | bounds=[x:$minX..$maxX, y:$minY..$maxY]")
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
        // Emoji is already set in XML, nothing else to configure
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

    /**
     * Get current screen rotation
     */
    private fun getCurrentRotation(): Int {
        // Use windowManager.defaultDisplay for all versions since Service context
        // is not associated with a display (would crash on Android R+)
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay.rotation
    }

    /**
     * Guess nav bar position based on screen rotation (for transparent/gesture nav bars)
     */
    private fun guessNavBarPositionFromRotation(rotation: Int): NavBarPosition {
        return when (rotation) {
            android.view.Surface.ROTATION_0 -> NavBarPosition.BOTTOM // Portrait
            android.view.Surface.ROTATION_90 -> NavBarPosition.RIGHT // Landscape (rotated left)
            android.view.Surface.ROTATION_180 -> NavBarPosition.BOTTOM // Portrait upside down
            android.view.Surface.ROTATION_270 -> NavBarPosition.LEFT // Landscape (rotated right)
            else -> NavBarPosition.BOTTOM
        }
    }

    /**
     * Calculate the navigation bar height using WindowInsets
     * This gets the ACTUAL current nav bar height, not a default value
     * Handles both portrait (bottom) and landscape (side) nav bars
     * Returns height in pixels (px)
     */
    private fun getNavigationBarHeight(): Int {
        // Return cached value if available
        cachedNavBarHeight?.let { return it }

        val density = context.resources.displayMetrics.density

        // Try to get nav bar from window insets (most accurate)
        floatingView?.let { view ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insets = view.rootWindowInsets
                if (insets != null) {
                    val navBarInsets = insets.getInsets(android.view.WindowInsets.Type.navigationBars())

                    // Check all sides
                    val bottomPx = navBarInsets.bottom
                    val leftPx = navBarInsets.left
                    val rightPx = navBarInsets.right

                    // Determine position based on which side has the largest inset
                    val navBarHeightPx: Int
                    when {
                        bottomPx > 0 && bottomPx >= leftPx && bottomPx >= rightPx -> {
                            cachedNavBarPosition = NavBarPosition.BOTTOM
                            navBarHeightPx = bottomPx
                        }
                        leftPx > 0 && leftPx >= bottomPx && leftPx >= rightPx -> {
                            cachedNavBarPosition = NavBarPosition.LEFT
                            navBarHeightPx = leftPx
                        }
                        rightPx > 0 && rightPx >= bottomPx && rightPx >= leftPx -> {
                            cachedNavBarPosition = NavBarPosition.RIGHT
                            navBarHeightPx = rightPx
                        }
                        else -> {
                            // All insets are 0 - transparent/gesture nav bar
                            // Guess position based on rotation
                            val rotation = getCurrentRotation()
                            cachedNavBarPosition = guessNavBarPositionFromRotation(rotation)
                            navBarHeightPx = 0
                            Log.d(NAV_TAG, "WindowInsets API: All 0dp (transparent/gesture nav), rotation=$rotation → Guessed position: ${cachedNavBarPosition.name.lowercase()}")
                        }
                    }

                    if (navBarHeightPx > 0) {
                        val navBarHeightDp = (navBarHeightPx / density).toInt()
                        Log.d(NAV_TAG, "WindowInsets API: ${navBarHeightDp}dp (${navBarHeightPx}px) at ${cachedNavBarPosition.name.lowercase()} (bottom=${(bottomPx/density).toInt()}dp, left=${(leftPx/density).toInt()}dp, right=${(rightPx/density).toInt()}dp)")
                    }

                    cachedNavBarHeight = navBarHeightPx
                    return navBarHeightPx
                }
            } else {
                // For older Android versions, use rootWindowInsets
                @Suppress("DEPRECATION")
                val insets = view.rootWindowInsets
                if (insets != null) {
                    @Suppress("DEPRECATION")
                    val navBarHeightPx = insets.systemWindowInsetBottom
                    val navBarHeightDp = (navBarHeightPx / density).toInt()

                    if (navBarHeightPx > 0) {
                        cachedNavBarPosition = NavBarPosition.BOTTOM
                    } else {
                        // Guess from rotation for transparent nav
                        val rotation = getCurrentRotation()
                        cachedNavBarPosition = guessNavBarPositionFromRotation(rotation)
                        Log.d(NAV_TAG, "Legacy WindowInsets: 0dp (transparent/gesture nav), rotation=$rotation → Guessed position: ${cachedNavBarPosition.name.lowercase()}")
                    }

                    Log.d(NAV_TAG, "Legacy WindowInsets: ${navBarHeightDp}dp (${navBarHeightPx}px) at ${cachedNavBarPosition.name.lowercase()}")
                    cachedNavBarHeight = navBarHeightPx
                    return navBarHeightPx
                }
            }
        }

        // Fallback: use system resources
        val resourceId = context.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarHeightPx = if (resourceId > 0) {
            context.resources.getDimensionPixelSize(resourceId)
        } else {
            0
        }

        val navBarHeightDp = (navBarHeightPx / density).toInt()

        if (navBarHeightPx > 0) {
            cachedNavBarPosition = NavBarPosition.BOTTOM
        } else {
            // Guess from rotation for transparent nav
            val rotation = getCurrentRotation()
            cachedNavBarPosition = guessNavBarPositionFromRotation(rotation)
            Log.d(NAV_TAG, "Fallback resources: 0dp (transparent/gesture nav), rotation=$rotation → Guessed position: ${cachedNavBarPosition.name.lowercase()}")
        }

        Log.d(NAV_TAG, "Fallback resources: ${navBarHeightDp}dp (${navBarHeightPx}px) at ${cachedNavBarPosition.name.lowercase()}")
        cachedNavBarHeight = navBarHeightPx
        return navBarHeightPx
    }

    private fun getDotSize(): Int {
        // Return the actual layout size to account for the halo
        return (AppConstants.OVERLAY_LAYOUT_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }

    /**
     * No-op: Halo effect removed for simpler UI
     */
    fun setDragMode(enabled: Boolean) {
        // No halo effect - just keep the emoji button visible
    }
}