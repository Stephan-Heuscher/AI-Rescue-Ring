package ch.heuscher.back_home_dot.service.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
 * Handles the floating dot and rescue ring display.
 */
class OverlayViewManager(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private var floatingView: View? = null
    private var floatingDot: View? = null
    private var rescueRing: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var touchListener: View.OnTouchListener? = null

    /**
     * Creates and adds the overlay view to the window.
     */
    fun createOverlayView(): View {
        if (floatingView != null) return floatingView!!

        floatingView = LayoutInflater.from(context).inflate(R.layout.overlay_layout, null)
        floatingDot = floatingView?.findViewById<View>(R.id.floating_dot)
        rescueRing = floatingView?.findViewById<TextView>(R.id.rescue_ring)

        setupLayoutParams()
        windowManager.addView(floatingView, layoutParams)

        touchListener?.let { listener ->
            floatingView?.setOnTouchListener(listener)
            floatingDot?.setOnTouchListener(listener)
            rescueRing?.setOnTouchListener(listener)
        }

        return floatingView!!
    }

    /**
     * Removes the overlay view from the window.
     */
    fun removeOverlayView() {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        floatingDot = null
        rescueRing = null
        layoutParams = null
    }

    /**
     * Updates the overlay appearance based on settings.
     */
    fun updateAppearance(settings: OverlaySettings) {
        when (settings.getOverlayMode()) {
            OverlayMode.NORMAL -> showNormalDot(settings)
            OverlayMode.RESCUE_RING -> showRescueRing()
        }
    }

    /**
     * Updates the position of the overlay view.
     */
    fun updatePosition(position: DotPosition) {
        layoutParams?.let { params ->
            params.x = position.x
            params.y = position.y
            floatingView?.let { windowManager.updateViewLayout(it, params) }
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
     * Registers a touch listener for gesture detection on overlay elements.
     */
    fun setTouchListener(listener: View.OnTouchListener) {
        touchListener = listener
        floatingView?.setOnTouchListener(listener)
        floatingDot?.setOnTouchListener(listener)
        rescueRing?.setOnTouchListener(listener)
    }

    /**
     * Calculates constrained position within screen bounds.
     */
    fun constrainPositionToBounds(x: Int, y: Int): Pair<Int, Int> {
        val screenSize = getScreenSize()
        val dotSize = getDotSize()
        val constrainedX = x.coerceIn(0, screenSize.x - dotSize)
        val constrainedY = y.coerceIn(0, screenSize.y - dotSize)
        return Pair(constrainedX, constrainedY)
    }

    private fun setupLayoutParams() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private fun showNormalDot(settings: OverlaySettings) {
        floatingDot?.visibility = View.VISIBLE
        rescueRing?.visibility = View.GONE

        floatingDot?.let { dotView ->
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(settings.getColorWithAlpha())
                setStroke(
                    (AppConstants.DOT_STROKE_WIDTH_DP * context.resources.displayMetrics.density).toInt(),
                    android.graphics.Color.WHITE
                )
            }
            dotView.background = drawable
        }
    }

    private fun showRescueRing() {
        floatingDot?.visibility = View.GONE
        rescueRing?.visibility = View.VISIBLE
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
        return (AppConstants.DOT_SIZE_DP * context.resources.displayMetrics.density).toInt()
    }
}